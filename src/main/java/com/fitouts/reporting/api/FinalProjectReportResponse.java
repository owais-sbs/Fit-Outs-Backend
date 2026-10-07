package com.fitouts.reporting.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Live, project-scoped snapshot for the Final Project Report PDF.
 * Amounts and counts are aggregated by {@code FinalProjectReportService}
 * from existing domain services. The PDF renderer does not recalculate them.
 */
@Getter
@Builder
public class FinalProjectReportResponse {
    private String fileName;
    private String generatedAt;
    private boolean internalView;
    private ProjectHeader project;
    private Kpis kpis;
    private ProgressBlock progress;
    private BoqBlock boq;
    private AuthorityBlock authorities;
    private ProgrammeBlock programme;
    private List<RoomRow> rooms;
    private TaskBlock tasks;
    private SnagBlock snags;
    private SubcontractBlock subcontractors;
    private BillingBlock billing;
    /** Null when the caller is not allowed to see internal subcontractor finance. */
    private ScCommercialBlock scCommercial;
    private VariationBlock variations;
    private List<CountRow> documents;
    private List<TimelineEvent> timeline;
    private List<AttentionItem> attention;
    private ClientBlock client;

    @Getter
    @Builder
    public static class ProjectHeader {
        private Long projectId;
        private String projectName;
        private String status;
        private String leadReference;
        private String manager;
        private String projectType;
        private String projectNature;
        private String developer;
        private String clientName;
        private String location;
    }

    @Getter
    @Builder
    public static class Kpis {
        private BigDecimal contractValue;
        private BigDecimal approvedBoqValue;
        private BigDecimal overallProgress;
        private String startDate;
        private String targetCompletion;
        private String projectStatus;
    }

    @Getter
    @Builder
    public static class ProgressBlock {
        private BigDecimal actualPercent;
        /** Present only when a stored baseline comparison already exists. Never invented. */
        private BigDecimal plannedPercent;
        private BigDecimal variance;
        private String source;
    }

    @Getter
    @Builder
    public static class BoqBlock {
        private boolean hasApproved;
        private String version;
        private String status;
        private String reference;
        private Integer lineCount;
        private String approvedDate;
        private BigDecimal approvedAmount;
        private BigDecimal contractValue;
        private BigDecimal difference;
        @Builder.Default
        private List<BoqVersionRow> versions = new ArrayList<>();
    }

    @Getter
    @Builder
    public static class BoqVersionRow {
        private String version;
        private String status;
        private String date;
        private BigDecimal amount;
    }

    @Getter
    @Builder
    public static class AuthorityBlock {
        private int live;
        private int blocked;
        private int awaiting;
        private int expiring;
        @Builder.Default
        private List<AuthorityRow> blockers = new ArrayList<>();
    }

    @Getter
    @Builder
    public static class AuthorityRow {
        private String approval;
        private String authority;
        private String status;
        private String reason;
        private String dueDate;
    }

    @Getter
    @Builder
    public static class ProgrammeBlock {
        private boolean published;
        private String baselineName;
        private String startDate;
        private String targetDate;
        private int total;
        private int completed;
        private int inProgress;
        private int delayed;
        private BigDecimal progressPercent;
        @Builder.Default
        private List<ActivityRow> delayedActivities = new ArrayList<>();
        @Builder.Default
        private List<CountRow> statusCounts = new ArrayList<>();
    }

    @Getter
    @Builder
    public static class ActivityRow {
        private String activity;
        private String plannedFinish;
        private String forecastOrActual;
        private String variance;
        private String owner;
        private String status;
    }

    @Getter
    @Builder
    public static class RoomRow {
        private String room;
        private int completedTasks;
        private int totalTasks;
        private BigDecimal completionPercent;
        private String status;
    }

    @Getter
    @Builder
    public static class TaskBlock {
        private int total;
        private int completed;
        private int inProgress;
        private int overdue;
        private int upcoming;
        @Builder.Default
        private List<CountRow> statusCounts = new ArrayList<>();
        @Builder.Default
        private List<TaskRow> openTasks = new ArrayList<>();
    }

    @Getter
    @Builder
    public static class TaskRow {
        private String task;
        private String location;
        private String type;
        private String owner;
        private String dueDate;
        private String status;
    }

    @Getter
    @Builder
    public static class SnagBlock {
        private int total;
        private int open;
        private int inProgress;
        private int readyForInspection;
        private int resolved;
        private int closed;
        @Builder.Default
        private List<CountRow> statusCounts = new ArrayList<>();
        @Builder.Default
        private List<SnagRow> openSnags = new ArrayList<>();
    }

    @Getter
    @Builder
    public static class SnagRow {
        private String snag;
        private String location;
        private String trade;
        private String severity;
        private String dueDate;
        private String status;
    }

    @Getter
    @Builder
    public static class SubcontractBlock {
        private int total;
        private int tendering;
        private int awarded;
        private int active;
        private int completed;
        @Builder.Default
        private List<CountRow> statusCounts = new ArrayList<>();
        @Builder.Default
        private List<PackageRow> packages = new ArrayList<>();
    }

    @Getter
    @Builder
    public static class PackageRow {
        private String packageName;
        private String trade;
        private String subcontractor;
        private BigDecimal awardValue;
        private BigDecimal progress;
        private String status;
    }

    @Getter
    @Builder
    public static class BillingBlock {
        private boolean hasRecords;
        private BigDecimal total;
        private BigDecimal invoiced;
        private BigDecimal received;
        private BigDecimal outstanding;
        private BigDecimal collectionPercent;
        @Builder.Default
        private List<InvoiceRow> invoices = new ArrayList<>();
    }

    @Getter
    @Builder
    public static class InvoiceRow {
        private String invoice;
        private BigDecimal amount;
        private String issueDate;
        private String dueDate;
        private BigDecimal paid;
        private BigDecimal outstanding;
        private String status;
    }

    @Getter
    @Builder
    public static class ScCommercialBlock {
        private boolean hasRecords;
        private BigDecimal certified;
        private BigDecimal payable;
        private BigDecimal paid;
        private BigDecimal retentionHeld;
        private BigDecimal outstandingLiability;
    }

    @Getter
    @Builder
    public static class VariationBlock {
        private int total;
        private int approved;
        private int pending;
        private int rejected;
        private BigDecimal approvedValue;
        private BigDecimal pendingValue;
        @Builder.Default
        private List<VariationRow> rows = new ArrayList<>();
    }

    @Getter
    @Builder
    public static class VariationRow {
        private String variation;
        private String description;
        private BigDecimal costImpact;
        private String scheduleImpact;
        private String status;
    }

    @Getter
    @Builder
    public static class CountRow {
        private String label;
        private int count;
    }

    @Getter
    @Builder
    public static class TimelineEvent {
        private String at;
        private String event;
        private String description;
    }

    @Getter
    @Builder
    public static class AttentionItem {
        private String area;
        private String issue;
        private String priority;
        private String owner;
        private String nextAction;
    }

    @Getter
    @Builder
    public static class ClientBlock {
        private boolean assigned;
        private String name;
        private String clientId;
        private String account;
        private String location;
        private String contact;
    }
}
