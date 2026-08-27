package com.fitouts.communications.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.communications.domain.CommunicationChannelMember;
import com.fitouts.communications.domain.CommunicationChannelMemberId;

public interface CommunicationChannelMemberRepository
        extends JpaRepository<CommunicationChannelMember, CommunicationChannelMemberId> {

    List<CommunicationChannelMember> findByChannelUuid(UUID channelUuid);

    List<CommunicationChannelMember> findByAccountId(Long accountId);
}
