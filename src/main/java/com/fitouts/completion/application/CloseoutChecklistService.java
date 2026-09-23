package com.fitouts.completion.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.completion.api.CloseoutCarrySnagsRequest;
import com.fitouts.completion.api.CloseoutChecklistResponse;
import com.fitouts.completion.api.CloseoutChecklistResponse.Entry;
import com.fitouts.completion.api.CloseoutChecklistResponse.Item;
import com.fitouts.completion.api.CloseoutConfirmRequest;
import com.fitouts.completion.domain.CommercialLifecycleStage;
import com.fitouts.completion.domain.ProjectCloseoutCarriedSnag;
import com.fitouts.completion.domain.ProjectCloseoutCarriedSnagRepository;
import com.fitouts.completion.domain.ProjectCloseoutChecklist;
import com.fitouts.completion.domain.ProjectCloseoutChecklistRepository;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.snag.domain.Snag;
import com.fitouts.snag.domain.SnagRepository;
import com.fitouts.snag.domain.SnagStatus;
import com.fitouts.variation.domain.VariationRequest;
import com.fitouts.variation.domain.VariationRequestRepository;
import com.fitouts.variation.domain.VariationStatus;

@Service
public class CloseoutChecklistService {

    public static final String KEY_VARIATIONS = "VARIATIONS";
    public static final String KEY_SNAGS = "SNAGS";
    public static final String KEY_FINAL_INVOICE = "FINAL_INVOICE";
    public static final String KEY_ACCOUNTING = "ACCOUNTING";

    private static final Set<VariationStatus> TERMINAL_VARIATION_STATUSES =
            EnumSet.of(VariationStatus.APPROVED, VariationStatus.REJECTED);

    private final ProjectService projectService;
    private final VariationRequestRepository variationRepository;
    private final SnagRepository snagRepository;
    private final ProjectCloseoutChecklistRepository checklistRepository;
    private final ProjectCloseoutCarriedSnagRepository carriedSnagRepository;
    private final CommercialLifecycleService commercialLifecycleService;

    public CloseoutChecklistService(
            ProjectService projectService,
            VariationRequestRepository variationRepository,
            SnagRepository snagRepository,
            ProjectCloseoutChecklistRepository checklistRepository,
            ProjectCloseoutCarriedSnagRepository carriedSnagRepository,
            @Lazy CommercialLifecycleService commercialLifecycleService) {
        this.projectService = projectService;
        this.variationRepository = variationRepository;
        this.snagRepository = snagRepository;
        this.checklistRepository = checklistRepository;
        this.carriedSnagRepository = carriedSnagRepository;
        this.commercialLifecycleService = commercialLifecycleService;
    }

    @Transactional(readOnly = true)
    public CloseoutChecklistResponse get(Long projectId) {
        requireStaff();
        Project project = projectService.getById(projectId);
        return build(project);
    }

    @Transactional
    public CloseoutChecklistResponse confirmFinalInvoice(Long projectId, CloseoutConfirmRequest request) {
        AuthPrincipal principal = requireStaff();
        commercialLifecycleService.assertCommercialMutable(projectId);
        Project project = projectService.getById(projectId);
        ProjectCloseoutChecklist checklist = getOrCreate(project);
        applyConfirmation(request, true, principal, checklist);
        checklistRepository.save(checklist);
        return build(project);
    }

    @Transactional
    public CloseoutChecklistResponse confirmAccounting(Long projectId, CloseoutConfirmRequest request) {
        AuthPrincipal principal = requireStaff();
        commercialLifecycleService.assertCommercialMutable(projectId);
        Project project = projectService.getById(projectId);
        ProjectCloseoutChecklist checklist = getOrCreate(project);
        applyConfirmation(request, false, principal, checklist);
        checklistRepository.save(checklist);
        return build(project);
    }

    @Transactional
    public CloseoutChecklistResponse carrySnags(Long projectId, CloseoutCarrySnagsRequest request) {
        AuthPrincipal principal = requireStaff();
        commercialLifecycleService.assertCommercialMutable(projectId);
        Project project = projectService.getById(projectId);
        UUID companyId = CompanyContext.get();
        List<Snag> snags = snagRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), companyId);
        Set<UUID> requested = new LinkedHashSet<>();
        if (request != null && request.getSnagUuids() != null) {
            for (UUID uuid : request.getSnagUuids()) {
                if (uuid != null) requested.add(uuid);
            }
        }

        for (UUID snagUuid : requested) {
            Snag snag = snags.stream()
                    .filter(s -> snagUuid.equals(s.getUuid()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Snag is not on this project: " + snagUuid));
            if (snag.getStatus() == SnagStatus.CLOSED) {
                throw new BadRequestException("Closed snags cannot be carried forward: " + snag.getTitle());
            }
        }

        ProjectCloseoutChecklist checklist = getOrCreate(project);
        carriedSnagRepository.deleteByChecklistUuid(checklist.getUuid());
        OffsetDateTime now = OffsetDateTime.now();
        for (UUID snagUuid : requested) {
            ProjectCloseoutCarriedSnag row = new ProjectCloseoutCarriedSnag();
            row.setChecklistUuid(checklist.getUuid());
            row.setSnagUuid(snagUuid);
            row.setCarriedBy(principal.getAccountId());
            row.setCarriedAt(now);
            carriedSnagRepository.save(row);
        }
        return build(project);
    }

    private void applyConfirmation(
            CloseoutConfirmRequest request,
            boolean finalInvoice,
            AuthPrincipal principal,
            ProjectCloseoutChecklist checklist) {
        boolean confirmed = request == null || request.getConfirmed() == null || Boolean.TRUE.equals(request.getConfirmed());
        OffsetDateTime at = confirmed ? OffsetDateTime.now() : null;
        Long by = confirmed ? principal.getAccountId() : null;
        if (finalInvoice) {
            checklist.setFinalInvoiceConfirmedAt(at);
            checklist.setFinalInvoiceConfirmedBy(by);
        } else {
            checklist.setAccountingSyncedAt(at);
            checklist.setAccountingSyncedBy(by);
        }
    }

    private CloseoutChecklistResponse build(Project project) {
        UUID companyId = CompanyContext.get();
        ProjectCloseoutChecklist checklist = checklistRepository
                .findByProjectIdAndCompanyId(project.getId(), companyId)
                .orElse(null);
        Set<UUID> carried = carriedSnagIds(checklist);

        List<VariationRequest> variations = variationRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), companyId);
        List<Snag> snags = snagRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), companyId);

        Item variationsItem = variationsItem(variations);
        Item snagsItem = snagsItem(snags, carried);
        Item finalInvoiceItem = manualItem(
                KEY_FINAL_INVOICE,
                "Final invoice has been issued",
                checklist != null && checklist.isFinalInvoiceConfirmed(),
                checklist != null ? checklist.getFinalInvoiceConfirmedAt() : null,
                checklist != null ? checklist.getFinalInvoiceConfirmedBy() : null,
                "No final-invoice record exists in billing yet. Confirm once the final invoice has been issued.",
                "Final invoice has been marked as issued.");
        Item accountingItem = manualItem(
                KEY_ACCOUNTING,
                "Accounting has been synchronized",
                checklist != null && checklist.isAccountingSynced(),
                checklist != null ? checklist.getAccountingSyncedAt() : null,
                checklist != null ? checklist.getAccountingSyncedBy() : null,
                "No accounting integration exists yet. Confirm once project figures have been synchronized.",
                "Accounting has been marked as synchronized.");

        List<Item> items = List.of(variationsItem, snagsItem, finalInvoiceItem, accountingItem);
        long satisfiedCount = items.stream().filter(Item::isSatisfied).count();
        int outstandingCount = items.size() - (int) satisfiedCount;
        boolean allSatisfied = outstandingCount == 0;
        CommercialLifecycleStage stage = commercialLifecycleService.resolveStage(checklist, allSatisfied);
        return CloseoutChecklistResponse.builder()
                .projectId(project.getId())
                .allSatisfied(allSatisfied)
                .satisfiedCount((int) satisfiedCount)
                .outstandingCount(outstandingCount)
                .summary(buildSummary(outstandingCount))
                .items(items)
                .commercialStage(stage)
                .archiveEligible(commercialLifecycleService.isArchiveEligible(checklist))
                .commerciallyClosedAt(checklist != null ? checklist.getCommerciallyClosedAt() : null)
                .commerciallyClosedBy(checklist != null ? checklist.getCommerciallyClosedBy() : null)
                .dlpStartDate(checklist != null ? checklist.getDlpStartDate() : null)
                .dlpEndDate(checklist != null ? checklist.getDlpEndDate() : null)
                .dlpDurationMonths(checklist != null ? checklist.getDlpDurationMonths() : null)
                .archivedAt(checklist != null ? checklist.getArchivedAt() : null)
                .archivedBy(checklist != null ? checklist.getArchivedBy() : null)
                .build();
    }

    /** Internal checklist readiness without staff auth (used by commercial lifecycle enrichment). */
    @Transactional(readOnly = true)
    public boolean isAllSatisfied(Project project) {
        UUID companyId = CompanyContext.get();
        ProjectCloseoutChecklist checklist = checklistRepository
                .findByProjectIdAndCompanyId(project.getId(), companyId)
                .orElse(null);
        Set<UUID> carried = carriedSnagIds(checklist);
        List<VariationRequest> variations = variationRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), companyId);
        List<Snag> snags = snagRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), companyId);
        boolean variationsOk = variations.stream()
                .allMatch(v -> TERMINAL_VARIATION_STATUSES.contains(v.getStatus()));
        boolean snagsOk = snags.stream()
                .filter(s -> s.getStatus() != SnagStatus.CLOSED)
                .allMatch(s -> carried.contains(s.getUuid()));
        boolean finalInvoiceOk = checklist != null && checklist.isFinalInvoiceConfirmed();
        boolean accountingOk = checklist != null && checklist.isAccountingSynced();
        return variationsOk && snagsOk && finalInvoiceOk && accountingOk;
    }

    private Item variationsItem(List<VariationRequest> variations) {
        List<VariationRequest> open = variations.stream()
                .filter(v -> !TERMINAL_VARIATION_STATUSES.contains(v.getStatus()))
                .toList();
        boolean satisfied = open.isEmpty();
        String detail;
        if (variations.isEmpty()) {
            detail = "There are no client contract variations on this project.";
        } else if (satisfied) {
            detail = "All " + variations.size() + " client contract variation"
                    + (variations.size() == 1 ? " is" : "s are")
                    + " approved or rejected.";
        } else {
            detail = open.size() + " client contract variation"
                    + (open.size() == 1 ? " is" : "s are")
                    + " still open.";
        }
        List<Entry> entries = open.stream()
                .map(v -> Entry.builder()
                        .uuid(v.getUuid())
                        .reference(v.getCrNumber())
                        .title(v.getTitle())
                        .status(v.getStatus() != null ? v.getStatus().name() : null)
                        .carriedForward(false)
                        .build())
                .toList();
        return Item.builder()
                .key(KEY_VARIATIONS)
                .title("Variations are closed")
                .evaluation("AUTOMATIC")
                .satisfied(satisfied)
                .detail(detail)
                .outstandingCount(open.size())
                .totalCount(variations.size())
                .carriedForwardCount(0)
                .entries(entries)
                .build();
    }

    private Item snagsItem(List<Snag> snags, Set<UUID> carried) {
        List<Snag> notClosed = snags.stream()
                .filter(s -> s.getStatus() != SnagStatus.CLOSED)
                .toList();
        List<Snag> outstanding = notClosed.stream()
                .filter(s -> !carried.contains(s.getUuid()))
                .toList();
        List<Snag> carriedOpen = notClosed.stream()
                .filter(s -> carried.contains(s.getUuid()))
                .toList();
        boolean satisfied = outstanding.isEmpty();
        String detail;
        if (snags.isEmpty()) {
            detail = "There are no snags on this project.";
        } else if (satisfied && carriedOpen.isEmpty()) {
            detail = "All " + snags.size() + " snag"
                    + (snags.size() == 1 ? " is" : "s are")
                    + " closed.";
        } else if (satisfied) {
            detail = "Remaining snags have been carried forward to the defects period.";
        } else {
            detail = outstanding.size() + " snag"
                    + (outstanding.size() == 1 ? " is" : "s are")
                    + " still open and not carried forward.";
        }
        List<Entry> entries = new ArrayList<>();
        for (Snag snag : notClosed) {
            entries.add(Entry.builder()
                    .uuid(snag.getUuid())
                    .title(snag.getTitle())
                    .status(snag.getStatus() != null ? snag.getStatus().name() : null)
                    .carriedForward(carried.contains(snag.getUuid()))
                    .build());
        }
        return Item.builder()
                .key(KEY_SNAGS)
                .title("Snags are closed or carried forward")
                .evaluation("AUTOMATIC")
                .satisfied(satisfied)
                .detail(detail)
                .outstandingCount(outstanding.size())
                .totalCount(snags.size())
                .carriedForwardCount(carriedOpen.size())
                .entries(entries)
                .build();
    }

    private Item manualItem(
            String key,
            String title,
            boolean satisfied,
            OffsetDateTime confirmedAt,
            Long confirmedBy,
            String outstandingDetail,
            String satisfiedDetail) {
        return Item.builder()
                .key(key)
                .title(title)
                .evaluation("MANUAL")
                .satisfied(satisfied)
                .detail(satisfied ? satisfiedDetail : outstandingDetail)
                .outstandingCount(satisfied ? 0 : 1)
                .totalCount(1)
                .carriedForwardCount(0)
                .confirmedAt(confirmedAt)
                .confirmedBy(confirmedBy)
                .entries(List.of())
                .build();
    }

    private Set<UUID> carriedSnagIds(ProjectCloseoutChecklist checklist) {
        if (checklist == null) return Set.of();
        return carriedSnagRepository.findByChecklistUuid(checklist.getUuid()).stream()
                .map(ProjectCloseoutCarriedSnag::getSnagUuid)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private ProjectCloseoutChecklist getOrCreate(Project project) {
        UUID companyId = CompanyContext.get();
        return checklistRepository.findByProjectIdAndCompanyId(project.getId(), companyId)
                .orElseGet(() -> {
                    ProjectCloseoutChecklist created = new ProjectCloseoutChecklist();
                    created.setProjectId(project.getId());
                    created.setCompanyId(companyId);
                    return checklistRepository.save(created);
                });
    }

    public static String buildSummary(int outstandingCount) {
        if (outstandingCount <= 0) {
            return "This project is ready to close. All close-out requirements have been satisfied.";
        }
        if (outstandingCount == 1) {
            return "This project is almost ready to close, but there is still one thing that needs attention.";
        }
        return "This project is almost ready to close, but there are still "
                + numberWord(outstandingCount)
                + " things that need attention.";
    }

    private static String numberWord(int value) {
        return switch (value) {
            case 2 -> "two";
            case 3 -> "three";
            case 4 -> "four";
            default -> String.valueOf(value);
        };
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
}
