package com.fitouts.checklist.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.checklist.domain.ChecklistTemplate;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, UUID> {

    List<ChecklistTemplate> findByCompanyUuid(UUID companyUuid);

    java.util.Optional<ChecklistTemplate> findFirstByCompanyUuidAndNameIgnoreCase(UUID companyUuid, String name);
}
