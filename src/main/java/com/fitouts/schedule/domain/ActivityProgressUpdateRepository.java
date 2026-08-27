package com.fitouts.schedule.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityProgressUpdateRepository extends JpaRepository<ActivityProgressUpdate, UUID> {
    List<ActivityProgressUpdate> findByActivityUuidOrderByReportedAtDesc(UUID activityUuid);

    List<ActivityProgressUpdate> findByProjectIdAndCompanyIdOrderByReportedAtDesc(Long projectId, UUID companyId);
}
