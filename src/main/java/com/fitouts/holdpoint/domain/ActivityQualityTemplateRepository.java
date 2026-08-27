package com.fitouts.holdpoint.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityQualityTemplateRepository
        extends JpaRepository<ActivityQualityTemplate, ActivityQualityTemplateId> {

    Optional<ActivityQualityTemplate> findByCompanyIdAndActivityType(UUID companyId, String activityType);
}
