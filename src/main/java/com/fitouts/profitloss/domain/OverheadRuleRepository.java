package com.fitouts.profitloss.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OverheadRuleRepository extends JpaRepository<OverheadRule, UUID> {

    Optional<OverheadRule> findFirstByCompanyIdAndActiveTrueOrderByUpdatedAtDesc(UUID companyId);

    List<OverheadRule> findByCompanyIdAndActiveTrueOrderByUpdatedAtDesc(UUID companyId);
}
