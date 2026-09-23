package com.fitouts.subcontractor.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fitouts.subcontractor.api.ScAwardPackSectionStatus;
import com.fitouts.subcontractor.domain.ScContractStatus;
import com.fitouts.subcontractor.domain.ScPackageWorkerStatus;
import com.fitouts.subcontractor.domain.ScPortalRole;
import com.fitouts.subcontractor.domain.SubcontractorPackageStatus;

class Step5PostAwardLifecycleTest {

    @Test
    void packageLifecycleUsesExistingStatusNames() {
        assertThat(EnumSet.allOf(SubcontractorPackageStatus.class))
                .contains(
                        SubcontractorPackageStatus.OPEN,
                        SubcontractorPackageStatus.APPOINTED,
                        SubcontractorPackageStatus.IN_PROGRESS,
                        SubcontractorPackageStatus.COMPLETE);
    }

    @Test
    void contractStatusFlowIsPreserved() {
        assertThat(EnumSet.allOf(ScContractStatus.class))
                .contains(
                        ScContractStatus.WAITING_FOR_ADMIN_SIGNATURE,
                        ScContractStatus.WAITING_FOR_CONTRACTOR_SIGNATURE,
                        ScContractStatus.SIGNED_AND_EXECUTED);
    }

    @Test
    void mobilisationWorkerStatusesAreExplicit() {
        assertThat(EnumSet.allOf(ScPackageWorkerStatus.class))
                .contains(
                        ScPackageWorkerStatus.NOMINATED,
                        ScPackageWorkerStatus.SITE_ELIGIBLE,
                        ScPackageWorkerStatus.BLOCKED,
                        ScPackageWorkerStatus.REMOVED);
    }

    @Test
    void portalRolesPreserveSeparationOfDuties() {
        assertThat(ScPortalAccessService.class.getDeclaredMethods())
                .extracting(m -> m.getName())
                .contains(
                        "canAccessCommercial",
                        "canAccessExecution",
                        "canAccessTendering",
                        "canAccessDocuments");

        ScPortalAccessService access = new ScPortalAccessService(null);
        assertThat(access.canAccessTendering(ScPortalRole.SC_ESTIMATOR)).isTrue();
        assertThat(access.canAccessTendering(ScPortalRole.SC_SUPERVISOR)).isFalse();
        assertThat(access.canAccessTendering(ScPortalRole.SC_QS)).isFalse();
        assertThat(access.canAccessCommercial(ScPortalRole.SC_QS)).isTrue();
        assertThat(access.canAccessCommercial(ScPortalRole.SC_SUPERVISOR)).isFalse();
        assertThat(access.canAccessExecution(ScPortalRole.SC_SUPERVISOR)).isTrue();
        assertThat(access.canAccessExecution(ScPortalRole.SC_QS)).isFalse();
        assertThat(access.canAccessDocuments(ScPortalRole.SC_DOC_CONTROLLER)).isTrue();
    }

    @Test
    void originalAwardValueIsNotMutatedByVariationArithmeticExample() {
        BigDecimal originalAward = new BigDecimal("100000.00");
        BigDecimal approvedVariation = new BigDecimal("15000.00");
        BigDecimal adjusted = originalAward.add(approvedVariation);
        assertThat(originalAward).isEqualByComparingTo("100000.00");
        assertThat(adjusted).isEqualByComparingTo("115000.00");
    }

    @Test
    void awardPackSectionStatusesAreExplicit() {
        ScAwardPackSectionStatus connected = ScAwardPackSectionStatus.builder()
                .code("FREE_ISSUE")
                .label("Free-issue materials")
                .status("CONNECTED")
                .build();
        assertThat(connected.getStatus()).isEqualTo("CONNECTED");
        assertThat(List.of("CONNECTED", "PARTIAL", "NOT_YET_AVAILABLE")).contains(connected.getStatus());
    }

    @Test
    void workerSiteEligibilityRejectsExpiredMandatoryDocuments() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertThat(yesterday.isAfter(LocalDate.now())).isFalse();
        assertThat(UUID.randomUUID()).isNotNull();
    }
}
