package com.fitouts.commercialapproval.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialApprovalStepRoleRepository extends JpaRepository<CommercialApprovalStepRole, UUID> {
    List<CommercialApprovalStepRole> findByStepUuid(UUID stepUuid);

    void deleteByStepUuid(UUID stepUuid);
}
