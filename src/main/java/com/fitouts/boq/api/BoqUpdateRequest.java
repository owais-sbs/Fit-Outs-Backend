package com.fitouts.boq.api;

import java.util.List;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoqUpdateRequest {
    private String notes;
    private List<BoqLineRequest> lines;
}
