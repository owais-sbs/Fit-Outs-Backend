package com.fitouts.schedule.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, UUID> {

    /** System templates plus this tenant's own, newest tenant version winning on a code clash. */
    @Query("SELECT t FROM ScheduleTemplate t WHERE t.companyId IS NULL OR t.companyId = :companyId "
            + "ORDER BY t.companyId NULLS LAST, t.code")
    List<ScheduleTemplate> findVisible(@Param("companyId") UUID companyId);

    @Query("SELECT t FROM ScheduleTemplate t WHERE t.code = :code "
            + "AND (t.companyId IS NULL OR t.companyId = :companyId) ORDER BY t.companyId NULLS LAST")
    List<ScheduleTemplate> findVisibleByCode(@Param("code") String code, @Param("companyId") UUID companyId);

    Optional<ScheduleTemplate> findByCodeAndCompanyIdIsNull(String code);

    @Query("SELECT t FROM ScheduleTemplate t WHERE t.companyId IS NULL ORDER BY t.code")
    List<ScheduleTemplate> findSystemTemplates();

    List<ScheduleTemplate> findByCompanyId(UUID companyId);
}
