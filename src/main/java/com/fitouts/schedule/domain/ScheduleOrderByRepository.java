package com.fitouts.schedule.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleOrderByRepository extends JpaRepository<ScheduleOrderBy, UUID> {

    @Query("SELECT o FROM ScheduleOrderBy o WHERE o.projectId = :projectId ORDER BY o.orderByDate ASC")
    List<ScheduleOrderBy> findByProjectIdOrderByOrderByDateAsc(@Param("projectId") Long projectId);

    List<ScheduleOrderBy> findByCompanyIdAndOverdueTrue(UUID companyId);

    void deleteByProjectId(Long projectId);
}
