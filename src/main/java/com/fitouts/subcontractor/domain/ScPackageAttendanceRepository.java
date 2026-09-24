package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScPackageAttendanceRepository extends JpaRepository<ScPackageAttendance, UUID> {
    List<ScPackageAttendance> findByPackageUuidAndCompanyIdOrderBySortOrderAsc(
            UUID packageUuid, UUID companyId);
    void deleteByPackageUuidAndCompanyId(UUID packageUuid, UUID companyId);
}
