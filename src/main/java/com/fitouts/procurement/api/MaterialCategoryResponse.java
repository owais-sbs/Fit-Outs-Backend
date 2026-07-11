package com.fitouts.procurement.api;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialCategoryResponse {
    private UUID id;
    private UUID companyId;
    private String name;
    private String code;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
