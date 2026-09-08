package com.fitouts.schedule.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fitouts.schedule.domain.ScheduleTemplate;
import com.fitouts.schedule.domain.TemplateActivity;
import com.fitouts.schedule.engine.CpmActivity;
import com.fitouts.schedule.engine.CpmLink;
import com.fitouts.schedule.engine.CpmResult;
import com.fitouts.schedule.engine.DurationScaler;
import com.fitouts.schedule.engine.ScheduleParameters;

import lombok.Getter;
import lombok.Setter;

/**
 * One solved template: the scaled activities, the network, the CPM result, and everything the
 * preview screen and the apply cascade both need. Building it has no side effects, so preview
 * and apply run exactly the same code and cannot drift apart.
 */
@Getter
@Setter
public class TemplatePlan {

    private ScheduleTemplate template;
    private ScheduleParameters parameters;

    private List<CpmActivity> activities = new ArrayList<>();
    private List<CpmLink> links = new ArrayList<>();
    private CpmResult result;

    /** Source template row by activity code, for the fields CPM does not carry. */
    private Map<String, TemplateActivity> templateActivities = new LinkedHashMap<>();

    /** How each duration was arrived at, keyed by activity code. */
    private Map<String, DurationScaler.Scaled> scaling = new LinkedHashMap<>();

    private List<OrderByLine> orderByLines = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> blockers = new ArrayList<>();
    private int excludedByToggleCount;

    public LocalDate finishDate() {
        return result == null ? null : result.getProjectFinish();
    }

    public CpmActivity activity(String code) {
        return activities.stream().filter(a -> code.equals(a.getCode())).findFirst().orElse(null);
    }

    /** A procurement deadline computed backwards from the activity that installs the item. */
    @Getter
    @Setter
    public static class OrderByLine {
        private String itemName;
        private int leadTimeCalendarDays;
        private String installActivityCode;
        private LocalDate installStartDate;
        private LocalDate orderByDate;
        private boolean overdue;
        private String riskNote;
        private String siteInfoNeeded;
    }
}
