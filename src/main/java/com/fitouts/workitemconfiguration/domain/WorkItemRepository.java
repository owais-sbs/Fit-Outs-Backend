package com.fitouts.workitemconfiguration.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkItemRepository extends JpaRepository<WorkItem, UUID>,
        JpaSpecificationExecutor<WorkItem> {

    @Query("SELECT DISTINCT wi FROM WorkItem wi LEFT JOIN FETCH wi.scopeTags WHERE wi.id IN :ids")
    List<WorkItem> findByIdInWithScopeTags(@Param("ids") Collection<UUID> ids);

    Optional<WorkItem> findByIdAndDeletedFalse(UUID id);

    List<WorkItem> findByCompanyUuidAndDeletedFalse(UUID companyUuid);

    boolean existsByCompanyUuidAndWorkItemCodeAndDeletedFalse(UUID companyUuid, String workItemCode);

    boolean existsByCompanyUuidAndWorkItemCodeAndIdNotAndDeletedFalse(UUID companyUuid, String workItemCode, UUID id);
}
