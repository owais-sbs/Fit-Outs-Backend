package com.fitouts.subcontractor.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.domain.ScCompanyProfile;
import com.fitouts.subcontractor.domain.ScCompanyProfileRepository;
import com.fitouts.subcontractor.domain.ScCompanyStatus;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScOrganizationRepository;
import com.fitouts.subcontractor.domain.ScPortalRole;
import com.fitouts.subcontractor.domain.ScPortalUser;
import com.fitouts.subcontractor.domain.ScPortalUserRepository;
import com.fitouts.subcontractor.domain.ScPortalUserStatus;
import com.fitouts.subcontractor.domain.ScTenantMembership;
import com.fitouts.subcontractor.domain.ScTenantMembershipRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScOrganizationResolver {

    private final ScPortalUserRepository portalUserRepository;
    private final ScOrganizationRepository organizationRepository;
    private final ScTenantMembershipRepository membershipRepository;
    private final ScCompanyProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public ScPortalUser resolvePortalUser(Long accountId) {
        return portalUserRepository.findByAccountId(accountId)
                .orElseThrow(() -> new NotFoundException("Subcontractor portal user not found"));
    }

    @Transactional(readOnly = true)
    public ScOrganization resolveOrganization(Long accountId) {
        ScPortalUser portalUser = resolvePortalUser(accountId);
        return organizationRepository.findById(portalUser.getOrganizationUuid())
                .orElseThrow(() -> new NotFoundException("Subcontractor organization not found"));
    }

    @Transactional(readOnly = true)
    public ScTenantMembership resolveMembership(UUID organizationUuid) {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return membershipRepository.findByOrganizationUuidAndCompanyId(organizationUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Subcontractor is not linked to this tenant"));
    }

    @Transactional
    public ScOrganization bootstrapForAccount(Long accountId, UUID tenantCompanyId) {
        ScPortalUser existing = portalUserRepository.findByAccountId(accountId).orElse(null);
        if (existing != null) {
            return organizationRepository.findById(existing.getOrganizationUuid())
                    .orElseThrow(() -> new NotFoundException("Subcontractor organization not found"));
        }

        ScCompanyProfile profile = profileRepository
                .findByAdminAccountIdAndCompanyId(accountId, tenantCompanyId)
                .orElse(null);

        ScOrganization org;
        if (profile != null && profile.getOrganizationUuid() != null) {
            org = organizationRepository.findById(profile.getOrganizationUuid())
                    .orElseGet(() -> createOrgFromProfile(profile));
        } else {
            org = new ScOrganization();
            org = organizationRepository.save(org);
            if (profile != null) {
                profile.setOrganizationUuid(org.getUuid());
                profileRepository.save(profile);
            }
        }

        final UUID organizationUuid = org.getUuid();
        final ScCompanyStatus membershipStatus = profile != null ? profile.getStatus() : ScCompanyStatus.INVITED;

        membershipRepository.findByOrganizationUuidAndCompanyId(organizationUuid, tenantCompanyId)
                .orElseGet(() -> {
                    ScTenantMembership m = new ScTenantMembership();
                    m.setOrganizationUuid(organizationUuid);
                    m.setCompanyId(tenantCompanyId);
                    m.setStatus(membershipStatus);
                    return membershipRepository.save(m);
                });

        ScPortalUser portalUser = new ScPortalUser();
        portalUser.setOrganizationUuid(organizationUuid);
        portalUser.setAccountId(accountId);
        portalUser.setPortalRole(ScPortalRole.SC_ADMIN);
        portalUser.setStatus(ScPortalUserStatus.ACTIVE);
        portalUserRepository.save(portalUser);

        return org;
    }

    private ScOrganization createOrgFromProfile(ScCompanyProfile profile) {
        ScOrganization org = new ScOrganization();
        org.setLegalCompanyName(profile.getLegalCompanyName());
        org.setTradeLicenceNumber(profile.getTradeLicenceNumber());
        org.setTradeLicenceFilePath(profile.getTradeLicenceFilePath());
        org.setTradeLicenceExpiry(profile.getTradeLicenceExpiry());
        org.setTrn(profile.getTrn());
        org.setRegisteredAddress(profile.getRegisteredAddress());
        org.setPrimaryContactName(profile.getPrimaryContactName());
        org.setPrimaryContactEmail(profile.getPrimaryContactEmail());
        org.setPrimaryContactPhone(profile.getPrimaryContactPhone());
        org.setAccountsContactName(profile.getAccountsContactName());
        org.setAccountsContactEmail(profile.getAccountsContactEmail());
        org.setAccountsContactPhone(profile.getAccountsContactPhone());
        org.setTradeCategories(profile.getTradeCategories());
        org.setDeclaredCapacity(profile.getDeclaredCapacity());
        org = organizationRepository.save(org);
        profile.setOrganizationUuid(org.getUuid());
        profileRepository.save(profile);
        return org;
    }
}
