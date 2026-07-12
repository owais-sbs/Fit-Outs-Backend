package com.fitouts.boq.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.shared.enums.BoqDocumentStatus;

public interface BoqApprovalLogRepository extends JpaRepository<BoqApprovalLog, UUID> {
    List<BoqApprovalLog> findByBoqIdOrderByCreatedAtAsc(UUID boqId);
}
