package com.fitouts.subcontractor.application;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.checklist.mapper.SiteVisitEstimateMapper;
import com.fitouts.company.application.CoverLetterBrandingService;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.api.ScAdminSignContractRequest;
import com.fitouts.subcontractor.api.ScSignContractRequest;
import com.fitouts.subcontractor.api.ScSubcontractContractResponse;
import com.fitouts.subcontractor.domain.ScContractStatus;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScOrganizationRepository;
import com.fitouts.subcontractor.domain.ScPackageAward;
import com.fitouts.subcontractor.domain.ScPackageAwardRepository;
import com.fitouts.subcontractor.domain.ScPortalUser;
import com.fitouts.subcontractor.domain.ScPortalUserRepository;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScContractSignatureService {

    private final ScPackageAwardRepository awardRepository;
    private final SubcontractorPackageRepository packageRepository;
    private final ScOrganizationRepository organizationRepository;
    private final ScPortalUserRepository portalUserRepository;
    private final CompanyRepository companyRepository;
    private final ScPortalAccessService portalAccessService;
    private final FileStorageService fileStorageService;
    private final SubcontractPdfService pdfService;

    // ── Admin Signature Flow (Main Contractor / Staff) ─────────────────────────

    @Transactional
    public ScSubcontractContractResponse adminSignContract(
            Long projectId, UUID packageUuid, ScAdminSignContractRequest request, HttpServletRequest servletRequest) {
        AuthPrincipal principal = requireAdmin();
        UUID companyId = requireCompany();

        if (projectId == null || packageUuid == null) {
            throw new BadRequestException("projectId and packageUuid are required");
        }

        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Subcontractor package not found"));

        if (!pkg.getProjectId().equals(projectId)) {
            throw new BadRequestException("Package does not belong to this project");
        }

        ScPackageAward award = awardRepository.findByPackageUuid(packageUuid)
                .orElseThrow(() -> new NotFoundException("No subcontract award recorded for this package"));

        ScContractStatus currentStatus = award.getContractStatus();
        if (currentStatus == ScContractStatus.WAITING_FOR_CONTRACTOR_SIGNATURE
                || currentStatus == ScContractStatus.SIGNED_AND_EXECUTED
                || award.getAdminSignedAt() != null) {
            throw new BadRequestException("Main Contractor has already signed this contract");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"));

        String adminSigPath = company.getSignatureImagePath();
        if (!StringUtils.hasText(adminSigPath)) {
            throw new BadRequestException("Main Contractor digital signature is not configured. "
                    + "Please upload/configure the digital signature in Project Configuration -> Cover Letter.");
        }

        Optional<byte[]> adminSigBytesOpt = fileStorageService.readBytes(adminSigPath);
        if (adminSigBytesOpt.isEmpty() || adminSigBytesOpt.get().length == 0) {
            throw new BadRequestException("Main Contractor digital signature image file could not be read. "
                    + "Please re-upload signature in Project Configuration -> Cover Letter.");
        }

        ScOrganization org = organizationRepository.findById(award.getOrganizationUuid()).orElse(null);
        OffsetDateTime adminSignedAt = OffsetDateTime.now();
        String signerName = StringUtils.hasText(request != null ? request.getSignatureName() : null)
                ? request.getSignatureName().trim()
                : principal.getEmail();
        String signerTitle = request != null && StringUtils.hasText(request.getSignerTitle())
                ? request.getSignerTitle().trim()
                : "Main Contractor Admin";

        // Generate Stage 1 PDF with Admin signature embedded
        byte[] pdfBytes = pdfService.generateStage1AdminPdf(
                pkg, award, org, signerName, signerTitle, adminSignedAt, adminSigBytesOpt.get());

        String pdfFileName = "contract_" + packageUuid + "_stage1.pdf";
        String contractFilePath = fileStorageService.storeBytes(
                pdfBytes, pdfFileName, companyId, projectId, "sc-contracts");

        String clientIp = resolveClientIp(servletRequest);
        String userAgent = servletRequest != null ? servletRequest.getHeader("User-Agent") : "";
        String adminAuditJson = buildAdminAuditJson(
                award, packageUuid, principal, signerName, signerTitle, adminSignedAt, clientIp, userAgent);

        award.setAdminSignedAt(adminSignedAt);
        award.setAdminSignedBy(principal.getAccountId());
        award.setAdminSignerName(signerName);
        award.setAdminSignerTitle(signerTitle);
        award.setAdminSignatureAuditJson(adminAuditJson);
        award.setContractFilePath(contractFilePath);
        award.setContractStatus(ScContractStatus.WAITING_FOR_CONTRACTOR_SIGNATURE);

        ScPackageAward saved = awardRepository.save(award);
        return toResponse(pkg, saved, org, null);
    }

    // ── Subcontractor Digital Signature Upload & View ────────────────────────

    @Transactional
    public String uploadSubcontractorSignature(MultipartFile file) {
        AuthPrincipal principal = requireAuthenticated();
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);

        CoverLetterBrandingService.requireImage(file);

        String previousPath = portalUser.getSignatureImagePath();
        String path = fileStorageService.store(file, "sc-signatures/" + portalUser.getUuid());

        portalUser.setSignatureImagePath(path);
        portalUserRepository.save(portalUser);

        if (StringUtils.hasText(previousPath)) {
            fileStorageService.deleteIfExists(previousPath);
        }

        return SiteVisitEstimateMapper.toFileUrl(path);
    }

    @Transactional(readOnly = true)
    public ScSubcontractorSignatureInfo getSubcontractorSignature() {
        AuthPrincipal principal = requireAuthenticated();
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        String path = portalUser.getSignatureImagePath();
        boolean uploaded = StringUtils.hasText(path);
        return new ScSubcontractorSignatureInfo(
                uploaded,
                uploaded ? SiteVisitEstimateMapper.toFileUrl(path) : null);
    }

    public record ScSubcontractorSignatureInfo(boolean signatureUploaded, String signatureUrl) {}

    // ── Subcontractor Contract View & Sign ───────────────────────────────────

    @Transactional(readOnly = true)
    public ScSubcontractContractResponse getContract(UUID packageUuid) {
        AuthPrincipal principal = requireAuthenticated();
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        UUID companyId = requireCompany();

        if (packageUuid == null) {
            throw new BadRequestException("packageUuid is required");
        }

        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Subcontractor package not found"));

        ScPackageAward award = awardRepository.findByPackageUuid(packageUuid)
                .orElseThrow(() -> new NotFoundException("No subcontract award recorded for this package"));

        if (!award.getOrganizationUuid().equals(portalUser.getOrganizationUuid())) {
            throw new ForbiddenException("You are not authorized to view this subcontract agreement");
        }

        ScOrganization org = organizationRepository.findById(award.getOrganizationUuid()).orElse(null);
        return toResponse(pkg, award, org, portalUser);
    }

    @Transactional
    public ScSubcontractContractResponse signContract(
            UUID packageUuid, ScSignContractRequest request, HttpServletRequest servletRequest) {
        AuthPrincipal principal = requireAuthenticated();
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        UUID companyId = requireCompany();

        if (packageUuid == null) {
            throw new BadRequestException("packageUuid is required");
        }
        if (request == null || !StringUtils.hasText(request.getSignatureName())) {
            throw new BadRequestException("signatureName is required");
        }
        if (!request.isDeclarationAccepted()) {
            throw new BadRequestException("You must accept the declaration to sign the contract");
        }

        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Subcontractor package not found"));

        ScPackageAward award = awardRepository.findByPackageUuid(packageUuid)
                .orElseThrow(() -> new NotFoundException("No subcontract award recorded for this package"));

        if (!award.getOrganizationUuid().equals(portalUser.getOrganizationUuid())) {
            throw new ForbiddenException("You are not authorized to sign this subcontract agreement");
        }

        ScContractStatus status = award.getContractStatus();
        if (status == ScContractStatus.WAITING_FOR_ADMIN_SIGNATURE && award.getAdminSignedAt() == null) {
            throw new BadRequestException("Contract is not ready for subcontractor signature. "
                    + "Main Contractor must sign the contract first.");
        }

        if (status == ScContractStatus.SIGNED_AND_EXECUTED || award.getSignedAt() != null) {
            throw new BadRequestException("Contract has already been signed by the subcontractor on " + award.getSignedAt());
        }

        String subSigPath = portalUser.getSignatureImagePath();
        if (!StringUtils.hasText(subSigPath)) {
            throw new BadRequestException("Please upload your digital signature before signing the contract.");
        }

        Optional<byte[]> subSigBytesOpt = fileStorageService.readBytes(subSigPath);
        if (subSigBytesOpt.isEmpty() || subSigBytesOpt.get().length == 0) {
            throw new BadRequestException("Uploaded digital signature image file could not be read. "
                    + "Please re-upload your digital signature.");
        }

        Company company = companyRepository.findById(companyId).orElse(null);
        byte[] adminSigBytes = null;
        if (company != null && StringUtils.hasText(company.getSignatureImagePath())) {
            adminSigBytes = fileStorageService.readBytes(company.getSignatureImagePath()).orElse(null);
        }

        ScOrganization org = organizationRepository.findById(award.getOrganizationUuid()).orElse(null);
        OffsetDateTime signedAt = OffsetDateTime.now();
        String subSignerName = request.getSignatureName().trim();
        String subSignerTitle = StringUtils.hasText(request.getSignerTitle()) ? request.getSignerTitle().trim() : "Subcontractor Representative";

        // Generate Stage 2 PDF with BOTH Admin and Subcontractor signatures embedded
        byte[] finalPdfBytes = pdfService.generateStage2FinalPdf(
                pkg,
                award,
                org,
                award.getAdminSignerName() != null ? award.getAdminSignerName() : "Main Contractor",
                award.getAdminSignerTitle() != null ? award.getAdminSignerTitle() : "",
                award.getAdminSignedAt(),
                adminSigBytes,
                subSignerName,
                subSignerTitle,
                signedAt,
                subSigBytesOpt.get());

        String pdfFileName = "contract_" + packageUuid + "_executed.pdf";
        String finalContractFilePath = fileStorageService.storeBytes(
                finalPdfBytes, pdfFileName, companyId, pkg.getProjectId(), "sc-contracts");

        String clientIp = resolveClientIp(servletRequest);
        String userAgent = servletRequest != null ? servletRequest.getHeader("User-Agent") : "";
        String subAuditJson = buildSubcontractorAuditJson(
                award, packageUuid, principal, request, signedAt, clientIp, userAgent);

        award.setSignedAt(signedAt);
        award.setSubcontractorSignedBy(principal.getAccountId());
        award.setSubcontractorSignerName(subSignerName);
        award.setSubcontractorSignerTitle(subSignerTitle);
        award.setSignatureAuditJson(subAuditJson);
        award.setContractFilePath(finalContractFilePath);
        award.setContractStatus(ScContractStatus.SIGNED_AND_EXECUTED);

        ScPackageAward savedAward = awardRepository.save(award);
        return toResponse(pkg, savedAward, org, portalUser);
    }

    // ── Response Mapping & Helpers ───────────────────────────────────────────

    private ScSubcontractContractResponse toResponse(
            SubcontractorPackage pkg, ScPackageAward award, ScOrganization org, ScPortalUser portalUser) {

        ScContractStatus status = award.getContractStatus();
        boolean adminSigned = award.getAdminSignedAt() != null;
        boolean subSigned = award.getSignedAt() != null;
        boolean subSigUploaded = portalUser != null && StringUtils.hasText(portalUser.getSignatureImagePath());
        String subSigUrl = (portalUser != null && subSigUploaded)
                ? SiteVisitEstimateMapper.toFileUrl(portalUser.getSignatureImagePath())
                : null;

        return ScSubcontractContractResponse.builder()
                .awardUuid(award.getUuid())
                .packageUuid(pkg.getUuid())
                .packageName(pkg.getName())
                .organizationUuid(award.getOrganizationUuid())
                .organizationName(org != null ? org.getLegalCompanyName() : null)
                .awardedValue(award.getAwardedValue())
                .awardedAt(award.getAwardedAt())
                .contractStatus(status.name())
                .contractFilePath(award.getContractFilePath())
                .contractAvailable(StringUtils.hasText(award.getContractFilePath()) || award.getUuid() != null)
                .adminSigned(adminSigned)
                .adminSignedAt(award.getAdminSignedAt())
                .adminSignerName(award.getAdminSignerName())
                .adminSignerTitle(award.getAdminSignerTitle())
                .adminSignatureAuditJson(award.getAdminSignatureAuditJson())
                .subcontractorSignatureUploaded(subSigUploaded)
                .subcontractorSignatureUrl(subSigUrl)
                .signed(subSigned)
                .signedAt(award.getSignedAt())
                .subcontractorSignerName(award.getSubcontractorSignerName())
                .subcontractorSignerTitle(award.getSubcontractorSignerTitle())
                .signatureAuditJson(award.getSignatureAuditJson())
                .build();
    }

    private String buildAdminAuditJson(
            ScPackageAward award,
            UUID packageUuid,
            AuthPrincipal principal,
            String signerName,
            String signerTitle,
            OffsetDateTime adminSignedAt,
            String clientIp,
            String userAgent) {
        return String.format(
                "{\"signatureType\":\"MAIN_CONTRACTOR_DIGITAL_SIGNATURE\",\"signedAt\":\"%s\",\"signerAccountId\":%d,\"signerEmail\":\"%s\",\"signerName\":\"%s\",\"signerTitle\":\"%s\",\"packageUuid\":\"%s\",\"awardUuid\":\"%s\",\"ipAddress\":\"%s\",\"userAgent\":\"%s\"}",
                adminSignedAt.toString(),
                principal.getAccountId(),
                escapeJson(principal.getEmail()),
                escapeJson(signerName),
                escapeJson(signerTitle),
                packageUuid.toString(),
                award.getUuid().toString(),
                escapeJson(clientIp != null ? clientIp : ""),
                escapeJson(userAgent != null ? userAgent : "")
        );
    }

    private String buildSubcontractorAuditJson(
            ScPackageAward award,
            UUID packageUuid,
            AuthPrincipal principal,
            ScSignContractRequest request,
            OffsetDateTime signedAt,
            String clientIp,
            String userAgent) {
        return String.format(
                "{\"signatureType\":\"SUBCONTRACTOR_DIGITAL_SIGNATURE\",\"signedAt\":\"%s\",\"signerAccountId\":%d,\"signerEmail\":\"%s\",\"signerName\":\"%s\",\"signerTitle\":\"%s\",\"organizationUuid\":\"%s\",\"packageUuid\":\"%s\",\"awardUuid\":\"%s\",\"ipAddress\":\"%s\",\"userAgent\":\"%s\",\"declarationAccepted\":true}",
                signedAt.toString(),
                principal.getAccountId(),
                escapeJson(principal.getEmail()),
                escapeJson(request.getSignatureName().trim()),
                escapeJson(request.getSignerTitle() != null ? request.getSignerTitle().trim() : ""),
                award.getOrganizationUuid().toString(),
                packageUuid.toString(),
                award.getUuid().toString(),
                escapeJson(clientIp != null ? clientIp : ""),
                escapeJson(userAgent != null ? userAgent : "")
        );
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String[] headers = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
        };
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private AuthPrincipal requireAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ForbiddenException("Authentication required");
        }
        return principal;
    }

    private AuthPrincipal requireAdmin() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() == null) {
            throw new ForbiddenException("Admin authorization required");
        }
        boolean allowed = principal.getRoles().contains(Role.ADMIN)
                || principal.getRoles().contains(Role.SUPER_ADMIN)
                || principal.getRoles().contains(Role.BUSINESS_OWNER);
        if (!allowed) {
            throw new ForbiddenException("Only Admin, Super Admin, or Business Owner can execute contract signature");
        }
        return principal;
    }

    private AuthPrincipal requireStaff() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() == null) {
            throw new ForbiddenException("Staff access required");
        }
        boolean allowed = principal.getRoles().contains(Role.ADMIN)
                || principal.getRoles().contains(Role.SUPER_ADMIN)
                || principal.getRoles().contains(Role.BUSINESS_OWNER)
                || principal.getRoles().contains(Role.PROJECT_MANAGER)
                || principal.getRoles().contains(Role.QS)
                || principal.getRoles().contains(Role.SENIOR_QS);
        if (!allowed) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return companyId;
    }
}
