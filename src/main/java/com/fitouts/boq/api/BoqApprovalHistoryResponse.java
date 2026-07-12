package com.fitouts.boq.api;

import java.util.List;
import java.util.UUID;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoqApprovalHistoryResponse {
    private UUID boqId;
    private List<BoqApprovalLogResponse> log;
    private List<BoqVersionSummaryResponse> versions;
}
