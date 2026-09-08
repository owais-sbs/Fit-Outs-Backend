package com.fitouts.approvalconfig.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalAuthorityRepository extends JpaRepository<ApprovalAuthority, UUID> {

    List<ApprovalAuthority> findByCompanyIdAndDeletedFalseOrderByNameAsc(UUID companyId);

    Optional<ApprovalAuthority> findByIdAndDeletedFalse(UUID id);

    Optional<ApprovalAuthority> findByCompanyIdAndCodeAndDeletedFalse(UUID companyId, String code);

    boolean existsByCompanyIdAndDeletedFalse(UUID companyId);
}
