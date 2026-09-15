package com.fitouts.commercialapproval.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommercialApprovalAlertScheduler {

    private final CommercialApprovalService commercialApprovalService;

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Dubai")
    public void runDailySweeps() {
        try {
            int raised = commercialApprovalService.sweepRemindersAndEscalations();
            if (raised > 0) {
                log.info("Commercial approval SLA sweep raised {} alerts", raised);
            }
        } catch (Exception e) {
            log.error("Commercial approval SLA sweep failed", e);
        }
    }
}
