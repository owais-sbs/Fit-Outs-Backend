package com.fitouts.approvalconfig.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.approvalconfig.api.JurisdictionPackRequest;
import com.fitouts.approvalconfig.api.JurisdictionPackResponse;
import com.fitouts.approvalconfig.api.PackDocumentResponse;
import com.fitouts.approvalconfig.api.PackPermitLineRequest;
import com.fitouts.approvalconfig.api.PackPermitResponse;
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
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JurisdictionPackService {

    private final ApprovalConfigSeedService seedService;
    private final JurisdictionPackRepository packRepository;
    private final JurisdictionPackPermitRepository packPermitRepository;
    private final JurisdictionPackDocumentRepository packDocumentRepository;
    private final ApprovalAuthorityRepository authorityRepository;
    private final ApprovalPermitTypeRepository permitTypeRepository;
    private final ApprovalDocumentTypeRepository documentTypeRepository;

    @Transactional(readOnly = true)
    public List<JurisdictionPackResponse> list(boolean selectableOnly) {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        List<JurisdictionPack> packs = selectableOnly
                ? packRepository.findByCompanyIdAndDeletedFalseAndSelectableTrueAndActiveTrueOrderByNameAsc(companyId)
                : packRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId);
        return toResponses(companyId, packs);
    }

    @Transactional
    public JurisdictionPackResponse get(UUID id) {
        return toResponse(pack(id));
    }

    @Transactional
    public JurisdictionPackResponse create(JurisdictionPackRequest request) {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        JurisdictionPack pack = new JurisdictionPack();
        pack.setCompanyId(companyId);
        applyHeader(pack, request, true);
        pack = packRepository.save(pack);
        replacePermits(pack, request.getPermits());
        return toResponse(pack);
    }

    @Transactional
    public JurisdictionPackResponse update(UUID id, JurisdictionPackRequest request) {
        JurisdictionPack pack = pack(id);
        applyHeader(pack, request, false);
        pack = packRepository.save(pack);
        if (request.getPermits() != null) {
            replacePermits(pack, request.getPermits());
        }
        return toResponse(pack);
    }

    @Transactional
    public void delete(UUID id) {
        JurisdictionPack pack = pack(id);
        pack.setDeleted(true);
        pack.setActive(false);
        pack.setSelectable(false);
        packRepository.save(pack);
    }

    private void applyHeader(JurisdictionPack pack, JurisdictionPackRequest request, boolean creating) {
        if (creating || StringUtils.hasText(request.getCode())) {
            pack.setCode(request.getCode().trim().toUpperCase());
        }
        if (creating || StringUtils.hasText(request.getName())) {
            pack.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            pack.setDescription(request.getDescription());
        }
        if (request.getCommunityAuthorityId() != null) {
            pack.setCommunityAuthorityId(request.getCommunityAuthorityId());
        }
        if (request.getPrimaryAuthorityId() != null) {
            pack.setPrimaryAuthorityId(request.getPrimaryAuthorityId());
        }
        if (request.getSelectable() != null) {
            pack.setSelectable(request.getSelectable());
        }
        if (request.getActive() != null) {
            pack.setActive(request.getActive());
        }
    }

    private void replacePermits(JurisdictionPack pack, List<PackPermitLineRequest> lines) {
        List<JurisdictionPackPermit> existing = packPermitRepository.findByPackIdOrderBySortOrderAsc(pack.getId());
        List<UUID> existingIds = existing.stream().map(JurisdictionPackPermit::getId).toList();
        if (!existingIds.isEmpty()) {
            packDocumentRepository.deleteByPackPermitIdIn(existingIds);
        }
        packPermitRepository.deleteByPackId(pack.getId());
        if (lines == null) {
            return;
        }
        int sort = 0;
        for (PackPermitLineRequest line : lines) {
            if (line.getPermitTypeId() == null) {
                continue;
            }
            JurisdictionPackPermit permit = new JurisdictionPackPermit();
            permit.setPackId(pack.getId());
            permit.setPermitTypeId(line.getPermitTypeId());
            permit.setIssuingAuthorityId(line.getIssuingAuthorityId());
            permit.setInclusionRule(StringUtils.hasText(line.getInclusionRule()) ? line.getInclusionRule() : "ALWAYS");
            permit.setSortOrder(line.getSortOrder() != null ? line.getSortOrder() : sort);
            permit = packPermitRepository.save(permit);
            sort++;
            if (line.getDocumentTypeIds() == null) {
                continue;
            }
            for (UUID documentTypeId : line.getDocumentTypeIds()) {
                JurisdictionPackDocument doc = new JurisdictionPackDocument();
                doc.setPackPermitId(permit.getId());
                doc.setDocumentTypeId(documentTypeId);
                packDocumentRepository.save(doc);
            }
        }
    }

    private List<JurisdictionPackResponse> toResponses(UUID companyId, List<JurisdictionPack> packs) {
        if (packs.isEmpty()) {
            return List.of();
        }
        Map<UUID, ApprovalAuthority> authorities = authorityRepository
                .findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .collect(Collectors.toMap(ApprovalAuthority::getId, Function.identity()));
        Map<UUID, ApprovalPermitType> permits = permitTypeRepository
                .findByCompanyIdAndDeletedFalseOrderByPermitCodeAsc(companyId).stream()
                .collect(Collectors.toMap(ApprovalPermitType::getId, Function.identity()));
        Map<UUID, ApprovalDocumentType> documents = documentTypeRepository
                .findByCompanyIdAndDeletedFalseOrderByDocCodeAsc(companyId).stream()
                .collect(Collectors.toMap(ApprovalDocumentType::getId, Function.identity()));

        List<UUID> packIds = packs.stream().map(JurisdictionPack::getId).toList();
        Map<UUID, List<JurisdictionPackPermit>> permitsByPack = packPermitRepository
                .findByPackIdInOrderBySortOrderAsc(packIds).stream()
                .collect(Collectors.groupingBy(JurisdictionPackPermit::getPackId));
        List<UUID> packPermitIds = permitsByPack.values().stream()
                .flatMap(List::stream)
                .map(JurisdictionPackPermit::getId)
                .toList();
        Map<UUID, List<JurisdictionPackDocument>> docsByPermit = packPermitIds.isEmpty()
                ? Map.of()
                : packDocumentRepository.findByPackPermitIdIn(packPermitIds).stream()
                        .collect(Collectors.groupingBy(JurisdictionPackDocument::getPackPermitId));

        return packs.stream()
                .map(pack -> toResponse(pack, authorities, permits, documents,
                        permitsByPack.getOrDefault(pack.getId(), List.of()), docsByPermit))
                .toList();
    }

    private JurisdictionPackResponse toResponse(JurisdictionPack pack) {
        return toResponses(pack.getCompanyId(), List.of(pack)).get(0);
    }

    private JurisdictionPackResponse toResponse(
            JurisdictionPack pack,
            Map<UUID, ApprovalAuthority> authorities,
            Map<UUID, ApprovalPermitType> permits,
            Map<UUID, ApprovalDocumentType> documents,
            List<JurisdictionPackPermit> lines,
            Map<UUID, List<JurisdictionPackDocument>> docsByPermit) {
        List<PackPermitResponse> permitResponses = new ArrayList<>();
        for (JurisdictionPackPermit line : lines) {
            ApprovalPermitType permit = permits.get(line.getPermitTypeId());
            ApprovalAuthority issuer = line.getIssuingAuthorityId() != null
                    ? authorities.get(line.getIssuingAuthorityId())
                    : null;
            List<PackDocumentResponse> docs = docsByPermit.getOrDefault(line.getId(), List.of()).stream()
                    .map(doc -> {
                        ApprovalDocumentType type = documents.get(doc.getDocumentTypeId());
                        return PackDocumentResponse.builder()
                                .id(doc.getId())
                                .documentTypeId(doc.getDocumentTypeId())
                                .docCode(type != null ? type.getDocCode() : null)
                                .documentName(type != null ? type.getName() : null)
                                .build();
                    })
                    .toList();
            permitResponses.add(PackPermitResponse.builder()
                    .id(line.getId())
                    .permitTypeId(line.getPermitTypeId())
                    .permitCode(permit != null ? permit.getPermitCode() : null)
                    .permitName(permit != null ? permit.getName() : null)
                    .issuingAuthorityId(line.getIssuingAuthorityId())
                    .issuingAuthorityName(issuer != null ? issuer.getName() : null)
                    .inclusionRule(line.getInclusionRule())
                    .sortOrder(line.getSortOrder())
                    .documents(docs)
                    .build());
        }

        ApprovalAuthority community = pack.getCommunityAuthorityId() != null
                ? authorities.get(pack.getCommunityAuthorityId())
                : null;
        ApprovalAuthority primary = pack.getPrimaryAuthorityId() != null
                ? authorities.get(pack.getPrimaryAuthorityId())
                : null;

        return JurisdictionPackResponse.builder()
                .id(pack.getId())
                .code(pack.getCode())
                .name(pack.getName())
                .description(pack.getDescription())
                .communityAuthorityId(pack.getCommunityAuthorityId())
                .communityAuthorityName(community != null ? community.getName() : null)
                .primaryAuthorityId(pack.getPrimaryAuthorityId())
                .primaryAuthorityName(primary != null ? primary.getName() : null)
                .selectable(pack.isSelectable())
                .active(pack.isActive())
                .permits(permitResponses)
                .build();
    }

    private JurisdictionPack pack(UUID id) {
        JurisdictionPack pack = packRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Jurisdiction pack not found"));
        if (!requireCompany().equals(pack.getCompanyId())) {
            throw new NotFoundException("Jurisdiction pack not found");
        }
        return pack;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new NotFoundException("Company context is required");
        }
        return companyId;
    }
}
