package com.fitouts.platformuser.application;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyTenantPurgeService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void purgeCompany(UUID companyUuid) {
        if (companyUuid == null) {
            return;
        }
        log.warn("Purging company tenant {}", companyUuid);
        jdbcTemplate.execute("SELECT purge_company_tenant('" + companyUuid + "')");
    }

    @Transactional
    public void purgeAccountOnly(Long accountId) {
        if (accountId == null) {
            return;
        }
        log.warn("Purging standalone account {}", accountId);
        jdbcTemplate.update("DELETE FROM auth_session_records WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM account_roles WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", accountId);
    }
}
