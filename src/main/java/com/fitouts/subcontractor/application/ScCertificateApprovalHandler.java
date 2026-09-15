package com.fitouts.subcontractor.application;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.commercialapproval.application.CommercialApprovalCompletionHandler;
import com.fitouts.commercialapproval.domain.CommercialEventType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ScCertificateApprovalHandler implements CommercialApprovalCompletionHandler {

    private final ScWave7CommercialService scWave7CommercialService;

    @Override
    public CommercialEventType supports() {
        return CommercialEventType.SC_CERTIFICATE;
    }

    @Override
    @Transactional
    public void onApproved(UUID entityUuid, UUID runUuid) {
        scWave7CommercialService.issueCertificateAfterMatrix(entityUuid);
    }

    @Override
    @Transactional
    public void onRejected(UUID entityUuid, UUID runUuid, String comment) {
        scWave7CommercialService.cancelCertificateAfterMatrixReject(entityUuid, comment);
    }
}
