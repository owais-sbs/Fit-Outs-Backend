package com.fitouts.roomcollab.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTaskMessageRepository extends JpaRepository<RoomTaskMessage, UUID> {
    List<RoomTaskMessage> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    Optional<RoomTaskMessage> findFirstByTaskIdOrderByCreatedAtDesc(UUID taskId);

    long countByTaskIdAndCreatedAtAfter(UUID taskId, OffsetDateTime after);
}
