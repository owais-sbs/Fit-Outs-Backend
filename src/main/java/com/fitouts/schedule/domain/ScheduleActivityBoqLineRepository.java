package com.fitouts.schedule.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleActivityBoqLineRepository extends JpaRepository<ScheduleActivityBoqLine, UUID> {

    List<ScheduleActivityBoqLine> findByProjectIdAndCompanyId(Long projectId, UUID companyId);

    List<ScheduleActivityBoqLine> findByScheduleActivityUuidIn(Collection<UUID> activityUuids);

    void deleteByProjectIdAndCompanyId(Long projectId, UUID companyId);
}
