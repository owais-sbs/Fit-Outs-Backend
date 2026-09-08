package com.fitouts.billing.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.billing.domain.PaymentRequestEvent;
import com.fitouts.billing.domain.PaymentRequestEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BillingApprovalEventService {

    private final PaymentRequestEventRepository paymentRequestEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID paymentRequestUuid, UUID companyId, String action, String step, Long actorId, String comments) {
        if (paymentRequestUuid == null || companyId == null) {
            return;
        }
        PaymentRequestEvent event = new PaymentRequestEvent();
        event.setPaymentRequestUuid(paymentRequestUuid);
        event.setCompanyId(companyId);
        event.setAction(action);
        event.setStep(step);
        event.setActorId(actorId);
        event.setComments(StringUtils.hasText(comments) ? comments.trim() : null);
        paymentRequestEventRepository.save(event);
    }
}
