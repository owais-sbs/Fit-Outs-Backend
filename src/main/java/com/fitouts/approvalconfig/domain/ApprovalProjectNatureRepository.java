package com.fitouts.approvalconfig.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalProjectNatureRepository extends JpaRepository<ApprovalProjectNature, UUID> {

    List<ApprovalProjectNature> findByCompanyIdAndDeletedFalseOrderByNameAsc(UUID companyId);

    Optional<ApprovalProjectNature> findByIdAndDeletedFalse(UUID id);

    Optional<ApprovalProjectNature> findByCompanyIdAndCodeAndDeletedFalse(UUID companyId, String code);

    List<ApprovalProjectNature> findByCompanyIdAndDeletedFalseAndIdIn(UUID companyId, Collection<UUID> ids);
}
