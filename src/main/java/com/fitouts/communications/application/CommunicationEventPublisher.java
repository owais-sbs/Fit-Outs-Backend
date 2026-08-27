package com.fitouts.communications.application;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.fitouts.communications.dto.ChannelMessageResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CommunicationEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishMessageCreated(java.util.UUID channelUuid, ChannelMessageResponse message) {
        messagingTemplate.convertAndSend("/topic/channels/" + channelUuid, message);
    }

    public void publishInboxUpdated(Long accountId) {
        messagingTemplate.convertAndSend("/topic/inbox/" + accountId, "refresh");
    }
}
