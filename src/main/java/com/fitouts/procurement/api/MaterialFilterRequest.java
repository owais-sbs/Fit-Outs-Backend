package com.fitouts.procurement.api;

import java.util.UUID;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialFilterRequest {
    private String search;
    private UUID materialCategoryId;
    private Boolean active;
}
