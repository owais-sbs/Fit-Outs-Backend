package com.fitouts.approval.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthorityRepository extends JpaRepository<Authority, UUID> {

    /** Global seed rows plus this tenant's own additions. */
    @Query("SELECT a FROM Authority a WHERE (a.companyId IS NULL OR a.companyId = :companyId) "
            + "AND a.active = true ORDER BY a.type ASC, a.name ASC")
    List<Authority> findVisible(@Param("companyId") UUID companyId);

    @Query("SELECT a FROM Authority a WHERE a.code = :code "
            + "AND (a.companyId IS NULL OR a.companyId = :companyId) "
            + "ORDER BY CASE WHEN a.companyId IS NULL THEN 1 ELSE 0 END ASC")
    List<Authority> findByCodeVisible(@Param("code") String code, @Param("companyId") UUID companyId);

    Optional<Authority> findByCodeAndCompanyIdIsNull(String code);

    List<Authority> findByCompanyIdIsNull();
}
