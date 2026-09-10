package com.fitouts.snag.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SnagRepository extends JpaRepository<Snag, UUID> {

    List<Snag> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);

    Optional<Snag> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<Snag> findByProjectIdAndCompanyIdAndClientVisibleTrueOrderByCreatedAtDesc(Long projectId, UUID companyId);

    List<Snag> findByAssigneeAccountIdAndCompanyIdOrderByCreatedAtDesc(Long assigneeAccountId, UUID companyId);

    List<Snag> findByAssigneeAccountIdAndProjectIdInAndCompanyIdOrderByCreatedAtDesc(
            Long assigneeAccountId, List<Long> projectIds, UUID companyId);
}
