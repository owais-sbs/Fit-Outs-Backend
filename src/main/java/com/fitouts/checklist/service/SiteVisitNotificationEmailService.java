package com.fitouts.checklist.service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.account.domain.Account;
import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitAssignment;
import com.fitouts.checklist.domain.SiteVisitLocationDetails;
import com.fitouts.checklist.repository.SiteVisitRepository;
import com.fitouts.lead.domain.Lead;
import com.fitouts.lead.domain.LeadRepository;
import com.fitouts.shared.email.EmailMessage;
import com.fitouts.shared.email.EmailService;
import com.fitouts.shared.email.EmailTemplateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteVisitNotificationEmailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final SiteVisitRepository siteVisitRepository;
    private final LeadRepository leadRepository;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;

    @Value("${app.public-url:http://localhost:3002}")
    private String publicUrl;

    @Transactional
    public void sendInitialNotification(UUID siteVisitUuid) {
        SiteVisit visit = siteVisitRepository.findById(siteVisitUuid).orElse(null);
        if (visit == null || visit.getInitialEmailSentAt() != null) {
            return;
        }
        Lead lead = leadRepository.findById(visit.getLeadId()).orElse(null);
        String visitUrl = visitReportUrl(siteVisitUuid);
        String schedule = formatSchedule(visit);
        String staffNames = assignedStaffNames(visit);

        if (lead != null && StringUtils.hasText(lead.getEmail())) {
            String html = emailTemplateService.render("site-visit-scheduled-lead", Map.of(
                    "clientName", StringUtils.hasText(lead.getClientName()) ? lead.getClientName() : "there",
                    "schedule", schedule,
                    "staffNames", StringUtils.hasText(staffNames) ? staffNames : "Our team",
                    "visitUrl", visitUrl));
            emailService.sendAsync(EmailMessage.builder()
                    .to(lead.getEmail())
                    .subject("Site visit scheduled — " + schedule)
                    .body(html)
                    .html(true)
                    .build());
        }

        for (String staffEmail : staffEmails(visit)) {
            String client = lead != null && StringUtils.hasText(lead.getClientName())
                    ? lead.getClientName()
                    : "Client";
            String html = emailTemplateService.render("site-visit-scheduled-staff", Map.of(
                    "clientName", client,
                    "schedule", schedule,
                    "staffNames", StringUtils.hasText(staffNames) ? staffNames : "Team",
                    "visitUrl", visitUrl));
            emailService.sendAsync(EmailMessage.builder()
                    .to(staffEmail)
                    .subject("Site visit assignment — " + schedule)
                    .body(html)
                    .html(true)
                    .build());
        }

        visit.setInitialEmailSentAt(java.time.OffsetDateTime.now());
        siteVisitRepository.save(visit);
    }

    @Transactional
    public void sendLocationUpdateNotification(UUID siteVisitUuid) {
        SiteVisit visit = siteVisitRepository.findById(siteVisitUuid).orElse(null);
        if (visit == null || visit.getLocationEmailSentAt() != null) {
            return;
        }
        Lead lead = leadRepository.findById(visit.getLeadId()).orElse(null);
        SiteVisitLocationDetails loc = visit.getLocationDetails();
        String visitUrl = visitReportUrl(siteVisitUuid);
        String schedule = formatSchedule(visit);
        Map<String, Object> locationVars = buildLocationVariables(loc, visit);

        if (lead != null && StringUtils.hasText(lead.getEmail())) {
            Map<String, Object> vars = new java.util.HashMap<>(locationVars);
            vars.put("forClient", true);
            vars.put("clientName", StringUtils.hasText(lead.getClientName()) ? lead.getClientName() : "there");
            vars.put("schedule", schedule);
            vars.put("visitUrl", visitUrl);
            String html = emailTemplateService.render("site-visit-location-updated", vars);
            emailService.sendAsync(EmailMessage.builder()
                    .to(lead.getEmail())
                    .subject("Site visit location confirmed — " + schedule)
                    .body(html)
                    .html(true)
                    .build());
        }

        for (String staffEmail : staffEmails(visit)) {
            Map<String, Object> vars = new java.util.HashMap<>(locationVars);
            vars.put("forClient", false);
            vars.put("clientName", lead != null && StringUtils.hasText(lead.getClientName())
                    ? lead.getClientName()
                    : "Client");
            vars.put("schedule", schedule);
            vars.put("visitUrl", visitUrl);
            String html = emailTemplateService.render("site-visit-location-updated", vars);
            emailService.sendAsync(EmailMessage.builder()
                    .to(staffEmail)
                    .subject("Site visit location update — " + schedule)
                    .body(html)
                    .html(true)
                    .build());
        }

        visit.setLocationEmailSentAt(java.time.OffsetDateTime.now());
        siteVisitRepository.save(visit);
    }

    private Map<String, Object> buildLocationVariables(SiteVisitLocationDetails loc, SiteVisit visit) {
        java.util.HashMap<String, Object> vars = new java.util.HashMap<>();
        if (loc == null) {
            vars.put("coordinates", visit.getLatitude() + ", " + visit.getLongitude());
            return vars;
        }
        vars.put("addressLine1", loc.getAddressLine1());
        vars.put("addressLine2", loc.getAddressLine2());
        vars.put("buildingName", loc.getBuildingName());
        vars.put("unitNumber", loc.getUnitNumber());
        vars.put("floor", loc.getFloor());
        vars.put("landmark", loc.getLandmark());
        vars.put("area", loc.getArea());
        vars.put("cityLine", String.join(", ",
                nonBlank(loc.getCity()),
                nonBlank(loc.getState()),
                nonBlank(loc.getPincode()),
                nonBlank(loc.getCountry())).replaceAll("^[,\\s]+|[,\\s]+$", ""));
        vars.put("mapsShareUrl", loc.getMapsShareUrl());
        if (!StringUtils.hasText(loc.getMapsShareUrl())) {
            vars.put("coordinates", visit.getLatitude() + ", " + visit.getLongitude());
        }
        return vars;
    }

    private Set<String> staffEmails(SiteVisit visit) {
        Set<String> emails = new LinkedHashSet<>();
        if (visit.getAssignments() != null) {
            for (SiteVisitAssignment assignment : visit.getAssignments()) {
                Account account = assignment.getEmployee();
                if (account != null && StringUtils.hasText(account.getEmail())) {
                    emails.add(account.getEmail().trim().toLowerCase());
                }
            }
        }
        if (visit.getAssignedTo() != null && StringUtils.hasText(visit.getAssignedTo().getEmail())) {
            emails.add(visit.getAssignedTo().getEmail().trim().toLowerCase());
        }
        return emails;
    }

    private String assignedStaffNames(SiteVisit visit) {
        if (visit.getAssignments() == null || visit.getAssignments().isEmpty()) {
            return "Assigned team";
        }
        return visit.getAssignments().stream()
                .map(SiteVisitAssignment::getEmployee)
                .filter(Objects::nonNull)
                .map(Account::getFullName)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(", "));
    }

    private String formatSchedule(SiteVisit visit) {
        String date = visit.getScheduledDate() != null ? visit.getScheduledDate().format(DATE_FMT) : "TBC";
        String time = visit.getScheduledTime() != null ? visit.getScheduledTime().format(TIME_FMT) : "";
        return time.isBlank() ? date : date + " at " + time;
    }

    private String visitReportUrl(UUID uuid) {
        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        return base + "/admin/site-visits/" + uuid + "/report";
    }

    private String nonBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
