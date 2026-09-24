package com.fitouts.subcontractor.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.schedule.application.TradePackageCatalogue;
import com.fitouts.schedule.domain.TradePackage;
import com.fitouts.schedule.domain.TradePackageRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.api.ScAppointmentEligibilityResponse;
import com.fitouts.subcontractor.api.ScEligibilityCheckResponse;
import com.fitouts.subcontractor.api.ScEligibilityResponse;
import com.fitouts.subcontractor.domain.ScBankDetailRepository;
import com.fitouts.subcontractor.domain.ScBankVerificationStatus;
import com.fitouts.subcontractor.domain.ScCommunityRegistration;
import com.fitouts.subcontractor.domain.ScCommunityRegistrationRepository;
import com.fitouts.subcontractor.domain.ScCompanyProfile;
import com.fitouts.subcontractor.domain.ScCompanyProfileRepository;
import com.fitouts.subcontractor.domain.ScCompanyStatus;
import com.fitouts.subcontractor.domain.ScComplianceDocType;
import com.fitouts.subcontractor.domain.ScComplianceDocument;
import com.fitouts.subcontractor.domain.ScComplianceDocumentRepository;
import com.fitouts.subcontractor.domain.ScComplianceStatus;
import com.fitouts.subcontractor.domain.ScTenantMembershipRepository;
import com.fitouts.subcontractor.domain.ScTenantMembership;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScOrganizationRepository;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScEligibilityService {

    private static final int EXPIRING_SOON_DAYS = 30;

    private static final EnumSet<ScComplianceDocType> CORE_INSURANCE = EnumSet.of(
            ScComplianceDocType.CAR_INSURANCE,
            ScComplianceDocType.TPL_INSURANCE,
            ScComplianceDocType.WC_INSURANCE);

    private final ScCompanyProfileRepository profileRepository;
    private final ScComplianceDocumentRepository complianceRepository;
    private final ScTenantMembershipRepository membershipRepository;
    private final ScCommunityRegistrationRepository communityRepository;
    private final ScBankDetailRepository bankDetailRepository;
    private final ProjectRepository projectRepository;
    private final TradePackageRepository tradePackageRepository;
    private final SubcontractorPackageRepository packageRepository;
    private final ScOrganizationRepository organizationRepository;

    private static final Set<String> HARD_MEMBERSHIP_BLOCKERS =
            Set.of("SUSPENDED", "BLACKLISTED", "INVITED", "UNDER_REVIEW", "REGISTERED");

    public ScEligibilityResponse checkEligibility(UUID organizationId, Long projectId, UUID packageUuid) {
        UUID companyId = CompanyContext.get();
        if (companyId == null) throw new BadRequestException("Company context is required");
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Package not found"));
        if (!project.getId().equals(pkg.getProjectId())) {
            throw new BadRequestException("Package does not belong to this project");
        }
        ScCompanyProfile profile = profileRepository.findByOrganizationUuidAndCompanyId(organizationId, companyId)
                .orElse(null);
        List<ScEligibilityCheckResponse> checks = new ArrayList<>();
        if (profile == null) {
            checks.add(check("ORGANIZATION_PROFILE", "Organization profile", "FAIL", true,
                    null, "Subcontractor organization is not visible in this tenant"));
            return result(checks);
        }
        ScOrganization organization = organizationRepository.findById(organizationId).orElse(null);
        ScTenantMembership membership = membershipRepository
                .findByOrganizationUuidAndCompanyId(organizationId, companyId).orElse(null);
        String membershipStatus = membership == null ? null : membership.getStatus().name();
        if (membership == null) {
            checks.add(check("TENANT_MEMBERSHIP", "Approved vendor status", "FAIL", true,
                    null, "No tenant membership exists"));
        } else if (HARD_MEMBERSHIP_BLOCKERS.contains(membershipStatus)) {
            checks.add(check("TENANT_MEMBERSHIP", "Approved vendor status", "FAIL", true,
                    null, "Vendor membership is " + membershipStatus));
        } else if ("CONDITIONAL".equals(membershipStatus)) {
            checks.add(check("TENANT_MEMBERSHIP", "Approved vendor status", "WARNING", false,
                    null, "Vendor is conditionally approved"));
        } else {
            checks.add(check("TENANT_MEMBERSHIP", "Approved vendor status", "PASS", true, null, null));
        }

        String tradeCode = pkg.getTradePackageCode();
        boolean tradeMatch = tradesMatchPackage(
                membership == null ? null : membership.getApprovedTrades(),
                profile.getTradeCategories(),
                tradeCode,
                pkg.getTradePackageName(),
                pkg.getName());
        // Trade mismatch warns for RFQ invite; hard-block at appointment if still mismatched.
        checks.add(check("TRADE_MATCH", "Approved trade",
                tradeMatch ? "PASS" : "WARNING",
                false,
                null,
                tradeMatch ? null : "Vendor is not approved for "
                        + (tradeCode == null ? "this package" : tradeCode)
                        + " — invite allowed; resolve before award/appointment"));

        LocalDate today = LocalDate.now();
        LocalDate licenceExpiry = profile.getTradeLicenceExpiry();
        checks.add(check("TRADE_LICENCE", "Trade licence",
                licenceExpiry != null && licenceExpiry.isAfter(today)
                        && profile.getTradeLicenceFilePath() != null && !profile.getTradeLicenceFilePath().isBlank()
                        ? "PASS" : "FAIL", true, licenceExpiry,
                licenceExpiry == null ? "Trade licence expiry is missing"
                        : !licenceExpiry.isAfter(today) ? "Expired on " + licenceExpiry
                        : "Trade licence file is missing"));

        List<ScComplianceDocument> docs = complianceRepository
                .findByProfileUuidOrderByDocumentTypeAsc(profile.getUuid());
        for (ScComplianceDocType type : CORE_INSURANCE) {
            ScComplianceDocument doc = findDoc(docs, type);
            LocalDate expiry = doc == null ? null : doc.getExpiryDate();
            String reason = doc == null ? "Document is missing"
                    : expiry == null ? "Expiry date is missing"
                    : !expiry.isAfter(today) ? "Expired on " + expiry : null;
            checks.add(check(type.name(), type.displayName(), reason == null ? "PASS" : "FAIL",
                    true, expiry, reason));
        }

        String specialist = packageEntityRequirement(pkg);
        if (specialist == null) {
            checks.add(check("SPECIALIST_LICENCE", "Specialist licence", "NOT_APPLICABLE", false, null, null));
        } else {
            Optional<ScComplianceDocType> mapped = mapSpecialLicenceText(specialist);
            if (mapped.isEmpty()) {
                checks.add(check("SPECIALIST_LICENCE", "Specialist licence", "WARNING", false, null,
                        "No compliance document mapping exists for requirement: " + specialist));
            } else {
                ScComplianceDocument doc = findDoc(docs, mapped.get());
                LocalDate expiry = doc == null ? null : doc.getExpiryDate();
                String reason = doc == null ? mapped.get().displayName() + " is missing"
                        : expiry == null ? "Expiry date is missing"
                        : !expiry.isAfter(today) ? "Expired on " + expiry : null;
                checks.add(check("SPECIALIST_LICENCE", specialist, reason == null ? "PASS" : "FAIL",
                        true, expiry, reason));
            }
        }

        appendCommunityCheck(project, organizationId, checks, today);
        if (membership != null && membership.getMaxPackageValue() != null
                && pkg.getEstimatedBoqValue() != null
                && pkg.getEstimatedBoqValue().compareTo(membership.getMaxPackageValue()) > 0) {
            checks.add(check("MAX_PACKAGE_VALUE", "Maximum approved package value", "WARNING", false, null,
                    "Package value exceeds approved ceiling of " + membership.getMaxPackageValue()));
        } else {
            checks.add(check("MAX_PACKAGE_VALUE", "Maximum approved package value", "PASS", false, null, null));
        }
        if (organization != null && organization.getMonthlyCapacityValue() != null && pkg.getEstimatedBoqValue() != null
                && pkg.getEstimatedBoqValue().compareTo(organization.getMonthlyCapacityValue()) > 0) {
            checks.add(check("CAPACITY", "Declared capacity", "WARNING", false, null,
                    "Package exceeds declared monthly capacity of " + organization.getMonthlyCapacityValue()));
        } else {
            checks.add(check("CAPACITY", "Declared capacity", "PASS", false, null, null));
        }
        if (organization == null || organization.getPerformanceScore() == null) {
            checks.add(check("PERFORMANCE", "Performance score", "NOT_APPLICABLE", false, null, null));
        } else {
            checks.add(check("PERFORMANCE", "Performance score", "WARNING", false, null,
                    "Current performance score: " + organization.getPerformanceScore()));
        }
        bankDetailRepository.findByOrganizationUuid(organizationId).ifPresentOrElse(bank -> {
            boolean ready = bank.getIban() != null && !bank.getIban().isBlank()
                    && bank.getBankLetterFilePath() != null && !bank.getBankLetterFilePath().isBlank()
                    && bank.getVerificationStatus() != ScBankVerificationStatus.REJECTED;
            checks.add(check("BANK_DETAILS", "Payment readiness", ready ? "PASS" : "WARNING", false, null,
                    ready ? null : "Bank details are incomplete or not verified; this does not block RFQ"));
        }, () -> checks.add(check("BANK_DETAILS", "Payment readiness", "WARNING", false, null,
                "Bank details are not recorded; this does not block RFQ")));
        return result(checks);
    }

    private String packageEntityRequirement(SubcontractorPackage pkg) {
        return pkg.getSpecialistLicenceRequired();
    }

    private void appendCommunityCheck(Project project, UUID organizationId,
            List<ScEligibilityCheckResponse> checks, LocalDate today) {
        if (project.getCommunityName() == null || project.getCommunityName().isBlank()) {
            checks.add(check("COMMUNITY_REGISTRATION", "Community registration", "NOT_APPLICABLE", false, null, null));
            return;
        }
        String community = project.getCommunityName().toLowerCase(Locale.ROOT);
        Optional<ScCommunityRegistration> match = communityRepository
                .findByOrganizationUuidOrderByAuthorityNameAsc(organizationId).stream()
                .filter(r -> community.contains(value(r.getAuthorityCode()))
                        || community.contains(value(r.getAuthorityName()))
                        || value(r.getAuthorityName()).contains(community))
                .findFirst();
        if (match.isEmpty()) {
            checks.add(check("COMMUNITY_REGISTRATION", "Community registration", "FAIL", true, null,
                    "Registration for " + project.getCommunityName() + " is missing"));
        } else {
            LocalDate expiry = match.get().getExpiryDate();
            checks.add(check("COMMUNITY_REGISTRATION", "Community registration",
                    expiry != null && expiry.isAfter(today) ? "PASS" : "FAIL", true, expiry,
                    expiry == null ? "Expiry date is missing" : "Expired on " + expiry));
        }
    }

    private static String value(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /**
     * Match package trade codes (e.g. PKG-PLB) to vendor trade labels
     * (e.g. "MEP — plumbing & drainage") via exact token or catalogue keywords.
     */
    private boolean tradesMatchPackage(
            String approvedTradesRaw,
            String profileTradesRaw,
            String tradeCode,
            String tradePackageName,
            String packageName) {
        String haystack = ((approvedTradesRaw == null ? "" : approvedTradesRaw) + " "
                + (profileTradesRaw == null ? "" : profileTradesRaw)).toLowerCase(Locale.ROOT);
        if (haystack.isBlank()) {
            return tradeCode == null || tradeCode.isBlank();
        }
        if (tradeCode != null && !tradeCode.isBlank()) {
            if (containsToken(approvedTradesRaw, tradeCode) || containsToken(profileTradesRaw, tradeCode)) {
                return true;
            }
            String keywords = TradePackageCatalogue.seedKeywords(tradeCode);
            if (keywords == null || keywords.isBlank()) {
                Optional<TradePackage> tp = tradePackageRepository.findByCodeAndCompanyIdIsNull(tradeCode);
                if (tp.isPresent()) {
                    keywords = tp.get().getMatchKeywords() != null ? tp.get().getMatchKeywords() : tp.get().getName();
                }
            }
            if (keywords != null) {
                for (String part : keywords.toLowerCase(Locale.ROOT).split("[,;|]")) {
                    String kw = part.trim();
                    if (kw.length() > 2 && haystack.contains(kw)) {
                        return true;
                    }
                }
            }
        }
        // Also match package display name (e.g. "plumbing") against vendor trades.
        for (String label : List.of(
                tradePackageName == null ? "" : tradePackageName,
                packageName == null ? "" : packageName)) {
            String needle = label.toLowerCase(Locale.ROOT).trim();
            if (needle.length() > 2 && haystack.contains(needle)) {
                return true;
            }
            for (String part : needle.split("[^a-z0-9]+")) {
                if (part.length() > 3 && haystack.contains(part)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsToken(String source, String token) {
        if (source == null || source.isBlank() || token == null || token.isBlank()) return false;
        String normalized = source.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", ",");
        String needle = token.toUpperCase(Locale.ROOT);
        return Arrays.asList(normalized.split(",")).contains(needle)
                || Arrays.asList(normalized.split(",")).contains(needle.replace("-", ""));
    }

    private static ScEligibilityCheckResponse check(String code, String label, String result,
            boolean blocking, LocalDate expiryDate, String reason) {
        return ScEligibilityCheckResponse.builder().code(code).label(label).result(result)
                .blocking(blocking).expiryDate(expiryDate).reason(reason).build();
    }

    private static ScEligibilityResponse result(List<ScEligibilityCheckResponse> checks) {
        List<ScEligibilityCheckResponse> blockers = checks.stream()
                .filter(c -> c.isBlocking() && "FAIL".equals(c.getResult())).toList();
        List<ScEligibilityCheckResponse> warnings = checks.stream()
                .filter(c -> "WARNING".equals(c.getResult())).toList();
        return ScEligibilityResponse.builder().eligible(blockers.isEmpty())
                .status(blockers.isEmpty() ? (warnings.isEmpty() ? "ELIGIBLE" : "ELIGIBLE_WITH_WARNINGS") : "INELIGIBLE")
                .checks(checks).blockers(blockers).warnings(warnings).build();
    }

    public ScCompanyProfile requireProfile(Long accountId) {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return profileRepository.findByAdminAccountIdAndCompanyId(accountId, companyId)
                .orElseThrow(() -> new NotFoundException("Subcontractor company profile not found"));
    }

    public ScAppointmentEligibilityResponse checkAppointmentEligibility(
            Long accountId, SubcontractorPackage packageEntity) {
        ScCompanyProfile profile = requireProfile(accountId);
        ScEligibilityResponse eligibility = checkEligibility(
                profile.getOrganizationUuid(), packageEntity.getProjectId(), packageEntity.getUuid());
        return ScAppointmentEligibilityResponse.builder()
                .eligible(eligibility.isEligible())
                .blockingReasons(eligibility.getBlockers().stream().map(ScEligibilityCheckResponse::getReason).toList())
                .complianceStatus(computeComplianceStatus(profile).name())
                .companyStatus(profile.getStatus().name())
                .build();
    }

    public void assertEligibleForAppointment(Long accountId, SubcontractorPackage packageEntity) {
        ScCompanyProfile profile = requireProfile(accountId);
        ScEligibilityResponse eligibility = checkEligibility(
                profile.getOrganizationUuid(), packageEntity.getProjectId(), packageEntity.getUuid());
        if (!eligibility.isEligible()) {
            throw new BadRequestException(eligibility.getBlockers().get(0).getReason());
        }
        boolean tradeOk = eligibility.getChecks().stream()
                .filter(c -> "TRADE_MATCH".equals(c.getCode()))
                .anyMatch(c -> "PASS".equals(c.getResult()));
        if (!tradeOk) {
            throw new BadRequestException("Vendor is not approved for this package trade — approve the trade in prequalification first");
        }
    }

    public ScComplianceStatus computeComplianceStatus(ScCompanyProfile profile) {
        LocalDate today = LocalDate.now();
        LocalDate nearestExpiry = null;
        boolean anyExpired = false;
        boolean anyIncomplete = false;

        if (profile.getTradeLicenceExpiry() == null
                || profile.getTradeLicenceFilePath() == null
                || profile.getTradeLicenceFilePath().isBlank()) {
            anyIncomplete = true;
        } else {
            nearestExpiry = minDate(nearestExpiry, profile.getTradeLicenceExpiry());
            if (!profile.getTradeLicenceExpiry().isAfter(today)) {
                anyExpired = true;
            }
        }

        List<ScComplianceDocument> docs = complianceRepository.findByProfileUuidOrderByDocumentTypeAsc(profile.getUuid());
        for (ScComplianceDocType type : CORE_INSURANCE) {
            ScComplianceDocument doc = findDoc(docs, type);
            if (doc == null || doc.getExpiryDate() == null) {
                anyIncomplete = true;
            } else {
                nearestExpiry = minDate(nearestExpiry, doc.getExpiryDate());
                if (!doc.getExpiryDate().isAfter(today)) {
                    anyExpired = true;
                }
            }
        }

        for (ScComplianceDocument doc : docs) {
            if (doc.getExpiryDate() != null) {
                nearestExpiry = minDate(nearestExpiry, doc.getExpiryDate());
                if (!doc.getExpiryDate().isAfter(today)) {
                    anyExpired = true;
                }
            }
        }

        if (anyExpired) {
            return ScComplianceStatus.EXPIRED;
        }
        if (anyIncomplete) {
            return ScComplianceStatus.INCOMPLETE;
        }
        if (nearestExpiry != null && !nearestExpiry.isAfter(today.plusDays(EXPIRING_SOON_DAYS))) {
            return ScComplianceStatus.EXPIRING_SOON;
        }
        return ScComplianceStatus.VALID;
    }

    public List<String> listComplianceGaps(ScCompanyProfile profile) {
        List<String> gaps = new ArrayList<>();
        LocalDate today = LocalDate.now();
        if (profile.getTradeLicenceExpiry() == null) {
            gaps.add("Trade licence expiry date missing");
        } else if (!profile.getTradeLicenceExpiry().isAfter(today)) {
            gaps.add("Trade licence expired on " + profile.getTradeLicenceExpiry());
        }
        if (profile.getTradeLicenceFilePath() == null || profile.getTradeLicenceFilePath().isBlank()) {
            gaps.add("Trade licence file not uploaded");
        }
        List<ScComplianceDocument> docs =
                complianceRepository.findByProfileUuidOrderByDocumentTypeAsc(profile.getUuid());
        for (ScComplianceDocType type : CORE_INSURANCE) {
            ScComplianceDocument doc = findDoc(docs, type);
            if (doc == null || doc.getExpiryDate() == null) {
                gaps.add(type.displayName() + " expiry missing");
            } else if (!doc.getExpiryDate().isAfter(today)) {
                gaps.add(type.displayName() + " expired on " + doc.getExpiryDate());
            }
        }
        return gaps;
    }

    List<String> collectBlockingReasons(ScCompanyProfile profile, SubcontractorPackage packageEntity) {
        List<String> reasons = new ArrayList<>();
        LocalDate today = LocalDate.now();

        if (profile.getStatus() == ScCompanyStatus.BLACKLISTED) {
            reasons.add("Subcontractor company is blacklisted and cannot be appointed to packages.");
            return reasons;
        }
        if (profile.getStatus() == ScCompanyStatus.SUSPENDED) {
            reasons.add("Subcontractor company is suspended and cannot be appointed to packages.");
            return reasons;
        }

        appendPrequalificationReasons(profile, reasons);

        if (profile.getTradeLicenceExpiry() == null) {
            reasons.add("Trade licence expiry date is missing on the subcontractor company profile.");
        } else if (!profile.getTradeLicenceExpiry().isAfter(today)) {
            reasons.add("Trade licence expired on " + profile.getTradeLicenceExpiry() + ".");
        }

        if (profile.getTradeLicenceFilePath() == null || profile.getTradeLicenceFilePath().isBlank()) {
            reasons.add("Trade licence document file is missing on the subcontractor company profile.");
        }

        List<ScComplianceDocument> docs = complianceRepository.findByProfileUuidOrderByDocumentTypeAsc(profile.getUuid());
        for (ScComplianceDocType type : CORE_INSURANCE) {
            ScComplianceDocument doc = findDoc(docs, type);
            if (doc == null || doc.getExpiryDate() == null) {
                reasons.add(type.displayName() + " is missing or has no expiry date recorded.");
            } else if (!doc.getExpiryDate().isAfter(today)) {
                reasons.add(type.displayName() + " expired on " + doc.getExpiryDate() + ".");
            }
        }

        resolveSpecialistDocType(packageEntity).ifPresent(requiredType -> {
            ScComplianceDocument doc = findDoc(docs, requiredType);
            if (doc == null || doc.getExpiryDate() == null) {
                reasons.add("This package requires " + requiredType.displayName()
                        + ", which is not recorded on the subcontractor profile.");
            } else if (!doc.getExpiryDate().isAfter(today)) {
                reasons.add(requiredType.displayName() + " expired on " + doc.getExpiryDate()
                        + " — required for this package.");
            } else if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
                reasons.add(requiredType.displayName()
                        + " file is missing — required for this package.");
            }
        });

        appendCommunityRegistrationReasons(profile, packageEntity, reasons, today);
        appendBankDetailReasons(profile, reasons);

        return reasons;
    }

    private void appendPrequalificationReasons(ScCompanyProfile profile, List<String> reasons) {
        if (profile.getOrganizationUuid() == null) {
            return;
        }
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            return;
        }
        membershipRepository.findByOrganizationUuidAndCompanyId(profile.getOrganizationUuid(), companyId)
                .ifPresent(membership -> {
                    ScCompanyStatus status = membership.getStatus();
                    if (status != ScCompanyStatus.APPROVED && status != ScCompanyStatus.CONDITIONAL) {
                        reasons.add("Subcontractor is not prequalified for appointment (status: "
                                + status.name().replace('_', ' ') + ").");
                    }
                });
    }

    private void appendCommunityRegistrationReasons(
            ScCompanyProfile profile, SubcontractorPackage packageEntity, List<String> reasons, LocalDate today) {
        if (profile.getOrganizationUuid() == null || packageEntity == null || packageEntity.getProjectId() == null) {
            return;
        }
        Project project = projectRepository.findById(packageEntity.getProjectId()).orElse(null);
        if (project == null || project.getCommunityName() == null || project.getCommunityName().isBlank()) {
            return;
        }
        String community = project.getCommunityName().trim().toLowerCase();
        List<ScCommunityRegistration> registrations = communityRepository
                .findByOrganizationUuidOrderByAuthorityNameAsc(profile.getOrganizationUuid());
        boolean matched = registrations.stream().anyMatch(reg -> {
            if (reg.getExpiryDate() != null && !reg.getExpiryDate().isAfter(today)) {
                return false;
            }
            String code = reg.getAuthorityCode() != null ? reg.getAuthorityCode().toLowerCase() : "";
            String name = reg.getAuthorityName() != null ? reg.getAuthorityName().toLowerCase() : "";
            return community.contains(code) || code.contains(community)
                    || community.contains(name) || name.contains(community);
        });
        if (!matched) {
            reasons.add("Subcontractor is not registered with the project community ("
                    + project.getCommunityName() + ").");
        }
    }

    private void appendBankDetailReasons(ScCompanyProfile profile, List<String> reasons) {
        if (profile.getOrganizationUuid() == null) {
            return;
        }
        bankDetailRepository.findByOrganizationUuid(profile.getOrganizationUuid()).ifPresent(bank -> {
            if (bank.getIban() == null || bank.getIban().isBlank()) {
                reasons.add("Bank IBAN is not recorded on the subcontractor profile.");
            } else if (bank.getBankLetterFilePath() == null || bank.getBankLetterFilePath().isBlank()) {
                reasons.add("Bank confirmation letter is missing on the subcontractor profile.");
            } else if (bank.getVerificationStatus() == ScBankVerificationStatus.REJECTED) {
                reasons.add("Bank details were rejected and must be corrected before appointment.");
            }
        });
    }

    Optional<ScComplianceDocType> resolveSpecialistDocType(SubcontractorPackage packageEntity) {
        if (packageEntity == null || packageEntity.getSpecialistLicenceRequired() == null) {
            return Optional.empty();
        }
        return mapSpecialLicenceText(packageEntity.getSpecialistLicenceRequired());
    }

    static Optional<ScComplianceDocType> mapSpecialLicenceText(String text) {
        if (text == null || text.isBlank() || "-".equals(text.trim())) {
            return Optional.empty();
        }
        String lower = text.toLowerCase();
        if (lower.contains("civil defence")) {
            return Optional.of(ScComplianceDocType.CIVIL_DEFENCE);
        }
        if (lower.contains("sira")) {
            return Optional.of(ScComplianceDocType.SIRA);
        }
        if (lower.contains("dewa") || lower.contains("utility enrolled") || lower.contains("electrical")) {
            return Optional.of(ScComplianceDocType.DEWA_ELECTRICAL);
        }
        return Optional.empty();
    }

    static String itemStatus(LocalDate expiry, String filePath, boolean required) {
        LocalDate today = LocalDate.now();
        if (expiry == null || (required && (filePath == null || filePath.isBlank()))) {
            return "INCOMPLETE";
        }
        if (!expiry.isAfter(today)) {
            return "EXPIRED";
        }
        if (!expiry.isAfter(today.plusDays(EXPIRING_SOON_DAYS))) {
            return "EXPIRING_SOON";
        }
        return "VALID";
    }

    private static ScComplianceDocument findDoc(List<ScComplianceDocument> docs, ScComplianceDocType type) {
        return docs.stream().filter(d -> d.getDocumentType() == type).findFirst().orElse(null);
    }

    private static LocalDate minDate(LocalDate current, LocalDate candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.isBefore(current)) {
            return candidate;
        }
        return current;
    }
}
