package com.fitouts.approval.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Daily sweeps for permit expiry, SLA breaches and stale deposits.
 *
 * <p>Runs at 06:00 Gulf time so the PRO finds the alerts waiting at the start of the working
 * day rather than mid-afternoon. Each sweep is independent: a failure in one does not stop
 * the others, because a mail outage should not also lose the expiry state changes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalAlertScheduler {

    private final ApprovalCaseService caseService;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Dubai")
    public void runDailySweeps() {
        runQuietly("permit expiry", caseService::sweepExpiries);
        runQuietly("SLA breach", caseService::sweepSlaBreaches);
        runQuietly("outstanding deposit", caseService::sweepStaleDeposits);
    }

    private void runQuietly(String name, java.util.function.IntSupplier sweep) {
        try {
            int raised = sweep.getAsInt();
            if (raised > 0) {
                log.info("Approval {} sweep raised {} alerts", name, raised);
            }
        } catch (Exception e) {
            log.error("Approval {} sweep failed", name, e);
        }
    }
}
