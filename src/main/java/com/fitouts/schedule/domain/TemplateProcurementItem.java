package com.fitouts.schedule.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A long-lead item. Lead times are calendar days, because a factory in Italy does not observe
 * the site's working week.
 */
@Entity
@Table(name = "template_procurement_item")
@Getter
@Setter
public class TemplateProcurementItem {

    @Id
    private UUID uuid;

    @Column(name = "template_uuid")
    private UUID templateUuid;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "lead_time_calendar_days_min")
    private Integer leadTimeCalendarDaysMin;

    @Column(name = "lead_time_calendar_days_max")
    private Integer leadTimeCalendarDaysMax;

    @Column(name = "lead_time_raw", length = 64)
    private String leadTimeRaw;

    @Column(name = "order_by_rule", columnDefinition = "text")
    private String orderByRule;

    @Column(name = "site_info_needed", columnDefinition = "text")
    private String siteInfoNeeded;

    @Column(name = "risk_note", columnDefinition = "text")
    private String riskNote;

    @Column(name = "linked_install_activity_code", length = 32)
    private String linkedInstallActivityCode;

    @Column(name = "match_keywords", columnDefinition = "text")
    private String matchKeywords;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * The lead time to plan against. Uses the pessimistic end of the range, because ordering
     * to the optimistic figure is how projects discover a twelve-week stone slab in week ten.
     */
    public int planningLeadDays() {
        if (leadTimeCalendarDaysMax != null && leadTimeCalendarDaysMax > 0) return leadTimeCalendarDaysMax;
        if (leadTimeCalendarDaysMin != null && leadTimeCalendarDaysMin > 0) return leadTimeCalendarDaysMin;
        return 0;
    }

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
    }
}
