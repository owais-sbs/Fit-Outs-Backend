package com.fitouts.communications.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.communications.domain.CommunicationMessage;

public interface CommunicationMessageRepository extends JpaRepository<CommunicationMessage, UUID> {

    List<CommunicationMessage> findByChannelUuidOrderByCreatedAtAsc(UUID channelUuid);

    Optional<CommunicationMessage> findFirstByChannelUuidOrderByCreatedAtDesc(UUID channelUuid);

    long countByChannelUuidAndCreatedAtAfter(UUID channelUuid, java.time.OffsetDateTime after);
}
