package com.fitouts.creditnote.application;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.commercialapproval.application.CommercialApprovalCompletionHandler;
import com.fitouts.commercialapproval.domain.CommercialEventType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CreditNoteApprovalHandler implements CommercialApprovalCompletionHandler {

    private final CreditNoteService creditNoteService;

    @Override
    public CommercialEventType supports() {
        return CommercialEventType.CREDIT_NOTE;
    }

    @Override
    @Transactional
    public void onApproved(UUID entityUuid, UUID runUuid) {
        creditNoteService.markApproved(entityUuid);
    }

    @Override
    @Transactional
    public void onRejected(UUID entityUuid, UUID runUuid, String comment) {
        creditNoteService.markRejected(entityUuid, comment);
    }
}
