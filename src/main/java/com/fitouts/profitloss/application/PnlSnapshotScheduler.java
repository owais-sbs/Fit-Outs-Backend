package com.fitouts.profitloss.application;

import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PnlSnapshotScheduler {

    private final PnlCalculationService pnlCalculationService;
    private final CompanyRepository companyRepository;

    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Dubai")
    public void nightlyRecalculate() {
        try {
            List<Company> companies = companyRepository.findAll();
            int count = 0;
            for (Company company : companies) {
                UUID companyId = company.getUuid();
                if (companyId == null) {
                    continue;
                }
                pnlCalculationService.recalculateAllForCompany(companyId);
                count++;
            }
            log.info("P&L nightly snapshot sweep completed for {} companies", count);
        } catch (Exception e) {
            log.error("P&L nightly snapshot sweep failed", e);
        }
    }
}
