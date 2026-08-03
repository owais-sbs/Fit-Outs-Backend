package com.fitouts.roomcollab.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTaskMessageRepository extends JpaRepository<RoomTaskMessage, UUID> {
    List<RoomTaskMessage> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}
