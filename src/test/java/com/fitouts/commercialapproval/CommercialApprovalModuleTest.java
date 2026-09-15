package com.fitouts.commercialapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.commercialapproval.api.TaskDecisionRequest;
import com.fitouts.commercialapproval.application.CommercialApprovalService;
import com.fitouts.commercialapproval.domain.ApprovalStepMode;
import com.fitouts.commercialapproval.domain.CommercialApprovalBand;
import com.fitouts.commercialapproval.domain.CommercialApprovalBandRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalEventRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalMatrix;
import com.fitouts.commercialapproval.domain.CommercialApprovalMatrixRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalRun;
import com.fitouts.commercialapproval.domain.CommercialApprovalRunRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalRunStatus;
import com.fitouts.commercialapproval.domain.CommercialApprovalStep;
import com.fitouts.commercialapproval.domain.CommercialApprovalStepRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalStepRole;
import com.fitouts.commercialapproval.domain.CommercialApprovalStepRoleRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalTask;
import com.fitouts.commercialapproval.domain.CommercialApprovalTaskRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalTaskStatus;
import com.fitouts.commercialapproval.domain.CommercialEventType;
import com.fitouts.notification.application.NotificationService;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;

class CommercialApprovalModuleTest {

    private CommercialApprovalMatrixRepository matrixRepository;
    private CommercialApprovalBandRepository bandRepository;
    private CommercialApprovalStepRepository stepRepository;
    private CommercialApprovalStepRoleRepository stepRoleRepository;
    private CommercialApprovalRunRepository runRepository;
    private CommercialApprovalTaskRepository taskRepository;
    private CommercialApprovalEventRepository eventRepository;
    private AccountRepository accountRepository;
    private NotificationService notificationService;
    private CommercialApprovalService service;

    private final UUID companyId = UUID.randomUUID();
    private final List<CommercialApprovalRun> runs = new ArrayList<>();
    private final List<CommercialApprovalTask> tasks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        matrixRepository = mock(CommercialApprovalMatrixRepository.class);
        bandRepository = mock(CommercialApprovalBandRepository.class);
        stepRepository = mock(CommercialApprovalStepRepository.class);
        stepRoleRepository = mock(CommercialApprovalStepRoleRepository.class);
        runRepository = mock(CommercialApprovalRunRepository.class);
        taskRepository = mock(CommercialApprovalTaskRepository.class);
        eventRepository = mock(CommercialApprovalEventRepository.class);
        accountRepository = mock(AccountRepository.class);
        notificationService = mock(NotificationService.class);

        service = new CommercialApprovalService(
                matrixRepository, bandRepository, stepRepository, stepRoleRepository,
                runRepository, taskRepository, eventRepository, accountRepository,
                notificationService, List.of());

        when(runRepository.save(any())).thenAnswer(inv -> {
            CommercialApprovalRun r = inv.getArgument(0);
            if (r.getUuid() == null) r.setUuid(UUID.randomUUID());
            runs.removeIf(x -> x.getUuid().equals(r.getUuid()));
            runs.add(r);
            return r;
        });
        when(runRepository.findById(any())).thenAnswer(inv ->
                runs.stream().filter(r -> r.getUuid().equals(inv.getArgument(0))).findFirst());
        when(taskRepository.save(any())).thenAnswer(inv -> {
            CommercialApprovalTask t = inv.getArgument(0);
            if (t.getUuid() == null) t.setUuid(UUID.randomUUID());
            tasks.removeIf(x -> x.getUuid().equals(t.getUuid()));
            tasks.add(t);
            return t;
        });
        when(taskRepository.findById(any())).thenAnswer(inv ->
                tasks.stream().filter(t -> t.getUuid().equals(inv.getArgument(0))).findFirst());
        when(taskRepository.findByRunUuidOrderByStepOrderAsc(any())).thenAnswer(inv ->
                tasks.stream().filter(t -> t.getRunUuid().equals(inv.getArgument(0)))
                        .sorted((a, b) -> Integer.compare(a.getStepOrder(), b.getStepOrder()))
                        .toList());
        when(taskRepository.findByRunUuidAndStepOrder(any(), any(Integer.class))).thenAnswer(inv ->
                tasks.stream().filter(t -> t.getRunUuid().equals(inv.getArgument(0))
                                && t.getStepOrder() == (Integer) inv.getArgument(1))
                        .toList());
        when(taskRepository.findByRunUuidAndStatus(any(), any())).thenAnswer(inv ->
                tasks.stream().filter(t -> t.getRunUuid().equals(inv.getArgument(0))
                                && t.getStatus() == inv.getArgument(1))
                        .toList());
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.findAllByCompanyUuidAndRole(any(), any())).thenReturn(List.of());
        when(runRepository.findFirstByEventTypeAndEntityUuidAndStatusOrderByStartedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    private void auth(Role role) {
        AuthPrincipal principal = AuthPrincipal.builder()
                .accountId(10L)
                .companyId(companyId)
                .email(role.name().toLowerCase() + "@test")
                .roles(Set.of(role))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        CompanyContext.set(companyId);
    }

    private void seedSequentialMatrix() {
        UUID matrixUuid = UUID.randomUUID();
        UUID bandUuid = UUID.randomUUID();
        UUID step1 = UUID.randomUUID();
        UUID step2 = UUID.randomUUID();

        CommercialApprovalMatrix matrix = new CommercialApprovalMatrix();
        matrix.setUuid(matrixUuid);
        matrix.setCompanyId(companyId);
        matrix.setEventType(CommercialEventType.VARIATION);
        matrix.setName("Test");
        matrix.setActive(true);

        when(matrixRepository.findFirstByCompanyIdAndEventTypeAndActiveTrueOrderByCreatedAtDesc(
                companyId, CommercialEventType.VARIATION)).thenReturn(Optional.of(matrix));

        CommercialApprovalBand band = new CommercialApprovalBand();
        band.setUuid(bandUuid);
        band.setMatrixUuid(matrixUuid);
        band.setMinAmount(BigDecimal.ZERO);
        band.setMaxAmount(null);
        band.setSortOrder(0);
        when(bandRepository.findByMatrixUuidOrderBySortOrderAscMinAmountAsc(matrixUuid))
                .thenReturn(List.of(band));

        CommercialApprovalStep s1 = new CommercialApprovalStep();
        s1.setUuid(step1);
        s1.setBandUuid(bandUuid);
        s1.setStepOrder(1);
        s1.setMode(ApprovalStepMode.SEQUENTIAL);
        s1.setSlaHours(24);
        CommercialApprovalStep s2 = new CommercialApprovalStep();
        s2.setUuid(step2);
        s2.setBandUuid(bandUuid);
        s2.setStepOrder(2);
        s2.setMode(ApprovalStepMode.SEQUENTIAL);
        s2.setSlaHours(24);
        when(stepRepository.findByBandUuidOrderByStepOrderAsc(bandUuid)).thenReturn(List.of(s1, s2));
        when(stepRepository.findById(step1)).thenReturn(Optional.of(s1));
        when(stepRepository.findById(step2)).thenReturn(Optional.of(s2));

        CommercialApprovalStepRole r1 = new CommercialApprovalStepRole();
        r1.setUuid(UUID.randomUUID());
        r1.setStepUuid(step1);
        r1.setRole(Role.SENIOR_QS);
        CommercialApprovalStepRole r2 = new CommercialApprovalStepRole();
        r2.setUuid(UUID.randomUUID());
        r2.setStepUuid(step2);
        r2.setRole(Role.PROJECT_MANAGER);
        when(stepRoleRepository.findByStepUuid(step1)).thenReturn(List.of(r1));
        when(stepRoleRepository.findByStepUuid(step2)).thenReturn(List.of(r2));
    }

    @Test
    @DisplayName("sequential matrix requires both steps before APPROVED")
    void sequentialTwoStep() {
        seedSequentialMatrix();
        auth(Role.ADMIN);

        UUID entity = UUID.randomUUID();
        CommercialApprovalRun run = service.startRun(
                CommercialEventType.VARIATION, entity, 1L, new BigDecimal("75000"), 10L)
                .orElseThrow();

        assertThat(run.getStatus()).isEqualTo(CommercialApprovalRunStatus.IN_PROGRESS);
        assertThat(tasks).hasSize(2);
        assertThat(tasks.stream().filter(t -> t.getStatus() == CommercialApprovalTaskStatus.PENDING)).hasSize(1);
        assertThat(tasks.stream().filter(t -> t.getStatus() == CommercialApprovalTaskStatus.WAITING)).hasSize(1);

        CommercialApprovalTask qsTask = tasks.stream()
                .filter(t -> t.getRole() == Role.SENIOR_QS).findFirst().orElseThrow();
        auth(Role.SENIOR_QS);
        service.approveTask(qsTask.getUuid(), new TaskDecisionRequest());

        assertThat(runs.get(0).getStatus()).isEqualTo(CommercialApprovalRunStatus.IN_PROGRESS);
        assertThat(runs.get(0).getCurrentStepOrder()).isEqualTo(2);

        CommercialApprovalTask pmTask = tasks.stream()
                .filter(t -> t.getRole() == Role.PROJECT_MANAGER).findFirst().orElseThrow();
        assertThat(pmTask.getStatus()).isEqualTo(CommercialApprovalTaskStatus.PENDING);

        auth(Role.PROJECT_MANAGER);
        service.approveTask(pmTask.getUuid(), new TaskDecisionRequest());
        assertThat(runs.get(0).getStatus()).isEqualTo(CommercialApprovalRunStatus.APPROVED);
    }

    @Test
    @DisplayName("reject fails the whole run")
    void rejectFailsRun() {
        seedSequentialMatrix();
        auth(Role.ADMIN);
        UUID entity = UUID.randomUUID();
        service.startRun(CommercialEventType.VARIATION, entity, 1L, new BigDecimal("10000"), 10L);

        CommercialApprovalTask qsTask = tasks.stream()
                .filter(t -> t.getRole() == Role.SENIOR_QS).findFirst().orElseThrow();
        auth(Role.SENIOR_QS);
        TaskDecisionRequest req = new TaskDecisionRequest();
        req.setComment("too expensive");
        service.rejectTask(qsTask.getUuid(), req);
        assertThat(runs.get(0).getStatus()).isEqualTo(CommercialApprovalRunStatus.REJECTED);
    }

    @Test
    @DisplayName("reject without comment is rejected")
    void rejectRequiresComment() {
        seedSequentialMatrix();
        auth(Role.ADMIN);
        service.startRun(CommercialEventType.VARIATION, UUID.randomUUID(), 1L, BigDecimal.TEN, 10L);
        CommercialApprovalTask qsTask = tasks.stream()
                .filter(t -> t.getRole() == Role.SENIOR_QS).findFirst().orElseThrow();
        auth(Role.SENIOR_QS);
        assertThatThrownBy(() -> service.rejectTask(qsTask.getUuid(), new TaskDecisionRequest()))
                .isInstanceOf(BadRequestException.class);
    }
}
