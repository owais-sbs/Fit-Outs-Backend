package com.fitouts.validation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressValidationRepository extends JpaRepository<ProgressValidation, UUID> {
    List<ProgressValidation> findByCompanyIdAndStatusOrderByCreatedAtDesc(UUID companyId, ProgressValidationStatus status);

    List<ProgressValidation> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);

    Optional<ProgressValidation> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    Optional<ProgressValidation> findByProgressUpdateUuid(UUID progressUpdateUuid);

    boolean existsByProgressUpdateUuid(UUID progressUpdateUuid);
}
