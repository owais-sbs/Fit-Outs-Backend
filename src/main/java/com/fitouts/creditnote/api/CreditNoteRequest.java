package com.fitouts.creditnote.api;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class CreditNoteRequest {
    private String title;
    private String reason;
    private BigDecimal amount;
}
