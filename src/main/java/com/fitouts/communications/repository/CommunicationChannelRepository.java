package com.fitouts.communications.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitouts.communications.domain.CommunicationChannel;
import com.fitouts.communications.domain.ChannelType;

public interface CommunicationChannelRepository extends JpaRepository<CommunicationChannel, UUID> {

    List<CommunicationChannel> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<CommunicationChannel> findByCompanyIdAndChannelTypeOrderByCreatedAtDesc(
            UUID companyId, ChannelType channelType);

    @Query("""
            SELECT c FROM CommunicationChannel c, CommunicationChannelMember m
            WHERE m.channelUuid = c.uuid AND m.accountId = :accountId AND c.companyId = :companyId
            ORDER BY c.createdAt DESC
            """)
    List<CommunicationChannel> findMemberChannels(
            @Param("accountId") Long accountId, @Param("companyId") UUID companyId);

    boolean existsByProjectRoomId(UUID projectRoomId);

    boolean existsByRoomTaskId(UUID roomTaskId);

    List<CommunicationChannel> findByProjectRoomIdOrderByCreatedAtAsc(UUID projectRoomId);

    List<CommunicationChannel> findByRoomTaskIdOrderByCreatedAtAsc(UUID roomTaskId);
}
