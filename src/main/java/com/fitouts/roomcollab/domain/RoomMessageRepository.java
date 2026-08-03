package com.fitouts.roomcollab.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMessageRepository extends JpaRepository<RoomMessage, UUID> {
    List<RoomMessage> findByProjectRoomIdOrderByCreatedAtAsc(UUID projectRoomId);
}
