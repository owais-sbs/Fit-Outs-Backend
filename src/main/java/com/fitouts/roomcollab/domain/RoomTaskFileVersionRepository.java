package com.fitouts.roomcollab.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTaskFileVersionRepository extends JpaRepository<RoomTaskFileVersion, UUID> {
    List<RoomTaskFileVersion> findByTaskIdOrderByVersionNoAsc(UUID taskId);
    Optional<RoomTaskFileVersion> findFirstByTaskIdOrderByVersionNoDesc(UUID taskId);
    Optional<RoomTaskFileVersion> findByUuidAndTaskId(UUID uuid, UUID taskId);
    List<RoomTaskFileVersion> findByTaskIdAndIsFinalTrue(UUID taskId);

    List<RoomTaskFileVersion> findByTaskIdInAndIsFinalTrue(List<UUID> taskIds);
}
