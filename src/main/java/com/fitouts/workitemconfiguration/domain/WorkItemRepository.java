package com.fitouts.workitemconfiguration.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkItemRepository extends JpaRepository<WorkItem, UUID>,
        JpaSpecificationExecutor<WorkItem> {

    Optional<WorkItem> findByIdAndDeletedFalse(UUID id);

    List<WorkItem> findByCompanyUuidAndDeletedFalse(UUID companyUuid);

    boolean existsByCompanyUuidAndWorkItemCodeAndDeletedFalse(UUID companyUuid, String workItemCode);

    boolean existsByCompanyUuidAndWorkItemCodeAndIdNotAndDeletedFalse(UUID companyUuid, String workItemCode, UUID id);

    @Query("SELECT COUNT(rt) FROM RoomType rt JOIN rt.workItems wi WHERE wi.id = :workItemId AND rt.deleted = false")
    long countRoomTypesByWorkItemId(@Param("workItemId") UUID workItemId);
}
