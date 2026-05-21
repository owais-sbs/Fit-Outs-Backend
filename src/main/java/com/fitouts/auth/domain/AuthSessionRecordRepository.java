package com.fitouts.auth.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRecordRepository extends JpaRepository<AuthSessionRecord, String> {

    List<AuthSessionRecord> findByAccountId(Long accountId);
}
