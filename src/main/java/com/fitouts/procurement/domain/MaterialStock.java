package com.fitouts.procurement.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.company.domain.Company;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "material_stock", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"company_id", "material_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialStock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "quantity_on_hand", nullable = false, precision = 14, scale = 3)
    @Builder.Default
    private BigDecimal quantityOnHand = BigDecimal.ZERO;

    @Column(name = "quantity_reserved", nullable = false, precision = 14, scale = 3)
    @Builder.Default
    private BigDecimal quantityReserved = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public BigDecimal availableQuantity() {
        BigDecimal onHand = quantityOnHand != null ? quantityOnHand : BigDecimal.ZERO;
        BigDecimal reserved = quantityReserved != null ? quantityReserved : BigDecimal.ZERO;
        return onHand.subtract(reserved);
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
