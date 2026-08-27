package com.fitouts.communications.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "communication_channel_members")
@IdClass(CommunicationChannelMemberId.class)
@Getter
@Setter
public class CommunicationChannelMember {

    @Id
    @Column(name = "channel_uuid")
    private UUID channelUuid;

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false, length = 20)
    private String role = "MEMBER";

    @Column(name = "last_read_at")
    private OffsetDateTime lastReadAt;
}
