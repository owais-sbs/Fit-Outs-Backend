package com.fitouts.schedule.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Data;

@Data
public class ScheduleActivityRequest {
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer percentComplete;
    private BigDecimal weight;
    private UUID parentUuid;
    private UUID projectRoomId;
    private UUID roomTaskId;
    private Long assigneeAccountId;
    private Integer sortOrder;
    private String delayReason;
    /** When true on update, clears projectRoomId and roomTaskId. */
    private Boolean clearRoomLinks;
}
