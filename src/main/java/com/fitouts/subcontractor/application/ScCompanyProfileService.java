package com.fitouts.subcontractor.application;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.api.ScAppointmentEligibilityResponse;
import com.fitouts.subcontractor.api.ScBankDetailRequest;
import com.fitouts.subcontractor.api.ScBankDetailResponse;
import com.fitouts.subcontractor.api.ScCommunityRegistrationRequest;
import com.fitouts.subcontractor.api.ScCommunityRegistrationResponse;
import com.fitouts.subcontractor.api.ScCompanyProfileRequest;
import com.fitouts.subcontractor.api.ScCompanyProfileResponse;
import com.fitouts.subcontractor.api.ScComplianceDocumentRequest;
import com.fitouts.subcontractor.api.ScComplianceDocumentResponse;
import com.fitouts.subcontractor.api.ScAddTeamMemberRequest;
import com.fitouts.subcontractor.api.ScInviteTeamMemberRequest;
import com.fitouts.subcontractor.api.ScOrganizationExtensionRequest;
import com.fitouts.subcontractor.api.ScOrganizationExtensionResponse;
import com.fitouts.subcontractor.api.ScOrganizationReferenceRequest;
import com.fitouts.subcontractor.api.ScOrganizationReferenceResponse;
import com.fitouts.subcontractor.api.ScPortalContextResponse;
import com.fitouts.subcontractor.api.ScPortalTeamMemberResponse;
import com.fitouts.subcontractor.api.ScUpdateTeamMemberRequest;
import com.fitouts.subcontractor.api.ScWorkerRequest;
import com.fitouts.subcontractor.api.ScWorkerResponse;
import com.fitouts.subcontractor.domain.ScOrgDocumentCode;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScCompanyProfile;
import com.fitouts.subcontractor.domain.ScCompanyProfileRepository;
import com.fitouts.subcontractor.domain.ScCompanyStatus;
import com.fitouts.subcontractor.domain.ScComplianceDocType;
import com.fitouts.subcontractor.domain.ScComplianceDocument;
import com.fitouts.subcontractor.domain.ScComplianceDocumentRepository;
import com.fitouts.subcontractor.domain.ScWorker;
import com.fitouts.subcontractor.domain.ScWorkerRepository;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScCompanyProfileService {

    public static final List<String> TRADE_CATEGORY_OPTIONS = List.of(
            "Joinery & carpentry",
            "MEP — electrical",
            "MEP — plumbing & drainage",
            "MEP — HVAC",
            "Partitions & drywall",
            "Ceilings",
            "Flooring",
            "Painting & decorating",
            "Tiling & stone",
            "Glazing & aluminium",
            "Fire protection",
            "Security & low voltage",
            "Scaffolding",
            "Demolition & strip-out",
            "General fit-out");

    private final ScCompanyProfileRepository profileRepository;
    private final ScComplianceDocumentRepository complianceRepository;
    private final ScWorkerRepository workerRepository;
    private final SubcontractorPackageRepository packageRepository;
    private final FileStorageService fileStorageService;
    private final ScEligibilityService eligibilityService;
    private final ObjectMapper objectMapper;
    private final ScOrganizationResolver organizationResolver;
    private final ScVendorService vendorService;
    private final ScOrganizationProfileService organizationProfileService;
    private final ScPortalAccessService portalAccessService;
    private final ScPortalTeamService portalTeamService;

    @Transactional
    public ScCompanyProfile ensureProfileForAccount(Long accountId) {
        UUID companyId = requireCompany();
        return profileRepository.findByAdminAccountIdAndCompanyId(accountId, companyId)
                .orElseGet(() -> {
                    ScCompanyProfile profile = new ScCompanyProfile();
                    profile.setCompanyId(companyId);
                    profile.setAdminAccountId(accountId);
                    profile.setStatus(ScCompanyStatus.INVITED);
                    profile = profileRepository.save(profile);
                    ScOrganization org = organizationResolver.bootstrapForAccount(accountId, companyId);
                    profile.setOrganizationUuid(org.getUuid());
                    return profileRepository.save(profile);
                });
    }

    @Transactional(readOnly = true)
    public ScCompanyProfile findProfileForAccount(Long accountId) {
        UUID companyId = requireCompany();
        return profileRepository.findByAdminAccountIdAndCompanyId(accountId, companyId)
                .orElseThrow(() -> new NotFoundException("Subcontractor company profile not found"));
    }

    @Transactional
    public ScCompanyProfileResponse getMyProfile() {
        AuthPrincipal principal = requireSubcontractor();
        portalAccessService.requireOrgAdmin(principal);
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        return toProfileResponse(profile, principal);
    }

    @Transactional
    public ScCompanyProfileResponse updateMyProfile(ScCompanyProfileRequest request) {
        AuthPrincipal principal = requireSubcontractor();
        portalAccessService.requireOrgAdmin(principal);
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        applyProfileRequest(profile, request, false);
        if (profile.getStatus() == ScCompanyStatus.INVITED) {
            profile.setStatus(ScCompanyStatus.REGISTERED);
        }
        ScCompanyProfile saved = profileRepository.save(profile);
        organizationProfileService.syncFromLegacyProfile(saved);
        vendorService.markProfileSubmittedForReview(principal.getAccountId());
        return toProfileResponse(saved, principal);
    }

    @Transactional
    public ScCompanyProfileResponse uploadTradeLicence(MultipartFile file) {
        AuthPrincipal principal = requireSubcontractor();
        portalAccessService.requireOrgAdmin(principal);
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        String path = fileStorageService.store(file, "sc-compliance/" + profile.getUuid());
        profile.setTradeLicenceFilePath(path);
        if (profile.getStatus() == ScCompanyStatus.INVITED) {
            profile.setStatus(ScCompanyStatus.REGISTERED);
        }
        ScCompanyProfile saved = profileRepository.save(profile);
        organizationProfileService.syncFromLegacyProfile(saved);
        return toProfileResponse(saved, principal);
    }

    @Transactional
    public ScComplianceDocumentResponse upsertComplianceDocument(
            ScComplianceDocType docType, ScComplianceDocumentRequest request) {
        AuthPrincipal principal = requireSubcontractor();
        portalAccessService.requireOrgAdmin(principal);
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        ScComplianceDocument doc = complianceRepository
                .findByProfileUuidAndDocumentType(profile.getUuid(), docType)
                .orElseGet(() -> {
                    ScComplianceDocument created = new ScComplianceDocument();
                    created.setProfileUuid(profile.getUuid());
                    created.setOrganizationUuid(profile.getOrganizationUuid());
                    created.setDocumentType(docType);
                    return created;
                });
        if (request != null) {
            if (request.getReferenceNo() != null) {
                doc.setReferenceNo(trimToNull(request.getReferenceNo()));
            }
            if (request.getExpiryDate() != null) {
                doc.setExpiryDate(parseDate(request.getExpiryDate(), "expiryDate"));
            }
        }
        if (profile.getStatus() == ScCompanyStatus.INVITED) {
            profile.setStatus(ScCompanyStatus.REGISTERED);
            profileRepository.save(profile);
        }
        return toComplianceResponse(complianceRepository.save(doc), isCoreInsurance(docType));
    }

    @Transactional
    public ScComplianceDocumentResponse uploadComplianceDocument(
            ScComplianceDocType docType, MultipartFile file) {
        AuthPrincipal principal = requireSubcontractor();
        portalAccessService.requireOrgAdmin(principal);
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        ScComplianceDocument doc = complianceRepository
                .findByProfileUuidAndDocumentType(profile.getUuid(), docType)
                .orElseGet(() -> {
                    ScComplianceDocument created = new ScComplianceDocument();
                    created.setProfileUuid(profile.getUuid());
                    created.setOrganizationUuid(profile.getOrganizationUuid());
                    created.setDocumentType(docType);
                    return created;
                });
        String path = fileStorageService.store(file, "sc-compliance/" + profile.getUuid());
        doc.setFilePath(path);
        if (profile.getStatus() == ScCompanyStatus.INVITED) {
            profile.setStatus(ScCompanyStatus.REGISTERED);
            profileRepository.save(profile);
        }
        return toComplianceResponse(complianceRepository.save(doc), isCoreInsurance(docType));
    }

    @Transactional
    public List<ScWorkerResponse> listMyWorkers() {
        AuthPrincipal principal = requireSubcontractor();
        portalAccessService.requireOrgAdmin(principal);
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        return workerRepository.findByProfileUuidOrderByFullNameAsc(profile.getUuid())
                .stream()
                .map(this::toWorkerResponse)
                .toList();
    }

    @Transactional
    public ScWorkerResponse createWorker(ScWorkerRequest request) {
        AuthPrincipal principal = requireSubcontractor();
        portalAccessService.requireOrgAdmin(principal);
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        if (request == null || !StringUtils.hasText(request.getFullName())) {
            throw new BadRequestException("Worker full name is required");
        }
        ScWorker worker = new ScWorker();
        worker.setProfileUuid(profile.getUuid());
        worker.setOrganizationUuid(profile.getOrganizationUuid());
        applyWorkerRequest(worker, request);
        return toWorkerResponse(workerRepository.save(worker));
    }

    @Transactional
    public ScWorkerResponse updateWorker(java.util.UUID workerUuid, ScWorkerRequest request) {
        AuthPrincipal principal = requireSubcontractor();
        portalAccessService.requireOrgAdmin(principal);
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        ScWorker worker = workerRepository.findByUuidAndProfileUuid(workerUuid, profile.getUuid())
                .orElseThrow(() -> new NotFoundException("Worker not found"));
        applyWorkerRequest(worker, request);
        return toWorkerResponse(workerRepository.save(worker));
    }

    @Transactional
    public void deleteWorker(java.util.UUID workerUuid) {
        AuthPrincipal principal = requireSubcontractor();
        portalAccessService.requireOrgAdmin(principal);
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        ScWorker worker = workerRepository.findByUuidAndProfileUuid(workerUuid, profile.getUuid())
                .orElseThrow(() -> new NotFoundException("Worker not found"));
        workerRepository.delete(worker);
    }

    @Transactional(readOnly = true)
    public ScAppointmentEligibilityResponse checkAppointmentEligibility(Long accountId, java.util.UUID packageUuid) {
        requireStaff();
        SubcontractorPackage pkg = packageRepository
                .findByUuidAndCompanyId(packageUuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Package not found"));
        ensureProfileForAccount(accountId);
        return eligibilityService.checkAppointmentEligibility(accountId, pkg);
    }

    @Transactional(readOnly = true)
    public List<String> tradeCategoryOptions() {
        return TRADE_CATEGORY_OPTIONS;
    }

    @Transactional(readOnly = true)
    public ScPortalContextResponse getPortalContext() {
        AuthPrincipal principal = requireSubcontractor();
        return portalAccessService.buildPortalContext(principal);
    }

    @Transactional
    public ScOrganizationExtensionResponse updateOrganizationExtension(ScOrganizationExtensionRequest request) {
        AuthPrincipal principal = requireSubcontractor();
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        ScOrganizationExtensionResponse ext = organizationProfileService.updateExtension(principal, profile, request);
        vendorService.markProfileSubmittedForReview(principal.getAccountId());
        return ext;
    }

    @Transactional
    public ScBankDetailResponse updateBankDetail(ScBankDetailRequest request) {
        AuthPrincipal principal = requireSubcontractor();
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        return organizationProfileService.updateBankDetail(principal, profile, request);
    }

    @Transactional
    public ScBankDetailResponse uploadBankLetter(MultipartFile file) {
        AuthPrincipal principal = requireSubcontractor();
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        return organizationProfileService.uploadBankLetter(principal, profile, file);
    }

    @Transactional
    public ScCommunityRegistrationResponse addCommunityRegistration(ScCommunityRegistrationRequest request) {
        AuthPrincipal principal = requireSubcontractor();
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        return organizationProfileService.addCommunityRegistration(principal, profile, request);
    }

    @Transactional
    public ScCommunityRegistrationResponse uploadCommunityFile(UUID registrationUuid, MultipartFile file) {
        AuthPrincipal principal = requireSubcontractor();
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        return organizationProfileService.uploadCommunityFile(principal, profile, registrationUuid, file);
    }

    @Transactional
    public void deleteCommunityRegistration(UUID registrationUuid) {
        AuthPrincipal principal = requireSubcontractor();
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        organizationProfileService.deleteCommunityRegistration(principal, profile, registrationUuid);
    }

    @Transactional
    public ScOrganizationReferenceResponse addReference(ScOrganizationReferenceRequest request) {
        AuthPrincipal principal = requireSubcontractor();
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        return organizationProfileService.addReference(principal, profile, request);
    }

    @Transactional
    public ScOrganizationReferenceResponse updateReference(UUID referenceUuid, ScOrganizationReferenceRequest request) {
        AuthPrincipal principal = requireSubcontractor();
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        return organizationProfileService.updateReference(principal, profile, referenceUuid, request);
    }

    @Transactional
    public void deleteReference(UUID referenceUuid) {
        AuthPrincipal principal = requireSubcontractor();
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        organizationProfileService.deleteReference(principal, profile, referenceUuid);
    }

    @Transactional
    public void uploadOrgDocument(ScOrgDocumentCode code, MultipartFile file) {
        AuthPrincipal principal = requireSubcontractor();
        ScCompanyProfile profile = ensureProfileForAccount(principal.getAccountId());
        organizationProfileService.uploadOrgDocument(principal, profile, code, file);
    }

    @Transactional(readOnly = true)
    public List<ScPortalTeamMemberResponse> listTeam() {
        AuthPrincipal principal = requireSubcontractor();
        return portalTeamService.listTeam(principal);
    }

    @Transactional
    public ScPortalTeamMemberResponse inviteTeamMember(ScInviteTeamMemberRequest request) {
        AuthPrincipal principal = requireSubcontractor();
        return portalTeamService.inviteTeamMember(principal, request);
    }

    @Transactional
    public ScPortalTeamMemberResponse addTeamMemberManually(ScAddTeamMemberRequest request) {
        AuthPrincipal principal = requireSubcontractor();
        return portalTeamService.addTeamMemberManually(principal, request);
    }

    @Transactional
    public ScPortalTeamMemberResponse updateTeamMember(UUID portalUserUuid, ScUpdateTeamMemberRequest request) {
        AuthPrincipal principal = requireSubcontractor();
        return portalTeamService.updateTeamMember(principal, portalUserUuid, request);
    }

    private ScCompanyProfileResponse toProfileResponse(ScCompanyProfile profile, AuthPrincipal principal) {
        List<ScComplianceDocument> docs = complianceRepository.findByProfileUuidOrderByDocumentTypeAsc(profile.getUuid());
        List<ScComplianceDocumentResponse> docResponses = new ArrayList<>();
        for (ScComplianceDocType type : ScComplianceDocType.values()) {
            ScComplianceDocument doc = docs.stream()
                    .filter(d -> d.getDocumentType() == type)
                    .findFirst()
                    .orElse(null);
            docResponses.add(toComplianceResponse(
                    doc != null ? doc : placeholderDoc(profile.getUuid(), type),
                    isCoreInsurance(type)));
        }
        return ScCompanyProfileResponse.builder()
                .uuid(profile.getUuid())
                .adminAccountId(profile.getAdminAccountId())
                .legalCompanyName(profile.getLegalCompanyName())
                .tradeLicenceNumber(profile.getTradeLicenceNumber())
                .tradeLicenceFilePath(profile.getTradeLicenceFilePath())
                .tradeLicenceExpiry(formatDate(profile.getTradeLicenceExpiry()))
                .trn(profile.getTrn())
                .registeredAddress(profile.getRegisteredAddress())
                .primaryContactName(profile.getPrimaryContactName())
                .primaryContactEmail(profile.getPrimaryContactEmail())
                .primaryContactPhone(profile.getPrimaryContactPhone())
                .accountsContactName(profile.getAccountsContactName())
                .accountsContactEmail(profile.getAccountsContactEmail())
                .accountsContactPhone(profile.getAccountsContactPhone())
                .tradeCategories(readCategories(profile.getTradeCategories()))
                .declaredCapacity(profile.getDeclaredCapacity())
                .status(profile.getStatus().name())
                .complianceStatus(eligibilityService.computeComplianceStatus(profile).name())
                .complianceDocuments(docResponses)
                .organizationExtension(organizationProfileService.getExtension(principal, profile))
                .build();
    }

    private ScComplianceDocument placeholderDoc(java.util.UUID profileUuid, ScComplianceDocType type) {
        ScComplianceDocument doc = new ScComplianceDocument();
        doc.setProfileUuid(profileUuid);
        doc.setDocumentType(type);
        return doc;
    }

    private ScComplianceDocumentResponse toComplianceResponse(ScComplianceDocument doc, boolean required) {
        return ScComplianceDocumentResponse.builder()
                .uuid(doc.getUuid())
                .documentType(doc.getDocumentType().name())
                .documentLabel(doc.getDocumentType().displayName())
                .referenceNo(doc.getReferenceNo())
                .expiryDate(formatDate(doc.getExpiryDate()))
                .filePath(doc.getFilePath())
                .requiredForAppointment(required)
                .itemStatus(ScEligibilityService.itemStatus(doc.getExpiryDate(), doc.getFilePath(), required))
                .build();
    }

    private ScWorkerResponse toWorkerResponse(ScWorker worker) {
        SiteEligibility eligibility = computeSiteEligibility(worker);
        return ScWorkerResponse.builder()
                .uuid(worker.getUuid())
                .fullName(worker.getFullName())
                .trade(worker.getTrade())
                .passportNumber(worker.getPassportNumber())
                .visaExpiry(formatDate(worker.getVisaExpiry()))
                .emiratesIdExpiry(formatDate(worker.getEmiratesIdExpiry()))
                .insuranceExpiry(formatDate(worker.getInsuranceExpiry()))
                .inductionDate(formatDate(worker.getInductionDate()))
                .accessCardExpiry(formatDate(worker.getAccessCardExpiry()))
                .tradeCertExpiry(formatDate(worker.getTradeCertExpiry()))
                .accessCardNumber(worker.getAccessCardNumber())
                .inductionCompleted(worker.isInductionCompleted())
                .active(worker.isActive())
                .siteEligible(eligibility.eligible)
                .siteEligibilityNote(eligibility.note)
                .build();
    }

    private record SiteEligibility(boolean eligible, String note) {}

    private SiteEligibility computeSiteEligibility(ScWorker worker) {
        if (!worker.isActive()) {
            return new SiteEligibility(false, "Worker is inactive.");
        }
        LocalDate today = LocalDate.now();
        List<String> expired = new ArrayList<>();
        if (worker.getVisaExpiry() != null && !worker.getVisaExpiry().isAfter(today)) {
            expired.add("visa expired " + worker.getVisaExpiry());
        }
        if (worker.getEmiratesIdExpiry() != null && !worker.getEmiratesIdExpiry().isAfter(today)) {
            expired.add("Emirates ID expired " + worker.getEmiratesIdExpiry());
        }
        if (worker.getInsuranceExpiry() != null && !worker.getInsuranceExpiry().isAfter(today)) {
            expired.add("insurance expired " + worker.getInsuranceExpiry());
        }
        if (worker.getAccessCardExpiry() != null && !worker.getAccessCardExpiry().isAfter(today)) {
            expired.add("access card expired " + worker.getAccessCardExpiry());
        }
        if (worker.getTradeCertExpiry() != null && !worker.getTradeCertExpiry().isAfter(today)) {
            expired.add("trade certificate expired " + worker.getTradeCertExpiry());
        }
        if (!worker.isInductionCompleted()) {
            expired.add("site induction not completed");
        }
        if (!expired.isEmpty()) {
            return new SiteEligibility(false, "Not site eligible: " + String.join("; ", expired) + ".");
        }
        return new SiteEligibility(true, "All tracked documents are current.");
    }

    private void applyProfileRequest(ScCompanyProfile profile, ScCompanyProfileRequest request, boolean staffEdit) {
        if (request == null) {
            return;
        }
        if (request.getLegalCompanyName() != null) {
            profile.setLegalCompanyName(trimToNull(request.getLegalCompanyName()));
        }
        if (request.getTradeLicenceNumber() != null) {
            profile.setTradeLicenceNumber(trimToNull(request.getTradeLicenceNumber()));
        }
        if (request.getTradeLicenceExpiry() != null) {
            profile.setTradeLicenceExpiry(parseDate(request.getTradeLicenceExpiry(), "tradeLicenceExpiry"));
        }
        if (request.getTrn() != null) {
            profile.setTrn(trimToNull(request.getTrn()));
        }
        if (request.getRegisteredAddress() != null) {
            profile.setRegisteredAddress(trimToNull(request.getRegisteredAddress()));
        }
        if (request.getPrimaryContactName() != null) {
            profile.setPrimaryContactName(trimToNull(request.getPrimaryContactName()));
        }
        if (request.getPrimaryContactEmail() != null) {
            profile.setPrimaryContactEmail(trimToNull(request.getPrimaryContactEmail()));
        }
        if (request.getPrimaryContactPhone() != null) {
            profile.setPrimaryContactPhone(trimToNull(request.getPrimaryContactPhone()));
        }
        if (request.getAccountsContactName() != null) {
            profile.setAccountsContactName(trimToNull(request.getAccountsContactName()));
        }
        if (request.getAccountsContactEmail() != null) {
            profile.setAccountsContactEmail(trimToNull(request.getAccountsContactEmail()));
        }
        if (request.getAccountsContactPhone() != null) {
            profile.setAccountsContactPhone(trimToNull(request.getAccountsContactPhone()));
        }
        if (request.getTradeCategories() != null) {
            profile.setTradeCategories(writeCategories(request.getTradeCategories()));
        }
        if (request.getDeclaredCapacity() != null) {
            profile.setDeclaredCapacity(trimToNull(request.getDeclaredCapacity()));
        }
        if (staffEdit && request.getStatus() != null && StringUtils.hasText(request.getStatus())) {
            profile.setStatus(ScCompanyStatus.valueOf(request.getStatus().trim().toUpperCase()));
        }
    }

    private void applyWorkerRequest(ScWorker worker, ScWorkerRequest request) {
        if (request == null) {
            return;
        }
        if (StringUtils.hasText(request.getFullName())) {
            worker.setFullName(request.getFullName().trim());
        }
        if (request.getTrade() != null) {
            worker.setTrade(trimToNull(request.getTrade()));
        }
        if (request.getPassportNumber() != null) {
            worker.setPassportNumber(trimToNull(request.getPassportNumber()));
        }
        if (request.getVisaExpiry() != null) {
            worker.setVisaExpiry(parseDate(request.getVisaExpiry(), "visaExpiry"));
        }
        if (request.getEmiratesIdExpiry() != null) {
            worker.setEmiratesIdExpiry(parseDate(request.getEmiratesIdExpiry(), "emiratesIdExpiry"));
        }
        if (request.getInsuranceExpiry() != null) {
            worker.setInsuranceExpiry(parseDate(request.getInsuranceExpiry(), "insuranceExpiry"));
        }
        if (request.getInductionDate() != null) {
            worker.setInductionDate(parseDate(request.getInductionDate(), "inductionDate"));
        }
        if (request.getAccessCardExpiry() != null) {
            worker.setAccessCardExpiry(parseDate(request.getAccessCardExpiry(), "accessCardExpiry"));
        }
        if (request.getTradeCertExpiry() != null) {
            worker.setTradeCertExpiry(parseDate(request.getTradeCertExpiry(), "tradeCertExpiry"));
        }
        if (request.getAccessCardNumber() != null) {
            worker.setAccessCardNumber(trimToNull(request.getAccessCardNumber()));
        }
        if (request.getInductionCompleted() != null) {
            worker.setInductionCompleted(request.getInductionCompleted());
        }
        if (request.getActive() != null) {
            worker.setActive(request.getActive());
        }
    }

    private List<String> readCategories(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
        }
    }

    private String writeCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(categories);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid trade categories");
        }
    }

    private static boolean isCoreInsurance(ScComplianceDocType type) {
        return type == ScComplianceDocType.CAR_INSURANCE
                || type == ScComplianceDocType.TPL_INSURANCE
                || type == ScComplianceDocType.WC_INSURANCE;
    }

    private static String formatDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static LocalDate parseDate(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid date for " + field);
        }
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return companyId;
    }

    private AuthPrincipal requireSubcontractor() {
        AuthPrincipal principal = currentPrincipal();
        if (principal == null || !principal.getRoles().contains(Role.SUBCONTRACTOR)) {
            throw new ForbiddenException("Subcontractor access required");
        }
        return principal;
    }

    private AuthPrincipal requireStaff() {
        AuthPrincipal principal = currentPrincipal();
        if (principal == null || principal.getRoles().contains(Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        if (principal.getRoles().contains(Role.SUBCONTRACTOR)
                && principal.getRoles().size() == 1) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private AuthPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
