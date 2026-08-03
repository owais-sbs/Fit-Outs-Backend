package com.fitouts.roomcollab.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTaskEventRepository extends JpaRepository<RoomTaskEvent, UUID> {
    List<RoomTaskEvent> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}
