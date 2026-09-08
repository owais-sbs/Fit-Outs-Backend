package com.fitouts.approvalconfig.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalPermitTypeRepository extends JpaRepository<ApprovalPermitType, UUID> {

    List<ApprovalPermitType> findByCompanyIdAndDeletedFalseOrderByPermitCodeAsc(UUID companyId);

    Optional<ApprovalPermitType> findByIdAndDeletedFalse(UUID id);

    Optional<ApprovalPermitType> findByCompanyIdAndPermitCodeAndDeletedFalse(UUID companyId, String permitCode);
}
