package com.fitouts.qto.api;

import java.util.UUID;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QtoSessionCreateRequest {
    private Long projectId;
    private UUID drawingId;
    private String notes;
}
