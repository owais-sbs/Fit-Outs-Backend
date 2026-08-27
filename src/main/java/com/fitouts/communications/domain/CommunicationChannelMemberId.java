package com.fitouts.communications.domain;

import java.io.Serializable;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CommunicationChannelMemberId implements Serializable {
    private UUID channelUuid;
    private Long accountId;
}
