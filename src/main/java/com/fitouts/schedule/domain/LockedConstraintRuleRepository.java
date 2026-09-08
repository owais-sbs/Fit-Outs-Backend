package com.fitouts.schedule.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LockedConstraintRuleRepository extends JpaRepository<LockedConstraintRule, UUID> {

    @Query("SELECT r FROM LockedConstraintRule r WHERE r.companyId IS NULL OR r.companyId = :companyId")
    List<LockedConstraintRule> findVisible(@Param("companyId") UUID companyId);

    Optional<LockedConstraintRule> findByNameAndCompanyIdIsNull(String name);
}
