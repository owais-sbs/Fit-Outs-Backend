package com.fitouts.variation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.boq.application.BoqProjectRules;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.commercialapproval.application.CommercialApprovalService;
import com.fitouts.commercialapproval.domain.CommercialApprovalRun;
import com.fitouts.commercialapproval.domain.CommercialEventType;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.notification.application.NotificationService;
import com.fitouts.profitloss.application.PnlCalculationService;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.schedule.api.ScheduleBaselineResponse;
import com.fitouts.schedule.application.ScheduleRescheduleService;
import com.fitouts.schedule.application.ScheduleService;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.variation.api.VariationUpsertRequest;
import com.fitouts.variation.application.VariationRebaselineService;
import com.fitouts.variation.application.VariationBoqApplyService;
import com.fitouts.variation.application.VariationService;
import com.fitouts.variation.domain.ProjectCommercial;
import com.fitouts.variation.domain.ProjectCommercialRepository;
import com.fitouts.variation.domain.VariationAttachmentRepository;
import com.fitouts.variation.domain.VariationBoqChangeRepository;
import com.fitouts.variation.domain.VariationEvent;
import com.fitouts.variation.domain.VariationEventRepository;
import com.fitouts.variation.domain.VariationLine;
import com.fitouts.variation.domain.VariationLineRepository;
import com.fitouts.variation.domain.VariationLink;
import com.fitouts.variation.domain.VariationLinkRepository;
import com.fitouts.variation.domain.VariationLinkType;
import com.fitouts.variation.domain.VariationRequest;
import com.fitouts.variation.domain.VariationRequestRepository;
import com.fitouts.variation.domain.VariationStatus;
import com.fitouts.workitemconfiguration.domain.WorkItemRepository;

class VariationModuleTest {

    private VariationRequestRepository variationRepository;
    private VariationLineRepository lineRepository;
    private VariationLinkRepository linkRepository;
    private VariationAttachmentRepository attachmentRepository;
    private VariationEventRepository eventRepository;
    private ProjectCommercialRepository commercialRepository;
    private ProjectService projectService;
    private BoqProjectRules boqProjectRules;
    private CommercialApprovalService commercialApprovalService;
    private NotificationService notificationService;
    private ScheduleActivityRepository activityRepository;
    private ScheduleRescheduleService scheduleRescheduleService;
    private ScheduleService scheduleService;
    private VariationRebaselineService rebaselineService;
    private VariationBoqApplyService variationBoqApplyService;
    private VariationBoqChangeRepository variationBoqChangeRepository;
    private VariationService service;

    private final UUID companyId = UUID.randomUUID();
    private final Long projectId = 42L;
    private final List<VariationRequest> store = new ArrayList<>();
    private final List<VariationLine> lines = new ArrayList<>();
    private final List<VariationEvent> recordedEvents = new ArrayList<>();
    private ProjectCommercial commercial;

    @BeforeEach
    void setUp() {
        variationRepository = mock(VariationRequestRepository.class);
        lineRepository = mock(VariationLineRepository.class);
        linkRepository = mock(VariationLinkRepository.class);
        attachmentRepository = mock(VariationAttachmentRepository.class);
        eventRepository = mock(VariationEventRepository.class);
        commercialRepository = mock(ProjectCommercialRepository.class);
        projectService = mock(ProjectService.class);
        boqProjectRules = mock(BoqProjectRules.class);
        BoqLineRepository boqLineRepository = mock(BoqLineRepository.class);
        WorkItemRepository workItemRepository = mock(WorkItemRepository.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        commercialApprovalService = mock(CommercialApprovalService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        activityRepository = mock(ScheduleActivityRepository.class);
        scheduleRescheduleService = mock(ScheduleRescheduleService.class);
        scheduleService = mock(ScheduleService.class);

        rebaselineService = new VariationRebaselineService(
                variationRepository, linkRepository, eventRepository, activityRepository,
                scheduleRescheduleService, scheduleService, notificationService,
                accountRepository, new ObjectMapper());
        variationBoqApplyService = mock(VariationBoqApplyService.class);
        variationBoqChangeRepository = mock(VariationBoqChangeRepository.class);
        when(variationBoqChangeRepository.findByVariationUuidOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());

        CommercialLifecycleService commercialLifecycleService = mock(CommercialLifecycleService.class);
        org.mockito.Mockito.doNothing().when(commercialLifecycleService).assertCommercialMutable(any());

        service = new VariationService(
                variationRepository, lineRepository, linkRepository, attachmentRepository,
                eventRepository, commercialRepository, projectService, boqProjectRules,
                boqLineRepository, workItemRepository, fileStorageService,
                commercialApprovalService, notificationService, accountRepository,
                rebaselineService, variationBoqApplyService, variationBoqChangeRepository,
                rebaselineService, commercialLifecycleService,
                variationBoqApplyService, variationBoqChangeRepository,
                mock(PnlCalculationService.class));

        Project project = new Project();
        project.setId(projectId);
        project.setCompanyId(companyId);
        project.setName("Test Project");
        project.setBudget(new BigDecimal("1000000"));
        project.setClientId(99L);
        when(projectService.getById(projectId)).thenReturn(project);
        when(boqProjectRules.findApproved(projectId)).thenReturn(Optional.empty());

        when(variationRepository.save(any())).thenAnswer(inv -> {
            VariationRequest v = inv.getArgument(0);
            if (v.getUuid() == null) v.setUuid(UUID.randomUUID());
            store.removeIf(x -> x.getUuid().equals(v.getUuid()));
            store.add(v);
            return v;
        });
        when(variationRepository.findByUuidAndCompanyId(any(), eq(companyId))).thenAnswer(inv ->
                store.stream().filter(v -> v.getUuid().equals(inv.getArgument(0))).findFirst());
        when(variationRepository.findById(any())).thenAnswer(inv ->
                store.stream().filter(v -> v.getUuid().equals(inv.getArgument(0))).findFirst());
        when(variationRepository.countByProject(projectId, companyId)).thenReturn(0L);

        when(lineRepository.save(any())).thenAnswer(inv -> {
            VariationLine l = inv.getArgument(0);
            if (l.getUuid() == null) l.setUuid(UUID.randomUUID());
            lines.add(l);
            return l;
        });
        when(lineRepository.findByVariationUuidOrderBySortOrderAsc(any())).thenAnswer(inv ->
                lines.stream().filter(l -> l.getVariationUuid().equals(inv.getArgument(0))).toList());
        when(linkRepository.findByVariationUuid(any())).thenReturn(List.of());
        when(attachmentRepository.findByVariationUuidOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(eventRepository.save(any())).thenAnswer(inv -> {
            VariationEvent e = inv.getArgument(0);
            recordedEvents.add(e);
            return e;
        });
        when(eventRepository.findByVariationUuidOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(accountRepository.findAllByCompanyUuidAndRole(any(), any())).thenReturn(List.of());

        commercial = new ProjectCommercial();
        commercial.setUuid(UUID.randomUUID());
        commercial.setProjectId(projectId);
        commercial.setCompanyId(companyId);
        commercial.setOriginalContractValue(new BigDecimal("1000000"));
        commercial.setCurrentContractValue(new BigDecimal("1000000"));
        commercial.setCurrentCost(BigDecimal.ZERO);
        commercial.setCurrentMargin(new BigDecimal("1000000"));
        when(commercialRepository.findByProjectIdAndCompanyId(projectId, companyId))
                .thenReturn(Optional.of(commercial));
        when(commercialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    private void auth(Role role, Long accountId) {
        AuthPrincipal principal = AuthPrincipal.builder()
                .accountId(accountId)
                .companyId(companyId)
                .email(role.name().toLowerCase() + "@test")
                .roles(Set.of(role))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        CompanyContext.set(companyId);
    }

    @Test
    @DisplayName("client raise goes to AWAITING_TRIAGE")
    void clientRaiseTriage() {
        auth(Role.CLIENT, 99L);
        VariationUpsertRequest req = new VariationUpsertRequest();
        req.setTitle("Extra wardrobes");
        req.setDescription("Please add");
        var resp = service.create(projectId, req);
        assertThat(resp.getStatus()).isEqualTo(VariationStatus.AWAITING_TRIAGE);
        assertThat(store.get(0).getOrigin().name()).isEqualTo("CLIENT");
    }

    @Test
    @DisplayName("staff submit starts matrix and moves to INTERNAL_REVIEW")
    void submitStartsMatrix() {
        auth(Role.QS, 5L);
        VariationUpsertRequest req = new VariationUpsertRequest();
        req.setTitle("Scope add");
        VariationUpsertRequest.LineRequest line = new VariationUpsertRequest.LineRequest();
        line.setLineType(com.fitouts.variation.domain.VariationLineType.LUMP_SUM);
        line.setQuantity(BigDecimal.ONE);
        line.setSellRate(new BigDecimal("80000"));
        line.setCostRate(new BigDecimal("50000"));
        req.setLines(List.of(line));
        var created = service.create(projectId, req);

        CommercialApprovalRun run = new CommercialApprovalRun();
        run.setUuid(UUID.randomUUID());
        when(commercialApprovalService.startRun(
                eq(CommercialEventType.VARIATION), any(), eq(projectId), any(), any()))
                .thenReturn(Optional.of(run));

        var submitted = service.submitReview(projectId, created.getUuid());
        assertThat(submitted.getStatus()).isEqualTo(VariationStatus.INTERNAL_REVIEW);
        assertThat(submitted.getApprovalRunUuid()).isEqualTo(run.getUuid());
    }

    @Test
    @DisplayName("client approve locks amounts and updates commercial")
    void clientApproveLocks() {
        auth(Role.QS, 5L);
        VariationUpsertRequest req = new VariationUpsertRequest();
        req.setTitle("CR");
        VariationUpsertRequest.LineRequest line = new VariationUpsertRequest.LineRequest();
        line.setLineType(com.fitouts.variation.domain.VariationLineType.LUMP_SUM);
        line.setQuantity(BigDecimal.ONE);
        line.setSellRate(new BigDecimal("10000"));
        line.setCostRate(new BigDecimal("4000"));
        req.setLines(List.of(line));
        var created = service.create(projectId, req);

        VariationRequest vr = store.get(0);
        vr.setStatus(VariationStatus.ISSUED_TO_CLIENT);
        vr.setSellDelta(new BigDecimal("10000"));
        vr.setCostDelta(new BigDecimal("4000"));

        auth(Role.CLIENT, 99L);
        var approved = service.clientApprove(projectId, created.getUuid());
        assertThat(approved.getStatus()).isEqualTo(VariationStatus.APPROVED);
        assertThat(approved.getLockedAt()).isNotNull();
        assertThat(commercial.getCurrentContractValue()).isEqualByComparingTo("1010000");
        assertThat(commercial.getCurrentCost()).isEqualByComparingTo("4000");
        verify(variationBoqApplyService).apply(vr, 99L);
        assertThat(recordedEvents).anyMatch(e -> e.getAction().equals("BOQ_APPLIED"));
    }

    @Test
    @DisplayName("missing approved BOQ is non-fatal and audited as skipped")
    void clientApprove_boqApplyFailure_stillApprovesAndAudits() {
        auth(Role.QS, 5L);
        VariationUpsertRequest req = new VariationUpsertRequest();
        req.setTitle("CR without approved BOQ");
        var created = service.create(projectId, req);
        VariationRequest vr = store.get(0);
        vr.setStatus(VariationStatus.ISSUED_TO_CLIENT);
        vr.setSellDelta(new BigDecimal("100"));
        vr.setCostDelta(BigDecimal.ZERO);
        doThrow(new BadRequestException("No approved BOQ exists for this project"))
                .when(variationBoqApplyService).apply(vr, 99L);

        auth(Role.CLIENT, 99L);
        var approved = service.clientApprove(projectId, created.getUuid());

        assertThat(approved.getStatus()).isEqualTo(VariationStatus.APPROVED);
        assertThat(recordedEvents).anyMatch(e -> e.getAction().equals("BOQ_SKIPPED_NO_APPROVED")
                && e.getDetail().contains("No approved BOQ"));
    }

    @Test
    @DisplayName("client approve with flag false updates commercial without reschedule or baseline")
    void clientApprove_flagFalse_noRescheduleOrBaseline() {
        auth(Role.QS, 5L);
        VariationUpsertRequest req = new VariationUpsertRequest();
        req.setTitle("CR Flag False");
        req.setApplyScheduleOnApproval(false);
        req.setProposedDelayDays(5);
        VariationUpsertRequest.LineRequest line = new VariationUpsertRequest.LineRequest();
        line.setLineType(com.fitouts.variation.domain.VariationLineType.LUMP_SUM);
        line.setQuantity(BigDecimal.ONE);
        line.setSellRate(new BigDecimal("10000"));
        line.setCostRate(new BigDecimal("4000"));
        req.setLines(List.of(line));
        var created = service.create(projectId, req);

        VariationRequest vr = store.get(0);
        vr.setStatus(VariationStatus.ISSUED_TO_CLIENT);
        vr.setSellDelta(new BigDecimal("10000"));
        vr.setCostDelta(new BigDecimal("4000"));

        auth(Role.CLIENT, 99L);
        var approved = service.clientApprove(projectId, created.getUuid());

        assertThat(approved.getStatus()).isEqualTo(VariationStatus.APPROVED);
        assertThat(approved.getLockedAt()).isNotNull();
        assertThat(commercial.getCurrentContractValue()).isEqualByComparingTo("1010000");

        verify(scheduleRescheduleService, never()).reschedule(any(), any());
        verify(scheduleService, never()).createBaselineForSystem(any(), any(), any());

        assertThat(recordedEvents).anyMatch(e -> e.getAction().equals("CLIENT_APPROVED")
                && e.getDetail() != null && e.getDetail().contains("\"status\":\"SKIPPED\""));
    }

    @Test
    @DisplayName("client approve with flag true and activity link extends duration and creates baseline")
    void clientApprove_flagTrue_withActivityAndDelay_reschedulesAndCreatesBaseline() {
        auth(Role.QS, 5L);
        UUID actUuid = UUID.randomUUID();
        VariationUpsertRequest req = new VariationUpsertRequest();
        req.setTitle("CR Flag True Delay");
        req.setApplyScheduleOnApproval(true);
        req.setProposedDelayDays(4);
        VariationUpsertRequest.LineRequest line = new VariationUpsertRequest.LineRequest();
        line.setLineType(com.fitouts.variation.domain.VariationLineType.LUMP_SUM);
        line.setQuantity(BigDecimal.ONE);
        line.setSellRate(new BigDecimal("20000"));
        line.setCostRate(new BigDecimal("8000"));
        req.setLines(List.of(line));
        var created = service.create(projectId, req);

        VariationRequest vr = store.get(0);
        vr.setStatus(VariationStatus.ISSUED_TO_CLIENT);
        vr.setSellDelta(new BigDecimal("20000"));
        vr.setCostDelta(new BigDecimal("8000"));

        VariationLink link = new VariationLink();
        link.setVariationUuid(vr.getUuid());
        link.setLinkType(VariationLinkType.ACTIVITY);
        link.setActivityUuid(actUuid);
        when(linkRepository.findByVariationUuid(vr.getUuid())).thenReturn(List.of(link));

        ScheduleActivity act = new ScheduleActivity();
        act.setUuid(actUuid);
        act.setName("Joinery Works");
        act.setDurationWorkingDays(6);
        when(activityRepository.findByUuidAndCompanyId(actUuid, companyId)).thenReturn(Optional.of(act));

        UUID baselineUuid = UUID.randomUUID();
        when(scheduleService.createBaselineForSystem(eq(projectId), eq(vr.getCrNumber() + " approved"), eq(99L)))
                .thenReturn(ScheduleBaselineResponse.builder().uuid(baselineUuid).name(vr.getCrNumber() + " approved").build());

        auth(Role.CLIENT, 99L);
        var approved = service.clientApprove(projectId, created.getUuid());

        assertThat(approved.getStatus()).isEqualTo(VariationStatus.APPROVED);
        assertThat(approved.getLockedAt()).isNotNull();

        verify(scheduleRescheduleService).reschedule(eq(projectId), argThat(r ->
                r.getActivityUuid().equals(actUuid) && r.getDurationWorkingDays() == 10));
        verify(scheduleService).createBaselineForSystem(eq(projectId), eq(vr.getCrNumber() + " approved"), eq(99L));

        assertThat(recordedEvents).anyMatch(e -> e.getAction().equals("CLIENT_APPROVED")
                && e.getDetail() != null
                && e.getDetail().contains(baselineUuid.toString())
                && e.getDetail().contains("\"newDuration\":10"));
    }

    @Test
    @DisplayName("client approve with flag true and no activity links snapshots baseline only")
    void clientApprove_flagTrue_noLinks_baselineOnly() {
        auth(Role.QS, 5L);
        VariationUpsertRequest req = new VariationUpsertRequest();
        req.setTitle("CR Flag True No Links");
        req.setApplyScheduleOnApproval(true);
        req.setProposedDelayDays(3);
        VariationUpsertRequest.LineRequest line = new VariationUpsertRequest.LineRequest();
        line.setLineType(com.fitouts.variation.domain.VariationLineType.LUMP_SUM);
        line.setQuantity(BigDecimal.ONE);
        line.setSellRate(new BigDecimal("15000"));
        line.setCostRate(new BigDecimal("5000"));
        req.setLines(List.of(line));
        var created = service.create(projectId, req);

        VariationRequest vr = store.get(0);
        vr.setStatus(VariationStatus.ISSUED_TO_CLIENT);
        vr.setSellDelta(new BigDecimal("15000"));
        vr.setCostDelta(new BigDecimal("5000"));

        when(linkRepository.findByVariationUuid(vr.getUuid())).thenReturn(List.of());

        UUID baselineUuid = UUID.randomUUID();
        when(scheduleService.createBaselineForSystem(eq(projectId), eq(vr.getCrNumber() + " approved"), eq(99L)))
                .thenReturn(ScheduleBaselineResponse.builder().uuid(baselineUuid).name(vr.getCrNumber() + " approved").build());

        auth(Role.CLIENT, 99L);
        var approved = service.clientApprove(projectId, created.getUuid());

        assertThat(approved.getStatus()).isEqualTo(VariationStatus.APPROVED);
        verify(scheduleRescheduleService, never()).reschedule(any(), any());
        verify(scheduleService).createBaselineForSystem(eq(projectId), eq(vr.getCrNumber() + " approved"), eq(99L));

        assertThat(recordedEvents).anyMatch(e -> e.getAction().equals("CLIENT_APPROVED")
                && e.getDetail() != null && e.getDetail().contains(baselineUuid.toString()));
    }

    @Test
    @DisplayName("schedule failure does not rollback commercial lock and records failure event")
    void clientApprove_scheduleFailure_commercialStillApproved() {
        auth(Role.QS, 5L);
        VariationUpsertRequest req = new VariationUpsertRequest();
        req.setTitle("CR Schedule Failure");
        req.setApplyScheduleOnApproval(true);
        req.setProposedDelayDays(2);
        VariationUpsertRequest.LineRequest line = new VariationUpsertRequest.LineRequest();
        line.setLineType(com.fitouts.variation.domain.VariationLineType.LUMP_SUM);
        line.setQuantity(BigDecimal.ONE);
        line.setSellRate(new BigDecimal("10000"));
        line.setCostRate(new BigDecimal("4000"));
        req.setLines(List.of(line));
        var created = service.create(projectId, req);

        VariationRequest vr = store.get(0);
        vr.setStatus(VariationStatus.ISSUED_TO_CLIENT);
        vr.setSellDelta(new BigDecimal("10000"));
        vr.setCostDelta(new BigDecimal("4000"));

        when(scheduleService.createBaselineForSystem(any(), any(), any()))
                .thenThrow(new RuntimeException("CPM solver failed unexpectedly"));

        auth(Role.CLIENT, 99L);
        var approved = service.clientApprove(projectId, created.getUuid());

        assertThat(approved.getStatus()).isEqualTo(VariationStatus.APPROVED);
        assertThat(approved.getLockedAt()).isNotNull();
        assertThat(commercial.getCurrentContractValue()).isEqualByComparingTo("1010000");

        assertThat(recordedEvents).anyMatch(e -> e.getAction().equals("SCHEDULE_REBASELINE_FAILED")
                && e.getDetail() != null && e.getDetail().contains("CPM solver failed unexpectedly"));
    }

    @Test
    @DisplayName("approved variation cannot be edited")
    void lockedCannotEdit() {
        auth(Role.QS, 5L);
        VariationUpsertRequest req = new VariationUpsertRequest();
        req.setTitle("CR");
        var created = service.create(projectId, req);
        VariationRequest vr = store.get(0);
        vr.setStatus(VariationStatus.APPROVED);
        vr.setLockedAt(java.time.OffsetDateTime.now());

        VariationUpsertRequest update = new VariationUpsertRequest();
        update.setTitle("Changed");
        assertThatThrownBy(() -> service.update(projectId, created.getUuid(), update))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("locked");
    }
}
