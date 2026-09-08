package com.fitouts.approval.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, UUID> {

    @Query("SELECT d FROM DocumentType d WHERE (d.companyId IS NULL OR d.companyId = :companyId) "
            + "AND d.active = true ORDER BY d.code ASC")
    List<DocumentType> findVisible(@Param("companyId") UUID companyId);

    @Query("SELECT d FROM DocumentType d WHERE d.code = :code "
            + "AND (d.companyId IS NULL OR d.companyId = :companyId) "
            + "ORDER BY CASE WHEN d.companyId IS NULL THEN 1 ELSE 0 END ASC")
    List<DocumentType> findByCodeVisible(@Param("code") String code, @Param("companyId") UUID companyId);

    Optional<DocumentType> findByCodeAndCompanyIdIsNull(String code);
}
