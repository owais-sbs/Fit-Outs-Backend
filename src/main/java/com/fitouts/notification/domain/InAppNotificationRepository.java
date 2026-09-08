package com.fitouts.notification.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {

    boolean existsByDedupeKey(String dedupeKey);

    @Query("SELECT n FROM InAppNotification n WHERE n.companyId = :companyId "
            + "AND (n.accountId IS NULL OR n.accountId = :accountId) "
            + "ORDER BY n.createdAt DESC")
    List<InAppNotification> findForAccount(@Param("companyId") UUID companyId,
                                           @Param("accountId") Long accountId,
                                           Pageable pageable);

    @Query("SELECT COUNT(n) FROM InAppNotification n WHERE n.companyId = :companyId "
            + "AND (n.accountId IS NULL OR n.accountId = :accountId) AND n.readAt IS NULL")
    long countUnread(@Param("companyId") UUID companyId, @Param("accountId") Long accountId);

    @Modifying
    @Query("UPDATE InAppNotification n SET n.readAt = CURRENT_TIMESTAMP "
            + "WHERE n.companyId = :companyId AND (n.accountId IS NULL OR n.accountId = :accountId) "
            + "AND n.readAt IS NULL")
    int markAllRead(@Param("companyId") UUID companyId, @Param("accountId") Long accountId);
}
