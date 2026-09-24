package com.fitouts.profitloss.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.profitloss.api.OverheadRuleResponse;
import com.fitouts.profitloss.api.OverheadRuleUpsertRequest;
import com.fitouts.profitloss.domain.OverheadBasis;
import com.fitouts.profitloss.domain.OverheadRule;
import com.fitouts.profitloss.domain.OverheadRuleRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OverheadAllocationService {

    private final OverheadRuleRepository overheadRuleRepository;

    @Transactional(readOnly = true)
    public BigDecimal allocate(UUID companyId, BigDecimal contractValue) {
        OverheadRule rule = activeRule(companyId);
        if (rule == null || rule.getPercentage() == null || contractValue == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        LocalDate today = LocalDate.now();
        if (rule.getEffectiveFrom() != null && today.isBefore(rule.getEffectiveFrom())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (rule.getEffectiveTo() != null && today.isAfter(rule.getEffectiveTo())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return contractValue
                .multiply(rule.getPercentage())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public OverheadRuleResponse getRule() {
        UUID companyId = requireCompany();
        OverheadRule rule = activeRule(companyId);
        if (rule == null) {
            return OverheadRuleResponse.builder()
                    .percentage(BigDecimal.ZERO)
                    .basis(OverheadBasis.CONTRACT_VALUE)
                    .active(true)
                    .build();
        }
        return toResponse(rule);
    }

    @Transactional
    public OverheadRuleResponse upsertRule(OverheadRuleUpsertRequest request) {
        UUID companyId = requireCompany();
        if (request == null || request.getPercentage() == null) {
            throw new BadRequestException("percentage is required");
        }
        if (request.getPercentage().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("percentage cannot be negative");
        }

        OverheadRule rule = overheadRuleRepository
                .findFirstByCompanyIdAndActiveTrueOrderByUpdatedAtDesc(companyId)
                .orElseGet(() -> {
                    OverheadRule created = new OverheadRule();
                    created.setCompanyId(companyId);
                    created.setBasis(OverheadBasis.CONTRACT_VALUE);
                    created.setActive(true);
                    return created;
                });
        rule.setPercentage(request.getPercentage().setScale(4, RoundingMode.HALF_UP));
        rule.setEffectiveFrom(request.getEffectiveFrom());
        rule.setEffectiveTo(request.getEffectiveTo());
        rule.setActive(true);
        return toResponse(overheadRuleRepository.save(rule));
    }

    private OverheadRule activeRule(UUID companyId) {
        List<OverheadRule> rules = overheadRuleRepository
                .findByCompanyIdAndActiveTrueOrderByUpdatedAtDesc(companyId);
        if (rules.isEmpty()) {
            return null;
        }
        // Historical duplicates: keep newest, deactivate the rest so Optional lookups stay safe.
        OverheadRule keep = rules.get(0);
        if (rules.size() > 1) {
            for (int i = 1; i < rules.size(); i++) {
                OverheadRule dup = rules.get(i);
                dup.setActive(false);
                overheadRuleRepository.save(dup);
            }
        }
        return keep;
    }

    private OverheadRuleResponse toResponse(OverheadRule rule) {
        return OverheadRuleResponse.builder()
                .uuid(rule.getUuid())
                .percentage(rule.getPercentage())
                .basis(rule.getBasis())
                .active(rule.isActive())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .build();
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context required");
        }
        return companyId;
    }
}
