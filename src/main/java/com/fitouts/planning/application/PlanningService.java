package com.fitouts.planning.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.planning.api.PlanningDecisionAuditResponse;
import com.fitouts.planning.api.PlanningGateConfigRequest;
import com.fitouts.planning.api.PlanningGateConfigResponse;
import com.fitouts.planning.api.PlanningStatusRequest;
import com.fitouts.planning.api.PlanningStatusResponse;
import com.fitouts.planning.domain.PlanAreaStatus;
import com.fitouts.planning.domain.PlanningDecisionAudit;
import com.fitouts.planning.domain.PlanningDecisionAuditRepository;
import com.fitouts.planning.domain.PlanningGateConfig;
import com.fitouts.planning.domain.PlanningGateConfigRepository;
import com.fitouts.planning.domain.ProjectPlanningStatus;
import com.fitouts.planning.domain.ProjectPlanningStatusRepository;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlanningService {

    private final ProjectPlanningStatusRepository repository;
    private final PlanningGateConfigRepository gateConfigRepository;
    private final PlanningDecisionAuditRepository auditRepository;
    private final ProjectService projectService;

    @Transactional(readOnly = true)
    public PlanningStatusResponse get(Long projectId) {
        Project project = projectService.getById(projectId);
        assertCompany(project);
        return toResponse(getOrCreate(project));
    }

    @Transactional
    public PlanningStatusResponse update(Long projectId, PlanningStatusRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = projectService.getById(projectId);
        assertCompany(project);
        ProjectPlanningStatus status = getOrCreate(project);
        UUID companyId = CompanyContext.get();
        List<PlanningDecisionAudit> audits = new ArrayList<>();

        if (request.getMaterialStatus() != null
                && request.getMaterialStatus() != status.getMaterialStatus()) {
            audits.add(audit(projectId, companyId, "MATERIAL_STATUS",
                    str(status.getMaterialStatus()), str(request.getMaterialStatus()),
                    principal.getAccountId()));
            status.setMaterialStatus(request.getMaterialStatus());
        }
        if (request.getResourceStatus() != null
                && request.getResourceStatus() != status.getResourceStatus()) {
            audits.add(audit(projectId, companyId, "RESOURCE_STATUS",
                    str(status.getResourceStatus()), str(request.getResourceStatus()),
                    principal.getAccountId()));
            status.setResourceStatus(request.getResourceStatus());
        }
        if (request.getLabourStatus() != null
                && request.getLabourStatus() != status.getLabourStatus()) {
            audits.add(audit(projectId, companyId, "LABOUR_STATUS",
                    str(status.getLabourStatus()), str(request.getLabourStatus()),
                    principal.getAccountId()));
            status.setLabourStatus(request.getLabourStatus());
        }
        if (request.getSubcontractorStatus() != null
                && request.getSubcontractorStatus() != status.getSubcontractorStatus()) {
            audits.add(audit(projectId, companyId, "SUBCONTRACTOR_STATUS",
                    str(status.getSubcontractorStatus()), str(request.getSubcontractorStatus()),
                    principal.getAccountId()));
            status.setSubcontractorStatus(request.getSubcontractorStatus());
        }

        if (request.getPlanningReady() != null
                && !Objects.equals(request.getPlanningReady(), status.isPlanningReady())) {
            audits.add(audit(projectId, companyId, "PLANNING_READY",
                    String.valueOf(status.isPlanningReady()), String.valueOf(request.getPlanningReady()),
                    principal.getAccountId()));
            status.setPlanningReady(request.getPlanningReady());
            // Wave A: planning ready unlocks gantt publish
            if (request.getPlanningReady() && !status.isGanttPublishAllowed()) {
                audits.add(audit(projectId, companyId, "GANTT_PUBLISH_ALLOWED",
                        "false", "true", principal.getAccountId()));
                status.setGanttPublishAllowed(true);
            }
        }
        if (request.getGanttPublishAllowed() != null && isAdmin(principal)
                && !Objects.equals(request.getGanttPublishAllowed(), status.isGanttPublishAllowed())) {
            audits.add(audit(projectId, companyId, "GANTT_PUBLISH_ALLOWED",
                    String.valueOf(status.isGanttPublishAllowed()),
                    String.valueOf(request.getGanttPublishAllowed()),
                    principal.getAccountId()));
            status.setGanttPublishAllowed(request.getGanttPublishAllowed());
        }

        status.setUpdatedBy(principal.getAccountId());
        ProjectPlanningStatus saved = repository.save(status);
        if (!audits.isEmpty()) {
            auditRepository.saveAll(audits);
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PlanningDecisionAuditResponse> listAudit(Long projectId) {
        Project project = projectService.getById(projectId);
        assertCompany(project);
        return auditRepository
                .findByProjectIdAndCompanyIdOrderByDecidedAtDesc(projectId, CompanyContext.get())
                .stream()
                .map(this::toAuditResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanningGateConfigResponse getGateConfig() {
        requireAdmin();
        UUID companyId = requireCompany();
        return toGateResponse(loadGateConfig(companyId));
    }

    @Transactional
    public PlanningGateConfigResponse updateGateConfig(PlanningGateConfigRequest request) {
        requireAdmin();
        UUID companyId = requireCompany();
        PlanningGateConfig config = gateConfigRepository.findByCompanyId(companyId)
                .orElseGet(() -> PlanningGateConfig.defaults(companyId));
        if (request != null) {
            if (request.getRequireMaterial() != null) config.setRequireMaterial(request.getRequireMaterial());
            if (request.getRequireResource() != null) config.setRequireResource(request.getRequireResource());
            if (request.getRequireLabour() != null) config.setRequireLabour(request.getRequireLabour());
            if (request.getRequireSubcontractor() != null) {
                config.setRequireSubcontractor(request.getRequireSubcontractor());
            }
            if (request.getRequirePlanningReady() != null) {
                config.setRequirePlanningReady(request.getRequirePlanningReady());
            }
        }
        return toGateResponse(gateConfigRepository.save(config));
    }

    @Transactional(readOnly = true)
    public void assertCanPublishGantt(Long projectId) {
        Project project = projectService.getById(projectId);
        assertCompany(project);
        ProjectPlanningStatus status = getOrCreate(project);
        PlanningGateConfig config = loadGateConfig(CompanyContext.get());

        assertRequiredArea(config.isRequireMaterial(), status.getMaterialStatus(), "material");
        assertRequiredArea(config.isRequireResource(), status.getResourceStatus(), "resource");
        assertRequiredArea(config.isRequireLabour(), status.getLabourStatus(), "labour");
        assertRequiredArea(config.isRequireSubcontractor(), status.getSubcontractorStatus(), "subcontractor");

        // Wave A primary unlock
        if (status.isPlanningReady()) {
            return;
        }
        // Wave B: admin override ganttPublishAllowed with required areas already satisfied above
        if (status.isGanttPublishAllowed()) {
            return;
        }
        if (config.isRequirePlanningReady()) {
            throw new BadRequestException("Planning is not ready — mark Planning ready before publishing the Gantt");
        }
    }

    @Transactional
    public void syncMaterialStatus(Long projectId, PlanAreaStatus materialStatus, Long updatedBy) {
        Project project = projectService.getById(projectId);
        assertCompany(project);
        ProjectPlanningStatus status = getOrCreate(project);
        if (status.getMaterialStatus() != materialStatus) {
            auditRepository.save(audit(projectId, CompanyContext.get(), "MATERIAL_STATUS",
                    str(status.getMaterialStatus()), str(materialStatus), updatedBy));
            status.setMaterialStatus(materialStatus);
        }
        status.setUpdatedBy(updatedBy);
        repository.save(status);
    }

    @Transactional
    public void syncSubcontractorStatus(Long projectId, PlanAreaStatus subcontractorStatus, Long updatedBy) {
        Project project = projectService.getById(projectId);
        assertCompany(project);
        ProjectPlanningStatus status = getOrCreate(project);
        if (status.getSubcontractorStatus() != subcontractorStatus) {
            auditRepository.save(audit(projectId, CompanyContext.get(), "SUBCONTRACTOR_STATUS",
                    str(status.getSubcontractorStatus()), str(subcontractorStatus), updatedBy));
            status.setSubcontractorStatus(subcontractorStatus);
        }
        status.setUpdatedBy(updatedBy);
        repository.save(status);
    }

    @Transactional
    public void syncLabourAndResourceStatus(Long projectId, PlanAreaStatus areaStatus, Long updatedBy) {
        Project project = projectService.getById(projectId);
        assertCompany(project);
        ProjectPlanningStatus status = getOrCreate(project);
        UUID companyId = CompanyContext.get();
        // Only auto-advance to IN_PROGRESS; leave READY to planning hub PUT
        if (areaStatus == PlanAreaStatus.IN_PROGRESS) {
            if (status.getLabourStatus() != PlanAreaStatus.READY
                    && status.getLabourStatus() != PlanAreaStatus.IN_PROGRESS) {
                auditRepository.save(audit(projectId, companyId, "LABOUR_STATUS",
                        str(status.getLabourStatus()), str(PlanAreaStatus.IN_PROGRESS), updatedBy));
                status.setLabourStatus(PlanAreaStatus.IN_PROGRESS);
            }
            if (status.getResourceStatus() != PlanAreaStatus.READY
                    && status.getResourceStatus() != PlanAreaStatus.IN_PROGRESS) {
                auditRepository.save(audit(projectId, companyId, "RESOURCE_STATUS",
                        str(status.getResourceStatus()), str(PlanAreaStatus.IN_PROGRESS), updatedBy));
                status.setResourceStatus(PlanAreaStatus.IN_PROGRESS);
            }
        } else if (areaStatus == PlanAreaStatus.NOT_STARTED) {
            if (status.getLabourStatus() == PlanAreaStatus.IN_PROGRESS) {
                auditRepository.save(audit(projectId, companyId, "LABOUR_STATUS",
                        str(status.getLabourStatus()), str(PlanAreaStatus.NOT_STARTED), updatedBy));
                status.setLabourStatus(PlanAreaStatus.NOT_STARTED);
            }
            if (status.getResourceStatus() == PlanAreaStatus.IN_PROGRESS) {
                auditRepository.save(audit(projectId, companyId, "RESOURCE_STATUS",
                        str(status.getResourceStatus()), str(PlanAreaStatus.NOT_STARTED), updatedBy));
                status.setResourceStatus(PlanAreaStatus.NOT_STARTED);
            }
        }
        status.setUpdatedBy(updatedBy);
        repository.save(status);
    }

    private void assertRequiredArea(boolean required, PlanAreaStatus areaStatus, String areaName) {
        if (required && !isReadyOrNotRequired(areaStatus)) {
            throw new BadRequestException(
                    "Planning gate: " + areaName + " must be READY or NOT_REQUIRED before publishing the Gantt");
        }
    }

    private PlanningGateConfig loadGateConfig(UUID companyId) {
        return gateConfigRepository.findByCompanyId(companyId)
                .orElseGet(() -> PlanningGateConfig.defaults(companyId));
    }

    private boolean isReadyOrNotRequired(PlanAreaStatus s) {
        return s == PlanAreaStatus.READY || s == PlanAreaStatus.NOT_REQUIRED;
    }

    private ProjectPlanningStatus getOrCreate(Project project) {
        UUID companyId = CompanyContext.get();
        return repository.findByProjectIdAndCompanyId(project.getId(), companyId)
                .orElseGet(() -> {
                    ProjectPlanningStatus created = new ProjectPlanningStatus();
                    created.setProjectId(project.getId());
                    created.setCompanyId(companyId);
                    created.setMaterialStatus(PlanAreaStatus.NOT_REQUIRED);
                    created.setResourceStatus(PlanAreaStatus.NOT_REQUIRED);
                    created.setLabourStatus(PlanAreaStatus.NOT_REQUIRED);
                    created.setSubcontractorStatus(PlanAreaStatus.NOT_REQUIRED);
                    return repository.save(created);
                });
    }

    private PlanningDecisionAudit audit(
            Long projectId, UUID companyId, String type, String from, String to, Long decidedBy) {
        PlanningDecisionAudit row = new PlanningDecisionAudit();
        row.setProjectId(projectId);
        row.setCompanyId(companyId);
        row.setDecisionType(type);
        row.setFromValue(from);
        row.setToValue(to);
        row.setDecidedBy(decidedBy);
        return row;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private PlanningStatusResponse toResponse(ProjectPlanningStatus s) {
        return PlanningStatusResponse.builder()
                .projectId(s.getProjectId())
                .companyId(s.getCompanyId())
                .materialStatus(s.getMaterialStatus())
                .resourceStatus(s.getResourceStatus())
                .labourStatus(s.getLabourStatus())
                .subcontractorStatus(s.getSubcontractorStatus())
                .planningReady(s.isPlanningReady())
                .ganttPublishAllowed(s.isGanttPublishAllowed() || s.isPlanningReady())
                .updatedBy(s.getUpdatedBy())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private PlanningGateConfigResponse toGateResponse(PlanningGateConfig c) {
        return PlanningGateConfigResponse.builder()
                .companyId(c.getCompanyId())
                .requireMaterial(c.isRequireMaterial())
                .requireResource(c.isRequireResource())
                .requireLabour(c.isRequireLabour())
                .requireSubcontractor(c.isRequireSubcontractor())
                .requirePlanningReady(c.isRequirePlanningReady())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private PlanningDecisionAuditResponse toAuditResponse(PlanningDecisionAudit a) {
        return PlanningDecisionAuditResponse.builder()
                .uuid(a.getUuid())
                .projectId(a.getProjectId())
                .companyId(a.getCompanyId())
                .decisionType(a.getDecisionType())
                .fromValue(a.getFromValue())
                .toValue(a.getToValue())
                .decidedBy(a.getDecidedBy())
                .decidedAt(a.getDecidedAt())
                .notes(a.getNotes())
                .build();
    }

    private void assertCompany(Project project) {
        UUID companyId = CompanyContext.get();
        if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        return companyId;
    }

    private AuthPrincipal requireStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private AuthPrincipal requireAdmin() {
        AuthPrincipal principal = requireStaff();
        if (!isAdmin(principal)) {
            throw new ForbiddenException("Admin access required");
        }
        return principal;
    }

    private boolean isAdmin(AuthPrincipal principal) {
        return principal.getRoles() != null && (
                principal.getRoles().contains(Role.ADMIN)
                        || principal.getRoles().contains(Role.SUPER_ADMIN)
                        || principal.getRoles().contains(Role.BUSINESS_OWNER));
    }
}
