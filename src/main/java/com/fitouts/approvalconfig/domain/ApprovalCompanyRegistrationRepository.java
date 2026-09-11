package com.fitouts.approvalconfig.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalCompanyRegistrationRepository extends JpaRepository<ApprovalCompanyRegistration, UUID> {

    List<ApprovalCompanyRegistration> findByCompanyIdAndDeletedFalseOrderByUpdatedAtDesc(UUID companyId);

    Optional<ApprovalCompanyRegistration> findByIdAndDeletedFalse(UUID id);

    Optional<ApprovalCompanyRegistration> findByCompanyIdAndAuthorityIdAndDeletedFalse(UUID companyId, UUID authorityId);
}
