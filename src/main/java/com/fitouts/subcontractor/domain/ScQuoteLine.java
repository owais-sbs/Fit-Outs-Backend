package com.fitouts.subcontractor.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sc_quote_line")
@Getter
@Setter
public class ScQuoteLine {

    @Id
    private UUID uuid;

    @Column(name = "quote_uuid", nullable = false)
    private UUID quoteUuid;

    @Column(name = "boq_line_id")
    private UUID boqLineId;

    @Column(precision = 18, scale = 4)
    private BigDecimal rate;

    @Column(precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_status", nullable = false, length = 32)
    private ScQuoteLineStatus lineStatus = ScQuoteLineStatus.QUOTED;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
