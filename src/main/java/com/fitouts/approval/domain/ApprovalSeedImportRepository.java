package com.fitouts.approval.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalSeedImportRepository extends JpaRepository<ApprovalSeedImport, UUID> {

    List<ApprovalSeedImport> findAllByOrderByImportedAtDesc();
}
