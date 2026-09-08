package com.fitouts.billing.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRequestEventRepository extends JpaRepository<PaymentRequestEvent, UUID> {

    List<PaymentRequestEvent> findByPaymentRequestUuidOrderByCreatedAtAsc(UUID paymentRequestUuid);

    List<PaymentRequestEvent> findByPaymentRequestUuidInOrderByCreatedAtAsc(Collection<UUID> paymentRequestUuids);
}
