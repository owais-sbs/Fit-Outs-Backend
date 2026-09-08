package com.fitouts.approval.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermitTypeRepository extends JpaRepository<PermitType, UUID> {

    @Query("SELECT p FROM PermitType p WHERE (p.companyId IS NULL OR p.companyId = :companyId) "
            + "AND p.active = true ORDER BY p.code ASC")
    List<PermitType> findVisible(@Param("companyId") UUID companyId);

    @Query("SELECT p FROM PermitType p WHERE p.code = :code "
            + "AND (p.companyId IS NULL OR p.companyId = :companyId) "
            + "ORDER BY CASE WHEN p.companyId IS NULL THEN 1 ELSE 0 END ASC")
    List<PermitType> findByCodeVisible(@Param("code") String code, @Param("companyId") UUID companyId);

    Optional<PermitType> findByCodeAndCompanyIdIsNull(String code);

    List<PermitType> findByCompanyIdIsNull();
}
