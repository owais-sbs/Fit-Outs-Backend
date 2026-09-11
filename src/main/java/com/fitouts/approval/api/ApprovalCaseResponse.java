package com.fitouts.approval.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApprovalCaseResponse {

    private UUID uuid;
    private Long projectId;
    private String projectName;
    private String caseNumber;
    private String permitTypeCode;
    private String permitTypeName;
    private String authorityCode;
    private String authorityName;
    private List<String> candidateAuthorityCodes;
    private boolean authorityManuallySet;
    private String status;
    private Long assignedToAccountId;
    private String assignedToName;
    private LocalDate targetSubmissionDate;
    private LocalDate submittedDate;
    private String authorityReference;
    private LocalDate slaDueDate;
    private Integer slaDays;
    private Long daysToSlaDue;
    private LocalDate approvedDate;
    private String permitNumber;
    private String permitFilePath;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private Long daysToExpiry;
    private BigDecimal feePaid;
    private BigDecimal depositPaid;
    private String depositStatus;
    private int currentVersion;
    private List<String> prerequisitePermitCodes;
    private List<String> blocksActivityCodes;
    private List<UUID> linkedActivityUuids;
    private String notes;

    // Gate state, computed so the UI never has to work out why a button is disabled.

    /** Required checklist items that are still missing or expired. */
    private int blockingChecklistCount;
    /** Prerequisite cases on this project that are not yet approved. */
    private List<String> unmetPrerequisites;
    /** Company documents that are missing or expired. */
    private List<String> blockingCompanyDocuments;
    private boolean readyToSubmit;
    private String blockReason;
}
