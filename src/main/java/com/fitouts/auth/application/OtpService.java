package com.fitouts.auth.application;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.auth.config.AuthProperties;
import com.fitouts.auth.domain.OtpChallenge;
import com.fitouts.auth.domain.OtpChallengeRepository;
import com.fitouts.auth.domain.RememberedDevice;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.shared.error.UnauthorizedException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository otpChallengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    @Transactional
    public GeneratedOtp createChallenge(Account account, RememberedDevice device) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));

        OtpChallenge challenge = new OtpChallenge();
        challenge.setChallengeId(UUID.randomUUID().toString());
        challenge.setAccount(account);
        challenge.setCompany(account.getCompany());
        challenge.setDevice(device);
        challenge.setOtpHash(passwordEncoder.encode(otp));
        challenge.setCreatedAt(OffsetDateTime.now());
        challenge.setExpiresAt(OffsetDateTime.now().plusMinutes(authProperties.getOtp().getExpiryMinutes()));
        challenge.setFailedAttempts(0);
        challenge.setConsumed(false);

        OtpChallenge saved = otpChallengeRepository.save(challenge);
        return new GeneratedOtp(saved, otp);
    }

    @Transactional
    public OtpChallenge verify(String challengeId, String otp) {
        OtpChallenge challenge = otpChallengeRepository.findByChallengeId(challengeId)
                .orElseThrow(() -> new NotFoundException("OTP challenge not found"));

        if (Boolean.TRUE.equals(challenge.getConsumed())) {
            throw new BadRequestException("OTP challenge already used");
        }
        if (challenge.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new UnauthorizedException("OTP expired");
        }
        if (challenge.getFailedAttempts() >= authProperties.getOtp().getMaxAttempts()) {
            throw new UnauthorizedException("OTP attempts exceeded");
        }

        boolean bypass = authProperties.getOtp().isDevBypassEnabled();
        boolean matched = bypass || (otp != null && passwordEncoder.matches(otp, challenge.getOtpHash()));
        if (!matched) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            otpChallengeRepository.save(challenge);
            throw new UnauthorizedException("Invalid OTP");
        }

        challenge.setConsumed(true);
        return otpChallengeRepository.save(challenge);
    }

    public record GeneratedOtp(OtpChallenge challenge, String rawOtp) {
    }
}
