package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcontractorUserRepository extends JpaRepository<SubcontractorUser, UUID> {

    List<SubcontractorUser> findBySubcontractorId(UUID subcontractorId);

    List<SubcontractorUser> findByUserId(Long userId);

    Optional<SubcontractorUser> findBySubcontractorIdAndUserId(UUID subcontractorId, Long userId);
}
