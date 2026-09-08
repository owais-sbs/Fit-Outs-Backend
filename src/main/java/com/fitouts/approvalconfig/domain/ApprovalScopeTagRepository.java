package com.fitouts.approvalconfig.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalScopeTagRepository extends JpaRepository<ApprovalScopeTag, UUID> {

    List<ApprovalScopeTag> findByCompanyIdAndDeletedFalseOrderByNameAsc(UUID companyId);

    Optional<ApprovalScopeTag> findByIdAndDeletedFalse(UUID id);

    Optional<ApprovalScopeTag> findByCompanyIdAndCodeAndDeletedFalse(UUID companyId, String code);

    List<ApprovalScopeTag> findByCompanyIdAndDeletedFalseAndIdIn(UUID companyId, Collection<UUID> ids);
}
