package com.fitouts.schedule.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityMaterialIssueRepository extends JpaRepository<ActivityMaterialIssue, UUID> {

    List<ActivityMaterialIssue> findByProgressUpdateUuidOrderByCreatedAtAsc(UUID progressUpdateUuid);

    List<ActivityMaterialIssue> findByActivityUuidAndCompanyIdOrderByCreatedAtAsc(UUID activityUuid, UUID companyId);

    List<ActivityMaterialIssue> findByProjectIdAndCompanyIdAndStatus(
            Long projectId, UUID companyId, ActivityMaterialIssueStatus status);

    List<ActivityMaterialIssue> findByProgressUpdateUuidIn(Collection<UUID> progressUpdateUuids);
}
