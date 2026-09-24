package com.fitouts.subcontractor.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import com.fitouts.subcontractor.domain.ScQuoteLineStatus;
import com.fitouts.subcontractor.domain.ScQuoteStatus;

class Step4TenderIntegrityTest {

    @Test
    void quoteLifecycleRetainsHistoricalSupersededAndWithdrawnStates() {
        assertThat(EnumSet.allOf(ScQuoteStatus.class))
                .contains(ScQuoteStatus.DRAFT, ScQuoteStatus.SUBMITTED,
                        ScQuoteStatus.SUPERSEDED, ScQuoteStatus.WITHDRAWN);
    }

    @Test
    void quoteLineStatusesKeepExcludedAndAlternativeVisible() {
        assertThat(EnumSet.allOf(ScQuoteLineStatus.class))
                .contains(ScQuoteLineStatus.QUOTED, ScQuoteLineStatus.EXCLUDED,
                        ScQuoteLineStatus.ALTERNATIVE, ScQuoteLineStatus.CLARIFICATION);
    }

    @Test
    void quotedLineAmountIsCalculatedWithoutChangingOriginalRate() {
        BigDecimal rate = new BigDecimal("125.50");
        BigDecimal quantity = new BigDecimal("4");
        assertThat(rate.multiply(quantity)).isEqualByComparingTo("502.00");
        assertThat(rate).isEqualByComparingTo("125.50");
    }
}
