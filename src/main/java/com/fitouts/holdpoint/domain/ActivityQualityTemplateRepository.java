package com.fitouts.holdpoint.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityQualityTemplateRepository
        extends JpaRepository<ActivityQualityTemplate, ActivityQualityTemplateId> {

    Optional<ActivityQualityTemplate> findByCompanyIdAndActivityType(UUID companyId, String activityType);

    List<ActivityQualityTemplate> findByCompanyIdOrderByActivityTypeAsc(UUID companyId);
}
