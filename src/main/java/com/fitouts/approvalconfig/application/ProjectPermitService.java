package com.fitouts.approvalconfig.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.approvalconfig.api.PermitCaseStatusRequest;
import com.fitouts.approvalconfig.api.PermitDocumentStatusRequest;
import com.fitouts.approvalconfig.api.ProjectPermitCaseResponse;
import com.fitouts.approvalconfig.api.ProjectPermitDocumentResponse;
import com.fitouts.approvalconfig.domain.ApprovalAuthority;
import com.fitouts.approvalconfig.domain.ApprovalAuthorityRepository;
import com.fitouts.approvalconfig.domain.ApprovalDocumentType;
import com.fitouts.approvalconfig.domain.ApprovalDocumentTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitType;
import com.fitouts.approvalconfig.domain.ApprovalPermitTypeRepository;
import com.fitouts.approvalconfig.domain.JurisdictionPack;
import com.fitouts.approvalconfig.domain.JurisdictionPackDocument;
import com.fitouts.approvalconfig.domain.JurisdictionPackDocumentRepository;
import com.fitouts.approvalconfig.domain.JurisdictionPackPermit;
import com.fitouts.approvalconfig.domain.JurisdictionPackPermitRepository;
import com.fitouts.approvalconfig.domain.JurisdictionPackRepository;
import com.fitouts.approvalconfig.domain.ProjectPermitCase;
import com.fitouts.approvalconfig.domain.ProjectPermitCaseRepository;
import com.fitouts.approvalconfig.domain.ProjectPermitDocument;
import com.fitouts.approvalconfig.domain.ProjectPermitDocumentRepository;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectPermitService {

    private static final Set<String> CASE_STATUSES = Set.of(
            "Not started", "In progress", "Submitted", "Approved", "Rejected", "Not required");
    private static final Set<String> DOC_STATUSES = Set.of("Pending", "Received", "Not required");

    private final ApprovalConfigSeedService seedService;
    private final JurisdictionPackRepository packRepository;
    private final JurisdictionPackPermitRepository packPermitRepository;
    private final JurisdictionPackDocumentRepository packDocumentRepository;
    private final ApprovalPermitTypeRepository permitTypeRepository;
    private final ApprovalAuthorityRepository authorityRepository;
    private final ApprovalDocumentTypeRepository documentTypeRepository;
    private final ProjectPermitCaseRepository caseRepository;
    private final ProjectPermitDocumentRepository caseDocumentRepository;

    @Transactional
    public void instantiateForProject(Project project) {
        if (project == null || project.getId() == null || project.getJurisdictionPackId() == null) {
            return;
        }
        if (caseRepository.existsByProjectId(project.getId())) {
            return;
        }
        UUID companyId = project.getCompanyId();
        seedService.ensureSeeded(companyId);
        JurisdictionPack pack = packRepository.findByIdAndDeletedFalse(project.getJurisdictionPackId())
                .orElseThrow(() -> new NotFoundException("Jurisdiction pack not found"));
        Map<UUID, ApprovalPermitType> permits = permitTypeRepository
                .findByCompanyIdAndDeletedFalseOrderByPermitCodeAsc(companyId).stream()
                .collect(Collectors.toMap(ApprovalPermitType::getId, Function.identity()));

        int sort = 0;
        for (JurisdictionPackPermit line : packPermitRepository.findByPackIdOrderBySortOrderAsc(pack.getId())) {
            if (!include(line.getInclusionRule(), project)) {
                continue;
            }
            ApprovalPermitType permit = permits.get(line.getPermitTypeId());
            ProjectPermitCase permitCase = new ProjectPermitCase();
            permitCase.setProjectId(project.getId());
            permitCase.setCompanyId(companyId);
            permitCase.setPermitTypeId(line.getPermitTypeId());
            permitCase.setIssuingAuthorityId(line.getIssuingAuthorityId());
            permitCase.setStatus("Not started");
            permitCase.setSlaWorkingDays(permit != null ? permit.getSlaWorkingDays() : null);
            permitCase.setBlocksActivities(permit != null ? permit.getBlocksActivities() : null);
            permitCase.setInclusionRule(line.getInclusionRule());
            permitCase.setSortOrder(sort++);
            permitCase = caseRepository.save(permitCase);
            for (JurisdictionPackDocument doc : packDocumentRepository.findByPackPermitId(line.getId())) {
                ProjectPermitDocument caseDoc = new ProjectPermitDocument();
                caseDoc.setCaseId(permitCase.getId());
                caseDoc.setDocumentTypeId(doc.getDocumentTypeId());
                caseDoc.setStatus("Pending");
                caseDocumentRepository.save(caseDoc);
            }
        }
    }

    @Transactional
    public List<ProjectPermitCaseResponse> listForProject(Long projectId, UUID companyId) {
        seedService.ensureSeeded(companyId);
        List<ProjectPermitCase> cases = caseRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        if (cases.isEmpty()) {
            return List.of();
        }
        Map<UUID, ApprovalPermitType> permits = permitTypeRepository
                .findByCompanyIdAndDeletedFalseOrderByPermitCodeAsc(companyId).stream()
                .collect(Collectors.toMap(ApprovalPermitType::getId, Function.identity()));
        Map<UUID, ApprovalAuthority> authorities = authorityRepository
                .findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .collect(Collectors.toMap(ApprovalAuthority::getId, Function.identity()));
        Map<UUID, ApprovalDocumentType> documents = documentTypeRepository
                .findByCompanyIdAndDeletedFalseOrderByDocCodeAsc(companyId).stream()
                .collect(Collectors.toMap(ApprovalDocumentType::getId, Function.identity()));
        List<UUID> caseIds = cases.stream().map(ProjectPermitCase::getId).toList();
        Map<UUID, List<ProjectPermitDocument>> docsByCase = caseDocumentRepository.findByCaseIdIn(caseIds).stream()
                .collect(Collectors.groupingBy(ProjectPermitDocument::getCaseId));

        List<ProjectPermitCaseResponse> responses = new ArrayList<>();
        for (ProjectPermitCase permitCase : cases) {
            ApprovalPermitType permit = permits.get(permitCase.getPermitTypeId());
            ApprovalAuthority issuer = permitCase.getIssuingAuthorityId() != null
                    ? authorities.get(permitCase.getIssuingAuthorityId())
                    : null;
            List<ProjectPermitDocumentResponse> docs = docsByCase
                    .getOrDefault(permitCase.getId(), List.of()).stream()
                    .map(doc -> {
                        ApprovalDocumentType type = documents.get(doc.getDocumentTypeId());
                        return ProjectPermitDocumentResponse.builder()
                                .id(doc.getId())
                                .documentTypeId(doc.getDocumentTypeId())
                                .docCode(type != null ? type.getDocCode() : null)
                                .documentName(type != null ? type.getName() : null)
                                .category(type != null ? type.getCategory() : null)
                                .sourceOwner(type != null ? type.getSourceOwner() : null)
                                .expiryTracked(type != null && type.isExpiryTracked())
                                .status(doc.getStatus())
                                .build();
                    })
                    .toList();
            responses.add(ProjectPermitCaseResponse.builder()
                    .id(permitCase.getId())
                    .permitTypeId(permitCase.getPermitTypeId())
                    .permitCode(permit != null ? permit.getPermitCode() : null)
                    .permitName(permit != null ? permit.getName() : null)
                    .issuingAuthorityId(permitCase.getIssuingAuthorityId())
                    .issuingAuthorityName(issuer != null ? issuer.getName() : null)
                    .status(permitCase.getStatus())
                    .slaWorkingDays(permitCase.getSlaWorkingDays())
                    .blocksActivities(permitCase.getBlocksActivities())
                    .inclusionRule(permitCase.getInclusionRule())
                    .sortOrder(permitCase.getSortOrder())
                    .notes(permitCase.getNotes())
                    .documents(docs)
                    .build());
        }
        return responses;
    }

    @Transactional
    public ProjectPermitCaseResponse updateCase(UUID caseId, PermitCaseStatusRequest request) {
        UUID companyId = CompanyContext.get();
        ProjectPermitCase permitCase = caseRepository.findByIdAndCompanyId(caseId, companyId)
                .orElseThrow(() -> new NotFoundException("Permit case not found"));
        if (StringUtils.hasText(request.getStatus())) {
            if (!CASE_STATUSES.contains(request.getStatus())) {
                throw new IllegalArgumentException("Invalid case status");
            }
            permitCase.setStatus(request.getStatus());
        }
        if (request.getNotes() != null) {
            permitCase.setNotes(request.getNotes());
        }
        caseRepository.save(permitCase);
        return listForProject(permitCase.getProjectId(), companyId).stream()
                .filter(item -> item.getId().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Permit case not found"));
    }

    @Transactional
    public void updateDocument(UUID documentId, PermitDocumentStatusRequest request) {
        ProjectPermitDocument document = caseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Permit document not found"));
        ProjectPermitCase permitCase = caseRepository.findByIdAndCompanyId(document.getCaseId(), CompanyContext.get())
                .orElseThrow(() -> new NotFoundException("Permit document not found"));
        if (!StringUtils.hasText(request.getStatus()) || !DOC_STATUSES.contains(request.getStatus())) {
            throw new IllegalArgumentException("Invalid document status");
        }
        document.setStatus(request.getStatus());
        caseDocumentRepository.save(document);
        permitCase.getId();
    }

    private boolean include(String rule, Project project) {
        if (rule == null || "ALWAYS".equals(rule)) {
            return true;
        }
        return switch (rule) {
            case "OPTIONAL_KITCHEN" -> project.isApprovalScopeKitchen();
            case "OPTIONAL_SIRA" -> project.isApprovalScopeCctv();
            case "OPTIONAL_RTA" -> project.isApprovalScopeRta();
            case "OPTIONAL_DEMO" -> project.isApprovalScopeDemo();
            case "OPTIONAL_LOAD" -> project.isApprovalScopeLoad();
            default -> true;
        };
    }
}
