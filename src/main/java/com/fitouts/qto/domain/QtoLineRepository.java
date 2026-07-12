package com.fitouts.qto.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QtoLineRepository extends JpaRepository<QtoLine, UUID> {
    List<QtoLine> findBySessionIdOrderBySortOrderAsc(UUID sessionId);
    void deleteBySessionId(UUID sessionId);
}
