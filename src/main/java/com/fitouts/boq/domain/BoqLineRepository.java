package com.fitouts.boq.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoqLineRepository extends JpaRepository<BoqLine, UUID> {
    List<BoqLine> findByBoqIdOrderBySortOrderAsc(UUID boqId);

    @Query("SELECT DISTINCT l FROM BoqLine l "
            + "LEFT JOIN FETCH l.workItem "
            + "LEFT JOIN FETCH l.qtoLine ql "
            + "LEFT JOIN FETCH ql.workItem "
            + "WHERE l.boq.id = :boqId")
    List<BoqLine> findByBoqIdWithWorkItems(@Param("boqId") UUID boqId);

    void deleteByBoqId(UUID boqId);
}
