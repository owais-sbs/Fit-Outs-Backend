package com.fitouts.schedule.engine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * First-party critical path solver.
 *
 * <p>Supports all four precedence types with working-day lags, honours per-activity constraint
 * dates (an unapproved permit holding an activity back), and computes total and free float on
 * the working calendar rather than on raw calendar days.
 *
 * <p>Everything is inclusive-date based: an activity of one working day starts and finishes on
 * the same date, which is how site programmes are read, and avoids the off-by-one that comes
 * from treating a finish as an exclusive boundary.
 */
@Component
public class CpmEngine {

    /** Guards against a cyclic network turning relaxation into an infinite loop. */
    private static final int RELAXATION_FACTOR = 4;

    public CpmResult solve(List<CpmActivity> activities, List<CpmLink> links,
                           LocalDate projectStart, WorkingCalendar calendar) {
        CpmResult result = new CpmResult();
        result.setActivities(activities);
        if (activities.isEmpty()) {
            result.setProjectStart(projectStart);
            result.setProjectFinish(projectStart);
            return result;
        }

        Map<String, CpmActivity> byCode = new LinkedHashMap<>();
        for (CpmActivity a : activities) {
            if (a.getCode() == null || a.getCode().isBlank()) {
                result.getWarnings().add("Activity '" + a.getName() + "' has no code and cannot be linked.");
                continue;
            }
            if (byCode.putIfAbsent(a.getCode(), a) != null) {
                result.getWarnings().add("Duplicate activity code " + a.getCode() + "; only the first is used.");
            }
        }

        List<CpmLink> validLinks = new ArrayList<>();
        for (CpmLink link : links) {
            if (!byCode.containsKey(link.getPredecessorCode())) {
                result.getWarnings().add("Dependency references unknown predecessor "
                        + link.getPredecessorCode() + " and was ignored.");
                continue;
            }
            if (!byCode.containsKey(link.getSuccessorCode())) {
                result.getWarnings().add("Dependency references unknown successor "
                        + link.getSuccessorCode() + " and was ignored.");
                continue;
            }
            if (link.getPredecessorCode().equals(link.getSuccessorCode())) {
                result.getWarnings().add("Activity " + link.getPredecessorCode()
                        + " depends on itself; the link was ignored.");
                continue;
            }
            validLinks.add(link);
        }

        Map<String, List<CpmLink>> incoming = new HashMap<>();
        Map<String, List<CpmLink>> outgoing = new HashMap<>();
        for (CpmLink link : validLinks) {
            incoming.computeIfAbsent(link.getSuccessorCode(), k -> new ArrayList<>()).add(link);
            outgoing.computeIfAbsent(link.getPredecessorCode(), k -> new ArrayList<>()).add(link);
        }

        List<String> order = topologicalOrder(byCode, incoming, outgoing, result);
        LocalDate start = calendar.nextWorkingDay(projectStart);

        forwardPass(order, byCode, incoming, start, calendar);

        LocalDate projectFinish = byCode.values().stream()
                .map(CpmActivity::getEarlyFinish)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(start);

        backwardPass(order, byCode, outgoing, projectFinish, calendar);
        computeFloat(byCode, outgoing, calendar);

        result.setProjectStart(start);
        result.setProjectFinish(projectFinish);
        result.setTotalWorkingDays(calendar.workingDaysBetweenInclusive(start, projectFinish));
        result.setTotalCalendarDays((int) ChronoUnit.DAYS.between(start, projectFinish) + 1);
        result.setCriticalPaths(longestPaths(byCode, incoming, calendar, 3));
        return result;
    }

    // ---------------------------------------------------------------- ordering

    /**
     * Kahn's algorithm. A cycle leaves nodes unvisited; those are appended in declaration order
     * and reported, so a bad seed file produces a flagged schedule rather than a hang.
     */
    private List<String> topologicalOrder(Map<String, CpmActivity> byCode,
                                          Map<String, List<CpmLink>> incoming,
                                          Map<String, List<CpmLink>> outgoing,
                                          CpmResult result) {
        Map<String, Integer> indegree = new HashMap<>();
        for (String code : byCode.keySet()) {
            indegree.put(code, incoming.getOrDefault(code, List.of()).size());
        }

        Deque<String> queue = new ArrayDeque<>();
        byCode.keySet().stream().filter(c -> indegree.get(c) == 0).forEach(queue::add);

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String code = queue.poll();
            order.add(code);
            for (CpmLink link : outgoing.getOrDefault(code, List.of())) {
                String next = link.getSuccessorCode();
                int remaining = indegree.merge(next, -1, Integer::sum);
                if (remaining == 0) queue.add(next);
            }
        }

        if (order.size() < byCode.size()) {
            Set<String> stuck = new LinkedHashSet<>(byCode.keySet());
            stuck.removeAll(order);
            result.getWarnings().add("Circular dependency involving " + String.join(", ", stuck)
                    + ". These activities were scheduled in declaration order instead.");
            order.addAll(stuck);
        }
        return order;
    }

    // ------------------------------------------------------------ forward pass

    private void forwardPass(List<String> order, Map<String, CpmActivity> byCode,
                             Map<String, List<CpmLink>> incoming, LocalDate projectStart,
                             WorkingCalendar calendar) {
        for (CpmActivity a : byCode.values()) {
            a.setEarlyStart(null);
            a.setEarlyFinish(null);
        }

        // A cyclic network breaks the topological guarantee, so relax repeatedly until the
        // dates stop moving. Acyclic networks converge on the first sweep.
        int maxSweeps = Math.max(1, byCode.size() * RELAXATION_FACTOR);
        boolean changed = true;
        for (int sweep = 0; sweep < maxSweeps && changed; sweep++) {
            changed = false;
            for (String code : order) {
                CpmActivity a = byCode.get(code);
                LocalDate earliest = projectStart;

                if (a.getConstraintStart() != null) {
                    LocalDate constrained = calendar.nextWorkingDay(a.getConstraintStart());
                    if (constrained.isAfter(earliest)) earliest = constrained;
                }

                LocalDate earliestFinish = null;
                for (CpmLink link : incoming.getOrDefault(code, List.of())) {
                    CpmActivity pred = byCode.get(link.getPredecessorCode());
                    if (pred.getEarlyStart() == null) continue;

                    switch (link.getType()) {
                        case FS -> {
                            LocalDate candidate = shift(calendar, pred.getEarlyFinish(), link.getLagWorkingDays() + 1);
                            if (candidate.isAfter(earliest)) earliest = candidate;
                        }
                        case SS -> {
                            LocalDate candidate = shift(calendar, pred.getEarlyStart(), link.getLagWorkingDays());
                            if (candidate.isAfter(earliest)) earliest = candidate;
                        }
                        case FF -> {
                            LocalDate candidate = shift(calendar, pred.getEarlyFinish(), link.getLagWorkingDays());
                            if (earliestFinish == null || candidate.isAfter(earliestFinish)) earliestFinish = candidate;
                        }
                        case SF -> {
                            LocalDate candidate = shift(calendar, pred.getEarlyStart(), link.getLagWorkingDays());
                            if (earliestFinish == null || candidate.isAfter(earliestFinish)) earliestFinish = candidate;
                        }
                    }
                }

                int duration = a.effectiveDuration();
                LocalDate newStart = calendar.nextWorkingDay(earliest);

                // A finish-driven link can push the activity later but never pull it earlier
                // than its start-driven predecessors allow.
                if (earliestFinish != null) {
                    LocalDate impliedStart = duration == 0
                            ? calendar.nextWorkingDay(earliestFinish)
                            : calendar.startOf(earliestFinish, duration);
                    if (impliedStart.isAfter(newStart)) newStart = impliedStart;
                }

                LocalDate newFinish = duration == 0
                        ? newStart
                        : calendar.finishOf(newStart, duration);

                if (!newStart.equals(a.getEarlyStart()) || !newFinish.equals(a.getEarlyFinish())) {
                    a.setEarlyStart(newStart);
                    a.setEarlyFinish(newFinish);
                    changed = true;
                }
            }
        }
    }

    // ----------------------------------------------------------- backward pass

    private void backwardPass(List<String> order, Map<String, CpmActivity> byCode,
                              Map<String, List<CpmLink>> outgoing, LocalDate projectFinish,
                              WorkingCalendar calendar) {
        for (CpmActivity a : byCode.values()) {
            a.setLateStart(null);
            a.setLateFinish(null);
        }

        List<String> reverse = new ArrayList<>(order);
        java.util.Collections.reverse(reverse);

        int maxSweeps = Math.max(1, byCode.size() * RELAXATION_FACTOR);
        boolean changed = true;
        for (int sweep = 0; sweep < maxSweeps && changed; sweep++) {
            changed = false;
            for (String code : reverse) {
                CpmActivity a = byCode.get(code);
                int duration = a.effectiveDuration();

                LocalDate latestFinish = projectFinish;
                LocalDate latestStart = null;

                for (CpmLink link : outgoing.getOrDefault(code, List.of())) {
                    CpmActivity succ = byCode.get(link.getSuccessorCode());
                    if (succ.getLateStart() == null) continue;

                    switch (link.getType()) {
                        case FS -> {
                            LocalDate candidate = shift(calendar, succ.getLateStart(), -(link.getLagWorkingDays() + 1));
                            if (candidate.isBefore(latestFinish)) latestFinish = candidate;
                        }
                        case SS -> {
                            LocalDate candidate = shift(calendar, succ.getLateStart(), -link.getLagWorkingDays());
                            if (latestStart == null || candidate.isBefore(latestStart)) latestStart = candidate;
                        }
                        case FF -> {
                            LocalDate candidate = shift(calendar, succ.getLateFinish(), -link.getLagWorkingDays());
                            if (candidate.isBefore(latestFinish)) latestFinish = candidate;
                        }
                        case SF -> {
                            LocalDate candidate = shift(calendar, succ.getLateFinish(), -link.getLagWorkingDays());
                            if (latestStart == null || candidate.isBefore(latestStart)) latestStart = candidate;
                        }
                    }
                }

                LocalDate newFinish = calendar.previousWorkingDay(latestFinish);
                if (latestStart != null) {
                    LocalDate impliedFinish = duration == 0
                            ? calendar.previousWorkingDay(latestStart)
                            : calendar.finishOf(latestStart, duration);
                    if (impliedFinish.isBefore(newFinish)) newFinish = impliedFinish;
                }

                LocalDate newStart = duration == 0 ? newFinish : calendar.startOf(newFinish, duration);

                if (!newFinish.equals(a.getLateFinish()) || !newStart.equals(a.getLateStart())) {
                    a.setLateFinish(newFinish);
                    a.setLateStart(newStart);
                    changed = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------- float

    private void computeFloat(Map<String, CpmActivity> byCode,
                              Map<String, List<CpmLink>> outgoing,
                              WorkingCalendar calendar) {
        for (CpmActivity a : byCode.values()) {
            int total = calendar.floatBetween(a.getEarlyStart(), a.getLateStart());
            a.setTotalFloat(total);
            a.setCritical(total <= 0);

            Integer free = null;
            for (CpmLink link : outgoing.getOrDefault(a.getCode(), List.of())) {
                CpmActivity succ = byCode.get(link.getSuccessorCode());
                if (succ == null || succ.getEarlyStart() == null) continue;
                // Compare like with like: SS and SF constrain this activity's start, FS and FF
                // constrain its finish.
                boolean startDriven = link.getType() == DependencyType.SS || link.getType() == DependencyType.SF;
                LocalDate mine = startDriven ? a.getEarlyStart() : a.getEarlyFinish();
                LocalDate requiredBy = switch (link.getType()) {
                    case FS -> shift(calendar, succ.getEarlyStart(), -(link.getLagWorkingDays() + 1));
                    case FF -> shift(calendar, succ.getEarlyFinish(), -link.getLagWorkingDays());
                    case SS -> shift(calendar, succ.getEarlyStart(), -link.getLagWorkingDays());
                    case SF -> shift(calendar, succ.getEarlyFinish(), -link.getLagWorkingDays());
                };
                int slack = calendar.floatBetween(mine, requiredBy);
                free = free == null ? slack : Math.min(free, slack);
            }
            // No successors means the activity is free until the project finish, which total
            // float already expresses.
            a.setFreeFloat(free == null ? total : Math.max(0, free));
        }
    }

    // ----------------------------------------------------------- longest paths

    /**
     * The {@code n} longest chains through the network, measured in working days. Uses the
     * float ranking rather than an exhaustive path enumeration, which would be exponential on
     * a 300-activity programme.
     */
    private List<List<String>> longestPaths(Map<String, CpmActivity> byCode,
                                            Map<String, List<CpmLink>> incoming,
                                            WorkingCalendar calendar, int n) {
        List<CpmActivity> endpoints = byCode.values().stream()
                .filter(a -> a.getEarlyFinish() != null)
                .sorted(Comparator.comparingInt(CpmActivity::getTotalFloat)
                        .thenComparing(CpmActivity::getEarlyFinish, Comparator.reverseOrder()))
                .toList();

        List<List<String>> paths = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();

        for (CpmActivity endpoint : endpoints) {
            if (paths.size() >= n) break;
            List<String> chain = traceBack(endpoint, byCode, incoming, calendar);
            String signature = String.join(">", chain);
            if (chain.size() > 1 && seenSignatures.add(signature)) {
                paths.add(chain);
            }
        }
        return paths;
    }

    /** Walks back from an activity through its most constraining predecessor at each step. */
    private List<String> traceBack(CpmActivity endpoint, Map<String, CpmActivity> byCode,
                                   Map<String, List<CpmLink>> incoming, WorkingCalendar calendar) {
        List<String> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        CpmActivity cursor = endpoint;

        while (cursor != null && visited.add(cursor.getCode())) {
            chain.add(cursor.getCode());
            CpmActivity driver = null;
            LocalDate driverDate = null;

            for (CpmLink link : incoming.getOrDefault(cursor.getCode(), List.of())) {
                CpmActivity pred = byCode.get(link.getPredecessorCode());
                if (pred == null || pred.getEarlyFinish() == null || visited.contains(pred.getCode())) continue;
                LocalDate pushesTo = switch (link.getType()) {
                    case FS -> shift(calendar, pred.getEarlyFinish(), link.getLagWorkingDays() + 1);
                    case SS -> shift(calendar, pred.getEarlyStart(), link.getLagWorkingDays());
                    case FF, SF -> pred.getEarlyFinish();
                };
                if (driverDate == null || pushesTo.isAfter(driverDate)) {
                    driverDate = pushesTo;
                    driver = pred;
                }
            }
            cursor = driver;
        }
        java.util.Collections.reverse(chain);
        return chain;
    }

    private LocalDate shift(WorkingCalendar calendar, LocalDate from, int workingDays) {
        if (from == null) return null;
        if (workingDays == 0) return calendar.nextWorkingDay(from);
        return workingDays > 0
                ? calendar.addWorkingDays(from, workingDays)
                : calendar.subtractWorkingDays(from, -workingDays);
    }
}
