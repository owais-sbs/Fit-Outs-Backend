package com.fitouts.boq.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BoqLineRepository extends JpaRepository<BoqLine, UUID> {
    List<BoqLine> findByBoqIdOrderBySortOrderAsc(UUID boqId);
    void deleteByBoqId(UUID boqId);
}
