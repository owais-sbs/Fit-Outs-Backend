package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcontractorRegistrationRepository extends JpaRepository<SubcontractorRegistration, UUID> {

    List<SubcontractorRegistration> findBySubcontractorId(UUID subcontractorId);
}
