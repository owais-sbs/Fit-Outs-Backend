package com.fitouts.billing;

import static org.assertj.core.api.Assertions.assertThat;
import com.fitouts.shared.error.BadRequestException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fitouts.account.domain.AccountRepository;
import com.fitouts.billing.application.BillingApprovalEventService;
import com.fitouts.billing.application.BillingPaymentEmailService;
import com.fitouts.billing.application.BillingService;
import com.fitouts.billing.domain.BillingMilestone;
import com.fitouts.billing.domain.BillingMilestoneRepository;
import com.fitouts.billing.domain.BillingStatus;
import com.fitouts.billing.domain.PaymentRequest;
import com.fitouts.billing.domain.PaymentRequestRepository;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.schedule.domain.ScheduleActivityRepository;

class BillingModuleTest {

    private BillingMilestoneRepository milestoneRepository;
    private PaymentRequestRepository paymentRequestRepository;
    private BillingApprovalEventService billingApprovalEventService;
    private ProjectService projectService;
    private ScheduleActivityRepository activityRepository;
    private BillingPaymentEmailService billingPaymentEmailService;
    private AccountRepository accountRepository;

    private BillingService billingService;

    private final UUID companyId = UUID.randomUUID();
    private final Long projectId = 100L;

    @BeforeEach
    void setUp() {
        milestoneRepository = mock(BillingMilestoneRepository.class);
        paymentRequestRepository = mock(PaymentRequestRepository.class);
        billingApprovalEventService = mock(BillingApprovalEventService.class);
        projectService = mock(ProjectService.class);
        activityRepository = mock(ScheduleActivityRepository.class);
        billingPaymentEmailService = mock(BillingPaymentEmailService.class);
        accountRepository = mock(AccountRepository.class);

        billingService = new BillingService(
                milestoneRepository,
                paymentRequestRepository,
                billingApprovalEventService,
                projectService,
                activityRepository,
                billingPaymentEmailService,
                accountRepository
        );
    }

    private PaymentRequest createPaymentRequest(UUID milestoneUuid, BillingStatus status, OffsetDateTime reminderSentAt) {
        PaymentRequest pr = new PaymentRequest();
        pr.setUuid(UUID.randomUUID());
        pr.setMilestoneUuid(milestoneUuid);
        pr.setProjectId(projectId);
        pr.setCompanyId(companyId);
        pr.setAmount(new BigDecimal("100.00"));
        pr.setStatus(status);
        pr.setReminderSentAt(reminderSentAt);
        return pr;
    }

    private BillingMilestone createMilestone(UUID uuid, String name, LocalDate dueDate, BillingStatus status) {
        BillingMilestone m = new BillingMilestone();
        m.setUuid(uuid);
        m.setProjectId(projectId);
        m.setCompanyId(companyId);
        m.setName(name);
        m.setAmount(new BigDecimal("100.00"));
        m.setDueDate(dueDate);
        m.setStatus(status);
        return m;
    }

    @Test
    @DisplayName("TEST A — 3 days before due date (due 12 Sep, today 9 Sep) -> reminder sent")
    void testA_threeDaysBeforeDueDate_eligible() {
        UUID milestoneUuid = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        LocalDate dueDate = today.plusDays(3); // reminderDate = today

        PaymentRequest pr = createPaymentRequest(milestoneUuid, BillingStatus.CLIENT_ACCEPTED, null);
        BillingMilestone milestone = createMilestone(milestoneUuid, "Payment 1", dueDate, BillingStatus.CLIENT_ACCEPTED);
        Project project = new Project();
        project.setId(projectId);

        when(paymentRequestRepository.findById(pr.getUuid())).thenReturn(Optional.of(pr));
        when(milestoneRepository.findByUuidAndCompanyId(milestoneUuid, companyId)).thenReturn(Optional.of(milestone));
        when(projectService.getById(projectId)).thenReturn(project);
        when(billingPaymentEmailService.notifyClient(project, milestone, pr))
                .thenReturn(BillingPaymentEmailService.SendResult.ok("client@example.com"));

        boolean sent = billingService.processSinglePaymentReminder(pr.getUuid());

        assertThat(sent).isTrue();
        assertThat(pr.getReminderSentAt()).isNotNull();
        verify(paymentRequestRepository).save(pr);
    }

    @Test
    @DisplayName("TEST B — Before reminder date (due 12 Sep, today 8 Sep) -> no reminder")
    void testB_beforeReminderDate_ineligible() {
        UUID milestoneUuid = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        LocalDate dueDate = today.plusDays(4); // reminderDate = today + 1 (future)

        PaymentRequest pr = createPaymentRequest(milestoneUuid, BillingStatus.CLIENT_ACCEPTED, null);
        BillingMilestone milestone = createMilestone(milestoneUuid, "Payment 2", dueDate, BillingStatus.CLIENT_ACCEPTED);

        when(paymentRequestRepository.findById(pr.getUuid())).thenReturn(Optional.of(pr));
        when(milestoneRepository.findByUuidAndCompanyId(milestoneUuid, companyId)).thenReturn(Optional.of(milestone));

        boolean sent = billingService.processSinglePaymentReminder(pr.getUuid());

        assertThat(sent).isFalse();
        assertThat(pr.getReminderSentAt()).isNull();
        verify(billingPaymentEmailService, never()).notifyClient(any(), any(), any());
    }

    @Test
    @DisplayName("TEST C — After reminder date / overdue (due 12 Sep, today 10 Sep) -> reminder sent if not sent")
    void testC_afterReminderDate_eligible() {
        UUID milestoneUuid = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        LocalDate dueDate = today.minusDays(2); // reminderDate = today - 5 (past)

        PaymentRequest pr = createPaymentRequest(milestoneUuid, BillingStatus.CLIENT_ACCEPTED, null);
        BillingMilestone milestone = createMilestone(milestoneUuid, "Overdue Payment", dueDate, BillingStatus.CLIENT_ACCEPTED);
        Project project = new Project();
        project.setId(projectId);

        when(paymentRequestRepository.findById(pr.getUuid())).thenReturn(Optional.of(pr));
        when(milestoneRepository.findByUuidAndCompanyId(milestoneUuid, companyId)).thenReturn(Optional.of(milestone));
        when(projectService.getById(projectId)).thenReturn(project);
        when(billingPaymentEmailService.notifyClient(project, milestone, pr))
                .thenReturn(BillingPaymentEmailService.SendResult.ok("client@example.com"));

        boolean sent = billingService.processSinglePaymentReminder(pr.getUuid());

        assertThat(sent).isTrue();
        assertThat(pr.getReminderSentAt()).isNotNull();
    }

    @Test
    @DisplayName("TEST D — Already PAID -> no reminder")
    void testD_alreadyPaid_noReminder() {
        UUID milestoneUuid = UUID.randomUUID();
        LocalDate dueDate = LocalDate.now().minusDays(10);

        PaymentRequest pr = createPaymentRequest(milestoneUuid, BillingStatus.PAID, null);
        BillingMilestone milestone = createMilestone(milestoneUuid, "Paid Payment", dueDate, BillingStatus.PAID);

        when(paymentRequestRepository.findById(pr.getUuid())).thenReturn(Optional.of(pr));
        when(milestoneRepository.findByUuidAndCompanyId(milestoneUuid, companyId)).thenReturn(Optional.of(milestone));

        boolean sent = billingService.processSinglePaymentReminder(pr.getUuid());

        assertThat(sent).isFalse();
        verify(billingPaymentEmailService, never()).notifyClient(any(), any(), any());
    }

    @Test
    @DisplayName("TEST E — Not client accepted (e.g. PENDING_DIRECTOR) -> no reminder")
    void testE_notClientAccepted_noReminder() {
        UUID milestoneUuid = UUID.randomUUID();
        LocalDate dueDate = LocalDate.now().plusDays(2);

        PaymentRequest pr = createPaymentRequest(milestoneUuid, BillingStatus.PENDING_DIRECTOR, null);
        BillingMilestone milestone = createMilestone(milestoneUuid, "Pending Director", dueDate, BillingStatus.PENDING_DIRECTOR);

        when(paymentRequestRepository.findById(pr.getUuid())).thenReturn(Optional.of(pr));

        boolean sent = billingService.processSinglePaymentReminder(pr.getUuid());

        assertThat(sent).isFalse();
        verify(billingPaymentEmailService, never()).notifyClient(any(), any(), any());
    }

    @Test
    @DisplayName("TEST F — Five independent payments in a project")
    void testF_fiveIndependentPaymentSlices() {
        LocalDate today = LocalDate.now();

        // P1: overdue -> eligible
        UUID m1Uuid = UUID.randomUUID();
        PaymentRequest pr1 = createPaymentRequest(m1Uuid, BillingStatus.CLIENT_ACCEPTED, null);
        BillingMilestone m1 = createMilestone(m1Uuid, "P1", today.minusDays(5), BillingStatus.CLIENT_ACCEPTED);

        // P2: future -> ineligible
        UUID m2Uuid = UUID.randomUUID();
        PaymentRequest pr2 = createPaymentRequest(m2Uuid, BillingStatus.CLIENT_ACCEPTED, null);
        BillingMilestone m2 = createMilestone(m2Uuid, "P2", today.plusDays(30), BillingStatus.CLIENT_ACCEPTED);

        // P3: PAID -> ineligible
        UUID m3Uuid = UUID.randomUUID();
        PaymentRequest pr3 = createPaymentRequest(m3Uuid, BillingStatus.PAID, null);
        BillingMilestone m3 = createMilestone(m3Uuid, "P3", today.minusDays(10), BillingStatus.PAID);

        // P4: due in 3 days -> eligible
        UUID m4Uuid = UUID.randomUUID();
        PaymentRequest pr4 = createPaymentRequest(m4Uuid, BillingStatus.CLIENT_ACCEPTED, null);
        BillingMilestone m4 = createMilestone(m4Uuid, "P4", today.plusDays(3), BillingStatus.CLIENT_ACCEPTED);

        // P5: future -> ineligible
        UUID m5Uuid = UUID.randomUUID();
        PaymentRequest pr5 = createPaymentRequest(m5Uuid, BillingStatus.CLIENT_ACCEPTED, null);
        BillingMilestone m5 = createMilestone(m5Uuid, "P5", today.plusDays(30), BillingStatus.CLIENT_ACCEPTED);

        Project project = new Project();
        project.setId(projectId);

        List<PaymentRequest> eligibleFromDb = List.of(pr1, pr2, pr4, pr5);
        when(paymentRequestRepository.findByStatusAndReminderSentAtIsNull(BillingStatus.CLIENT_ACCEPTED))
                .thenReturn(eligibleFromDb);

        when(paymentRequestRepository.findById(pr1.getUuid())).thenReturn(Optional.of(pr1));
        when(paymentRequestRepository.findById(pr2.getUuid())).thenReturn(Optional.of(pr2));
        when(paymentRequestRepository.findById(pr3.getUuid())).thenReturn(Optional.of(pr3));
        when(paymentRequestRepository.findById(pr4.getUuid())).thenReturn(Optional.of(pr4));
        when(paymentRequestRepository.findById(pr5.getUuid())).thenReturn(Optional.of(pr5));

        when(milestoneRepository.findByUuidAndCompanyId(m1Uuid, companyId)).thenReturn(Optional.of(m1));
        when(milestoneRepository.findByUuidAndCompanyId(m2Uuid, companyId)).thenReturn(Optional.of(m2));
        when(milestoneRepository.findByUuidAndCompanyId(m3Uuid, companyId)).thenReturn(Optional.of(m3));
        when(milestoneRepository.findByUuidAndCompanyId(m4Uuid, companyId)).thenReturn(Optional.of(m4));
        when(milestoneRepository.findByUuidAndCompanyId(m5Uuid, companyId)).thenReturn(Optional.of(m5));

        when(projectService.getById(projectId)).thenReturn(project);
        when(billingPaymentEmailService.notifyClient(any(), any(), any()))
                .thenReturn(BillingPaymentEmailService.SendResult.ok("client@example.com"));

        billingService.processAutomated3DayPaymentReminders();

        assertThat(pr1.getReminderSentAt()).isNotNull();
        assertThat(pr2.getReminderSentAt()).isNull();
        assertThat(pr3.getReminderSentAt()).isNull();
        assertThat(pr4.getReminderSentAt()).isNotNull();
        assertThat(pr5.getReminderSentAt()).isNull();
    }

    @Test
    @DisplayName("TEST G — Duplicate scheduler execution sends 1 email, not 2")
    void testG_duplicateSchedulerExecution_idempotent() {
        LocalDate today = LocalDate.now();
        UUID mUuid = UUID.randomUUID();
        PaymentRequest pr = createPaymentRequest(mUuid, BillingStatus.CLIENT_ACCEPTED, null);
        BillingMilestone m = createMilestone(mUuid, "P1", today.plusDays(1), BillingStatus.CLIENT_ACCEPTED);

        Project project = new Project();
        project.setId(projectId);

        List<PaymentRequest> dbCandidates = new ArrayList<>(List.of(pr));
        when(paymentRequestRepository.findByStatusAndReminderSentAtIsNull(BillingStatus.CLIENT_ACCEPTED))
                .thenAnswer(inv -> dbCandidates.stream().filter(p -> p.getReminderSentAt() == null).toList());

        when(paymentRequestRepository.findById(pr.getUuid())).thenReturn(Optional.of(pr));
        when(milestoneRepository.findByUuidAndCompanyId(mUuid, companyId)).thenReturn(Optional.of(m));
        when(projectService.getById(projectId)).thenReturn(project);
        when(billingPaymentEmailService.notifyClient(project, m, pr))
                .thenReturn(BillingPaymentEmailService.SendResult.ok("client@example.com"));

        // First run
        billingService.processAutomated3DayPaymentReminders();
        assertThat(pr.getReminderSentAt()).isNotNull();

        // Second run
        billingService.processAutomated3DayPaymentReminders();

        // Total email calls must be exactly 1
        verify(billingPaymentEmailService, times(1)).notifyClient(any(), any(), any());
    }

    @Test
    @DisplayName("TEST H & I — SMTP failure does NOT permanently record reminder sent; retry succeeds later")
    void testH_and_I_smtpFailureRetry() {
        LocalDate today = LocalDate.now();
        UUID mUuid = UUID.randomUUID();
        PaymentRequest pr = createPaymentRequest(mUuid, BillingStatus.CLIENT_ACCEPTED, null);
        BillingMilestone m = createMilestone(mUuid, "P1", today.plusDays(1), BillingStatus.CLIENT_ACCEPTED);

        Project project = new Project();
        project.setId(projectId);

        when(paymentRequestRepository.findById(pr.getUuid())).thenReturn(Optional.of(pr));
        when(milestoneRepository.findByUuidAndCompanyId(mUuid, companyId)).thenReturn(Optional.of(m));
        when(projectService.getById(projectId)).thenReturn(project);

        // TEST H: Force SMTP failure
        when(billingPaymentEmailService.notifyClient(project, m, pr))
                .thenReturn(BillingPaymentEmailService.SendResult.failed("client@example.com"));

        boolean resultH = billingService.processSinglePaymentReminder(pr.getUuid());

        assertThat(resultH).isFalse();
        assertThat(pr.getReminderSentAt()).isNull(); // Must NOT be recorded as sent!

        // TEST I: SMTP fixed on next run
        when(billingPaymentEmailService.notifyClient(project, m, pr))
                .thenReturn(BillingPaymentEmailService.SendResult.ok("client@example.com"));

        boolean resultI = billingService.processSinglePaymentReminder(pr.getUuid());

        assertThat(resultI).isTrue();
        assertThat(pr.getReminderSentAt()).isNotNull(); // Now successfully recorded!
    }

    @Test
    @DisplayName("TEST J — markPaid on ISSUED status throws exception (ISSUED -> PAID is impossible)")
    void testJ_markPaidOnIssued_throwsException() {
        UUID mUuid = UUID.randomUUID();
        PaymentRequest pr = createPaymentRequest(mUuid, BillingStatus.ISSUED, null);

        when(paymentRequestRepository.findByUuidAndCompanyId(any(), any())).thenReturn(Optional.of(pr));

        assertThatThrownBy(() -> billingService.markPaid(pr.getUuid()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only CLIENT_ACCEPTED or PART_PAID payment requests can be marked paid");
    }

    @Test
    @DisplayName("TEST K — markPaid on CLIENT_ACCEPTED status transitions to PAID")
    void testK_markPaidOnClientAccepted_succeeds() {
        UUID mUuid = UUID.randomUUID();
        PaymentRequest pr = createPaymentRequest(mUuid, BillingStatus.CLIENT_ACCEPTED, null);
        BillingMilestone m = createMilestone(mUuid, "P1", LocalDate.now(), BillingStatus.CLIENT_ACCEPTED);

        when(paymentRequestRepository.findByUuidAndCompanyId(any(), any())).thenReturn(Optional.of(pr));
        when(milestoneRepository.findByUuidAndCompanyId(any(), any())).thenReturn(Optional.of(m));
        when(paymentRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        billingService.markPaid(pr.getUuid());

        assertThat(pr.getStatus()).isEqualTo(BillingStatus.PAID);
        assertThat(m.getStatus()).isEqualTo(BillingStatus.PAID);
    }
}
