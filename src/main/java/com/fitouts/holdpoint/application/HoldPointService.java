package com.fitouts.holdpoint.application;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.holdpoint.api.HoldPointRequest;
import com.fitouts.holdpoint.api.HoldPointResponse;
import com.fitouts.holdpoint.api.QualityTemplateRequest;
import com.fitouts.holdpoint.api.QualityTemplateResponse;
import com.fitouts.holdpoint.domain.ActivityQualityTemplate;
import com.fitouts.holdpoint.domain.ActivityQualityTemplateRepository;
import com.fitouts.holdpoint.domain.HoldPointStatus;
import com.fitouts.holdpoint.domain.QualityHoldPoint;
import com.fitouts.holdpoint.domain.QualityHoldPointRepository;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HoldPointService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final QualityHoldPointRepository holdPointRepository;
    private final ActivityQualityTemplateRepository templateRepository;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<HoldPointResponse> list(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        return holdPointRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), CompanyContext.get())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public HoldPointResponse get(Long projectId, UUID uuid) {
        requireStaff();
        requireProject(projectId);
        return toResponse(requireHoldPoint(uuid, projectId));
    }

    @Transactional
    public HoldPointResponse create(Long projectId, HoldPointRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new BadRequestException("title is required");
        }

        QualityHoldPoint hp = new QualityHoldPoint();
        hp.setProjectId(project.getId());
        hp.setCompanyId(CompanyContext.get());
        hp.setActivityUuid(request.getActivityUuid());
        hp.setTitle(request.getTitle().trim());
        hp.setStatus(request.getStatus() != null ? request.getStatus() : HoldPointStatus.OPEN);
        hp.setChecklistJson(resolveChecklistJson(request));
        hp.setActivityType(trimToNull(request.getActivityType()));
        hp.setNotes(request.getNotes());
        hp.setCreatedBy(principal.getAccountId());
        return toResponse(holdPointRepository.save(hp));
    }

    @Transactional
    public HoldPointResponse update(Long projectId, UUID uuid, HoldPointRequest request) {
        requireStaff();
        requireProject(projectId);
        QualityHoldPoint hp = requireHoldPoint(uuid, projectId);
        if (request == null) {
            return toResponse(hp);
        }
        if (StringUtils.hasText(request.getTitle())) {
            hp.setTitle(request.getTitle().trim());
        }
        if (request.getActivityUuid() != null) {
            hp.setActivityUuid(request.getActivityUuid());
        }
        if (request.getChecklistItems() != null || request.getChecklistJson() != null) {
            hp.setChecklistJson(resolveChecklistJson(request));
        }
        if (request.getActivityType() != null) {
            hp.setActivityType(trimToNull(request.getActivityType()));
        }
        if (request.getNotes() != null) {
            hp.setNotes(request.getNotes());
        }
        if (request.getStatus() != null) {
            hp.setStatus(request.getStatus());
        }
        return toResponse(holdPointRepository.save(hp));
    }

    @Transactional
    public void delete(Long projectId, UUID uuid) {
        requireStaff();
        requireProject(projectId);
        holdPointRepository.delete(requireHoldPoint(uuid, projectId));
    }

    @Transactional
    public HoldPointResponse clear(Long projectId, UUID uuid) {
        AuthPrincipal principal = requireStaff();
        requireProject(projectId);
        QualityHoldPoint hp = requireHoldPoint(uuid, projectId);
        hp.setStatus(HoldPointStatus.CLEARED);
        hp.setDecidedBy(principal.getAccountId());
        return toResponse(holdPointRepository.save(hp));
    }

    @Transactional
    public HoldPointResponse hold(Long projectId, UUID uuid) {
        AuthPrincipal principal = requireStaff();
        requireProject(projectId);
        QualityHoldPoint hp = requireHoldPoint(uuid, projectId);
        hp.setStatus(HoldPointStatus.HELD);
        hp.setDecidedBy(principal.getAccountId());
        return toResponse(holdPointRepository.save(hp));
    }

    @Transactional(readOnly = true)
    public QualityTemplateResponse getTemplate(String activityType) {
        requireAdmin();
        UUID companyId = requireCompany();
        String type = requireActivityType(activityType);
        return templateRepository.findByCompanyIdAndActivityType(companyId, type)
                .map(this::toTemplateResponse)
                .orElseGet(() -> QualityTemplateResponse.builder()
                        .companyId(companyId)
                        .activityType(type)
                        .checklistJson("[]")
                        .checklistItems(List.of())
                        .updatedAt(null)
                        .build());
    }

    @Transactional
    public QualityTemplateResponse putTemplate(String activityType, QualityTemplateRequest request) {
        requireAdmin();
        UUID companyId = requireCompany();
        String type = requireActivityType(activityType);
        ActivityQualityTemplate template = templateRepository.findByCompanyIdAndActivityType(companyId, type)
                .orElseGet(() -> {
                    ActivityQualityTemplate t = new ActivityQualityTemplate();
                    t.setCompanyId(companyId);
                    t.setActivityType(type);
                    return t;
                });
        template.setChecklistJson(resolveTemplateChecklistJson(request));
        return toTemplateResponse(templateRepository.save(template));
    }

    private String resolveChecklistJson(HoldPointRequest request) {
        if (request.getChecklistItems() != null) {
            return writeChecklist(request.getChecklistItems());
        }
        if (StringUtils.hasText(request.getChecklistJson())) {
            return request.getChecklistJson().trim();
        }
        return null;
    }

    private String resolveTemplateChecklistJson(QualityTemplateRequest request) {
        if (request != null && request.getChecklistItems() != null) {
            return writeChecklist(request.getChecklistItems());
        }
        if (request != null && StringUtils.hasText(request.getChecklistJson())) {
            return request.getChecklistJson().trim();
        }
        return "[]";
    }

    private String writeChecklist(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items != null ? items : List.of());
        } catch (Exception e) {
            throw new BadRequestException("Invalid checklistItems");
        }
    }

    private List<String> parseChecklist(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> items = objectMapper.readValue(json, STRING_LIST);
            return items != null ? items : List.of();
        } catch (Exception e) {
            return Collections.singletonList(json);
        }
    }

    private QualityHoldPoint requireHoldPoint(UUID uuid, Long projectId) {
        QualityHoldPoint hp = holdPointRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Hold point not found"));
        if (!hp.getProjectId().equals(projectId)) {
            throw new BadRequestException("Hold point does not belong to this project");
        }
        return hp;
    }

    private HoldPointResponse toResponse(QualityHoldPoint hp) {
        return HoldPointResponse.builder()
                .uuid(hp.getUuid())
                .projectId(hp.getProjectId())
                .companyId(hp.getCompanyId())
                .activityUuid(hp.getActivityUuid())
                .title(hp.getTitle())
                .status(hp.getStatus())
                .checklistJson(hp.getChecklistJson())
                .checklistItems(parseChecklist(hp.getChecklistJson()))
                .activityType(hp.getActivityType())
                .notes(hp.getNotes())
                .createdBy(hp.getCreatedBy())
                .decidedBy(hp.getDecidedBy())
                .createdAt(hp.getCreatedAt())
                .updatedAt(hp.getUpdatedAt())
                .build();
    }

    private QualityTemplateResponse toTemplateResponse(ActivityQualityTemplate t) {
        return QualityTemplateResponse.builder()
                .companyId(t.getCompanyId())
                .activityType(t.getActivityType())
                .checklistJson(t.getChecklistJson())
                .checklistItems(parseChecklist(t.getChecklistJson()))
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        UUID companyId = CompanyContext.get();
        if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }
        return project;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        return companyId;
    }

    private static String requireActivityType(String activityType) {
        if (!StringUtils.hasText(activityType)) {
            throw new BadRequestException("activityType is required");
        }
        return activityType.trim();
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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
        if (principal.getRoles() == null || !(
                principal.getRoles().contains(Role.ADMIN)
                        || principal.getRoles().contains(Role.SUPER_ADMIN)
                        || principal.getRoles().contains(Role.BUSINESS_OWNER))) {
            throw new ForbiddenException("Admin access required");
        }
        return principal;
    }
}
