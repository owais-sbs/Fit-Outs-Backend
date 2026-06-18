package com.fitouts.workitemconfiguration.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkItemMasterRepository extends JpaRepository<WorkItemMaster, UUID>,
        JpaSpecificationExecutor<WorkItemMaster> {

    Optional<WorkItemMaster> findByIdAndDeletedFalse(UUID id);

    List<WorkItemMaster> findByCompanyUuidAndDeletedFalse(UUID companyUuid);

    boolean existsByCompanyUuidAndCodeAndDeletedFalse(UUID companyUuid, String code);

    boolean existsByCompanyUuidAndCodeAndIdNotAndDeletedFalse(UUID companyUuid, String code, UUID id);
}
