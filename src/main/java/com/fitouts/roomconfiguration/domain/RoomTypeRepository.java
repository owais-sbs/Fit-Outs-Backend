package com.fitouts.roomconfiguration.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomTypeRepository extends JpaRepository<RoomType, UUID>,
        JpaSpecificationExecutor<RoomType> {

    Optional<RoomType> findByIdAndDeletedFalse(UUID id);

    List<RoomType> findByCompanyUuidAndDeletedFalse(UUID companyUuid);

    boolean existsByCompanyUuidAndRoomCodeAndDeletedFalse(UUID companyUuid, String roomCode);

    boolean existsByCompanyUuidAndRoomCodeAndIdNotAndDeletedFalse(UUID companyUuid, String roomCode, UUID id);

    @Query("SELECT rt FROM RoomType rt JOIN rt.workItems wi WHERE wi.id = :workItemId AND rt.deleted = false")
    List<RoomType> findByWorkItemId(@Param("workItemId") UUID workItemId);
}
