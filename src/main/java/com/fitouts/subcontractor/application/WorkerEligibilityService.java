package com.fitouts.subcontractor.application;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.fitouts.subcontractor.domain.SubcontractorWorker;

@Service
public class WorkerEligibilityService {

    public boolean calculateSiteEligibility(SubcontractorWorker worker) {
        if (worker == null) {
            return false;
        }
        LocalDate today = LocalDate.now();

        if (worker.getVisaExpiry() != null && worker.getVisaExpiry().isBefore(today)) {
            return false;
        }
        if (worker.getEidExpiry() != null && worker.getEidExpiry().isBefore(today)) {
            return false;
        }
        if (worker.getInsuranceExpiry() != null && worker.getInsuranceExpiry().isBefore(today)) {
            return false;
        }
        if (worker.getAccessCardExpiry() != null && worker.getAccessCardExpiry().isBefore(today)) {
            return false;
        }
        return true;
    }
}
