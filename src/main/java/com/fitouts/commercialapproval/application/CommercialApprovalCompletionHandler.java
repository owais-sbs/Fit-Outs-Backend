package com.fitouts.commercialapproval.application;

import java.util.UUID;

import com.fitouts.commercialapproval.domain.CommercialEventType;

/**
 * Callback when a commercial approval run reaches a terminal state.
 * Implemented by variation / SC certificate services.
 */
public interface CommercialApprovalCompletionHandler {
    CommercialEventType supports();

    void onApproved(UUID entityUuid, UUID runUuid);

    void onRejected(UUID entityUuid, UUID runUuid, String comment);
}
