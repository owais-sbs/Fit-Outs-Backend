package com.fitouts.approval.application;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.approval.domain.ApprovalCaseRepository;

import lombok.RequiredArgsConstructor;

/**
 * Produces human-quotable case numbers such as {@code AC-00142-P-COMM-NOC}.
 *
 * <p>The PRO reads these out on the phone to an authority, so they need to be short and
 * stable. The sequence is per tenant, and collisions from a concurrent generate walk forward
 * rather than failing the whole batch.
 */
@Component
@RequiredArgsConstructor
public class CaseNumberGenerator {

    private final ApprovalCaseRepository caseRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(UUID companyId, String permitCode) {
        long sequence = caseRepository.countByCompanyId(companyId) + 1;
        for (int attempt = 0; attempt < 200; attempt++) {
            String candidate = String.format("AC-%05d-%s", sequence + attempt, permitCode);
            if (!caseRepository.existsByCompanyIdAndCaseNumber(companyId, candidate)) {
                return candidate;
            }
        }
        return "AC-" + UUID.randomUUID().toString().substring(0, 8) + "-" + permitCode;
    }
}
