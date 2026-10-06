package com.fitouts.approval.application;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.approval.api.CompanyComplianceRequest;
import com.fitouts.approval.api.CompanyComplianceResponse;
import com.fitouts.approval.domain.ApprovalCase;
import com.fitouts.approval.domain.ApprovalCaseRepository;
import com.fitouts.approval.domain.ApprovalCaseStatus;
import com.fitouts.approval.domain.CaseChecklistItem;
import com.fitouts.approval.domain.CaseChecklistItemRepository;
import com.fitouts.approval.domain.CompanyCompliance;
import com.fitouts.approval.domain.CompanyComplianceRepository;
import com.fitouts.approvalconfig.domain.ApprovalDocumentType;
import com.fitouts.approvalconfig.domain.ApprovalDocumentTypeRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Company and insurance files are uploaded once on the approvals-config document catalogue
 * and copied onto permit checklists. A file attached on a specific permit stays put.
 */
@Service
@RequiredArgsConstructor
public class CompanyDocumentMasterService {

    private final ApprovalDocumentTypeRepository documentTypeRepository;
    private final CompanyComplianceRepository complianceRepository;
    private final CaseChecklistItemRepository checklistRepository;
    private final ApprovalCaseRepository caseRepository;
    private final ApprovalLibraryService libraryService;
    private final FileStorageService fileStorageService;

    public static boolean companyHeld(String category) {
        if (category == null) return false;
        String value = category.trim();
        return value.equalsIgnoreCase("Company") || value.equalsIgnoreCase("Insurance");
    }

    @Transactional(readOnly = true)
    public Map<String, String> categoriesByCode(UUID companyId) {
        Map<String, String> categories = new LinkedHashMap<>();
        for (ApprovalDocumentType type : documentTypeRepository
                .findByCompanyIdAndDeletedFalseOrderByDocCodeAsc(companyId)) {
            if (type.getDocCode() == null) continue;
            categories.put(type.getDocCode().trim().toUpperCase(Locale.ROOT), type.getCategory());
        }
        return categories;
    }

    @Transactional
    public CompanyComplianceResponse saveRegister(UUID documentTypeId, LocalDate expiryDate,
                                                  LocalDate issueDate, String referenceNo) {
        requireStaff();
        ApprovalDocumentType type = requireCompanyHeld(documentTypeId);
        CompanyComplianceRequest request = new CompanyComplianceRequest();
        request.setDocumentTypeCode(type.getDocCode());
        request.setExpiryDate(expiryDate);
        request.setIssueDate(issueDate);
        request.setReferenceNo(referenceNo);
        CompanyComplianceResponse saved = libraryService.upsertCompanyCompliance(request);
        copyOntoOpenChecklists(requireCompany(), saved.getDocumentTypeCode(), saved.getFilePath(), saved.getExpiryDate());
        return saved;
    }

    @Transactional
    public CompanyComplianceResponse upload(UUID documentTypeId, MultipartFile file,
                                            LocalDate expiryDate, LocalDate issueDate, String referenceNo) {
        requireStaff();
        UUID companyId = requireCompany();
        ApprovalDocumentType type = requireCompanyHeld(documentTypeId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required");
        }

        String path = fileStorageService.store(file, companyId, 0L, "approvals-master");
        CompanyComplianceRequest request = new CompanyComplianceRequest();
        request.setDocumentTypeCode(type.getDocCode());
        request.setFilePath(path);
        request.setExpiryDate(expiryDate);
        request.setIssueDate(issueDate);
        request.setReferenceNo(referenceNo);
        CompanyComplianceResponse saved = libraryService.upsertCompanyCompliance(request);
        copyOntoOpenChecklists(companyId, saved.getDocumentTypeCode(), saved.getFilePath(), saved.getExpiryDate());
        return saved;
    }

    /**
     * Fills empty company and insurance checklist rows from the register.
     * Rows a person uploaded on the permit are left unchanged.
     */
    @Transactional
    public void applyHeldFiles(UUID companyId, List<CaseChecklistItem> items) {
        if (companyId == null || items == null || items.isEmpty()) return;
        Map<String, ApprovalDocumentType> types = documentTypeRepository
                .findByCompanyIdAndDeletedFalseOrderByDocCodeAsc(companyId).stream()
                .filter(type -> type.getDocCode() != null)
                .collect(Collectors.toMap(
                        type -> type.getDocCode().trim().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, CompanyCompliance> held = heldByCode(companyId);
        Set<UUID> affected = new HashSet<>();
        boolean changed = false;
        for (CaseChecklistItem item : items) {
            if (item.getFilePath() != null && !item.getFilePath().isBlank()) continue;
            if ("UPLOADED".equals(item.getSource())) continue;
            if ("WAIVED".equals(item.getStatus()) || "NOT_APPLICABLE".equals(item.getStatus())) continue;
            String code = normalize(item.getDocumentTypeCode());
            ApprovalDocumentType type = types.get(code);
            if (type == null || !companyHeld(type.getCategory())) continue;
            CompanyCompliance row = held.get(code);
            if (row == null || row.getFilePath() == null || row.getFilePath().isBlank()) continue;
            item.setFilePath(row.getFilePath());
            item.setExpiryDate(row.getExpiryDate());
            item.setSource("AUTO_COLLECTED");
            item.refreshStatus();
            affected.add(item.getCaseUuid());
            changed = true;
        }
        if (!changed) return;
        checklistRepository.saveAll(items);
        refreshPreparation(companyId, affected);
    }

    private void copyOntoOpenChecklists(UUID companyId, String documentTypeCode, String filePath, LocalDate expiryDate) {
        if (filePath == null || filePath.isBlank() || documentTypeCode == null) return;
        List<CaseChecklistItem> items = checklistRepository.findByCompanyAndDocumentTypeCode(companyId, documentTypeCode);
        Set<UUID> affected = new HashSet<>();
        for (CaseChecklistItem item : items) {
            if ("UPLOADED".equals(item.getSource())) continue;
            if ("WAIVED".equals(item.getStatus()) || "NOT_APPLICABLE".equals(item.getStatus())) continue;
            boolean empty = item.getFilePath() == null || item.getFilePath().isBlank();
            boolean auto = "AUTO_COLLECTED".equals(item.getSource());
            if (!empty && !auto) continue;
            item.setFilePath(filePath);
            item.setExpiryDate(expiryDate);
            item.setSource("AUTO_COLLECTED");
            item.refreshStatus();
            affected.add(item.getCaseUuid());
        }
        if (affected.isEmpty()) return;
        checklistRepository.saveAll(items);
        refreshPreparation(companyId, affected);
    }

    private void refreshPreparation(UUID companyId, Set<UUID> caseUuids) {
        for (UUID caseUuid : caseUuids) {
            ApprovalCase approvalCase = caseRepository.findByUuidAndCompanyId(caseUuid, companyId).orElse(null);
            if (approvalCase == null) continue;
            ApprovalCaseStatus current = approvalCase.getStatus();
            boolean preparing = current == ApprovalCaseStatus.NOT_STARTED
                    || current == ApprovalCaseStatus.PACK_IN_PREPARATION
                    || current == ApprovalCaseStatus.READY_TO_SUBMIT;
            if (!preparing) continue;
            List<CaseChecklistItem> items = checklistRepository
                    .findByCaseUuidOrderBySortOrderAscDocumentTypeCodeAsc(caseUuid);
            items.forEach(CaseChecklistItem::refreshStatus);
            boolean complete = items.stream().noneMatch(CaseChecklistItem::isBlocking);
            ApprovalCaseStatus next = complete
                    ? ApprovalCaseStatus.READY_TO_SUBMIT
                    : ApprovalCaseStatus.PACK_IN_PREPARATION;
            if (next != current) {
                approvalCase.setStatus(next);
                caseRepository.save(approvalCase);
            }
        }
    }

    private Map<String, CompanyCompliance> heldByCode(UUID companyId) {
        Map<String, CompanyCompliance> held = new LinkedHashMap<>();
        for (CompanyCompliance row : complianceRepository.findByCompanyIdOrderByDocumentTypeCodeAsc(companyId)) {
            held.put(normalize(row.getDocumentTypeCode()), row);
        }
        return held;
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private ApprovalDocumentType requireCompanyHeld(UUID documentTypeId) {
        UUID companyId = requireCompany();
        ApprovalDocumentType type = documentTypeRepository.findByIdAndDeletedFalse(documentTypeId)
                .orElseThrow(() -> new NotFoundException("Document type not found"));
        if (!companyId.equals(type.getCompanyId())) {
            throw new ForbiddenException("Document type not in your company");
        }
        if (!companyHeld(type.getCategory())) {
            throw new BadRequestException("This document is attached on the project permit, not the company register.");
        }
        return type;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        return companyId;
    }

    private void requireStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(role -> role == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
    }
}
