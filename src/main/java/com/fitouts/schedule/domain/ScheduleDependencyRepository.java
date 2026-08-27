package com.fitouts.schedule.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleDependencyRepository extends JpaRepository<ScheduleDependency, UUID> {
    List<ScheduleDependency> findByProjectIdAndCompanyId(Long projectId, UUID companyId);

    void deleteByPredecessorUuidOrSuccessorUuid(UUID predecessorUuid, UUID successorUuid);
}
