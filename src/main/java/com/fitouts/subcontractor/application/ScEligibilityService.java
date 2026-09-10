package com.fitouts.subcontractor.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.schedule.domain.TradePackage;
import com.fitouts.schedule.domain.TradePackageRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.api.ScAppointmentEligibilityResponse;
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
import com.fitouts.subcontractor.domain.SubcontractorPackage;

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
        List<String> reasons = collectBlockingReasons(profile, packageEntity);
        return ScAppointmentEligibilityResponse.builder()
                .eligible(reasons.isEmpty())
                .blockingReasons(reasons)
                .complianceStatus(computeComplianceStatus(profile).name())
                .companyStatus(profile.getStatus().name())
                .build();
    }

    public void assertEligibleForAppointment(Long accountId, SubcontractorPackage packageEntity) {
        List<String> reasons = collectBlockingReasons(requireProfile(accountId), packageEntity);
        if (!reasons.isEmpty()) {
            throw new BadRequestException(reasons.get(0));
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
        if (packageEntity == null || packageEntity.getTradePackageCode() == null) {
            return Optional.empty();
        }
        String code = packageEntity.getTradePackageCode();
        UUID companyId = CompanyContext.get();
        Optional<TradePackage> tradePackage = tradePackageRepository.findVisible(companyId).stream()
                .filter(tp -> code.equals(tp.getCode()))
                .findFirst();
        return tradePackage
                .map(TradePackage::getSpecialLicenceRequired)
                .flatMap(ScEligibilityService::mapSpecialLicenceText);
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
