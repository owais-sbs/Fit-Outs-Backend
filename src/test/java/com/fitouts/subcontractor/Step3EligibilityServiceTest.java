package com.fitouts.subcontractor.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fitouts.subcontractor.domain.ScComplianceDocType;

class Step3EligibilityServiceTest {

    @Test
    void specialistRequirementsUseStableDocumentTypes() {
        assertThat(ScEligibilityService.mapSpecialLicenceText("Civil Defence-approved contractor"))
                .contains(ScComplianceDocType.CIVIL_DEFENCE);
        assertThat(ScEligibilityService.mapSpecialLicenceText("SIRA installer licence"))
                .contains(ScComplianceDocType.SIRA);
        assertThat(ScEligibilityService.mapSpecialLicenceText("Utility-enrolled electrical contractor"))
                .contains(ScComplianceDocType.DEWA_ELECTRICAL);
        assertThat(ScEligibilityService.mapSpecialLicenceText("Unknown certificate")).isEmpty();
    }

    @Test
    void complianceStatusDistinguishesExpiredAndExpiringDocuments() {
        assertThat(ScEligibilityService.itemStatus(LocalDate.now().minusDays(1), "file.pdf", true))
                .isEqualTo("EXPIRED");
        assertThat(ScEligibilityService.itemStatus(LocalDate.now().plusDays(10), "file.pdf", true))
                .isEqualTo("EXPIRING_SOON");
        assertThat(ScEligibilityService.itemStatus(LocalDate.now().plusDays(90), "file.pdf", true))
                .isEqualTo("VALID");
        assertThat(ScEligibilityService.itemStatus(LocalDate.now().plusDays(90), null, true))
                .isEqualTo("INCOMPLETE");
    }
}
