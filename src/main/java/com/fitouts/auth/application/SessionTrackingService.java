package com.fitouts.auth.application;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.auth.domain.AuthSessionRecord;
import com.fitouts.auth.domain.AuthSessionRecordRepository;
import com.fitouts.auth.domain.RememberedDevice;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionTrackingService {

    private final AuthSessionRecordRepository authSessionRecordRepository;
    private final DeviceService deviceService;

    @Transactional
    public void registerSession(String sessionId, Account account, RememberedDevice device) {
        AuthSessionRecord record = authSessionRecordRepository.findById(sessionId)
                .orElseGet(AuthSessionRecord::new);
        OffsetDateTime now = OffsetDateTime.now();
        record.setSessionId(sessionId);
        record.setAccount(account);
        record.setTenant(account.getTenant());
        record.setDevice(device);
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(now);
        }
        record.setLastSeenAt(now);
        record.setRevokedAt(null);
        authSessionRecordRepository.save(record);
        deviceService.touch(device);
    }

    @Transactional
    public void touchSession(String sessionId, Long accountId) {
        Optional<AuthSessionRecord> optional = authSessionRecordRepository.findById(sessionId);
        optional.filter(record -> record.getAccount().getId().equals(accountId)).ifPresent(record -> {
            record.setLastSeenAt(OffsetDateTime.now());
            authSessionRecordRepository.save(record);
            deviceService.touch(record.getDevice());
        });
    }

    @Transactional
    public void revoke(String sessionId) {
        authSessionRecordRepository.findById(sessionId).ifPresent(record -> {
            record.setRevokedAt(OffsetDateTime.now());
            authSessionRecordRepository.save(record);
        });
    }

    @Transactional(readOnly = true)
    public AuthSessionRecord getRecord(String sessionId) {
        return authSessionRecordRepository.findById(sessionId).orElse(null);
    }
}
