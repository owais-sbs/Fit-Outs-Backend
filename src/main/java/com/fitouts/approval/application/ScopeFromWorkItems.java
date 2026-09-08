package com.fitouts.approval.application;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.approval.api.DerivedScopeTagView;
import com.fitouts.approval.api.ProjectScopeToggles;
import com.fitouts.approvalconfig.domain.ApprovalScopeTag;
import com.fitouts.boq.application.BoqProjectRules;
import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqLine;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.qto.domain.QtoLine;
import com.fitouts.workitemconfiguration.domain.WorkItem;
import com.fitouts.workitemconfiguration.domain.WorkItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * Builds the project's permit-triggering scope from tags on work items that appear on the
 * latest approved or final BOQ. Empty tags mean only ALWAYS and location-based permits apply.
 *
 * <p>Many BOQ lines never persist {@code work_item_id}; they only store a description that
 * matches a catalog name (same as the material plan). Those lines still count.
 */
@Service
@RequiredArgsConstructor
public class ScopeFromWorkItems {

    static final String NO_APPROVED_BOQ =
            "No approved BOQ. Only community, access and completion permits apply until work items are on an approved BOQ.";
    static final String NO_LINKED_WORK_ITEMS =
            "None of the approved BOQ lines map to catalog work items. Tag work items in configuration; line descriptions should match those names (or carry a work-item link).";
    static final String NO_TAGS =
            "Approved BOQ work items have no scope tags. Only community, access and completion permits apply.";

    public record DerivedScope(
            ProjectScopeToggles toggles,
            List<DerivedScopeTagView> tags,
            boolean hasApprovedBoq,
            String note
    ) {
    }

    private final BoqProjectRules boqProjectRules;
    private final BoqLineRepository boqLineRepository;
    private final WorkItemRepository workItemRepository;

    @Transactional(readOnly = true)
    public DerivedScope derive(Long projectId) {
        OptionalBoq approved = approvedBoq(projectId);
        if (approved == null) {
            return new DerivedScope(emptyToggles(), List.of(), false, NO_APPROVED_BOQ);
        }

        List<BoqLine> lines = boqLineRepository.findByBoqIdWithWorkItems(approved.id);
        List<WorkItem> catalog = workItemRepository.findByCompanyUuidAndDeletedFalse(approved.companyId);
        Map<String, UUID> byName = catalog.stream()
                .filter(wi -> StringUtils.hasText(wi.getWorkItemName()))
                .collect(Collectors.toMap(
                        wi -> normalizeWorkItemName(wi.getWorkItemName()),
                        WorkItem::getId,
                        (a, b) -> a,
                        LinkedHashMap::new));

        LinkedHashMap<UUID, UUID> matchedIds = new LinkedHashMap<>();
        for (BoqLine line : lines) {
            UUID id = resolveWorkItemId(line, byName);
            if (id != null) {
                matchedIds.put(id, id);
            }
        }
        if (matchedIds.isEmpty()) {
            return new DerivedScope(emptyToggles(), List.of(), true, NO_LINKED_WORK_ITEMS);
        }

        Map<String, String> byCode = new LinkedHashMap<>();
        for (WorkItem item : workItemRepository.findByIdInWithScopeTags(matchedIds.keySet())) {
            if (item.getScopeTags() == null) continue;
            for (ApprovalScopeTag tag : item.getScopeTags()) {
                if (tag == null || tag.isDeleted() || !tag.isActive()) continue;
                if (tag.getCode() == null || tag.getCode().isBlank()) continue;
                byCode.putIfAbsent(tag.getCode().trim().toUpperCase(Locale.ROOT), tag.getName());
            }
        }

        List<DerivedScopeTagView> tags = byCode.entrySet().stream()
                .map(e -> DerivedScopeTagView.builder().code(e.getKey()).name(e.getValue()).build())
                .sorted(Comparator.comparing(DerivedScopeTagView::getCode))
                .toList();
        String note = tags.isEmpty() ? NO_TAGS : null;
        return new DerivedScope(fromCodes(byCode.keySet()), tags, true, note);
    }

    private OptionalBoq approvedBoq(Long projectId) {
        return boqProjectRules.findApproved(projectId)
                .map(doc -> new OptionalBoq(doc.getId(), doc.getCompanyId()))
                .orElse(null);
    }

    private record OptionalBoq(UUID id, UUID companyId) {
    }

    static UUID resolveWorkItemId(BoqLine line, Map<String, UUID> workItemByName) {
        if (line == null) {
            return null;
        }
        if (line.getWorkItem() != null) {
            return line.getWorkItem().getId();
        }
        QtoLine qto = line.getQtoLine();
        if (qto != null && qto.getWorkItem() != null) {
            return qto.getWorkItem().getId();
        }
        return matchWorkItemIdByName(line.getDescription(), workItemByName);
    }

    static UUID matchWorkItemIdByName(String raw, Map<String, UUID> workItemByName) {
        if (!StringUtils.hasText(raw) || workItemByName == null || workItemByName.isEmpty()) {
            return null;
        }
        String needle = normalizeWorkItemName(raw);
        UUID exact = workItemByName.get(needle);
        if (exact != null) {
            return exact;
        }
        UUID best = null;
        int bestLen = -1;
        for (Map.Entry<String, UUID> entry : workItemByName.entrySet()) {
            String name = entry.getKey();
            if (name.isBlank()) continue;
            boolean match = needle.equals(name) || needle.startsWith(name) || name.startsWith(needle);
            if (!match) continue;
            if (name.length() > bestLen) {
                best = entry.getValue();
                bestLen = name.length();
            }
        }
        return best;
    }

    static String normalizeWorkItemName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (value.endsWith("at:")) {
            value = value.substring(0, value.length() - 3).trim();
        }
        return value;
    }

    public static ProjectScopeToggles emptyToggles() {
        return new ProjectScopeToggles();
    }

    /** Maps catalog tag codes onto the toggle fields {@link PermitTriggerRules} already understands. */
    public static ProjectScopeToggles fromCodes(Collection<String> codes) {
        ProjectScopeToggles toggles = emptyToggles();
        if (codes == null || codes.isEmpty()) {
            return toggles;
        }
        for (String raw : codes) {
            if (raw == null || raw.isBlank()) continue;
            switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "DEMOLITION" -> toggles.setDemolition(true);
                case "STRUCTURAL", "LAYOUT" -> toggles.setStructuralChange(true);
                case "FACADE" -> toggles.setFacadeChange(true);
                case "MEP_LOAD" -> toggles.setMepLoadChange(true);
                case "FIRE_LIFE" -> toggles.setFireSystem(true);
                case "KITCHEN" -> toggles.setCommercialKitchen(true);
                case "SIGNAGE" -> toggles.setSignage(true);
                case "SECURITY" -> toggles.setSecuritySystem(true);
                case "NIGHT" -> toggles.setNightWork(true);
                case "HOARDING" -> toggles.setHoardingOnRoad(true);
                default -> {
                    // HOT_WORKS and unknown codes do not generate cases.
                }
            }
        }
        return toggles;
    }
}
