package com.fitouts.subcontractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fitouts.subcontractor.domain.ScAttendanceParty;
import com.fitouts.subcontractor.domain.ScFreeIssueMaterial;
import com.fitouts.subcontractor.domain.ScFreeIssueSuppliedBy;
import com.fitouts.subcontractor.domain.ScPackageAttendance;
import com.fitouts.subcontractor.domain.SubcontractorPackage;

class Step21PackageStructureTest {

    @Test
    void freeIssueMaterialCarriesSupplierQuantityAndNotes() {
        ScFreeIssueMaterial material = new ScFreeIssueMaterial();
        material.setPackageUuid(UUID.randomUUID());
        material.setItemDescription("Light fittings");
        material.setSuppliedBy(ScFreeIssueSuppliedBy.MAIN_CONTRACTOR);
        material.setQuantity(new BigDecimal("120"));
        material.setUnit("No.");
        material.setNotes("Type per approved material schedule");
        material.setSortOrder(0);

        assertThat(material.getItemDescription()).isEqualTo("Light fittings");
        assertThat(material.getSuppliedBy()).isEqualTo(ScFreeIssueSuppliedBy.MAIN_CONTRACTOR);
        assertThat(material.getQuantity()).isEqualByComparingTo("120");
        assertThat(material.getNotes()).contains("approved material schedule");
    }

    @Test
    void attendanceSupportsRequiredResponsibilityParties() {
        ScPackageAttendance attendance = new ScPackageAttendance();
        attendance.setResponsibilityType("TEMPORARY_POWER");
        attendance.setResponsibleParty(ScAttendanceParty.MAIN_CONTRACTOR);

        assertThat(attendance.getResponsibilityType()).isEqualTo("TEMPORARY_POWER");
        assertThat(attendance.getResponsibleParty()).isEqualTo(ScAttendanceParty.MAIN_CONTRACTOR);
        assertThat(List.of(ScAttendanceParty.values()))
                .contains(ScAttendanceParty.SHARED, ScAttendanceParty.NOT_APPLICABLE);
    }

    @Test
    void packagePreservesStableSpecialistRequirementSnapshot() {
        SubcontractorPackage pkg = new SubcontractorPackage();
        pkg.setTradePackageCode("PKG-FIR");
        pkg.setTradePackageName("Fire");
        pkg.setSpecialistLicenceRequired("Civil Defence-approved contractor");

        assertThat(pkg.getTradePackageCode()).isEqualTo("PKG-FIR");
        assertThat(pkg.getSpecialistLicenceRequired())
                .isEqualTo("Civil Defence-approved contractor");
    }

    @Test
    void supportedSupplierValuesAreExplicitAndFinite() {
        assertThat(ScFreeIssueSuppliedBy.values())
                .containsExactly(
                        ScFreeIssueSuppliedBy.MAIN_CONTRACTOR,
                        ScFreeIssueSuppliedBy.SUBCONTRACTOR,
                        ScFreeIssueSuppliedBy.CLIENT,
                        ScFreeIssueSuppliedBy.OTHER);
    }
}
