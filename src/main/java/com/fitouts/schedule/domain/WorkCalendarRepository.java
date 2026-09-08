package com.fitouts.schedule.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkCalendarRepository extends JpaRepository<WorkCalendar, UUID> {

    @Query("SELECT c FROM WorkCalendar c WHERE c.companyId IS NULL OR c.companyId = :companyId "
            + "ORDER BY c.companyId NULLS LAST, c.name")
    List<WorkCalendar> findVisible(@Param("companyId") UUID companyId);

    Optional<WorkCalendar> findFirstByCompanyIdAndDefaultCalendarTrue(UUID companyId);

    Optional<WorkCalendar> findFirstByCompanyIdIsNullAndDefaultCalendarTrue();
}
