package com.fitouts.procurement.api;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialCategoryCreateRequest {
    private String name;
    private String code;
}
