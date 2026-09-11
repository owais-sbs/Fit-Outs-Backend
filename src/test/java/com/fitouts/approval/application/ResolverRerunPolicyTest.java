package com.fitouts.approval.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fitouts.approval.domain.ApprovalCaseStatus;

class ResolverRerunPolicyTest {

    @Test
    void liveStatusesBlockADuplicateCase() {
        assertThat(ResolverRerunPolicy.blocksDuplicate(ApprovalCaseStatus.NOT_STARTED)).isTrue();
        assertThat(ResolverRerunPolicy.blocksDuplicate(ApprovalCaseStatus.ISSUED)).isTrue();
        assertThat(ResolverRerunPolicy.blocksDuplicate(ApprovalCaseStatus.EXPIRED)).isTrue();
    }

    @Test
    void terminalStatusesDoNotBlockAFreshCase() {
        assertThat(ResolverRerunPolicy.blocksDuplicate(ApprovalCaseStatus.REJECTED)).isFalse();
        assertThat(ResolverRerunPolicy.blocksDuplicate(ApprovalCaseStatus.CLOSED)).isFalse();
        assertThat(ResolverRerunPolicy.blocksDuplicate(null)).isFalse();
    }
}
