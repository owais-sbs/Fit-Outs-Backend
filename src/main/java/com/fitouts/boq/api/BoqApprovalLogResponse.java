package com.fitouts.boq.api;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.shared.enums.BoqApprovalAction;
import com.fitouts.shared.enums.BoqApprovalStep;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoqApprovalLogResponse {
    private UUID id;
    private BoqApprovalStep step;
    private BoqApprovalAction action;
    private Long actorId;
    private String actorRole;
    private String actorName;
    private String comments;
    private LocalDateTime createdAt;
}
