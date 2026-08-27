package com.fitouts.roomcollab.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMessageRepository extends JpaRepository<RoomMessage, UUID> {
    List<RoomMessage> findByProjectRoomIdOrderByCreatedAtAsc(UUID projectRoomId);

    Optional<RoomMessage> findFirstByProjectRoomIdOrderByCreatedAtDesc(UUID projectRoomId);

    long countByProjectRoomIdAndCreatedAtAfter(UUID projectRoomId, OffsetDateTime after);
}
