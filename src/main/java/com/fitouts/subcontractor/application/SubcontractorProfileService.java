package com.fitouts.subcontractor.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.account.application.AccountService;
import com.fitouts.account.application.ClientAccountConversionResult;
import com.fitouts.account.application.ClientPortalInviteService;
import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.PasswordSetupToken;
import com.fitouts.auth.domain.PasswordSetupTokenRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.api.SubcontractorDocumentRequest;
import com.fitouts.subcontractor.api.SubcontractorDocumentResponse;
import com.fitouts.subcontractor.api.SubcontractorEligibilityResponse;
import com.fitouts.subcontractor.api.SubcontractorInviteRequest;
import com.fitouts.subcontractor.api.SubcontractorRegistrationRequest;
import com.fitouts.subcontractor.api.SubcontractorResponse;
import com.fitouts.subcontractor.api.SubcontractorStatusUpdateRequest;
import com.fitouts.subcontractor.api.SubcontractorTokenVerifyRequest;
import com.fitouts.subcontractor.api.SubcontractorTokenVerifyResponse;
import com.fitouts.subcontractor.api.SubcontractorUserRequest;
import com.fitouts.subcontractor.api.SubcontractorUserResponse;
import com.fitouts.subcontractor.api.SubcontractorWorkerRequest;
import com.fitouts.subcontractor.api.SubcontractorWorkerResponse;
import com.fitouts.subcontractor.domain.Subcontractor;
import com.fitouts.subcontractor.domain.SubcontractorDocument;
import com.fitouts.subcontractor.domain.SubcontractorDocumentRepository;
import com.fitouts.subcontractor.domain.SubcontractorRepository;
import com.fitouts.subcontractor.domain.SubcontractorStatus;
import com.fitouts.subcontractor.domain.SubcontractorUser;
import com.fitouts.subcontractor.domain.SubcontractorUserRepository;
import com.fitouts.subcontractor.domain.SubcontractorWorker;
import com.fitouts.subcontractor.domain.SubcontractorWorkerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubcontractorProfileService {

    private final SubcontractorRepository subcontractorRepository;
    private final SubcontractorDocumentRepository documentRepository;
    private final SubcontractorUserRepository subcontractorUserRepository;
    private final SubcontractorWorkerRepository workerRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final ClientPortalInviteService clientPortalInviteService;
    private final FileStorageService fileStorageService;
    private final WorkerEligibilityService workerEligibilityService;
    private final PasswordSetupTokenRepository tokenRepository;

    @Transactional
    public Map<String, Object> inviteSubcontractor(SubcontractorInviteRequest request) {
        if (request == null || !StringUtils.hasText(request.getEmail())) {
            throw new BadRequestException("Email is required for invitation");
        }

        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            AuthPrincipal principal = getCurrentPrincipal();
            if (principal != null && principal.getCompanyId() != null) {
                companyId = principal.getCompanyId();
                CompanyContext.set(companyId);
            }
        }
        if (companyId == null) {
            throw new ForbiddenException("Main Contractor company context required to issue invitations");
        }

        String email = request.getEmail().trim().toLowerCase();
        String displayName = email;
        String companyName = "Subcontractor";

        // Create or find subcontractor target account
        ClientAccountConversionResult accountResult = accountService.createOrUpdateSubcontractorAccount(
                displayName,
                email,
                null,
                companyName
        );

        Long accountId = accountResult.clientAccountId();
        if (accountId == null) {
            throw new BadRequestException("Failed to create or find account for email: " + email);
        }

        // Generate token and send invitation email
        boolean sent = clientPortalInviteService.sendSubcontractorPortalInvite(accountId, displayName);
        if (!sent) {
            throw new IllegalStateException("Failed to send invitation email to " + email);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("isSuccess", true);
        response.put("message", "Subcontractor invitation sent successfully to " + email);
        response.put("email", email);
        return response;
    }

    @Transactional(readOnly = true)
    public SubcontractorTokenVerifyResponse verifyToken(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            return SubcontractorTokenVerifyResponse.builder()
                    .valid(false)
                    .message("Token is missing")
                    .build();
        }

        PasswordSetupToken token = tokenRepository.findByTokenAndConsumedAtIsNull(tokenValue.trim())
                .orElse(null);

        if (token == null) {
            return SubcontractorTokenVerifyResponse.builder()
                    .valid(false)
                    .message("Token is invalid or has already been consumed")
                    .build();
        }

        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return SubcontractorTokenVerifyResponse.builder()
                    .valid(false)
                    .message("Token has expired")
                    .build();
        }

        Account account = token.getAccount();
        String companyName = account != null && account.getCompany() != null ? account.getCompany().getCompanyName() : null;

        return SubcontractorTokenVerifyResponse.builder()
                .valid(true)
                .email(account != null ? account.getEmail() : null)
                .companyName(companyName)
                .message("Token is valid and unexpired")
                .build();
    }

    @Transactional
    public SubcontractorResponse register(SubcontractorRegistrationRequest request) {
        if (request == null || !StringUtils.hasText(request.getToken())) {
            throw new BadRequestException("Invitation token is required for registration");
        }
        if (!StringUtils.hasText(request.getCompanyName())) {
            throw new BadRequestException("companyName is required for registration");
        }

        String tokenValue = request.getToken().trim();
        PasswordSetupToken setupToken = tokenRepository.findByTokenAndConsumedAtIsNull(tokenValue)
                .orElseThrow(() -> new BadRequestException("Invitation token is invalid or has already been used"));

        if (setupToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Invitation token has expired");
        }

        Account targetAccount = setupToken.getAccount();
        if (targetAccount == null || targetAccount.getCompany() == null) {
            throw new BadRequestException("Invitation token is not bound to a valid account or company");
        }

        UUID tokenCompanyId = targetAccount.getCompany().getUuid();
        UUID currentTenant = CompanyContext.get();
        if (currentTenant != null && !currentTenant.equals(tokenCompanyId)) {
            throw new BadRequestException("Invitation token belongs to another tenant/company");
        }

        Subcontractor sub = new Subcontractor();
        sub.setCompanyId(tokenCompanyId);
        sub.setCompanyName(request.getCompanyName().trim());
        sub.setLicenceNo(trimNullable(request.getLicenceNo()));
        sub.setLicenceExpiry(request.getLicenceExpiry());
        sub.setTrn(trimNullable(request.getTrn()));
        sub.setEstablishmentCardExpiry(request.getEstablishmentCardExpiry());
        sub.setAddress(trimNullable(request.getAddress()));
        sub.setLocationPin(trimNullable(request.getLocationPin()));
        if (request.getTrades() != null && !request.getTrades().isEmpty()) {
            sub.setTrades(String.join(",", request.getTrades()));
        }
        sub.setCapacityBand(trimNullable(request.getCapacityBand()));
        sub.setStatus(SubcontractorStatus.REGISTERED);
        sub.setIsCrossTenantVisible(false);

        Subcontractor saved = subcontractorRepository.save(sub);

        // Bind intended target account to subcontractor
        SubcontractorUser su = new SubcontractorUser();
        su.setSubcontractorId(saved.getId());
        su.setUserId(targetAccount.getId());
        su.setRole("SUBCONTRACTOR_ADMIN");
        su.setStatus("ACTIVE");
        subcontractorUserRepository.save(su);

        // Consume token to prevent token reuse
        setupToken.setConsumedAt(OffsetDateTime.now());
        tokenRepository.save(setupToken);

        return toSubcontractorResponse(saved);
    }

    @Transactional(readOnly = true)
    public SubcontractorResponse getProfile(UUID id) {
        Subcontractor sub = requireAccessToSubcontractor(id);
        return toSubcontractorResponse(sub);
    }

    @Transactional
    public SubcontractorResponse updateStatus(UUID id, SubcontractorStatusUpdateRequest request) {
        requireStaff();
        Subcontractor sub = requireAccessToSubcontractor(id);
        if (request == null || request.getStatus() == null) {
            throw new BadRequestException("Status is required");
        }
        sub.setStatus(request.getStatus());
        return toSubcontractorResponse(subcontractorRepository.save(sub));
    }

    @Transactional
    public SubcontractorDocumentResponse addDocument(UUID subcontractorId, SubcontractorDocumentRequest request, MultipartFile file) {
        Subcontractor sub = requireAccessToSubcontractor(subcontractorId);

        String fileId = null;
        if (file != null && !file.isEmpty()) {
            fileId = fileStorageService.store(file, "subcontractor-documents");
        }

        SubcontractorDocument doc = new SubcontractorDocument();
        doc.setSubcontractorId(sub.getId());
        doc.setDocumentTypeId(request != null ? trimNullable(request.getDocumentTypeId()) : null);
        doc.setReferenceNo(request != null ? trimNullable(request.getReferenceNo()) : null);
        doc.setIssueDate(request != null ? request.getIssueDate() : null);
        doc.setExpiryDate(request != null ? request.getExpiryDate() : null);
        doc.setFileId(fileId);
        doc.setStatus(request != null && StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : "PENDING");

        AuthPrincipal principal = getCurrentPrincipal();
        if (principal != null) {
            doc.setVerifiedBy(principal.getAccountId());
            doc.setVerifiedDate(OffsetDateTime.now());
        }

        return toDocumentResponse(documentRepository.save(doc));
    }

    @Transactional(readOnly = true)
    public List<SubcontractorDocumentResponse> listDocuments(UUID subcontractorId) {
        requireAccessToSubcontractor(subcontractorId);
        return documentRepository.findBySubcontractorId(subcontractorId)
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Transactional
    public SubcontractorUserResponse addTeamUser(UUID subcontractorId, SubcontractorUserRequest request) {
        Subcontractor sub = requireAccessToSubcontractor(subcontractorId);
        if (request == null || !StringUtils.hasText(request.getEmail())) {
            throw new BadRequestException("User email is required");
        }

        ClientAccountConversionResult result = accountService.createOrUpdateSubcontractorAccount(
                request.getFullName(),
                request.getEmail(),
                request.getPhone(),
                sub.getCompanyName()
        );

        Long accountId = result.clientAccountId();
        AuthPrincipal principal = getCurrentPrincipal();

        Optional<SubcontractorUser> existingUser = subcontractorUserRepository.findBySubcontractorIdAndUserId(sub.getId(), accountId);
        SubcontractorUser su;
        if (existingUser.isPresent()) {
            su = existingUser.get();
        } else {
            su = new SubcontractorUser();
            su.setSubcontractorId(sub.getId());
            su.setUserId(accountId);
            if (principal != null) {
                su.setInvitedBy(principal.getAccountId());
            }
        }

        su.setRole(StringUtils.hasText(request.getRole()) ? request.getRole().trim() : "SUBCONTRACTOR_ADMIN");
        su.setPermissionsJson(trimNullable(request.getPermissionsJson()));
        su.setStatus("ACTIVE");

        SubcontractorUser saved = subcontractorUserRepository.save(su);

        if (result.clientAccountCreated()) {
            clientPortalInviteService.sendSubcontractorPortalInvite(accountId, request.getFullName());
        }

        Account acc = accountRepository.findById(accountId).orElse(null);
        return toUserResponse(saved, acc);
    }

    @Transactional(readOnly = true)
    public List<SubcontractorUserResponse> listTeamUsers(UUID subcontractorId) {
        requireAccessToSubcontractor(subcontractorId);
        return subcontractorUserRepository.findBySubcontractorId(subcontractorId)
                .stream()
                .map(su -> {
                    Account acc = accountRepository.findById(su.getUserId()).orElse(null);
                    return toUserResponse(su, acc);
                })
                .toList();
    }

    @Transactional
    public SubcontractorWorkerResponse addWorker(UUID subcontractorId, SubcontractorWorkerRequest request) {
        Subcontractor sub = requireAccessToSubcontractor(subcontractorId);
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BadRequestException("Worker name is required");
        }

        SubcontractorWorker worker = new SubcontractorWorker();
        worker.setSubcontractorId(sub.getId());
        worker.setName(request.getName().trim());
        worker.setTrade(trimNullable(request.getTrade()));
        worker.setPassportNo(trimNullable(request.getPassportNo()));
        worker.setVisaExpiry(request.getVisaExpiry());
        worker.setEidExpiry(request.getEidExpiry());
        worker.setInsuranceExpiry(request.getInsuranceExpiry());
        worker.setCertificatesJson(trimNullable(request.getCertificatesJson()));
        worker.setInductionDate(request.getInductionDate());
        worker.setAccessCardNo(trimNullable(request.getAccessCardNo()));
        worker.setAccessCardExpiry(request.getAccessCardExpiry());
        worker.setPhotoFileId(trimNullable(request.getPhotoFileId()));

        worker.setIsSiteEligible(workerEligibilityService.calculateSiteEligibility(worker));

        return toWorkerResponse(workerRepository.save(worker));
    }

    @Transactional(readOnly = true)
    public List<SubcontractorWorkerResponse> listWorkers(UUID subcontractorId) {
        requireAccessToSubcontractor(subcontractorId);
        return workerRepository.findBySubcontractorId(subcontractorId)
                .stream()
                .map(worker -> {
                    SubcontractorWorkerResponse resp = toWorkerResponse(worker);
                    resp.setIsSiteEligible(workerEligibilityService.calculateSiteEligibility(worker));
                    return resp;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public SubcontractorEligibilityResponse evaluateEligibility(UUID subcontractorId) {
        Subcontractor sub = requireAccessToSubcontractor(subcontractorId);
        SubcontractorEligibilityResponse resp = new SubcontractorEligibilityResponse();
        resp.setSubcontractorId(sub.getId());
        resp.setStatus(sub.getStatus());

        List<String> reasons = new ArrayList<>();
        boolean eligible = true;

        if (sub.getStatus() != SubcontractorStatus.APPROVED && sub.getStatus() != SubcontractorStatus.CONDITIONAL) {
            eligible = false;
            reasons.add("Subcontractor status is " + sub.getStatus() + " (requires APPROVED or CONDITIONAL)");
        }

        boolean licenceExpired = sub.getLicenceExpiry() != null && sub.getLicenceExpiry().isBefore(LocalDate.now());
        resp.setLicenceExpired(licenceExpired);
        if (licenceExpired) {
            eligible = false;
            reasons.add("Trade licence expired on " + sub.getLicenceExpiry());
        }

        List<SubcontractorWorker> workers = workerRepository.findBySubcontractorId(sub.getId());
        long ineligibleWorkers = workers.stream().filter(w -> !workerEligibilityService.calculateSiteEligibility(w)).count();
        resp.setTotalWorkers((long) workers.size());
        resp.setIneligibleWorkersCount(ineligibleWorkers);

        if (ineligibleWorkers > 0) {
            reasons.add("Subcontractor has " + ineligibleWorkers + " ineligible worker(s) on roster due to expired credentials");
        }

        resp.setEligible(eligible);
        resp.setIneligibilityReasons(reasons);
        return resp;
    }

    public Subcontractor requireAccessToSubcontractor(UUID subcontractorId) {
        Subcontractor sub = subcontractorRepository.findById(subcontractorId)
                .orElseThrow(() -> new NotFoundException("Subcontractor not found"));

        AuthPrincipal principal = getCurrentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }

        if (principal.getRoles() != null && principal.getRoles().contains(Role.SUBCONTRACTOR)) {
            List<SubcontractorUser> links = subcontractorUserRepository.findByUserId(principal.getAccountId());
            boolean belongsToSub = links.stream().anyMatch(l -> l.getSubcontractorId().equals(subcontractorId));
            if (!belongsToSub) {
                throw new ForbiddenException("Unauthorized: Subcontractor user cannot access another subcontractor's profile");
            }
        } else {
            UUID tenantCompanyId = CompanyContext.get();
            if (tenantCompanyId != null && !tenantCompanyId.equals(sub.getCompanyId())) {
                throw new ForbiddenException("Unauthorized: Subcontractor does not belong to your tenant company");
            }
        }

        return sub;
    }

    private AuthPrincipal requireStaff() {
        AuthPrincipal principal = getCurrentPrincipal();
        if (principal == null || (principal.getRoles() != null && principal.getRoles().contains(Role.SUBCONTRACTOR))) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private AuthPrincipal getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
            return p;
        }
        return null;
    }

    private String trimNullable(String val) {
        return StringUtils.hasText(val) ? val.trim() : null;
    }

    private SubcontractorResponse toSubcontractorResponse(Subcontractor entity) {
        SubcontractorResponse resp = new SubcontractorResponse();
        resp.setId(entity.getId());
        resp.setCompanyId(entity.getCompanyId());
        resp.setCompanyName(entity.getCompanyName());
        resp.setLicenceNo(entity.getLicenceNo());
        resp.setLicenceExpiry(entity.getLicenceExpiry());
        resp.setTrn(entity.getTrn());
        resp.setEstablishmentCardExpiry(entity.getEstablishmentCardExpiry());
        resp.setAddress(entity.getAddress());
        resp.setLocationPin(entity.getLocationPin());
        resp.setTrades(entity.getTrades());
        resp.setCapacityBand(entity.getCapacityBand());
        resp.setStatus(entity.getStatus());
        resp.setApprovedTrades(entity.getApprovedTrades());
        resp.setMaxPackageValue(entity.getMaxPackageValue());
        resp.setPerformanceScore(entity.getPerformanceScore());
        resp.setIsCrossTenantVisible(entity.getIsCrossTenantVisible());
        resp.setCreatedAt(entity.getCreatedAt());
        return resp;
    }

    private SubcontractorDocumentResponse toDocumentResponse(SubcontractorDocument doc) {
        SubcontractorDocumentResponse resp = new SubcontractorDocumentResponse();
        resp.setId(doc.getId());
        resp.setSubcontractorId(doc.getSubcontractorId());
        resp.setDocumentTypeId(doc.getDocumentTypeId());
        resp.setReferenceNo(doc.getReferenceNo());
        resp.setIssueDate(doc.getIssueDate());
        resp.setExpiryDate(doc.getExpiryDate());
        resp.setFileId(doc.getFileId());
        resp.setStatus(doc.getStatus());
        resp.setVerifiedBy(doc.getVerifiedBy());
        resp.setVerifiedDate(doc.getVerifiedDate());
        return resp;
    }

    private SubcontractorUserResponse toUserResponse(SubcontractorUser su, Account acc) {
        SubcontractorUserResponse resp = new SubcontractorUserResponse();
        resp.setId(su.getId());
        resp.setSubcontractorId(su.getSubcontractorId());
        resp.setUserId(su.getUserId());
        if (acc != null) {
            resp.setFullName(acc.getFullName());
            resp.setEmail(acc.getEmail());
        }
        resp.setRole(su.getRole());
        resp.setPermissionsJson(su.getPermissionsJson());
        resp.setInvitedBy(su.getInvitedBy());
        resp.setStatus(su.getStatus());
        return resp;
    }

    private SubcontractorWorkerResponse toWorkerResponse(SubcontractorWorker worker) {
        SubcontractorWorkerResponse resp = new SubcontractorWorkerResponse();
        resp.setId(worker.getId());
        resp.setSubcontractorId(worker.getSubcontractorId());
        resp.setName(worker.getName());
        resp.setTrade(worker.getTrade());
        resp.setPassportNo(worker.getPassportNo());
        resp.setVisaExpiry(worker.getVisaExpiry());
        resp.setEidExpiry(worker.getEidExpiry());
        resp.setInsuranceExpiry(worker.getInsuranceExpiry());
        resp.setCertificatesJson(worker.getCertificatesJson());
        resp.setInductionDate(worker.getInductionDate());
        resp.setAccessCardNo(worker.getAccessCardNo());
        resp.setAccessCardExpiry(worker.getAccessCardExpiry());
        resp.setIsSiteEligible(worker.getIsSiteEligible());
        resp.setPhotoFileId(worker.getPhotoFileId());
        return resp;
    }
}
