package com.fitouts.qto.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QtoSessionRepository extends JpaRepository<QtoSession, UUID> {
    List<QtoSession> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    Optional<QtoSession> findByIdAndCompanyId(UUID id, UUID companyId);
}
