package com.fitouts.subcontractor.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.subcontractor.api.ScBankDetailRequest;
import com.fitouts.subcontractor.api.ScBankDetailResponse;
import com.fitouts.subcontractor.api.ScCommunityRegistrationRequest;
import com.fitouts.subcontractor.api.ScCommunityRegistrationResponse;
import com.fitouts.subcontractor.api.ScOrganizationExtensionRequest;
import com.fitouts.subcontractor.api.ScOrganizationExtensionResponse;
import com.fitouts.subcontractor.api.ScOrganizationReferenceRequest;
import com.fitouts.subcontractor.api.ScOrganizationReferenceResponse;
import com.fitouts.subcontractor.domain.ScBankDetail;
import com.fitouts.subcontractor.domain.ScBankDetailRepository;
import com.fitouts.subcontractor.domain.ScBankVerificationStatus;
import com.fitouts.subcontractor.domain.ScCommunityRegistration;
import com.fitouts.subcontractor.domain.ScCommunityRegistrationRepository;
import com.fitouts.subcontractor.domain.ScCompanyProfile;
import com.fitouts.subcontractor.domain.ScOrgDocumentCode;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScOrganizationDocument;
import com.fitouts.subcontractor.domain.ScOrganizationDocumentRepository;
import com.fitouts.subcontractor.domain.ScOrganizationReference;
import com.fitouts.subcontractor.domain.ScOrganizationReferenceRepository;
import com.fitouts.subcontractor.domain.ScOrganizationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScOrganizationProfileService {

    public static final List<String> COMMUNITY_AUTHORITY_OPTIONS = List.of(
            "Emaar",
            "Dubai Holding",
            "Nakheel",
            "Meraas",
            "Dubai Hills",
            "Arabian Ranches",
            "DAMAC",
            "Sobha",
            "Azizi",
            "Ellington",
            "Other");

    private final ScOrganizationRepository organizationRepository;
    private final ScOrganizationDocumentRepository documentRepository;
    private final ScBankDetailRepository bankDetailRepository;
    private final ScCommunityRegistrationRepository communityRepository;
    private final ScOrganizationReferenceRepository referenceRepository;
    private final ScOrganizationResolver organizationResolver;
    private final ScPortalAccessService portalAccessService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public ScOrganizationExtensionResponse getExtension(AuthPrincipal principal, ScCompanyProfile profile) {
        ScOrganization org = resolveOrg(principal, profile);
        return toExtensionResponse(org, principal);
    }

    @Transactional
    public ScOrganizationExtensionResponse updateExtension(
            AuthPrincipal principal, ScCompanyProfile profile, ScOrganizationExtensionRequest request) {
        portalAccessService.requireOrgAdmin(principal);
        ScOrganization org = resolveOrg(principal, profile);
        if (request == null) {
            return toExtensionResponse(org, principal);
        }
        if (request.getTradeLicenceAuthority() != null) {
            org.setTradeLicenceAuthority(trim(request.getTradeLicenceAuthority()));
        }
        if (request.getTradeLicenceActivities() != null) {
            org.setTradeLicenceActivities(trim(request.getTradeLicenceActivities()));
        }
        if (request.getEstablishmentCardExpiry() != null) {
            org.setEstablishmentCardExpiry(parseDate(request.getEstablishmentCardExpiry()));
        }
        if (request.getLocationPin() != null) {
            org.setLocationPin(trim(request.getLocationPin()));
        }
        if (request.getMonthlyCapacityValue() != null) {
            org.setMonthlyCapacityValue(parseDecimal(request.getMonthlyCapacityValue()));
        }
        if (request.getMonthlyCapacityManpower() != null) {
            org.setMonthlyCapacityManpower(request.getMonthlyCapacityManpower());
        }
        if (request.getYearsInOperation() != null) {
            org.setYearsInOperation(request.getYearsInOperation());
        }
        if (request.getAnnualTurnoverBand() != null) {
            org.setAnnualTurnoverBand(trim(request.getAnnualTurnoverBand()));
        }
        if (request.getWorkshopAddress() != null) {
            org.setWorkshopAddress(trim(request.getWorkshopAddress()));
        }
        if (request.getHseLtiCount() != null) {
            org.setHseLtiCount(request.getHseLtiCount());
        }
        if (request.getHseOfficerName() != null) {
            org.setHseOfficerName(trim(request.getHseOfficerName()));
        }
        if (request.getHseOfficerCertExpiry() != null) {
            org.setHseOfficerCertExpiry(parseDate(request.getHseOfficerCertExpiry()));
        }
        if (request.getPaymentTermsAccepted() != null) {
            org.setPaymentTermsAccepted(trim(request.getPaymentTermsAccepted()));
        }
        if (request.getRetentionPctAccepted() != null) {
            org.setRetentionPctAccepted(parseDecimal(request.getRetentionPctAccepted()));
        }
        if (request.getAdvancePaymentRequired() != null) {
            org.setAdvancePaymentRequired(parseDecimal(request.getAdvancePaymentRequired()));
        }
        if (request.getWorkforceTotal() != null) {
            org.setWorkforceTotal(request.getWorkforceTotal());
        }
        if (request.getWorkforceTradeBreakdown() != null) {
            org.setWorkforceTradeBreakdown(trim(request.getWorkforceTradeBreakdown()));
        }
        if (request.getCrossTenantVisible() != null) {
            org.setCrossTenantVisible(request.getCrossTenantVisible());
        }
        organizationRepository.save(org);
        syncLegacyFields(profile, org);
        return toExtensionResponse(org, principal);
    }

    @Transactional
    public void syncFromLegacyProfile(ScCompanyProfile profile) {
        if (profile.getOrganizationUuid() == null) {
            return;
        }
        ScOrganization org = organizationRepository.findById(profile.getOrganizationUuid()).orElse(null);
        if (org == null) {
            return;
        }
        syncLegacyFields(profile, org);
        organizationRepository.save(org);
    }

    @Transactional
    public ScBankDetailResponse updateBankDetail(
            AuthPrincipal principal, ScCompanyProfile profile, ScBankDetailRequest request) {
        portalAccessService.requireOrgAdmin(principal);
        ScOrganization org = resolveOrg(principal, profile);
        ScBankDetail bank = bankDetailRepository.findByOrganizationUuid(org.getUuid())
                .orElseGet(() -> {
                    ScBankDetail created = new ScBankDetail();
                    created.setOrganizationUuid(org.getUuid());
                    return created;
                });
        boolean materialChange = false;
        if (request != null) {
            if (request.getBankName() != null && !equalsTrim(bank.getBankName(), request.getBankName())) {
                bank.setBankName(trim(request.getBankName()));
                materialChange = true;
            }
            if (request.getAccountName() != null && !equalsTrim(bank.getAccountName(), request.getAccountName())) {
                bank.setAccountName(trim(request.getAccountName()));
                materialChange = true;
            }
            if (request.getIban() != null && !equalsTrim(bank.getIban(), request.getIban())) {
                bank.setIban(trim(request.getIban()));
                materialChange = true;
            }
            if (request.getSwiftCode() != null && !equalsTrim(bank.getSwiftCode(), request.getSwiftCode())) {
                bank.setSwiftCode(trim(request.getSwiftCode()));
                materialChange = true;
            }
        }
        if (materialChange && bank.getVerificationStatus() == ScBankVerificationStatus.VERIFIED) {
            bank.setVerificationStatus(ScBankVerificationStatus.CHANGE_PENDING);
            bank.setVerifiedAt(null);
            bank.setVerifiedByAccountId(null);
        }
        return toBankResponse(bankDetailRepository.save(bank));
    }

    @Transactional
    public ScBankDetailResponse uploadBankLetter(AuthPrincipal principal, ScCompanyProfile profile, MultipartFile file) {
        portalAccessService.requireOrgAdmin(principal);
        ScOrganization org = resolveOrg(principal, profile);
        ScBankDetail bank = bankDetailRepository.findByOrganizationUuid(org.getUuid())
                .orElseGet(() -> {
                    ScBankDetail created = new ScBankDetail();
                    created.setOrganizationUuid(org.getUuid());
                    return created;
                });
        String path = fileStorageService.store(file, "sc-org/" + org.getUuid() + "/bank");
        bank.setBankLetterFilePath(path);
        if (bank.getVerificationStatus() == ScBankVerificationStatus.VERIFIED) {
            bank.setVerificationStatus(ScBankVerificationStatus.CHANGE_PENDING);
            bank.setVerifiedAt(null);
            bank.setVerifiedByAccountId(null);
        }
        return toBankResponse(bankDetailRepository.save(bank));
    }

    @Transactional
    public ScCommunityRegistrationResponse addCommunityRegistration(
            AuthPrincipal principal, ScCompanyProfile profile, ScCommunityRegistrationRequest request) {
        portalAccessService.requireOrgAdmin(principal);
        if (request == null || !StringUtils.hasText(request.getAuthorityCode())) {
            throw new BadRequestException("Community authority code is required");
        }
        ScOrganization org = resolveOrg(principal, profile);
        ScCommunityRegistration reg = new ScCommunityRegistration();
        reg.setOrganizationUuid(org.getUuid());
        reg.setAuthorityCode(request.getAuthorityCode().trim().toUpperCase());
        reg.setAuthorityName(trim(request.getAuthorityName()));
        reg.setRegistrationNo(trim(request.getRegistrationNo()));
        if (request.getExpiryDate() != null) {
            reg.setExpiryDate(parseDate(request.getExpiryDate()));
        }
        return toCommunityResponse(communityRepository.save(reg));
    }

    @Transactional
    public ScCommunityRegistrationResponse uploadCommunityFile(
            AuthPrincipal principal, ScCompanyProfile profile, UUID registrationUuid, MultipartFile file) {
        portalAccessService.requireOrgAdmin(principal);
        ScOrganization org = resolveOrg(principal, profile);
        ScCommunityRegistration reg = communityRepository.findById(registrationUuid)
                .filter(r -> r.getOrganizationUuid().equals(org.getUuid()))
                .orElseThrow(() -> new BadRequestException("Community registration not found"));
        reg.setFilePath(fileStorageService.store(file, "sc-org/" + org.getUuid() + "/community"));
        return toCommunityResponse(communityRepository.save(reg));
    }

    @Transactional
    public void deleteCommunityRegistration(AuthPrincipal principal, ScCompanyProfile profile, UUID registrationUuid) {
        portalAccessService.requireOrgAdmin(principal);
        ScOrganization org = resolveOrg(principal, profile);
        ScCommunityRegistration reg = communityRepository.findById(registrationUuid)
                .filter(r -> r.getOrganizationUuid().equals(org.getUuid()))
                .orElseThrow(() -> new BadRequestException("Community registration not found"));
        communityRepository.delete(reg);
    }

    @Transactional
    public ScOrganizationReferenceResponse addReference(
            AuthPrincipal principal, ScCompanyProfile profile, ScOrganizationReferenceRequest request) {
        portalAccessService.requireOrgAdmin(principal);
        ScOrganization org = resolveOrg(principal, profile);
        ScOrganizationReference ref = new ScOrganizationReference();
        ref.setOrganizationUuid(org.getUuid());
        applyReference(ref, request);
        return toReferenceResponse(referenceRepository.save(ref));
    }

    @Transactional
    public ScOrganizationReferenceResponse updateReference(
            AuthPrincipal principal, ScCompanyProfile profile, UUID referenceUuid, ScOrganizationReferenceRequest request) {
        portalAccessService.requireOrgAdmin(principal);
        ScOrganization org = resolveOrg(principal, profile);
        ScOrganizationReference ref = referenceRepository.findByUuidAndOrganizationUuid(referenceUuid, org.getUuid())
                .orElseThrow(() -> new BadRequestException("Reference not found"));
        applyReference(ref, request);
        return toReferenceResponse(referenceRepository.save(ref));
    }

    @Transactional
    public void deleteReference(AuthPrincipal principal, ScCompanyProfile profile, UUID referenceUuid) {
        portalAccessService.requireOrgAdmin(principal);
        ScOrganization org = resolveOrg(principal, profile);
        ScOrganizationReference ref = referenceRepository.findByUuidAndOrganizationUuid(referenceUuid, org.getUuid())
                .orElseThrow(() -> new BadRequestException("Reference not found"));
        referenceRepository.delete(ref);
    }

    @Transactional
    public void uploadOrgDocument(
            AuthPrincipal principal, ScCompanyProfile profile, ScOrgDocumentCode code, MultipartFile file) {
        portalAccessService.requireOrgAdmin(principal);
        ScOrganization org = resolveOrg(principal, profile);
        ScOrganizationDocument doc = documentRepository
                .findByOrganizationUuidAndDocumentCode(org.getUuid(), code)
                .orElseGet(() -> {
                    ScOrganizationDocument created = new ScOrganizationDocument();
                    created.setOrganizationUuid(org.getUuid());
                    created.setDocumentCode(code);
                    return created;
                });
        String path = fileStorageService.store(file, "sc-org/" + org.getUuid() + "/docs");
        doc.setFilePath(path);
        documentRepository.save(doc);
        mirrorOrgDocumentToOrganization(org, code, path);
        organizationRepository.save(org);
    }

    @Transactional(readOnly = true)
    public List<String> communityAuthorityOptions() {
        return COMMUNITY_AUTHORITY_OPTIONS;
    }

    private void mirrorOrgDocumentToOrganization(ScOrganization org, ScOrgDocumentCode code, String path) {
        switch (code) {
            case ESTABLISHMENT_CARD -> org.setEstablishmentCardFilePath(path);
            case VAT_CERTIFICATE -> org.setVatCertificateFilePath(path);
            case HSE_POLICY -> org.setHsePolicyFilePath(path);
            case HSE_OFFICER_CERT -> org.setHseOfficerCertFilePath(path);
            default -> { }
        }
    }

    private void syncLegacyFields(ScCompanyProfile profile, ScOrganization org) {
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
    }

    private ScOrganization resolveOrg(AuthPrincipal principal, ScCompanyProfile profile) {
        if (profile.getOrganizationUuid() != null) {
            return organizationRepository.findById(profile.getOrganizationUuid())
                    .orElseGet(() -> organizationResolver.resolveOrganization(principal.getAccountId()));
        }
        return organizationResolver.resolveOrganization(principal.getAccountId());
    }

    private ScOrganizationExtensionResponse toExtensionResponse(ScOrganization org, AuthPrincipal principal) {
        ScOrganizationDocument hseOfficerDoc = documentRepository
                .findByOrganizationUuidAndDocumentCode(org.getUuid(), ScOrgDocumentCode.HSE_OFFICER_CERT)
                .orElse(null);
        return ScOrganizationExtensionResponse.builder()
                .organizationUuid(org.getUuid())
                .tradeLicenceAuthority(org.getTradeLicenceAuthority())
                .tradeLicenceActivities(org.getTradeLicenceActivities())
                .establishmentCardExpiry(formatDate(org.getEstablishmentCardExpiry()))
                .establishmentCardFilePath(org.getEstablishmentCardFilePath())
                .vatCertificateFilePath(org.getVatCertificateFilePath())
                .locationPin(org.getLocationPin())
                .monthlyCapacityValue(org.getMonthlyCapacityValue())
                .monthlyCapacityManpower(org.getMonthlyCapacityManpower())
                .yearsInOperation(org.getYearsInOperation())
                .annualTurnoverBand(org.getAnnualTurnoverBand())
                .workshopAddress(org.getWorkshopAddress())
                .hsePolicyFilePath(org.getHsePolicyFilePath())
                .hseLtiCount(org.getHseLtiCount())
                .hseOfficerName(org.getHseOfficerName())
                .hseOfficerCertExpiry(formatDate(org.getHseOfficerCertExpiry()))
                .hseOfficerCertFilePath(hseOfficerDoc != null ? hseOfficerDoc.getFilePath() : org.getHseOfficerCertFilePath())
                .paymentTermsAccepted(org.getPaymentTermsAccepted())
                .retentionPctAccepted(org.getRetentionPctAccepted())
                .advancePaymentRequired(org.getAdvancePaymentRequired())
                .workforceTotal(org.getWorkforceTotal())
                .workforceTradeBreakdown(org.getWorkforceTradeBreakdown())
                .crossTenantVisible(org.isCrossTenantVisible())
                .bankDetail(bankDetailRepository.findByOrganizationUuid(org.getUuid()).map(this::toBankResponse).orElse(null))
                .communityRegistrations(communityRepository.findByOrganizationUuidOrderByAuthorityNameAsc(org.getUuid())
                        .stream().map(this::toCommunityResponse).toList())
                .references(referenceRepository.findByOrganizationUuidOrderByYearCompletedDesc(org.getUuid())
                        .stream().map(this::toReferenceResponse).toList())
                .portalContext(portalAccessService.buildPortalContext(principal))
                .build();
    }

    private ScBankDetailResponse toBankResponse(ScBankDetail bank) {
        return ScBankDetailResponse.builder()
                .bankName(bank.getBankName())
                .accountName(bank.getAccountName())
                .iban(bank.getIban())
                .swiftCode(bank.getSwiftCode())
                .bankLetterFilePath(bank.getBankLetterFilePath())
                .verificationStatus(bank.getVerificationStatus().name())
                .build();
    }

    private ScCommunityRegistrationResponse toCommunityResponse(ScCommunityRegistration reg) {
        return ScCommunityRegistrationResponse.builder()
                .uuid(reg.getUuid())
                .authorityCode(reg.getAuthorityCode())
                .authorityName(reg.getAuthorityName())
                .registrationNo(reg.getRegistrationNo())
                .expiryDate(formatDate(reg.getExpiryDate()))
                .filePath(reg.getFilePath())
                .build();
    }

    private ScOrganizationReferenceResponse toReferenceResponse(ScOrganizationReference ref) {
        return ScOrganizationReferenceResponse.builder()
                .uuid(ref.getUuid())
                .clientName(ref.getClientName())
                .projectValue(ref.getProjectValue())
                .yearCompleted(ref.getYearCompleted())
                .scopeDescription(ref.getScopeDescription())
                .contactName(ref.getContactName())
                .contactPhone(ref.getContactPhone())
                .contactEmail(ref.getContactEmail())
                .build();
    }

    private void applyReference(ScOrganizationReference ref, ScOrganizationReferenceRequest request) {
        if (request == null) {
            return;
        }
        if (request.getClientName() != null) {
            ref.setClientName(trim(request.getClientName()));
        }
        if (request.getProjectValue() != null) {
            ref.setProjectValue(request.getProjectValue());
        }
        if (request.getYearCompleted() != null) {
            ref.setYearCompleted(request.getYearCompleted());
        }
        if (request.getScopeDescription() != null) {
            ref.setScopeDescription(trim(request.getScopeDescription()));
        }
        if (request.getContactName() != null) {
            ref.setContactName(trim(request.getContactName()));
        }
        if (request.getContactPhone() != null) {
            ref.setContactPhone(trim(request.getContactPhone()));
        }
        if (request.getContactEmail() != null) {
            ref.setContactEmail(trim(request.getContactEmail()));
        }
    }

    private static String formatDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid date: " + value);
        }
    }

    private static BigDecimal parseDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid number: " + value);
        }
    }

    private static String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static boolean equalsTrim(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        return left.equals(right);
    }
}
