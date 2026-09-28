package com.fitouts.subscription.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {

    @Query("""
            SELECT p FROM SubscriptionPayment p
            JOIN FETCH p.company
            JOIN FETCH p.plan
            LEFT JOIN FETCH p.decidedBy
            WHERE p.uuid = :uuid
            """)
    Optional<SubscriptionPayment> findDetailedById(@Param("uuid") UUID uuid);

    @Query("""
            SELECT p FROM SubscriptionPayment p
            JOIN FETCH p.company
            JOIN FETCH p.plan
            LEFT JOIN FETCH p.decidedBy
            ORDER BY p.createdAt DESC
            """)
    List<SubscriptionPayment> findAllDetailed();
}
