package com.fitouts.approvalconfig.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalPermitAuthorityRoleRepository
        extends JpaRepository<ApprovalPermitAuthorityRole, UUID> {

    List<ApprovalPermitAuthorityRole> findByCompanyId(UUID companyId);

    List<ApprovalPermitAuthorityRole> findByPermitTypeId(UUID permitTypeId);

    boolean existsByPermitTypeIdAndAuthorityRole(UUID permitTypeId, String authorityRole);

    void deleteByPermitTypeId(UUID permitTypeId);
}
