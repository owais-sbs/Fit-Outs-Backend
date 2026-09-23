package com.fitouts.boq.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitouts.shared.enums.BoqDocumentStatus;

public interface BoqDocumentRepository extends JpaRepository<BoqDocument, UUID> {
    List<BoqDocument> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    Optional<BoqDocument> findByIdAndCompanyId(UUID id, UUID companyId);
    List<BoqDocument> findByCompanyIdAndStatusInOrderBySubmittedAtDesc(UUID companyId, List<BoqDocumentStatus> statuses);
    List<BoqDocument> findByParentBoqIdOrderByCreatedAtAsc(UUID parentBoqId);

    @Query("SELECT d FROM BoqDocument d JOIN FETCH d.project p WHERE d.companyId = :companyId ORDER BY d.createdAt DESC")
    List<BoqDocument> findAllWithProjectByCompanyId(@Param("companyId") UUID companyId);

    @Query("""
            select distinct d.project.id
            from BoqDocument d
            where d.companyId = :companyId
              and d.status in :statuses
            """)
    List<Long> findDistinctProjectIdsByCompanyIdAndStatusIn(
            @Param("companyId") UUID companyId,
            @Param("statuses") List<BoqDocumentStatus> statuses);
}
