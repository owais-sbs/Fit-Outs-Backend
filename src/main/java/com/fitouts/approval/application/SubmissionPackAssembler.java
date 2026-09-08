package com.fitouts.approval.application;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;

import com.fitouts.approval.domain.ApprovalCase;
import com.fitouts.approval.domain.CaseChecklistItem;
import com.fitouts.approval.domain.CompanyCompliance;
import com.fitouts.approval.domain.CompanyComplianceRepository;
import com.fitouts.approval.domain.DocumentType;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.projectdoc.domain.ProjectDocument;
import com.fitouts.projectdoc.domain.ProjectDocumentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Collects everything the system already holds for a case and packages it.
 *
 * <p>This is the single biggest time saving in the module: instead of a PRO chasing the same
 * trade licence and insurance certificates by email for every submission, the assembler pulls
 * them from the company compliance record and the project document library, and reports only
 * what is genuinely missing or expired.
 *
 * <p>The output is a zip containing an index and every attached file renamed to the
 * authority's convention. A true indexed PDF needs a PDF library the project does not carry
 * yet; the index here is a plain-text manifest inside the zip.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubmissionPackAssembler {

    private final CompanyComplianceRepository complianceRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final FileStorageService fileStorageService;

    /** What the collection pass found, before anything is zipped. */
    public record Collection(
            List<String> autoCollected,
            List<String> missing,
            List<String> expired,
            List<String> waived) {
    }

    /**
     * Fills in checklist items from documents already on file.
     *
     * <p>Company-category items come from the compliance register; everything else is matched
     * against the project document library by code, then by name. Items a person already
     * attached or a director already waived are left untouched.
     *
     * @return which items were filled, and which are still blocking
     */
    public Collection collect(ApprovalCase approvalCase,
                              List<CaseChecklistItem> items,
                              Map<String, DocumentType> documentTypes) {
        Map<String, CompanyCompliance> compliance = new LinkedHashMap<>();
        for (CompanyCompliance row : complianceRepository
                .findByCompanyIdOrderByDocumentTypeCodeAsc(approvalCase.getCompanyId())) {
            compliance.put(row.getDocumentTypeCode(), row);
        }
        List<ProjectDocument> projectDocs = projectDocumentRepository
                .findByProjectIdAndCompanyIdAndDeletedFalseOrderByCreatedAtDesc(
                        approvalCase.getProjectId(), approvalCase.getCompanyId());

        List<String> autoCollected = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> expired = new ArrayList<>();
        List<String> waived = new ArrayList<>();

        for (CaseChecklistItem item : items) {
            if ("WAIVED".equals(item.getStatus())) {
                waived.add(label(item));
                continue;
            }
            if ("NOT_APPLICABLE".equals(item.getStatus())) {
                continue;
            }

            if (item.getFilePath() == null || item.getFilePath().isBlank()) {
                CompanyCompliance held = compliance.get(item.getDocumentTypeCode());
                if (held != null && held.getFilePath() != null && !held.getFilePath().isBlank()) {
                    item.setFilePath(held.getFilePath());
                    item.setExpiryDate(held.getExpiryDate());
                    item.setSource("AUTO_COLLECTED");
                    autoCollected.add(label(item) + " (company register)");
                } else {
                    ProjectDocument matched = matchProjectDocument(
                            projectDocs, item.getDocumentTypeCode(),
                            documentTypes.get(item.getDocumentTypeCode()));
                    if (matched != null) {
                        item.setFilePath(matched.getFilePath());
                        item.setSource("AUTO_COLLECTED");
                        autoCollected.add(label(item) + " (project documents)");
                    }
                }
            }

            item.refreshStatus();
            if ("MISSING".equals(item.getStatus()) && item.isRequired()) {
                missing.add(label(item));
            } else if ("EXPIRED".equals(item.getStatus()) && item.isRequired()) {
                expired.add(label(item) + " (expired " + item.getExpiryDate() + ")");
            }
        }

        return new Collection(autoCollected, missing, expired, waived);
    }

    /**
     * Zips the attached documents with an index manifest.
     *
     * @param namingPattern how each file is named inside the zip; supports
     *                      {@code {caseNumber}}, {@code {docCode}} and {@code {safeName}}
     * @return the stored relative path of the zip
     */
    public String buildPack(ApprovalCase approvalCase,
                            List<CaseChecklistItem> items,
                            Collection collection,
                            String namingPattern) {
        String pattern = namingPattern != null && !namingPattern.isBlank()
                ? namingPattern
                : "{caseNumber}_{docCode}_{safeName}";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int attached = 0;
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            List<String> indexLines = new ArrayList<>();
            indexLines.add("SUBMISSION PACK");
            indexLines.add("Case: " + approvalCase.getCaseNumber());
            indexLines.add("Permit: " + approvalCase.getPermitTypeName() + " (" + approvalCase.getPermitTypeCode() + ")");
            indexLines.add("Authority: " + (approvalCase.getAuthorityName() != null
                    ? approvalCase.getAuthorityName() : "not resolved"));
            indexLines.add("Assembled: " + LocalDate.now());
            indexLines.add("");
            indexLines.add("CONTENTS");

            int index = 1;
            for (CaseChecklistItem item : items) {
                if (item.getFilePath() == null || item.getFilePath().isBlank()) continue;
                Optional<byte[]> content = fileStorageService.readBytes(item.getFilePath());
                if (content.isEmpty()) {
                    indexLines.add(String.format("%2d. %s — file recorded but unreadable (%s)",
                            index++, label(item), item.getFilePath()));
                    continue;
                }
                String entryName = renderName(pattern, approvalCase, item);
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(content.get());
                zip.closeEntry();
                indexLines.add(String.format("%2d. %s — %s", index++, label(item), entryName));
                attached++;
            }

            if (!collection.missing().isEmpty()) {
                indexLines.add("");
                indexLines.add("MISSING — the authority will reject the pack without these");
                collection.missing().forEach(m -> indexLines.add("  - " + m));
            }
            if (!collection.expired().isEmpty()) {
                indexLines.add("");
                indexLines.add("EXPIRED — renew before submitting");
                collection.expired().forEach(m -> indexLines.add("  - " + m));
            }
            if (!collection.waived().isEmpty()) {
                indexLines.add("");
                indexLines.add("WAIVED BY DIRECTOR");
                collection.waived().forEach(m -> indexLines.add("  - " + m));
            }

            zip.putNextEntry(new ZipEntry("00_INDEX.txt"));
            zip.write(String.join(System.lineSeparator(), indexLines).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to assemble submission pack: " + e.getMessage(), e);
        }

        log.info("Assembled pack for case {} with {} documents", approvalCase.getCaseNumber(), attached);
        return fileStorageService.storeBytes(
                buffer.toByteArray(),
                approvalCase.getCaseNumber() + "_pack.zip",
                approvalCase.getCompanyId(),
                approvalCase.getProjectId(),
                "approval-packs");
    }

    /**
     * Finds a project document for a checklist item. Matching is deliberately loose because
     * the document library is free text: the code in the title wins, then the document type
     * name, then the category.
     */
    private ProjectDocument matchProjectDocument(List<ProjectDocument> documents, String code, DocumentType type) {
        String lowerCode = code.toLowerCase(Locale.ROOT);
        for (ProjectDocument doc : documents) {
            String title = doc.getTitle() == null ? "" : doc.getTitle().toLowerCase(Locale.ROOT);
            if (title.contains(lowerCode)) return doc;
        }
        if (type == null || type.getName() == null) return null;

        String typeName = type.getName().toLowerCase(Locale.ROOT);
        for (ProjectDocument doc : documents) {
            String title = doc.getTitle() == null ? "" : doc.getTitle().toLowerCase(Locale.ROOT);
            if (title.contains(typeName) || typeName.contains(title) && title.length() > 6) return doc;
        }
        for (ProjectDocument doc : documents) {
            String category = doc.getCategory() == null ? "" : doc.getCategory().toLowerCase(Locale.ROOT);
            if (!category.isEmpty() && typeName.contains(category)) return doc;
        }
        return null;
    }

    private String renderName(String pattern, ApprovalCase approvalCase, CaseChecklistItem item) {
        String original = item.getFilePath();
        int slash = original.lastIndexOf('/');
        String base = slash >= 0 ? original.substring(slash + 1) : original;
        // Stored files carry a UUID prefix; strip it so the authority sees a readable name.
        int underscore = base.indexOf('_');
        if (underscore == 36) {
            base = base.substring(underscore + 1);
        }
        return pattern
                .replace("{caseNumber}", approvalCase.getCaseNumber())
                .replace("{docCode}", item.getDocumentTypeCode())
                .replace("{safeName}", base.replaceAll("[^a-zA-Z0-9._-]", "_"));
    }

    private String label(CaseChecklistItem item) {
        return item.getDocumentTypeCode() + " "
                + (item.getDocumentTypeName() != null ? item.getDocumentTypeName() : "");
    }
}
