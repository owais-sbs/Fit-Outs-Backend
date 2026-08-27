package com.fitouts.communications.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.communications.domain.CommunicationOutbox;

public interface CommunicationOutboxRepository extends JpaRepository<CommunicationOutbox, UUID> {

    List<CommunicationOutbox> findTop20ByRecipientEmailIgnoreCaseOrderBySentAtDesc(String recipientEmail);

    List<CommunicationOutbox> findTop20BySentByOrderBySentAtDesc(Long sentBy);

    java.util.Optional<CommunicationOutbox> findTopByChannelUuidOrderBySentAtDesc(UUID channelUuid);

    java.util.Optional<CommunicationOutbox> findTopByChannelUuidAndSentByOrderBySentAtDesc(
            UUID channelUuid, Long sentBy);
}
