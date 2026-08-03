package com.fitouts.roomcollab.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTaskRepository extends JpaRepository<RoomTask, UUID> {
    List<RoomTask> findByProjectRoomIdOrderByCreatedAtDesc(UUID projectRoomId);
    List<RoomTask> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<RoomTask> findByProjectIdAndStatusOrderByUpdatedAtDesc(Long projectId, RoomTaskStatus status);
    List<RoomTask> findByProjectIdAndStatusInOrderByUpdatedAtDesc(Long projectId, List<RoomTaskStatus> statuses);
    Optional<RoomTask> findByUuidAndProjectId(UUID uuid, Long projectId);
    List<RoomTask> findByProjectIdAndStatus(Long projectId, RoomTaskStatus status);
}
