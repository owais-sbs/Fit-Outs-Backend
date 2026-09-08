package com.fitouts.schedule.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateDependencyRepository extends JpaRepository<TemplateDependency, UUID> {

    List<TemplateDependency> findByTemplateUuid(UUID templateUuid);

    void deleteByTemplateUuid(UUID templateUuid);
}
