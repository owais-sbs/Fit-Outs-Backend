package com.fitouts.procurement.api;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialCategoryUpdateRequest {
    private String name;
    private String code;
}
