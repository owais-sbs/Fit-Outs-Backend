package com.fitouts.boq.api;

import java.util.List;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoqSurveySaveRequest {
    private Long projectId;
    private String version;
    private String notes;
    private List<BoqLineRequest> lines;
}
