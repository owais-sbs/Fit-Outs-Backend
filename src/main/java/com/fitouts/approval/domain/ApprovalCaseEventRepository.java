package com.fitouts.approval.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalCaseEventRepository extends JpaRepository<ApprovalCaseEvent, UUID> {

    List<ApprovalCaseEvent> findByCaseUuidOrderByCreatedAtDesc(UUID caseUuid);

    void deleteByCaseUuid(UUID caseUuid);
}
