package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcontractorWorkerRepository extends JpaRepository<SubcontractorWorker, UUID> {

    List<SubcontractorWorker> findBySubcontractorId(UUID subcontractorId);
}
