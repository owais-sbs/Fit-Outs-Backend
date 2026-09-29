package com.fitouts.platformuser.application;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountPurgeScheduler {

    private final AccountRepository accountRepository;
    private final CompanyTenantPurgeService companyTenantPurgeService;

    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Dubai")
    @Transactional
    public void purgeDueAccounts() {
        List<Account> dueAccounts = accountRepository.findAllByPurgeAtIsNotNullAndPurgeAtLessThanEqual(
                OffsetDateTime.now());
        if (dueAccounts.isEmpty()) {
            return;
        }

        Set<UUID> companyIds = new HashSet<>();
        for (Account account : dueAccounts) {
            if (account.getCompany() != null) {
                companyIds.add(account.getCompany().getUuid());
            }
        }

        for (UUID companyUuid : companyIds) {
            try {
                companyTenantPurgeService.purgeCompany(companyUuid);
            } catch (Exception exception) {
                log.error("Failed to purge company {}: {}", companyUuid, exception.getMessage(), exception);
            }
        }

        for (Account account : dueAccounts) {
            if (account.getCompany() != null) {
                continue;
            }
            try {
                companyTenantPurgeService.purgeAccountOnly(account.getId());
            } catch (Exception exception) {
                log.error("Failed to purge account {}: {}", account.getId(), exception.getMessage(), exception);
            }
        }
    }
}
