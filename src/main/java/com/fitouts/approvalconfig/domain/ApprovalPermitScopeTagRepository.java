package com.fitouts.approvalconfig.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalPermitScopeTagRepository extends JpaRepository<ApprovalPermitScopeTag, UUID> {

    boolean existsByCompanyId(UUID companyId);

    List<ApprovalPermitScopeTag> findByCompanyId(UUID companyId);

    List<ApprovalPermitScopeTag> findByPermitTypeId(UUID permitTypeId);

    List<ApprovalPermitScopeTag> findByScopeTagId(UUID scopeTagId);

    List<ApprovalPermitScopeTag> findByPermitTypeIdIn(Collection<UUID> permitTypeIds);

    boolean existsByPermitTypeIdAndScopeTagId(UUID permitTypeId, UUID scopeTagId);
}
