package com.fitouts.boq.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BoqApprovalLogRepository extends JpaRepository<BoqApprovalLog, UUID> {
    List<BoqApprovalLog> findByBoqIdOrderByCreatedAtAsc(UUID boqId);

    List<BoqApprovalLog> findByBoqIdInOrderByCreatedAtAsc(List<UUID> boqIds);
}
