package com.fitouts.approvalconfig.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalPropertyTypeRepository extends JpaRepository<ApprovalPropertyType, UUID> {

    List<ApprovalPropertyType> findByCompanyIdAndDeletedFalseOrderByNameAsc(UUID companyId);

    Optional<ApprovalPropertyType> findByIdAndDeletedFalse(UUID id);

    Optional<ApprovalPropertyType> findByCompanyIdAndCodeAndDeletedFalse(UUID companyId, String code);

    List<ApprovalPropertyType> findByCompanyIdAndDeletedFalseAndIdIn(UUID companyId, Collection<UUID> ids);
}
