package com.fitouts.approval.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseCommentRepository extends JpaRepository<CaseComment, UUID> {

    List<CaseComment> findByCaseUuidOrderByRaisedDateAsc(UUID caseUuid);
}
