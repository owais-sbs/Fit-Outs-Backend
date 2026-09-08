package com.fitouts.schedule.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScopeToggleRepository extends JpaRepository<ScopeToggle, UUID> {

    @Query("SELECT s FROM ScopeToggle s WHERE s.companyId IS NULL OR s.companyId = :companyId ORDER BY s.label")
    List<ScopeToggle> findVisible(@Param("companyId") UUID companyId);

    Optional<ScopeToggle> findByCodeAndCompanyIdIsNull(String code);
}
