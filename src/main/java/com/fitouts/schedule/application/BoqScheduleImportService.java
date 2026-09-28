package com.fitouts.schedule.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.boq.application.BoqProjectRules;
import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqLine;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.schedule.api.BoqMatchSuggestionResponse;
import com.fitouts.schedule.api.ProgrammeBuildRequest;
import com.fitouts.schedule.api.ScheduleApplyResponse;
import com.fitouts.schedule.api.SchedulePreviewRequest;
import com.fitouts.schedule.api.SchedulePreviewResponse;
import com.fitouts.schedule.domain.BoqMatchSource;
import com.fitouts.schedule.domain.ScheduleTemplate;
import com.fitouts.schedule.domain.ScheduleTemplateRepository;
import com.fitouts.schedule.domain.TemplateActivity;
import com.fitouts.schedule.domain.TemplateActivityRepository;
import com.fitouts.schedule.engine.CpmActivity;
import com.fitouts.schedule.engine.CpmEngine;
import com.fitouts.schedule.engine.CpmLink;
import com.fitouts.schedule.engine.CpmResult;
import com.fitouts.schedule.engine.ScheduleParameters;
import com.fitouts.schedule.engine.WorkingCalendar;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoqScheduleImportService {

    private final BoqProjectRules boqProjectRules;
    private final BoqLineRepository boqLineRepository;
    private final ScheduleTemplateRepository templateRepository;
    private final TemplateActivityRepository templateActivityRepository;
    private final TemplatePlanner planner;
    private final CpmEngine cpmEngine;
    private final WorkCalendarService workCalendarService;
    private final ProgrammeReplaceWriter programmeReplaceWriter;
    private final ProjectService projectService;
    private final CommercialLifecycleService commercialLifecycleService;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------- suggest matches

    @Transactional(readOnly = true)
    public List<BoqMatchSuggestionResponse> suggestMatches(Long projectId, UUID templateUuid) {
        requireStaff();
        requireProject(projectId);
        if (templateUuid == null) {
            throw new BadRequestException("templateUuid is required");
        }
        ScheduleTemplate template = requireTemplate(templateUuid);
        List<BoqLine> lines = usableLines(requireApprovedBoq(projectId));
        List<TemplateActivity> activities =
                templateActivityRepository.findByTemplateUuidOrderBySortOrderAsc(template.getUuid());

        List<BoqMatchSuggestionResponse> out = new ArrayList<>();
        for (BoqLine line : lines) {
            ScoredMatch best = bestMatch(line, activities);
            out.add(BoqMatchSuggestionResponse.builder()
                    .boqLineId(line.getId())
                    .sortOrder(line.getSortOrder())
                    .description(line.getDescription())
                    .categoryCode(line.getCategoryCode())
                    .categoryName(line.getCategoryName())
                    .suggestedActivityCode(best == null ? null : best.code())
                    .suggestedActivityName(best == null ? null : best.name())
                    .confidence(best == null ? 0.0 : best.confidence())
                    .build());
        }
        return out;
    }

    // ----------------------------------------------------------- preview/apply

    @Transactional(readOnly = true)
    public SchedulePreviewResponse preview(Long projectId, ProgrammeBuildRequest request) {
        requireStaff();
        Project project = requireProject(projectId);
        BuiltProgramme built = build(project, request);
        return built.preview();
    }

    @Transactional
    public ScheduleApplyResponse apply(Long projectId, ProgrammeBuildRequest request) {
        AuthPrincipal principal = requireStaff();
        commercialLifecycleService.assertNotArchived(projectId);
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        BuiltProgramme built = build(project, request);
        if (!built.preview().getBlockers().isEmpty()) {
            throw new BadRequestException(String.join(" ", built.preview().getBlockers()));
        }
        if (built.preview().isRequiresFastTrackAcknowledgement()) {
            throw new BadRequestException(
                    "This fast-track programme cannot be published until all fast-track conditions are acknowledged.");
        }

        String paramsJson;
        try {
            paramsJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            paramsJson = "{}";
        }

        return programmeReplaceWriter.write(new ProgrammeReplaceWriter.WriteRequest(
                project,
                companyId,
                principal.getAccountId(),
                built.plan(),
                built.calendar(),
                request.getWorkCalendarUuid(),
                built.template(),
                built.template() != null ? built.template().getCode()
                        : ("BLEND".equalsIgnoreCase(request.getMode()) ? "BLEND" : "BOQ"),
                paramsJson,
                null,
                null,
                built.preview(),
                built.boqLineIdByActivityCode(),
                built.attachments()
        ));
    }

    // ----------------------------------------------------------------- build

    private BuiltProgramme build(Project project, ProgrammeBuildRequest request) {
        if (request == null || !StringUtils.hasText(request.getMode())) {
            throw new BadRequestException("mode is required (BOQ or BLEND)");
        }
        String mode = request.getMode().trim().toUpperCase(Locale.ROOT);
        LocalDate start = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();
        WorkingCalendar calendar = workCalendarService.resolve(request.getWorkCalendarUuid());
        BoqDocument boq = requireApprovedBoq(project.getId());
        List<BoqLine> lines = usableLines(boq);
        if (lines.isEmpty()) {
            throw new BadRequestException("Approved BOQ has no usable line items");
        }

        return switch (mode) {
            case "BOQ" -> buildBoqOnly(project, request, lines, start, calendar);
            case "BLEND" -> buildBlend(project, request, lines, start, calendar);
            default -> throw new BadRequestException("mode must be BOQ or BLEND");
        };
    }

    private BuiltProgramme buildBoqOnly(Project project, ProgrammeBuildRequest request,
                                        List<BoqLine> lines, LocalDate start, WorkingCalendar calendar) {
        Map<UUID, Integer> durations = durationMap(request.getLineDurations(), lines, true);

        List<CpmActivity> activities = new ArrayList<>();
        List<CpmLink> links = new ArrayList<>();
        Map<String, UUID> boqByCode = new LinkedHashMap<>();
        String prevCode = null;
        Set<String> usedCodes = new HashSet<>();
        int index = 0;
        for (BoqLine line : lines) {
            int dur = durations.get(line.getId());
            String code = uniqueActivityCode(line, index++, usedCodes);
            CpmActivity a = new CpmActivity();
            a.setCode(code);
            a.setName(activityName(line));
            a.setWbsPhase(wbsPhase(line));
            a.setDurationWorkingDays(dur);
            a.setRef(line);
            activities.add(a);
            boqByCode.put(code, line.getId());
            if (prevCode != null) {
                links.add(CpmLink.fs(prevCode, code));
            }
            prevCode = code;
        }

        CpmResult result = cpmEngine.solve(activities, links, start, calendar);
        TemplatePlan plan = new TemplatePlan();
        plan.setActivities(activities);
        plan.setLinks(links);
        plan.setResult(result);
        plan.setWarnings(new ArrayList<>(result.getWarnings()));

        SchedulePreviewResponse preview = toPreview(null, plan, Map.of(), Map.of(), boqByCode);
        return new BuiltProgramme(plan, calendar, null, preview, boqByCode, List.of());
    }

    private BuiltProgramme buildBlend(Project project, ProgrammeBuildRequest request,
                                      List<BoqLine> lines, LocalDate start, WorkingCalendar calendar) {
        ScheduleTemplate template = resolveTemplate(request);
        SchedulePreviewRequest tplReq = toTemplateRequest(request);
        ScheduleParameters params = toParameters(template, tplReq, project);
        TemplatePlan templatePlan = planner.plan(template, params, calendar);

        Map<UUID, BoqLine> lineById = lines.stream()
                .collect(Collectors.toMap(BoqLine::getId, l -> l, (a, b) -> a, LinkedHashMap::new));

        Map<UUID, String> matchByLine = new LinkedHashMap<>();
        Map<UUID, BoqMatchSource> matchSourceByLine = new HashMap<>();
        Set<UUID> matchedIds = new HashSet<>();
        if (request.getMatches() != null) {
            for (ProgrammeBuildRequest.BoqMatch m : request.getMatches()) {
                if (m == null || m.getBoqLineId() == null) continue;
                if (!lineById.containsKey(m.getBoqLineId())) continue;
                if (StringUtils.hasText(m.getActivityCode())) {
                    matchByLine.put(m.getBoqLineId(), m.getActivityCode().trim());
                    matchedIds.add(m.getBoqLineId());
                    matchSourceByLine.put(m.getBoqLineId(), parseMatchSource(m.getMatchSource()));
                }
            }
        }

        List<BoqLine> unmatched = lines.stream()
                .filter(l -> !matchedIds.contains(l.getId()))
                .toList();
        Map<UUID, Integer> unmatchedDurations = durationMap(request.getUnmatchedDurations(), unmatched, true);

        // Attachments: matched lines → template activity codes
        Map<String, List<UUID>> attachmentsByCode = new LinkedHashMap<>();
        List<ProgrammeReplaceWriter.Attachment> attachments = new ArrayList<>();
        for (Map.Entry<UUID, String> e : matchByLine.entrySet()) {
            String code = e.getValue();
            boolean known = templatePlan.getActivities().stream().anyMatch(a -> code.equals(a.getCode()));
            if (!known) {
                throw new BadRequestException("Unknown template activity code in match: " + code);
            }
            attachmentsByCode.computeIfAbsent(code, k -> new ArrayList<>()).add(e.getKey());
            attachments.add(new ProgrammeReplaceWriter.Attachment(
                    code, e.getKey(), matchSourceByLine.getOrDefault(e.getKey(), BoqMatchSource.USER_CHANGED)));
        }

        // Start from template network, then append unmatched
        List<CpmActivity> activities = new ArrayList<>(templatePlan.getActivities());
        List<CpmLink> links = new ArrayList<>(templatePlan.getLinks());
        Map<String, UUID> boqByCode = new LinkedHashMap<>();

        Map<String, String> lastTemplateCodeByPhase = new LinkedHashMap<>();
        String lastTemplateCodeOverall = null;
        for (CpmActivity a : templatePlan.getActivities()) {
            lastTemplateCodeOverall = a.getCode();
            String phase = normalize(a.getWbsPhase());
            if (StringUtils.hasText(phase)) {
                lastTemplateCodeByPhase.put(phase, a.getCode());
            }
        }

        Map<String, String> lastUnmatchedInPhase = new HashMap<>();
        String lastEndOfProgramme = lastTemplateCodeOverall;
        Set<String> usedCodes = new HashSet<>();
        for (CpmActivity existing : activities) {
            if (existing.getCode() != null) usedCodes.add(existing.getCode());
        }

        int unmatchedIndex = 0;
        for (BoqLine line : unmatched) {
            int dur = unmatchedDurations.get(line.getId());
            String code = uniqueActivityCode(line, unmatchedIndex++, usedCodes);
            CpmActivity a = new CpmActivity();
            a.setCode(code);
            a.setName(activityName(line));
            a.setWbsPhase(wbsPhase(line));
            a.setDurationWorkingDays(dur);
            a.setRef(line);
            activities.add(a);
            boqByCode.put(code, line.getId());

            String phase = normalize(wbsPhase(line));
            String predecessor = null;
            if (StringUtils.hasText(phase) && lastUnmatchedInPhase.containsKey(phase)) {
                predecessor = lastUnmatchedInPhase.get(phase);
            } else if (StringUtils.hasText(phase) && lastTemplateCodeByPhase.containsKey(phase)) {
                predecessor = lastTemplateCodeByPhase.get(phase);
            } else if (lastEndOfProgramme != null) {
                predecessor = lastEndOfProgramme;
            }
            if (predecessor != null) {
                links.add(CpmLink.fs(predecessor, code));
            }
            if (StringUtils.hasText(phase) && lastTemplateCodeByPhase.containsKey(phase)) {
                lastUnmatchedInPhase.put(phase, code);
            } else {
                lastEndOfProgramme = code;
            }
        }

        CpmResult result = cpmEngine.solve(activities, links, start, calendar);
        TemplatePlan plan = new TemplatePlan();
        plan.setTemplate(template);
        plan.setParameters(params);
        plan.setActivities(activities);
        plan.setLinks(links);
        plan.setResult(result);
        plan.setTemplateActivities(templatePlan.getTemplateActivities());
        plan.setScaling(templatePlan.getScaling());
        plan.setOrderByLines(templatePlan.getOrderByLines());
        plan.setWarnings(new ArrayList<>());
        plan.getWarnings().addAll(templatePlan.getWarnings());
        plan.getWarnings().addAll(result.getWarnings());
        plan.setBlockers(new ArrayList<>(templatePlan.getBlockers()));
        plan.setExcludedByToggleCount(templatePlan.getExcludedByToggleCount());

        // Fast-track / blockers from template planner still apply
        List<String> fastTrackConditions = fastTrackConditions(template);
        List<String> stillUnacked = unacknowledged(template, tplReq);

        SchedulePreviewResponse preview = toPreview(template, plan, attachmentsByCode, Map.of(), boqByCode);
        preview = SchedulePreviewResponse.builder()
                .templateUuid(preview.getTemplateUuid())
                .templateCode(preview.getTemplateCode())
                .templateName(preview.getTemplateName())
                .startDate(preview.getStartDate())
                .finishDate(preview.getFinishDate())
                .workingDays(preview.getWorkingDays())
                .calendarDays(preview.getCalendarDays())
                .templateTargetWorkingDays(template.getTargetWorkingDays())
                .targetVarianceNote(preview.getTargetVarianceNote())
                .activityCount(preview.getActivityCount())
                .excludedByToggleCount(plan.getExcludedByToggleCount())
                .activities(preview.getActivities())
                .dependencies(preview.getDependencies())
                .criticalPaths(preview.getCriticalPaths())
                .orderByDates(preview.getOrderByDates())
                .approvalTargets(List.of())
                .packages(List.of())
                .warnings(plan.getWarnings())
                .blockers(plan.getBlockers())
                .requiresFastTrackAcknowledgement(!stillUnacked.isEmpty())
                .fastTrackConditions(fastTrackConditions)
                .canPublish(plan.getBlockers().isEmpty() && stillUnacked.isEmpty())
                .build();

        return new BuiltProgramme(plan, calendar, template, preview, boqByCode, attachments);
    }

    private SchedulePreviewResponse toPreview(
            ScheduleTemplate template,
            TemplatePlan plan,
            Map<String, List<UUID>> attachmentsByCode,
            Map<String, Object> unused,
            Map<String, UUID> boqByCode) {

        LocalDate projectStart = plan.getResult() != null ? plan.getResult().getProjectStart() : null;
        Map<String, List<UUID>> attachedByCode =
                attachmentsByCode != null ? attachmentsByCode : Map.of();
        Map<String, UUID> lineByCode = boqByCode != null ? boqByCode : Map.of();
        List<SchedulePreviewResponse.PreviewActivity> activities = new ArrayList<>();
        for (CpmActivity a : plan.getActivities()) {
            List<UUID> attached = attachedByCode.getOrDefault(a.getCode(), List.of());
            activities.add(SchedulePreviewResponse.PreviewActivity.builder()
                    .activityCode(a.getCode())
                    .name(a.getName())
                    .wbsPhase(a.getWbsPhase())
                    .tradePackageCode(a.getTradePackageCode())
                    .durationWorkingDays(a.effectiveDuration())
                    .baseDurationWorkingDays(a.effectiveDuration())
                    .earlyStart(a.getEarlyStart())
                    .earlyFinish(a.getEarlyFinish())
                    .lateStart(a.getLateStart())
                    .lateFinish(a.getLateFinish())
                    .totalFloat(a.getTotalFloat())
                    .freeFloat(a.getFreeFloat())
                    .critical(a.isCritical())
                    .milestone(a.isMilestone())
                    .lockedDuration(a.isLockedDuration())
                    .constraintNote(a.getConstraintNote())
                    .attachedBoqLineCount(attached.size())
                    .attachedBoqLineIds(attached.isEmpty() ? null : attached)
                    .boqLineId(lineByCode.get(a.getCode()))
                    .build());
        }

        int workingDays = plan.getResult() != null ? plan.getResult().getTotalWorkingDays() : 0;
        int calendarDays = plan.getResult() != null ? plan.getResult().getTotalCalendarDays() : 0;

        return SchedulePreviewResponse.builder()
                .templateUuid(template != null ? template.getUuid() : null)
                .templateCode(template != null ? template.getCode() : "BOQ")
                .templateName(template != null ? template.getName() : "BOQ programme")
                .startDate(projectStart)
                .finishDate(plan.finishDate())
                .workingDays(workingDays)
                .calendarDays(calendarDays)
                .templateTargetWorkingDays(template != null ? template.getTargetWorkingDays() : null)
                .activityCount(activities.size())
                .excludedByToggleCount(plan.getExcludedByToggleCount())
                .activities(activities)
                .dependencies(plan.getLinks().stream()
                        .map(l -> SchedulePreviewResponse.PreviewDependency.builder()
                                .predecessorCode(l.getPredecessorCode())
                                .successorCode(l.getSuccessorCode())
                                .type(l.getType().name())
                                .lagWorkingDays(l.getLagWorkingDays())
                                .locked(l.isLocked())
                                .lockReason(l.getLockReason())
                                .build())
                        .toList())
                .criticalPaths(plan.getResult() != null ? plan.getResult().getCriticalPaths() : List.of())
                .orderByDates(List.of())
                .approvalTargets(List.of())
                .packages(List.of())
                .warnings(plan.getWarnings())
                .blockers(plan.getBlockers())
                .requiresFastTrackAcknowledgement(false)
                .fastTrackConditions(List.of())
                .canPublish(plan.getBlockers() == null || plan.getBlockers().isEmpty())
                .build();
    }

    // -------------------------------------------------------------- matching

    private record ScoredMatch(String code, String name, double confidence) {}

    private ScoredMatch bestMatch(BoqLine line, List<TemplateActivity> activities) {
        ScoredMatch best = null;
        for (TemplateActivity ta : activities) {
            if (ta.isMilestone()) continue;
            double score = score(line, ta);
            if (best == null || score > best.confidence()) {
                best = new ScoredMatch(ta.getActivityCode(), ta.getName(), score);
            }
        }
        if (best != null && best.confidence() < 0.15) {
            return new ScoredMatch(null, null, best.confidence());
        }
        return best;
    }

    private double score(BoqLine line, TemplateActivity ta) {
        String cat = normalize(StringUtils.hasText(line.getCategoryCode())
                ? line.getCategoryCode() : line.getCategoryName());
        String desc = normalize(line.getDescription());
        String phase = normalize(ta.getWbsPhase());
        String name = normalize(ta.getName());
        String trade = normalize(ta.getTradePackageCode());

        double score = 0;
        if (StringUtils.hasText(cat) && StringUtils.hasText(phase) && (phase.contains(cat) || cat.contains(phase))) {
            score += 0.45;
        }
        if (StringUtils.hasText(cat) && StringUtils.hasText(trade) && (trade.contains(cat) || cat.contains(trade))) {
            score += 0.25;
        }
        score += tokenOverlap(desc, name) * 0.40;
        score += tokenOverlap(cat, name) * 0.15;
        return Math.min(1.0, score);
    }

    private double tokenOverlap(String a, String b) {
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) return 0;
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) return 0;
        int inter = 0;
        for (String t : ta) {
            if (tb.contains(t)) inter++;
        }
        return (double) inter / Math.max(ta.size(), tb.size());
    }

    private Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        for (String t : s.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (t.length() >= 3) out.add(t);
        }
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private Map<UUID, Integer> durationMap(List<ProgrammeBuildRequest.LineDuration> raw,
                                           List<BoqLine> requiredLines, boolean required) {
        Map<UUID, Integer> map = new HashMap<>();
        if (raw != null) {
            for (ProgrammeBuildRequest.LineDuration d : raw) {
                if (d == null || d.getBoqLineId() == null) continue;
                int days = d.getDurationWorkingDays() == null ? 0 : d.getDurationWorkingDays();
                map.put(d.getBoqLineId(), days);
            }
        }
        List<String> missing = new ArrayList<>();
        for (BoqLine line : requiredLines) {
            Integer days = map.get(line.getId());
            if (days == null || days < 1) {
                missing.add(activityName(line));
            } else {
                map.put(line.getId(), days);
            }
        }
        if (required && !missing.isEmpty()) {
            throw new BadRequestException(
                    "Each line needs a duration of at least 1 working day. Missing: "
                            + String.join("; ", missing.stream().limit(8).toList())
                            + (missing.size() > 8 ? "…" : ""));
        }
        return map;
    }

    private BoqDocument requireApprovedBoq(Long projectId) {
        return boqProjectRules.findApproved(projectId)
                .orElseThrow(() -> new BadRequestException("No APPROVED BOQ found for this project"));
    }

    private List<BoqLine> usableLines(BoqDocument boq) {
        return boqLineRepository.findByBoqIdOrderBySortOrderAsc(boq.getId()).stream()
                .filter(l -> StringUtils.hasText(l.getDescription()))
                .toList();
    }

    private static String activityCodeFor(BoqLine line) {
        int order = line.getSortOrder() != null ? line.getSortOrder() : 0;
        return "BOQ-" + order;
    }

    private static String uniqueActivityCode(BoqLine line, int index, Set<String> used) {
        String base = activityCodeFor(line);
        if (used.add(base)) return base;
        String withIndex = base + "-" + index;
        if (used.add(withIndex)) return withIndex;
        String withId = base + "-" + line.getId().toString().substring(0, 8);
        used.add(withId);
        return withId;
    }

    private static String activityName(BoqLine line) {
        if (StringUtils.hasText(line.getDescription())) {
            String d = line.getDescription().trim();
            return d.length() > 250 ? d.substring(0, 247) + "..." : d;
        }
        return wbsPhase(line);
    }

    private static String wbsPhase(BoqLine line) {
        if (StringUtils.hasText(line.getCategoryCode())) return line.getCategoryCode().trim();
        if (StringUtils.hasText(line.getCategoryName())) return line.getCategoryName().trim();
        return "GENERAL";
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static BoqMatchSource parseMatchSource(String raw) {
        if (!StringUtils.hasText(raw)) return BoqMatchSource.USER_CHANGED;
        try {
            return BoqMatchSource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return BoqMatchSource.USER_CHANGED;
        }
    }

    private ScheduleTemplate resolveTemplate(ProgrammeBuildRequest request) {
        if (request.getTemplateUuid() != null) {
            return requireTemplate(request.getTemplateUuid());
        }
        if (StringUtils.hasText(request.getTemplateCode())) {
            List<ScheduleTemplate> found =
                    templateRepository.findVisibleByCode(request.getTemplateCode(), CompanyContext.get());
            if (!found.isEmpty()) return found.get(0);
        }
        throw new BadRequestException("templateUuid or templateCode is required for BLEND mode");
    }

    private ScheduleTemplate requireTemplate(UUID uuid) {
        return templateRepository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Schedule template not found"));
    }

    private SchedulePreviewRequest toTemplateRequest(ProgrammeBuildRequest request) {
        SchedulePreviewRequest r = new SchedulePreviewRequest();
        r.setTemplateUuid(request.getTemplateUuid());
        r.setTemplateCode(request.getTemplateCode());
        r.setStartDate(request.getStartDate());
        r.setWorkCalendarUuid(request.getWorkCalendarUuid());
        r.setAreaSqft(request.getAreaSqft());
        r.setRoomCount(request.getRoomCount());
        r.setFloorCount(request.getFloorCount());
        r.setBedrooms(request.getBedrooms());
        r.setBathrooms(request.getBathrooms());
        r.setKitchens(request.getKitchens());
        r.setFinishLevel(request.getFinishLevel());
        r.setCrewCount(request.getCrewCount());
        r.setOccupiedBuilding(request.getOccupiedBuilding());
        if (request.getScopeToggles() != null) r.setScopeToggles(request.getScopeToggles());
        if (request.getQuantities() != null) r.setQuantities(request.getQuantities());
        if (request.getFastTrackAcknowledgements() != null) {
            r.setFastTrackAcknowledgements(request.getFastTrackAcknowledgements());
        }
        return r;
    }

    private ScheduleParameters toParameters(ScheduleTemplate template, SchedulePreviewRequest request,
                                            Project project) {
        ScheduleParameters params = baseParameters(template);
        params.setStartDate(request.getStartDate() == null ? LocalDate.now() : request.getStartDate());
        if (request.getAreaSqft() != null) params.setAreaSqft(request.getAreaSqft());
        if (request.getRoomCount() != null) params.setRoomCount(request.getRoomCount());
        if (request.getFloorCount() != null) params.setFloorCount(request.getFloorCount());
        if (request.getBedrooms() != null) params.setBedrooms(request.getBedrooms());
        if (request.getBathrooms() != null) params.setBathrooms(request.getBathrooms());
        if (request.getKitchens() != null) params.setKitchens(request.getKitchens());
        if (request.getFinishLevel() != null) params.setFinishLevel(request.getFinishLevel());
        if (request.getCrewCount() != null) params.setCrewCount(request.getCrewCount());
        params.setOccupiedBuilding(request.getOccupiedBuilding());
        if (request.getScopeToggles() != null) params.getScopeToggles().putAll(request.getScopeToggles());
        if (request.getQuantities() != null) params.getQuantities().putAll(request.getQuantities());
        if (params.getAreaSqft() == null) params.setAreaSqft(params.getBaseAreaSqft());
        return params;
    }

    private ScheduleParameters baseParameters(ScheduleTemplate template) {
        Map<String, Object> base = readJsonMap(template.getBaseParametersJson());
        ScheduleParameters params = new ScheduleParameters();
        params.setBaseAreaSqft(asDouble(base.get("areaSqft")));
        params.setBaseRoomCount(asInt(base.get("roomCount")));
        params.setBaseFloorCount(asInt(base.get("floorCount")));
        params.setBaseBedrooms(asInt(base.get("bedrooms")));
        params.setBaseBathrooms(asInt(base.get("bathrooms")));
        params.setBaseKitchens(asInt(base.get("kitchens")));
        params.setBaseFinishLevel(base.get("finishLevel") == null ? null : String.valueOf(base.get("finishLevel")));
        params.setBaseCrewCount(asInt(base.get("crewCount")));
        params.setAreaSqft(params.getBaseAreaSqft());
        params.setRoomCount(params.getBaseRoomCount());
        params.setFloorCount(params.getBaseFloorCount());
        params.setBedrooms(params.getBaseBedrooms());
        params.setBathrooms(params.getBaseBathrooms());
        params.setKitchens(params.getBaseKitchens());
        params.setFinishLevel(params.getBaseFinishLevel());
        params.setCrewCount(params.getBaseCrewCount());
        return params;
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> fastTrackConditions(ScheduleTemplate template) {
        if (template == null || !template.isFastTrack()) return List.of();
        String json = template.getFastTrackConditionsJson();
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> unacknowledged(ScheduleTemplate template, SchedulePreviewRequest request) {
        List<String> conditions = fastTrackConditions(template);
        if (conditions.isEmpty()) return List.of();
        Map<String, Boolean> ack = request.getFastTrackAcknowledgements() != null
                ? request.getFastTrackAcknowledgements() : Map.of();
        List<String> missing = new ArrayList<>();
        for (String c : conditions) {
            if (!Boolean.TRUE.equals(ack.get(c))) missing.add(c);
        }
        return missing;
    }

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        UUID ctx = CompanyContext.get();
        if (ctx == null || project.getCompanyId() == null || !ctx.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }
        return project;
    }

    private AuthPrincipal requireStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private record BuiltProgramme(
            TemplatePlan plan,
            WorkingCalendar calendar,
            ScheduleTemplate template,
            SchedulePreviewResponse preview,
            Map<String, UUID> boqLineIdByActivityCode,
            List<ProgrammeReplaceWriter.Attachment> attachments
    ) {}
}
