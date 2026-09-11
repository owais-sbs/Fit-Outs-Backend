package com.fitouts.approvalconfig.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalPermitProjectNatureRepository extends JpaRepository<ApprovalPermitProjectNature, UUID> {

    boolean existsByCompanyId(UUID companyId);

    List<ApprovalPermitProjectNature> findByCompanyId(UUID companyId);

    List<ApprovalPermitProjectNature> findByPermitTypeId(UUID permitTypeId);

    List<ApprovalPermitProjectNature> findByProjectNatureId(UUID projectNatureId);

    boolean existsByPermitTypeIdAndProjectNatureId(UUID permitTypeId, UUID projectNatureId);
}
