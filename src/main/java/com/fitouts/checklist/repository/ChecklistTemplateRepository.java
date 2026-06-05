package com.fitouts.checklist.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.checklist.domain.ChecklistTemplate;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, UUID> {
}
