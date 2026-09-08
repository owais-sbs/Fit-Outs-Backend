package com.fitouts.approval.application;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.approval.api.SeedImportSummary;
import com.fitouts.approval.domain.ApprovalSeedImport;
import com.fitouts.approval.domain.ApprovalSeedImportRepository;
import com.fitouts.approval.domain.Authority;
import com.fitouts.approval.domain.AuthorityRepository;
import com.fitouts.approval.domain.AuthorityType;
import com.fitouts.approval.domain.DocumentType;
import com.fitouts.approval.domain.DocumentTypeRepository;
import com.fitouts.approval.domain.Jurisdiction;
import com.fitouts.approval.domain.JurisdictionRepository;
import com.fitouts.approval.domain.PermitType;
import com.fitouts.approval.domain.PermitTypeRepository;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.error.BadRequestException;

import lombok.RequiredArgsConstructor;

/**
 * Loads the VetroBuild companion seed into the global catalogue.
 *
 * <p>Re-running is safe: rows are matched by code and updated in place, so a corrected seed
 * file can be re-imported without duplicating anything. Everything it writes lands as a
 * global row (null company) and unverified, because fees, SLAs and jurisdictions in the file
 * are explicitly indicative and must be checked against each authority before go-live.
 */
@Service
@RequiredArgsConstructor
public class ApprovalSeedImportService {

    private final AuthorityRepository authorityRepository;
    private final JurisdictionRepository jurisdictionRepository;
    private final PermitTypeRepository permitTypeRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final ApprovalSeedImportRepository importRepository;
    private final SeedFileLocator seedFileLocator;
    private final ObjectMapper objectMapper;

    /**
     * The schedule templates live in the same file but belong to the scheduling module.
     * Resolved lazily so the approval module does not depend on it at construction time.
     */
    private final ObjectProvider<ScheduleTemplateSeedImporter> templateImporter;

    @Transactional
    public SeedImportSummary importFromConfiguredFile() {
        try (InputStream in = seedFileLocator.open()) {
            JsonNode root = objectMapper.readTree(in);
            return importFrom(root, seedFileLocator.describeLocation());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Failed to read seed file: " + e.getMessage());
        }
    }

    @Transactional
    public SeedImportSummary importFromJson(byte[] content, String source) {
        try {
            JsonNode root = objectMapper.readTree(content);
            return importFrom(root, source);
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse uploaded seed JSON: " + e.getMessage());
        }
    }

    private SeedImportSummary importFrom(JsonNode root, String source) {
        SeedImportSummary summary = new SeedImportSummary();
        summary.setSource(source);
        summary.setSeedVersion(text(root.path("meta"), "version"));

        importAuthorities(root.path("authorities"), summary);
        importDocumentTypes(root.path("document_types"), summary);
        importPermitTypes(root.path("permit_types"), summary);
        deriveJurisdictions(summary);

        ScheduleTemplateSeedImporter importer = templateImporter.getIfAvailable();
        if (importer != null) {
            importer.importTemplates(root, summary);
        } else {
            summary.warn("Schedule template importer unavailable; templates were skipped.");
        }

        recordImport(summary);
        return summary;
    }

    private void importAuthorities(JsonNode node, SeedImportSummary summary) {
        if (!node.isArray()) {
            summary.warn("No authorities section in the seed file.");
            return;
        }
        int inserted = 0;
        int updated = 0;
        for (JsonNode row : node) {
            String code = upper(text(row, "code"));
            if (code == null) {
                summary.warn("Skipped an authority row with no code.");
                continue;
            }
            Optional<Authority> existing = authorityRepository.findByCodeAndCompanyIdIsNull(code);
            Authority authority = existing.orElseGet(Authority::new);
            boolean isNew = existing.isEmpty();

            authority.setCompanyId(null);
            authority.setCode(code);
            authority.setName(firstNonBlank(text(row, "authority__body"), text(row, "name"), code));
            authority.setType(AuthorityType.fromLabel(text(row, "type")));
            authority.setEmirate(text(row, "emirate"));
            authority.setJurisdictionAreas(firstNonBlank(
                    text(row, "jurisdiction__areas_covered"), text(row, "jurisdiction_areas")));
            authority.setPermitsIssuedTypical(text(row, "permits_issued_typical"));
            authority.setSubmissionChannel(text(row, "submission_channel"));
            authority.setNotes(firstNonBlank(text(row, "notes_for_configuration"), text(row, "notes")));
            authority.setActive(true);

            authorityRepository.save(authority);
            if (isNew) inserted++;
            else updated++;
        }
        summary.countInserted("authorities", inserted);
        summary.countUpdated("authorities", updated);
    }

    private void importDocumentTypes(JsonNode node, SeedImportSummary summary) {
        if (!node.isArray()) {
            summary.warn("No document_types section in the seed file.");
            return;
        }
        int inserted = 0;
        int updated = 0;
        for (JsonNode row : node) {
            String code = upper(firstNonBlank(text(row, "doc_code"), text(row, "code")));
            if (code == null) continue;
            Optional<DocumentType> existing = documentTypeRepository.findByCodeAndCompanyIdIsNull(code);
            DocumentType type = existing.orElseGet(DocumentType::new);
            boolean isNew = existing.isEmpty();

            type.setCompanyId(null);
            type.setCode(code);
            type.setName(firstNonBlank(text(row, "document"), text(row, "name"), code));
            type.setCategory(text(row, "category"));
            type.setTypicallyRequiredFor(text(row, "typically_required_for"));
            type.setExpiryTracked(SeedValueParser.isYes(text(row, "expiry_tracked")));
            type.setSourceOwner(firstNonBlank(text(row, "source__owner"), text(row, "source_owner")));
            type.setActive(true);

            documentTypeRepository.save(type);
            if (isNew) inserted++;
            else updated++;
        }
        summary.countInserted("documentTypes", inserted);
        summary.countUpdated("documentTypes", updated);
    }

    private void importPermitTypes(JsonNode node, SeedImportSummary summary) {
        if (!node.isArray()) {
            summary.warn("No permit_types section in the seed file.");
            return;
        }
        Map<String, Authority> byName = new LinkedHashMap<>();
        Map<AuthorityType, List<Authority>> byType = new LinkedHashMap<>();
        for (Authority authority : authorityRepository.findByCompanyIdIsNull()) {
            byName.put(authority.getName().toLowerCase(Locale.ROOT), authority);
            byType.computeIfAbsent(authority.getType(), k -> new ArrayList<>()).add(authority);
        }

        int inserted = 0;
        int updated = 0;
        for (JsonNode row : node) {
            String code = upper(firstNonBlank(text(row, "permit_code"), text(row, "code")));
            if (code == null) continue;
            Optional<PermitType> existing = permitTypeRepository.findByCodeAndCompanyIdIsNull(code);
            PermitType permit = existing.orElseGet(PermitType::new);
            boolean isNew = existing.isEmpty();

            permit.setCompanyId(null);
            permit.setCode(code);
            permit.setName(firstNonBlank(text(row, "permit__case_type"), text(row, "name"), code));

            // The seed's issuing_body is sometimes a specific authority ("Dubai Civil Defence")
            // and sometimes a layer ("Master Developer"), because which developer applies
            // depends on the project's community. Both are recorded.
            String issuingBody = text(row, "issuing_body");
            resolveIssuingBody(permit, issuingBody, byName);

            permit.setTypicalTrigger(text(row, "typical_trigger"));

            String prerequisites = text(row, "prerequisite_cases");
            permit.setPrerequisitePermitCodes(SeedValueParser.joinList(SeedValueParser.permitCodes(prerequisites)));
            permit.setPrerequisiteNotes(SeedValueParser.prerequisiteNarrative(prerequisites));

            String sla = text(row, "indicative_sla_working_days");
            permit.setIndicativeSlaRaw(sla);
            permit.setIndicativeSlaDaysMin(SeedValueParser.minOf(sla));
            permit.setIndicativeSlaDaysMax(SeedValueParser.maxOf(sla));

            String validity = text(row, "typical_validity");
            permit.setValidityRaw(validity);
            permit.setValidityDaysMin(SeedValueParser.validityDays(validity, false));
            permit.setValidityDaysMax(SeedValueParser.validityDays(validity, true));

            String deposit = text(row, "deposit");
            permit.setHasDeposit(SeedValueParser.isYes(deposit));
            permit.setDepositConditional(SeedValueParser.isConditional(deposit));

            permit.setRenewable(SeedValueParser.isYes(text(row, "renewable")));
            permit.setBlocksActivitiesRaw(text(row, "blocks_which_activities"));
            permit.setRequiredDocumentCodes(
                    SeedValueParser.joinList(SeedValueParser.documentCodes(text(row, "required_documents"))));
            permit.setActive(true);

            permitTypeRepository.save(permit);
            if (isNew) inserted++;
            else updated++;
        }

        summary.countInserted("permitTypes", inserted);
        summary.countUpdated("permitTypes", updated);
        summary.warn("Permit blocking activities are prose in the seed (\"Demolition, all site works\"). "
                + "They are stored verbatim and need mapping to template activity codes before approval "
                + "constraints affect the Gantt.");
    }

    /**
     * Points a permit at a specific authority when the seed names one, and otherwise records
     * the layer so the resolver can pick the right body for the project's community.
     */
    private void resolveIssuingBody(PermitType permit, String issuingBody, Map<String, Authority> byName) {
        permit.setAuthorityCode(null);
        permit.setAuthorityType(null);
        if (issuingBody == null || issuingBody.isBlank()) return;

        Authority exact = byName.get(issuingBody.toLowerCase(Locale.ROOT));
        if (exact != null) {
            permit.setAuthorityCode(exact.getCode());
            permit.setAuthorityType(exact.getType().name());
            return;
        }

        AuthorityType layer = AuthorityType.fromLabel(issuingBody);
        if (layer != AuthorityType.SPECIAL) {
            permit.setAuthorityType(layer.name());
            return;
        }

        // Entries such as "Regulator (DM / DDA / Trakhees)" name a layer with examples.
        String lower = issuingBody.toLowerCase(Locale.ROOT);
        if (lower.startsWith("regulator")) {
            permit.setAuthorityType(AuthorityType.REGULATOR.name());
        } else if (lower.contains("utility")) {
            permit.setAuthorityType(AuthorityType.UTILITY.name());
        } else if (lower.contains("building management")) {
            permit.setAuthorityType(AuthorityType.BUILDING_MANAGEMENT.name());
        } else if (lower.contains("master developer")) {
            permit.setAuthorityType(AuthorityType.MASTER_DEVELOPER.name());
        } else {
            // A named body we could not match, such as "DM Food Safety". Keep the layer open
            // and let the resolver fall back to the project's regulator.
            permit.setAuthorityType(AuthorityType.REGULATOR.name());
        }
    }

    /**
     * Builds the community-to-authority lookup the resolver needs. The seed has no such
     * table, so rows are derived from authority coverage prose and land unverified.
     */
    private void deriveJurisdictions(SeedImportSummary summary) {
        List<Authority> authorities = authorityRepository.findByCompanyIdIsNull();
        List<Jurisdiction> derived = new JurisdictionDeriver().derive(authorities);

        int inserted = 0;
        int updated = 0;
        for (Jurisdiction row : derived) {
            String building = row.getBuildingName() == null ? "" : row.getBuildingName();
            Optional<Jurisdiction> existing = jurisdictionRepository
                    .findByCommunityKeyAndBuildingNameAndCompanyIdIsNull(row.getCommunityKey(),
                            building.isEmpty() ? null : building);

            if (existing.isPresent()) {
                Jurisdiction target = existing.get();
                // A row VetroBuild has already confirmed is left alone, so re-importing the
                // seed never silently undoes a human decision.
                if (target.isVerified()) {
                    continue;
                }
                target.setEmirate(row.getEmirate());
                target.setCommunityName(row.getCommunityName());
                target.setRegulatorAuthorityCode(row.getRegulatorAuthorityCode());
                target.setMasterDeveloperAuthorityCode(row.getMasterDeveloperAuthorityCode());
                target.setBuildingManagementAuthorityCode(row.getBuildingManagementAuthorityCode());
                target.setUtilityAuthorityCode(row.getUtilityAuthorityCode());
                target.setAdditionalAuthorityCodes(row.getAdditionalAuthorityCodes());
                target.setDerivedFrom(row.getDerivedFrom());
                jurisdictionRepository.save(target);
                updated++;
            } else {
                jurisdictionRepository.save(row);
                inserted++;
            }
        }

        summary.countInserted("jurisdictions", inserted);
        summary.countUpdated("jurisdictions", updated);
        summary.warn("Jurisdiction rows are derived from authority coverage prose because the seed has no "
                + "jurisdiction table. All " + (inserted + updated) + " land unverified and need VetroBuild sign-off.");
    }

    private void recordImport(SeedImportSummary summary) {
        ApprovalSeedImport record = new ApprovalSeedImport();
        record.setSource(summary.getSource() != null ? summary.getSource() : "upload");
        record.setSeedVersion(summary.getSeedVersion());
        record.setImportedBy(currentAccountId());
        try {
            record.setSummaryJson(objectMapper.writeValueAsString(summary));
        } catch (Exception ignored) {
            record.setSummaryJson(null);
        }
        importRepository.save(record);
    }

    private Long currentAccountId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.getAccountId();
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        return SeedValueParser.trimToNull(value.asText());
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
