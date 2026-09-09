package com.fitouts.approvalconfig.application;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.approvalconfig.domain.ApprovalAuthority;
import com.fitouts.approvalconfig.domain.ApprovalAuthorityRepository;
import com.fitouts.approvalconfig.domain.ApprovalDocumentType;
import com.fitouts.approvalconfig.domain.ApprovalDocumentTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitProjectNature;
import com.fitouts.approvalconfig.domain.ApprovalPermitProjectNatureRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitPropertyType;
import com.fitouts.approvalconfig.domain.ApprovalPermitPropertyTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitScopeTag;
import com.fitouts.approvalconfig.domain.ApprovalPermitScopeTagRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitType;
import com.fitouts.approvalconfig.domain.ApprovalPermitTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalProjectNature;
import com.fitouts.approvalconfig.domain.ApprovalProjectNatureRepository;
import com.fitouts.approvalconfig.domain.ApprovalPropertyType;
import com.fitouts.approvalconfig.domain.ApprovalPropertyTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalScopeTag;
import com.fitouts.approvalconfig.domain.ApprovalScopeTagRepository;
import com.fitouts.approvalconfig.domain.PermitTriggerTypes;
import com.fitouts.approvalconfig.domain.JurisdictionPack;
import com.fitouts.approvalconfig.domain.JurisdictionPackDocument;
import com.fitouts.approvalconfig.domain.JurisdictionPackDocumentRepository;
import com.fitouts.approvalconfig.domain.JurisdictionPackPermit;
import com.fitouts.approvalconfig.domain.JurisdictionPackPermitRepository;
import com.fitouts.approvalconfig.domain.JurisdictionPackRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApprovalConfigSeedService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalConfigSeedService.class);
    private static final String SEED_PATH = "seed/vetrobuild_erp_seed_v1.json";
    private static final Set<String> DUBAI_CODES = Set.of(
            "DM", "DDA", "TRK", "DMCC", "DIFC", "DSO", "DXBS", "JAFZA", "DCD", "DEWA", "RTA", "SIRA",
            "DMW", "DMFS", "DET", "DHA", "KHDA", "EMR", "NAK", "MRS", "DAM", "SOB", "DP", "TAG",
            "JGE", "ALB", "OAFM");
    private static final List<ScopeTagDef> SCOPE_TAGS = List.of(
            new ScopeTagDef("STRUCTURAL", "Structural change",
                    "Changes to structure, slabs, beams, or load-bearing fabric."),
            new ScopeTagDef("LAYOUT", "Layout / partition change",
                    "New or moved internal walls, partitions, or room layout."),
            new ScopeTagDef("FACADE", "Façade / exterior change",
                    "External envelope, windows, doors, or boundary fabric."),
            new ScopeTagDef("MEP_LOAD", "MEP load or capacity change",
                    "Work that can increase connected electrical or HVAC load."),
            new ScopeTagDef("FIRE_LIFE", "Fire and life safety",
                    "Fire detection, suppression, fire-rated construction, gas, or emergency lighting."),
            new ScopeTagDef("DEMOLITION", "Demolition",
                    "Structural or full strip-out of walls and fabric, not finish-only removal."),
            new ScopeTagDef("SECURITY", "Security systems (CCTV / access control)",
                    "CCTV or access-control installation."),
            new ScopeTagDef("SIGNAGE", "Signage",
                    "External signage or fascia change."),
            new ScopeTagDef("KITCHEN", "Commercial kitchen fit-out",
                    "Commercial or food-service kitchen fit-out, not a villa kitchen refresh."),
            new ScopeTagDef("HOT_WORKS", "Hot works",
                    "Welding, grinding, cutting, or torch work."),
            new ScopeTagDef("NIGHT", "Night work",
                    "Work outside standard permitted hours."),
            new ScopeTagDef("HOARDING", "Road hoarding / lifting",
                    "Hoarding, skip, crane, or lifting on public right of way."));

    /** Catalogue triggers that are satisfied by any one of these scope tags (plain OR). */
    private static final List<PermitTagLink> PERMIT_SCOPE_TAG_LINKS = List.of(
            link("P-DEMO", "DEMOLITION"),
            link("P-MOD", "LAYOUT"),
            link("P-MOD", "STRUCTURAL"),
            link("P-MOD", "FACADE"),
            link("P-MOD", "MEP_LOAD"),
            link("P-DCD-NOC", "FIRE_LIFE"),
            link("P-DISCONNECT", "DEMOLITION"),
            link("P-DISCONNECT", "MEP_LOAD"),
            link("P-LOAD", "MEP_LOAD"),
            link("P-SIRA", "SECURITY"),
            link("P-RTA", "HOARDING"),
            link("P-NIGHT", "NIGHT"),
            link("P-HOT", "HOT_WORKS"),
            link("P-SIGN", "SIGNAGE"),
            link("P-KITCHEN", "KITCHEN"));

    private static final List<ScopeTagDef> PROPERTY_TYPES = List.of(
            new ScopeTagDef("VILLA", "Villa", "Detached or townhouse villa, not a tower unit."),
            new ScopeTagDef("APARTMENT", "Apartment / tower unit", "Unit inside a managed tower or apartment building."),
            new ScopeTagDef("COMMERCIAL_SHELL", "Commercial / retail shell", "Shop, restaurant or retail unit inside a shell."),
            new ScopeTagDef("OFFICE", "Office", "Office floor or suite, including fitted office space."));

    private static final List<ScopeTagDef> PROJECT_NATURES = List.of(
            new ScopeTagDef("NEW_BUILD", "New build", "Construction of a new building or villa."),
            new ScopeTagDef("MAJOR_REFURB", "Major refurbishment", "Substantial strip-out and rebuild of an existing property."),
            new ScopeTagDef("RENOVATION", "Renovation", "Alteration of layout, structure or services in an existing property."),
            new ScopeTagDef("FITOUT", "Minor works / fit-out", "Fit-out or finishes inside an existing shell or unit."));

    private static final Map<String, String> PERMIT_TRIGGER_TYPES = Map.ofEntries(
            Map.entry("P-COMM-REG", PermitTriggerTypes.COMPANY),
            Map.entry("P-COMM-NOC", PermitTriggerTypes.LOCATION),
            Map.entry("P-ACCESS", PermitTriggerTypes.LOCATION),
            Map.entry("P-WASTE", PermitTriggerTypes.LOCATION),
            Map.entry("P-BLDG-NOC", PermitTriggerTypes.PROPERTY_PROJECT),
            Map.entry("P-FITOUT", PermitTriggerTypes.PROPERTY_PROJECT),
            Map.entry("P-GREEN", PermitTriggerTypes.PROPERTY_PROJECT),
            Map.entry("P-DEWA-TEMP", PermitTriggerTypes.PROPERTY_PROJECT),
            Map.entry("P-LIFT", PermitTriggerTypes.PROPERTY_PROJECT),
            Map.entry("P-DCD-FINAL", PermitTriggerTypes.PREREQUISITE),
            Map.entry("P-RECONNECT", PermitTriggerTypes.PREREQUISITE),
            Map.entry("P-COMPLETE", PermitTriggerTypes.PREREQUISITE),
            Map.entry("P-DEPOSIT", PermitTriggerTypes.PREREQUISITE));

    private static final List<PermitTagLink> PERMIT_PROPERTY_LINKS = List.of(
            link("P-BLDG-NOC", "APARTMENT"),
            link("P-FITOUT", "COMMERCIAL_SHELL"),
            link("P-FITOUT", "OFFICE"),
            link("P-LIFT", "APARTMENT"));

    private static final List<PermitTagLink> PERMIT_NATURE_LINKS = List.of(
            link("P-GREEN", "NEW_BUILD"),
            link("P-GREEN", "MAJOR_REFURB"),
            link("P-DEWA-TEMP", "NEW_BUILD"));

    private final ApprovalAuthorityRepository authorityRepository;
    private final ApprovalPermitTypeRepository permitTypeRepository;
    private final ApprovalDocumentTypeRepository documentTypeRepository;
    private final ApprovalScopeTagRepository scopeTagRepository;
    private final ApprovalPropertyTypeRepository propertyTypeRepository;
    private final ApprovalProjectNatureRepository projectNatureRepository;
    private final ApprovalPermitScopeTagRepository permitScopeTagRepository;
    private final ApprovalPermitPropertyTypeRepository permitPropertyTypeRepository;
    private final ApprovalPermitProjectNatureRepository permitProjectNatureRepository;
    private final JurisdictionPackRepository packRepository;
    private final JurisdictionPackPermitRepository packPermitRepository;
    private final JurisdictionPackDocumentRepository packDocumentRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public void ensureSeeded(UUID companyId) {
        if (companyId == null) {
            return;
        }
        synchronized (this) {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(status -> {
                if (!packRepository.existsByCompanyIdAndDeletedFalse(companyId)) {
                    try {
                        seedCompany(companyId);
                    } catch (DataIntegrityViolationException e) {
                        log.warn("Approval catalog already seeded for company {}", companyId);
                        status.setRollbackOnly();
                        return;
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        log.error("Failed to seed approval config for company {}", companyId, e);
                        throw new IllegalStateException(rootMessage(e), e);
                    }
                }
                seedScopeTags(companyId);
                seedPermitScopeTags(companyId);
                seedPropertyTypes(companyId);
                seedProjectNatures(companyId);
                seedPermitPropertyLinks(companyId);
                seedPermitNatureLinks(companyId);
            });
        }
    }

    private void seedScopeTags(UUID companyId) {
        for (ScopeTagDef def : SCOPE_TAGS) {
            scopeTagRepository.findByCompanyIdAndCodeAndDeletedFalse(companyId, def.code)
                    .orElseGet(() -> {
                        ApprovalScopeTag created = new ApprovalScopeTag();
                        created.setCompanyId(companyId);
                        created.setCode(def.code);
                        created.setName(def.name);
                        created.setDescription(def.description);
                        created.setActive(true);
                        return scopeTagRepository.save(created);
                    });
        }
    }

    private void seedPermitScopeTags(UUID companyId) {
        if (permitScopeTagRepository.existsByCompanyId(companyId)) {
            return;
        }
        for (PermitTagLink link : PERMIT_SCOPE_TAG_LINKS) {
            ApprovalPermitType permit = permitTypeRepository
                    .findByCompanyIdAndPermitCodeAndDeletedFalse(companyId, link.permitCode())
                    .orElse(null);
            ApprovalScopeTag tag = scopeTagRepository
                    .findByCompanyIdAndCodeAndDeletedFalse(companyId, link.tagCode())
                    .orElse(null);
            if (permit == null || tag == null) {
                continue;
            }
            if (permitScopeTagRepository.existsByPermitTypeIdAndScopeTagId(permit.getId(), tag.getId())) {
                continue;
            }
            ApprovalPermitScopeTag row = new ApprovalPermitScopeTag();
            row.setCompanyId(companyId);
            row.setPermitTypeId(permit.getId());
            row.setScopeTagId(tag.getId());
            row.setCreatedByName("seed");
            permitScopeTagRepository.save(row);
        }
    }

    private void seedPropertyTypes(UUID companyId) {
        for (ScopeTagDef def : PROPERTY_TYPES) {
            propertyTypeRepository.findByCompanyIdAndCodeAndDeletedFalse(companyId, def.code)
                    .orElseGet(() -> {
                        ApprovalPropertyType created = new ApprovalPropertyType();
                        created.setCompanyId(companyId);
                        created.setCode(def.code);
                        created.setName(def.name);
                        created.setDescription(def.description);
                        created.setActive(true);
                        return propertyTypeRepository.save(created);
                    });
        }
    }

    private void seedProjectNatures(UUID companyId) {
        for (ScopeTagDef def : PROJECT_NATURES) {
            projectNatureRepository.findByCompanyIdAndCodeAndDeletedFalse(companyId, def.code)
                    .orElseGet(() -> {
                        ApprovalProjectNature created = new ApprovalProjectNature();
                        created.setCompanyId(companyId);
                        created.setCode(def.code);
                        created.setName(def.name);
                        created.setDescription(def.description);
                        created.setActive(true);
                        return projectNatureRepository.save(created);
                    });
        }
    }

    private void seedPermitPropertyLinks(UUID companyId) {
        if (permitPropertyTypeRepository.existsByCompanyId(companyId)) {
            return;
        }
        for (PermitTagLink link : PERMIT_PROPERTY_LINKS) {
            ApprovalPermitType permit = permitTypeRepository
                    .findByCompanyIdAndPermitCodeAndDeletedFalse(companyId, link.permitCode())
                    .orElse(null);
            ApprovalPropertyType item = propertyTypeRepository
                    .findByCompanyIdAndCodeAndDeletedFalse(companyId, link.tagCode())
                    .orElse(null);
            if (permit == null || item == null) {
                continue;
            }
            ApprovalPermitPropertyType row = new ApprovalPermitPropertyType();
            row.setCompanyId(companyId);
            row.setPermitTypeId(permit.getId());
            row.setPropertyTypeId(item.getId());
            row.setCreatedByName("seed");
            permitPropertyTypeRepository.save(row);
        }
    }

    private void seedPermitNatureLinks(UUID companyId) {
        if (permitProjectNatureRepository.existsByCompanyId(companyId)) {
            return;
        }
        for (PermitTagLink link : PERMIT_NATURE_LINKS) {
            ApprovalPermitType permit = permitTypeRepository
                    .findByCompanyIdAndPermitCodeAndDeletedFalse(companyId, link.permitCode())
                    .orElse(null);
            ApprovalProjectNature item = projectNatureRepository
                    .findByCompanyIdAndCodeAndDeletedFalse(companyId, link.tagCode())
                    .orElse(null);
            if (permit == null || item == null) {
                continue;
            }
            ApprovalPermitProjectNature row = new ApprovalPermitProjectNature();
            row.setCompanyId(companyId);
            row.setPermitTypeId(permit.getId());
            row.setProjectNatureId(item.getId());
            row.setCreatedByName("seed");
            permitProjectNatureRepository.save(row);
        }
    }

    private static boolean requiresRegistration(String type, String code) {
        String t = type == null ? "" : type;
        String c = code == null ? "" : code;
        return t.equalsIgnoreCase("Master Developer")
                || Set.of("DDA", "TRK", "DMCC", "JAFZA").contains(c);
    }

    private static String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : e.getMessage();
    }

    private void seedCompany(UUID companyId) throws Exception {
        SeedFile seed = loadSeed();
        Map<String, ApprovalAuthority> authorities = new LinkedHashMap<>();
        for (AuthorityRow row : seed.authorities) {
            if (row.code == null || !DUBAI_CODES.contains(row.code)) {
                continue;
            }
            ApprovalAuthority entity = authorityRepository
                    .findByCompanyIdAndCodeAndDeletedFalse(companyId, row.code)
                    .orElseGet(() -> {
                        ApprovalAuthority created = new ApprovalAuthority();
                        created.setCompanyId(companyId);
                        created.setCode(row.code);
                        created.setName(row.body);
                        created.setType(row.type);
                        created.setEmirate(row.emirate);
                        created.setJurisdictionAreas(row.areas);
                        created.setPermitsIssued(row.permits);
                        created.setSubmissionChannel(row.channel);
                        created.setNotes(blankToNull(row.notes));
                        created.setRequiresCompanyRegistration(requiresRegistration(row.type, row.code));
                        return authorityRepository.save(created);
                    });
            authorities.put(row.code, entity);
        }

        Map<String, ApprovalPermitType> permits = new LinkedHashMap<>();
        for (PermitRow row : seed.permitTypes) {
            ApprovalPermitType entity = permitTypeRepository
                    .findByCompanyIdAndPermitCodeAndDeletedFalse(companyId, row.permitCode)
                    .orElseGet(() -> {
                        ApprovalPermitType created = new ApprovalPermitType();
                        created.setCompanyId(companyId);
                        created.setPermitCode(row.permitCode);
                        created.setName(row.caseType);
                        created.setIssuingBody(row.issuingBody);
                        created.setTypicalTrigger(row.trigger);
                        created.setTriggerType(PERMIT_TRIGGER_TYPES.getOrDefault(row.permitCode, PermitTriggerTypes.SCOPE_TAG));
                        created.setPrerequisiteCases(blankToNull(row.prerequisites));
                        created.setSlaWorkingDays(row.sla);
                        created.setTypicalValidity(blankToNull(row.validity));
                        created.setDeposit(row.deposit);
                        created.setRenewable(row.renewable);
                        created.setBlocksActivities(row.blocks);
                        return permitTypeRepository.save(created);
                    });
            permits.put(row.permitCode, entity);
        }

        Map<String, ApprovalDocumentType> documents = new LinkedHashMap<>();
        for (DocumentRow row : seed.documentTypes) {
            ApprovalDocumentType entity = documentTypeRepository
                    .findByCompanyIdAndDocCodeAndDeletedFalse(companyId, row.docCode)
                    .orElseGet(() -> {
                        ApprovalDocumentType created = new ApprovalDocumentType();
                        created.setCompanyId(companyId);
                        created.setDocCode(row.docCode);
                        created.setName(row.document);
                        created.setCategory(row.category);
                        created.setTypicallyRequiredFor(row.requiredFor);
                        created.setExpiryTracked("Yes".equalsIgnoreCase(row.expiryTracked));
                        created.setSourceOwner(row.sourceOwner);
                        return documentTypeRepository.save(created);
                    });
            documents.put(row.docCode, entity);
        }

        for (PackDef pack : dubaiPacks()) {
            seedPack(companyId, pack, authorities, permits, documents);
        }
        log.info("Seeded Dubai approval catalog for company {}", companyId);
    }

    private void seedPack(
            UUID companyId,
            PackDef def,
            Map<String, ApprovalAuthority> authorities,
            Map<String, ApprovalPermitType> permits,
            Map<String, ApprovalDocumentType> documents) {
        if (packRepository.findByCompanyIdAndCodeAndDeletedFalse(companyId, def.code).isPresent()) {
            return;
        }
        JurisdictionPack pack = new JurisdictionPack();
        pack.setCompanyId(companyId);
        pack.setCode(def.code);
        pack.setName(def.name);
        pack.setDescription(def.description);
        pack.setCommunityAuthorityId(idOf(authorities, def.communityCode));
        pack.setPrimaryAuthorityId(idOf(authorities, def.primaryCode));
        pack = packRepository.save(pack);

        int sort = 0;
        for (PermitLine line : def.family.lines) {
            ApprovalPermitType permit = permits.get(line.permitCode);
            if (permit == null) {
                continue;
            }
            JurisdictionPackPermit packPermit = new JurisdictionPackPermit();
            packPermit.setPackId(pack.getId());
            packPermit.setPermitTypeId(permit.getId());
            packPermit.setIssuingAuthorityId(resolveIssuer(
                    line.permitCode, def, authorities));
            packPermit.setInclusionRule(line.rule);
            packPermit.setSortOrder(sort++);
            packPermit = packPermitRepository.save(packPermit);

            for (String docCode : documentsFor(line.permitCode)) {
                ApprovalDocumentType doc = documents.get(docCode);
                if (doc == null) {
                    continue;
                }
                JurisdictionPackDocument packDoc = new JurisdictionPackDocument();
                packDoc.setPackPermitId(packPermit.getId());
                packDoc.setDocumentTypeId(doc.getId());
                packDocumentRepository.save(packDoc);
            }
        }
    }

    private UUID resolveIssuer(String permitCode, PackDef def, Map<String, ApprovalAuthority> authorities) {
        return switch (permitCode) {
            case "P-COMM-REG", "P-COMM-NOC", "P-ACCESS", "P-NIGHT", "P-DEPOSIT" ->
                    firstId(authorities, def.communityCode, def.primaryCode);
            case "P-BLDG-NOC", "P-LIFT", "P-HOT" ->
                    firstId(authorities, def.communityCode != null ? def.communityCode : "OAFM", def.primaryCode);
            case "P-DCD-NOC", "P-DCD-FINAL" -> idOf(authorities, "DCD");
            case "P-DEWA-TEMP", "P-DISCONNECT", "P-RECONNECT", "P-LOAD" -> idOf(authorities, "DEWA");
            case "P-SIRA" -> idOf(authorities, "SIRA");
            case "P-RTA" -> idOf(authorities, "RTA");
            case "P-WASTE" -> idOf(authorities, "DMW");
            case "P-KITCHEN" -> idOf(authorities, "DMFS");
            case "P-SIGN" -> idOf(authorities, "DET");
            default -> idOf(authorities, def.primaryCode);
        };
    }

    private static UUID idOf(Map<String, ApprovalAuthority> authorities, String code) {
        if (code == null) {
            return null;
        }
        ApprovalAuthority authority = authorities.get(code);
        return authority != null ? authority.getId() : null;
    }

    private static UUID firstId(Map<String, ApprovalAuthority> authorities, String first, String second) {
        UUID id = idOf(authorities, first);
        return id != null ? id : idOf(authorities, second);
    }

    private SeedFile loadSeed() throws Exception {
        ClassPathResource resource = new ClassPathResource(SEED_PATH);
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, SeedFile.class);
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        return value;
    }

    private static List<String> documentsFor(String permitCode) {
        return switch (permitCode) {
            case "P-COMM-REG" -> List.of("D01", "D02", "D03", "D08");
            case "P-COMM-NOC" -> List.of("D01", "D04", "D05", "D06", "D08", "D14", "D20", "D21", "D26", "D27", "D28", "D29");
            case "P-BLDG-NOC" -> List.of("D01", "D04", "D05", "D14", "D15", "D28", "D29");
            case "P-ACCESS" -> List.of("D02", "D06", "D23", "D24");
            case "P-DEMO" -> List.of("D01", "D12", "D14", "D17");
            case "P-MOD" -> List.of("D01", "D07", "D12", "D13", "D14", "D16", "D17", "D18", "D30");
            case "P-FITOUT" -> List.of("D01", "D04", "D05", "D14", "D15", "D16", "D18", "D21", "D27", "D30");
            case "P-DCD-NOC" -> List.of("D09", "D18", "D19");
            case "P-DCD-FINAL" -> List.of("D09", "D19", "D33");
            case "P-DEWA-TEMP", "P-LOAD", "P-RECONNECT" -> List.of("D11", "D18");
            case "P-DISCONNECT" -> List.of("D11", "D14");
            case "P-SIRA" -> List.of("D10");
            case "P-RTA" -> List.of("D16", "D20");
            case "P-WASTE" -> List.of("D22", "D35");
            case "P-NIGHT" -> List.of("D20", "D21");
            case "P-HOT" -> List.of("D20", "D25");
            case "P-LIFT" -> List.of("D20", "D27");
            case "P-SIGN" -> List.of("D16", "D31");
            case "P-KITCHEN" -> List.of("D16", "D18");
            case "P-GREEN" -> List.of("D16");
            case "P-COMPLETE" -> List.of("D32", "D33");
            case "P-DEPOSIT" -> List.of("D34", "D35");
            default -> List.of("D01", "D14");
        };
    }

    private static List<PackDef> dubaiPacks() {
        List<PackDef> packs = new ArrayList<>();
        packs.add(new PackDef("EMR", "Emaar communities",
                "Emaar/LOAMS community NOC first, then Dubai Municipality in parallel with Civil Defence.",
                "EMR", "DM", PackFamily.COMMUNITY));
        packs.add(new PackDef("NAK", "Nakheel communities",
                "Nakheel community NOC in parallel with Trakhees; Civil Defence always additional.",
                "NAK", "TRK", PackFamily.COMMUNITY));
        packs.add(new PackDef("MRS", "Meraas / Dubai Holding communities",
                "Community NOC then mainland/DM building control with Civil Defence in parallel.",
                "MRS", "DM", PackFamily.COMMUNITY));
        packs.add(new PackDef("DAM", "DAMAC communities",
                "DAMAC OA works permit and deposit; typical primary regulator is Dubai Municipality.",
                "DAM", "DM", PackFamily.COMMUNITY));
        packs.add(new PackDef("SOB", "Sobha communities",
                "Sobha OA NOC; Dubai Municipality as default primary planning authority.",
                "SOB", "DM", PackFamily.COMMUNITY));
        packs.add(new PackDef("DP", "Dubai Properties / Wasl",
                "Community or third-party OA NOC; override primary authority if the plot sits in a special zone.",
                "DP", "DM", PackFamily.COMMUNITY));
        packs.add(new PackDef("TAG", "Tilal Al Ghaf",
                "MAF community design review plus Dubai Municipality modification/fit-out.",
                "TAG", "DM", PackFamily.COMMUNITY));
        packs.add(new PackDef("JGE", "Jumeirah Golf Estates",
                "Architectural design review for facade/extension plus DM permit pack.",
                "JGE", "DM", PackFamily.COMMUNITY));
        packs.add(new PackDef("ALB", "Al Barari",
                "Landscape-sensitive community NOC plus Dubai Municipality.",
                "ALB", "DM", PackFamily.COMMUNITY));
        packs.add(new PackDef("OAFM", "Tower / OA building management",
                "Building NOC, lift booking and deposit; Dubai Municipality as default statutory authority.",
                "OAFM", "DM", PackFamily.TOWER));
        packs.add(new PackDef("DM_MAINLAND", "Dubai mainland (no named developer)",
                "Default DM building permit route with Civil Defence and DEWA. Override if the plot is in a free zone.",
                null, "DM", PackFamily.MAINLAND));
        packs.add(new PackDef("DDA", "DDA / TECOM zones",
                "Fit-out via DDA portal (registered contractor). Civil Defence in parallel.",
                "OAFM", "DDA", PackFamily.FREEZONE));
        packs.add(new PackDef("DMCC", "DMCC / JLT",
                "DMCC member portal fit-out plus building management NOC; Civil Defence in parallel.",
                "OAFM", "DMCC", PackFamily.FREEZONE));
        packs.add(new PackDef("DIFC", "DIFC",
                "DIFC Building Control fit-out; strict working-hours constraints in occupied towers.",
                "OAFM", "DIFC", PackFamily.FREEZONE));
        packs.add(new PackDef("DSO", "Dubai Silicon Oasis / DTEC",
                "DIEZ/DSOA fit-out; separate civil/electrical/mechanical reviews may apply.",
                "OAFM", "DSO", PackFamily.FREEZONE));
        packs.add(new PackDef("DXBS", "Dubai South",
                "Dubai South portal; aviation-district restrictions may apply.",
                "OAFM", "DXBS", PackFamily.FREEZONE));
        packs.add(new PackDef("JAFZA", "Jebel Ali Free Zone",
                "JAFZA Dubai Trade fit-out with additional EHS for industrial units.",
                "OAFM", "JAFZA", PackFamily.FREEZONE));
        return packs;
    }

    private record ScopeTagDef(String code, String name, String description) {
    }

    private record PermitTagLink(String permitCode, String tagCode) {
    }

    private static PermitTagLink link(String permitCode, String tagCode) {
        return new PermitTagLink(permitCode, tagCode);
    }

    private record PackDef(
            String code,
            String name,
            String description,
            String communityCode,
            String primaryCode,
            PackFamily family) {
    }

    private enum PackFamily {
        COMMUNITY(List.of(
                line("P-COMM-REG", "ALWAYS"),
                line("P-COMM-NOC", "ALWAYS"),
                line("P-ACCESS", "ALWAYS"),
                line("P-MOD", "ALWAYS"),
                line("P-DCD-NOC", "ALWAYS"),
                line("P-DCD-FINAL", "ALWAYS"),
                line("P-WASTE", "ALWAYS"),
                line("P-COMPLETE", "ALWAYS"),
                line("P-DEPOSIT", "ALWAYS"),
                line("P-DEMO", "OPTIONAL_DEMO"),
                line("P-DISCONNECT", "OPTIONAL_DEMO"),
                line("P-LOAD", "OPTIONAL_LOAD"),
                line("P-RECONNECT", "OPTIONAL_LOAD"),
                line("P-KITCHEN", "OPTIONAL_KITCHEN"),
                line("P-SIRA", "OPTIONAL_SIRA"),
                line("P-RTA", "OPTIONAL_RTA"))),
        TOWER(List.of(
                line("P-BLDG-NOC", "ALWAYS"),
                line("P-ACCESS", "ALWAYS"),
                line("P-LIFT", "ALWAYS"),
                line("P-FITOUT", "ALWAYS"),
                line("P-DCD-NOC", "ALWAYS"),
                line("P-DCD-FINAL", "ALWAYS"),
                line("P-WASTE", "ALWAYS"),
                line("P-COMPLETE", "ALWAYS"),
                line("P-DEPOSIT", "ALWAYS"),
                line("P-HOT", "ALWAYS"),
                line("P-DEMO", "OPTIONAL_DEMO"),
                line("P-DISCONNECT", "OPTIONAL_DEMO"),
                line("P-LOAD", "OPTIONAL_LOAD"),
                line("P-RECONNECT", "OPTIONAL_LOAD"),
                line("P-KITCHEN", "OPTIONAL_KITCHEN"),
                line("P-SIRA", "OPTIONAL_SIRA"),
                line("P-RTA", "OPTIONAL_RTA"))),
        MAINLAND(List.of(
                line("P-MOD", "ALWAYS"),
                line("P-DCD-NOC", "ALWAYS"),
                line("P-DCD-FINAL", "ALWAYS"),
                line("P-WASTE", "ALWAYS"),
                line("P-COMPLETE", "ALWAYS"),
                line("P-DEMO", "OPTIONAL_DEMO"),
                line("P-DISCONNECT", "OPTIONAL_DEMO"),
                line("P-LOAD", "OPTIONAL_LOAD"),
                line("P-RECONNECT", "OPTIONAL_LOAD"),
                line("P-KITCHEN", "OPTIONAL_KITCHEN"),
                line("P-SIRA", "OPTIONAL_SIRA"),
                line("P-RTA", "OPTIONAL_RTA"))),
        FREEZONE(List.of(
                line("P-BLDG-NOC", "ALWAYS"),
                line("P-ACCESS", "ALWAYS"),
                line("P-FITOUT", "ALWAYS"),
                line("P-DCD-NOC", "ALWAYS"),
                line("P-DCD-FINAL", "ALWAYS"),
                line("P-WASTE", "ALWAYS"),
                line("P-COMPLETE", "ALWAYS"),
                line("P-DEPOSIT", "ALWAYS"),
                line("P-LIFT", "ALWAYS"),
                line("P-DEMO", "OPTIONAL_DEMO"),
                line("P-DISCONNECT", "OPTIONAL_DEMO"),
                line("P-LOAD", "OPTIONAL_LOAD"),
                line("P-RECONNECT", "OPTIONAL_LOAD"),
                line("P-KITCHEN", "OPTIONAL_KITCHEN"),
                line("P-SIRA", "OPTIONAL_SIRA"),
                line("P-RTA", "OPTIONAL_RTA")));

        final List<PermitLine> lines;

        PackFamily(List<PermitLine> lines) {
            this.lines = lines;
        }
    }

    private record PermitLine(String permitCode, String rule) {
    }

    private static PermitLine line(String code, String rule) {
        return new PermitLine(code, rule);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SeedFile {
        public List<AuthorityRow> authorities = List.of();
        @JsonProperty("permit_types")
        public List<PermitRow> permitTypes = List.of();
        @JsonProperty("document_types")
        public List<DocumentRow> documentTypes = List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AuthorityRow {
        public String code;
        @JsonProperty("authority__body")
        public String body;
        public String type;
        public String emirate;
        @JsonProperty("jurisdiction__areas_covered")
        public String areas;
        @JsonProperty("permits_issued_typical")
        public String permits;
        @JsonProperty("submission_channel")
        public String channel;
        @JsonProperty("notes_for_configuration")
        public String notes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PermitRow {
        @JsonProperty("permit_code")
        public String permitCode;
        @JsonProperty("permit__case_type")
        public String caseType;
        @JsonProperty("issuing_body")
        public String issuingBody;
        @JsonProperty("typical_trigger")
        public String trigger;
        @JsonProperty("prerequisite_cases")
        public String prerequisites;
        @JsonProperty("indicative_sla_working_days")
        public String sla;
        @JsonProperty("typical_validity")
        public String validity;
        public String deposit;
        public String renewable;
        @JsonProperty("blocks_which_activities")
        public String blocks;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DocumentRow {
        @JsonProperty("doc_code")
        public String docCode;
        public String document;
        public String category;
        @JsonProperty("typically_required_for")
        public String requiredFor;
        @JsonProperty("expiry_tracked")
        public String expiryTracked;
        @JsonProperty("source__owner")
        public String sourceOwner;
    }
}
