package com.fitouts.approval.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.approval.application.PermitTriggerEvaluator.Match;
import com.fitouts.approval.application.PermitTriggerEvaluator.PermitDef;
import com.fitouts.approvalconfig.domain.ApprovalPermitProjectNature;
import com.fitouts.approvalconfig.domain.ApprovalPermitProjectNatureRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitPropertyType;
import com.fitouts.approvalconfig.domain.ApprovalPermitPropertyTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitScopeTag;
import com.fitouts.approvalconfig.domain.ApprovalPermitScopeTagRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitType;
import com.fitouts.approvalconfig.domain.ApprovalPermitTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalScopeTag;
import com.fitouts.approvalconfig.domain.ApprovalScopeTagRepository;

import lombok.RequiredArgsConstructor;

/**
 * Loads the tenant Permit Catalogue and evaluates every active row against one project.
 */
@Service
@RequiredArgsConstructor
public class PermitCatalogueResolver {

    private final ApprovalPermitTypeRepository permitTypeRepository;
    private final ApprovalPermitScopeTagRepository permitScopeTagRepository;
    private final ApprovalScopeTagRepository scopeTagRepository;
    private final ApprovalPermitPropertyTypeRepository permitPropertyTypeRepository;
    private final ApprovalPermitProjectNatureRepository permitProjectNatureRepository;

    @Transactional(readOnly = true)
    public List<Match> evaluate(
            UUID companyId,
            Set<String> projectScopeTagCodes,
            UUID propertyTypeId,
            UUID projectNatureId,
            Predicate<PermitDef> authorityApplicable) {
        return PermitTriggerEvaluator.select(
                loadDefs(companyId),
                projectScopeTagCodes,
                propertyTypeId,
                projectNatureId,
                authorityApplicable);
    }

    private List<PermitDef> loadDefs(UUID companyId) {
        List<ApprovalPermitType> permits = permitTypeRepository
                .findByCompanyIdAndDeletedFalseOrderByPermitCodeAsc(companyId);

        Map<UUID, String> tagCodes = new LinkedHashMap<>();
        for (ApprovalScopeTag tag : scopeTagRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId)) {
            if (!tag.isActive() || tag.getCode() == null || tag.getCode().isBlank()) continue;
            tagCodes.put(tag.getId(), tag.getCode().trim().toUpperCase(Locale.ROOT));
        }

        Map<UUID, Set<String>> tagsByPermit = new LinkedHashMap<>();
        for (ApprovalPermitScopeTag row : permitScopeTagRepository.findByCompanyId(companyId)) {
            String code = tagCodes.get(row.getScopeTagId());
            if (code == null) continue;
            tagsByPermit.computeIfAbsent(row.getPermitTypeId(), k -> new LinkedHashSet<>()).add(code);
        }

        Map<UUID, Set<UUID>> propertyByPermit = new LinkedHashMap<>();
        for (ApprovalPermitPropertyType row : permitPropertyTypeRepository.findByCompanyId(companyId)) {
            propertyByPermit.computeIfAbsent(row.getPermitTypeId(), k -> new LinkedHashSet<>())
                    .add(row.getPropertyTypeId());
        }

        Map<UUID, Set<UUID>> natureByPermit = new LinkedHashMap<>();
        for (ApprovalPermitProjectNature row : permitProjectNatureRepository.findByCompanyId(companyId)) {
            natureByPermit.computeIfAbsent(row.getPermitTypeId(), k -> new LinkedHashSet<>())
                    .add(row.getProjectNatureId());
        }

        List<PermitDef> defs = new ArrayList<>();
        for (ApprovalPermitType permit : permits) {
            if (!permit.isActive()) continue;
            defs.add(new PermitDef(
                    permit.getPermitCode(),
                    permit.getName(),
                    permit.getTriggerType(),
                    permit.getTypicalTrigger(),
                    permit.getIssuingBody(),
                    tagsByPermit.getOrDefault(permit.getId(), Set.of()),
                    propertyByPermit.getOrDefault(permit.getId(), Set.of()),
                    natureByPermit.getOrDefault(permit.getId(), Set.of()),
                    SeedValueParser.permitCodes(permit.getPrerequisiteCases())));
        }
        return defs;
    }
}
