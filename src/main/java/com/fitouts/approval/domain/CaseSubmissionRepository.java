package com.fitouts.approval.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseSubmissionRepository extends JpaRepository<CaseSubmission, UUID> {

    List<CaseSubmission> findByCaseUuidOrderByVersionAsc(UUID caseUuid);

    Optional<CaseSubmission> findFirstByCaseUuidOrderByVersionDesc(UUID caseUuid);
}
