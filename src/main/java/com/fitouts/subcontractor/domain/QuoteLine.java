package com.fitouts.subcontractor.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tender_quote_lines")
@Getter
@Setter
public class QuoteLine implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Column(name = "boq_line_id")
    private UUID boqLineId;

    @Column(name = "section_code")
    private String sectionCode;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "quantity", precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit")
    private String unit;

    @Column(name = "unit_rate", precision = 14, scale = 2)
    private BigDecimal unitRate;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;
}
