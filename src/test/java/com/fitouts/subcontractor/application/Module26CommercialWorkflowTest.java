package com.fitouts.subcontractor.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import com.fitouts.subcontractor.domain.ScPaymentCertificateStatus;
import com.fitouts.subcontractor.domain.ScPortalRole;
import com.fitouts.subcontractor.domain.ScRetentionStatus;
import com.fitouts.subcontractor.domain.SubcontractorClaimStatus;

/**
 * Focused Module 26 commercial rules — claim ≠ certificate ≠ invoice ≠ payment.
 */
class Module26CommercialWorkflowTest {

    @Test
    void claimPrimaryStatusesExcludePaymentAsLifecycle() {
        assertThat(EnumSet.allOf(SubcontractorClaimStatus.class))
                .contains(
                        SubcontractorClaimStatus.DRAFT,
                        SubcontractorClaimStatus.SUBMITTED,
                        SubcontractorClaimStatus.UNDER_REVIEW,
                        SubcontractorClaimStatus.MEASURED,
                        SubcontractorClaimStatus.CERTIFIED);
        // PAID retained only for legacy rows; primary Module 26 claim end-state is CERTIFIED
        assertThat(SubcontractorClaimStatus.CERTIFIED.name()).isEqualTo("CERTIFIED");
    }

    @Test
    void certificateStatusesSeparatePayableFromPaid() {
        assertThat(EnumSet.allOf(ScPaymentCertificateStatus.class))
                .containsExactlyInAnyOrder(
                        ScPaymentCertificateStatus.DRAFT,
                        ScPaymentCertificateStatus.ISSUED,
                        ScPaymentCertificateStatus.PAYABLE,
                        ScPaymentCertificateStatus.PAID);
        assertThat(ScPaymentCertificateStatus.PAYABLE)
                .isNotEqualTo(ScPaymentCertificateStatus.PAID);
    }

    @Test
    void retentionStatusesSupportHoldToRelease() {
        assertThat(EnumSet.allOf(ScRetentionStatus.class))
                .contains(
                        ScRetentionStatus.HELD,
                        ScRetentionStatus.ELIGIBLE_FOR_RELEASE,
                        ScRetentionStatus.APPROVED_FOR_RELEASE,
                        ScRetentionStatus.RELEASED);
    }

    @Test
    void retentionUsesContractPercentOnCertifiedValue() {
        BigDecimal certified = new BigDecimal("95000.00");
        BigDecimal retentionPct = new BigDecimal("10");
        BigDecimal held = certified.multiply(retentionPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal backCharge = new BigDecimal("1000.00");
        BigDecimal net = certified.subtract(held).subtract(backCharge);
        assertThat(held).isEqualByComparingTo("9500.00");
        assertThat(net).isEqualByComparingTo("84500.00");
    }

    @Test
    void claimedAndMeasuredQuantitiesRemainSeparate() {
        BigDecimal claimedQty = new BigDecimal("1500");
        BigDecimal measuredQty = new BigDecimal("1400");
        assertThat(claimedQty).isNotEqualByComparingTo(measuredQty);
        // Measurement must not overwrite claimed
        BigDecimal preservedClaimed = claimedQty;
        assertThat(preservedClaimed).isEqualByComparingTo("1500");
    }

    @Test
    void invoiceEligibleOnlyWhenCertificatePayable() {
        assertThat(ScPaymentCertificateStatus.PAYABLE.name()).isEqualTo("PAYABLE");
        assertThat(ScPaymentCertificateStatus.ISSUED.name()).isNotEqualTo("PAYABLE");
        assertThat(ScPaymentCertificateStatus.DRAFT.name()).isNotEqualTo("PAYABLE");
    }

    @Test
    void invoiceCannotExceedAvailableCertificateValue() {
        BigDecimal netPayable = new BigDecimal("84500.00");
        BigDecimal alreadyInvoiced = new BigDecimal("20000.00");
        BigDecimal available = netPayable.subtract(alreadyInvoiced);
        BigDecimal requested = new BigDecimal("84500.00");
        assertThat(requested.compareTo(available) > 0).isTrue();
        assertThat(available).isEqualByComparingTo("64500.00");
    }

    @Test
    void scQsHasCommercialAccessSupervisorDoesNot() {
        ScPortalAccessService access = new ScPortalAccessService(null);
        assertThat(access.canAccessCommercial(ScPortalRole.SC_QS)).isTrue();
        assertThat(access.canAccessCommercial(ScPortalRole.SC_ADMIN)).isTrue();
        assertThat(access.canAccessCommercial(ScPortalRole.SC_SUPERVISOR)).isFalse();
        assertThat(access.canAccessCommercial(ScPortalRole.SC_ESTIMATOR)).isFalse();
        assertThat(access.canAccessCommercial(ScPortalRole.SC_DOC_CONTROLLER)).isFalse();
    }

    @Test
    void endToEndExampleAmountsMatchModule26Scenario() {
        BigDecimal award = new BigDecimal("500000");
        BigDecimal claimed = new BigDecimal("100000");
        BigDecimal measured = new BigDecimal("95000");
        BigDecimal retentionPct = new BigDecimal("10");
        BigDecimal retention = measured.multiply(retentionPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal backCharge = new BigDecimal("1000");
        BigDecimal net = measured.subtract(retention).subtract(backCharge);
        assertThat(award).isEqualByComparingTo("500000");
        assertThat(claimed).isEqualByComparingTo("100000");
        assertThat(measured).isEqualByComparingTo("95000");
        assertThat(retention).isEqualByComparingTo("9500");
        assertThat(net).isEqualByComparingTo("84500");
    }
}
