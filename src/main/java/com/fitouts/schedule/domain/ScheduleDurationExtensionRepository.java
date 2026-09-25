package com.fitouts.schedule.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleDurationExtensionRepository extends JpaRepository<ScheduleDurationExtension, UUID> {

    List<ScheduleDurationExtension> findByCompanyIdAndStatusOrderByCreatedAtDesc(
            UUID companyId, ScheduleDurationExtensionStatus status);

    Optional<ScheduleDurationExtension> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    boolean existsByActivityUuidAndStatus(UUID activityUuid, ScheduleDurationExtensionStatus status);
}
