package com.fitouts.schedule.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateActivityRepository extends JpaRepository<TemplateActivity, UUID> {

    List<TemplateActivity> findByTemplateUuidOrderBySortOrderAsc(UUID templateUuid);

    void deleteByTemplateUuid(UUID templateUuid);
}
