package com.fitouts.auth.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RememberedDeviceRepository extends JpaRepository<RememberedDevice, Long> {

    Optional<RememberedDevice> findByTokenHash(String tokenHash);

    List<RememberedDevice> findByAccountId(Long accountId);
}
