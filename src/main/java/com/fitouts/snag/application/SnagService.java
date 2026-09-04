package com.fitouts.snag.application;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.roomcollab.domain.ProjectRoom;
import com.fitouts.roomcollab.domain.ProjectRoomRepository;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.snag.api.SnagRequest;
import com.fitouts.snag.api.SnagResponse;
import com.fitouts.snag.api.SnagStatusRequest;
import com.fitouts.snag.domain.Snag;
import com.fitouts.snag.domain.SnagRepository;
import com.fitouts.snag.domain.SnagSeverity;
import com.fitouts.snag.domain.SnagStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SnagService {

    private static final Set<SnagStatus> OPEN_TO = EnumSet.of(
            SnagStatus.IN_PROGRESS, SnagStatus.READY_FOR_INSPECTION, SnagStatus.RESOLVED, SnagStatus.CLOSED);
    private static final Set<SnagStatus> IN_PROGRESS_TO = EnumSet.of(
            SnagStatus.READY_FOR_INSPECTION, SnagStatus.RESOLVED, SnagStatus.CLOSED, SnagStatus.OPEN);
    private static final Set<SnagStatus> READY_FOR_INSPECTION_TO = EnumSet.of(
            SnagStatus.RESOLVED, SnagStatus.IN_PROGRESS, SnagStatus.CLOSED);
    private static final Set<SnagStatus> RESOLVED_TO = EnumSet.of(SnagStatus.CLOSED, SnagStatus.IN_PROGRESS);
    private static final Set<SnagStatus> CLOSED_TO = EnumSet.of(SnagStatus.OPEN, SnagStatus.IN_PROGRESS);

    private final SnagRepository snagRepository;
    private final ProjectService projectService;
    private final FileStorageService fileStorageService;
    private final ProjectRoomRepository projectRoomRepository;
    private final ScheduleActivityRepository scheduleActivityRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<SnagResponse> list(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        return snagRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), CompanyContext.get())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SnagResponse get(Long projectId, UUID uuid) {
        requireStaff();
        requireProject(projectId);
        return toResponse(requireSnag(uuid, projectId));
    }

    @Transactional
    public SnagResponse create(Long projectId, SnagRequest request) {
        AuthPrincipal principal = requireStaff();
        return createInternal(projectId, request, principal, false);
    }

    @Transactional
    public SnagResponse createByClient(Long projectId, SnagRequest request) {
        AuthPrincipal principal = requireClient();
        if (request == null) {
            request = new SnagRequest();
        }
        request.setClientVisible(true);
        return createInternal(projectId, request, principal, true);
    }

    private SnagResponse createInternal(Long projectId, SnagRequest request, AuthPrincipal principal, boolean byClient) {
        Project project = requireProject(projectId);
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new BadRequestException("title is required");
        }

        Snag snag = new Snag();
        snag.setProjectId(project.getId());
        snag.setCompanyId(CompanyContext.get());
        snag.setTitle(request.getTitle().trim());
        snag.setDescription(request.getDescription());
        snag.setLocation(request.getLocation());
        applyRoomAndActivity(snag, project.getId(), request.getProjectRoomId(), request.getActivityUuid());
        fillLocationFromLinks(snag);
        snag.setPhotoPaths(request.getPhotoPaths());
        snag.setStatus(SnagStatus.OPEN);
        snag.setSeverity(request.getSeverity() != null ? request.getSeverity() : SnagSeverity.MEDIUM);
        snag.setDueDate(request.getDueDate());
        snag.setRaisedBy(principal.getAccountId());
        snag.setRaisedByClient(byClient);
        snag.setAssigneeAccountId(request.getAssigneeAccountId());
        snag.setClientVisible(byClient || Boolean.TRUE.equals(request.getClientVisible()));
        return toResponse(snagRepository.save(snag));
    }

    @Transactional
    public SnagResponse update(Long projectId, UUID uuid, SnagRequest request) {
        requireStaff();
        requireProject(projectId);
        Snag snag = requireSnag(uuid, projectId);
        if (request == null) {
            return toResponse(snag);
        }
        if (StringUtils.hasText(request.getTitle())) {
            snag.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            snag.setDescription(request.getDescription());
        }
        if (request.getLocation() != null) {
            snag.setLocation(request.getLocation());
        }
        if (request.getProjectRoomId() != null || request.getActivityUuid() != null) {
            applyRoomAndActivity(snag, projectId, request.getProjectRoomId(), request.getActivityUuid());
            if (!StringUtils.hasText(request.getLocation())) {
                fillLocationFromLinks(snag);
            }
        }
        if (request.getPhotoPaths() != null) {
            snag.setPhotoPaths(request.getPhotoPaths());
        }
        if (request.getSeverity() != null) {
            snag.setSeverity(request.getSeverity());
        }
        if (request.getDueDate() != null) {
            snag.setDueDate(request.getDueDate());
        }
        if (request.getAssigneeAccountId() != null) {
            snag.setAssigneeAccountId(request.getAssigneeAccountId() <= 0 ? null : request.getAssigneeAccountId());
        }
        if (request.getClientVisible() != null) {
            snag.setClientVisible(request.getClientVisible());
        }
        if (request.getStatus() != null && request.getStatus() != snag.getStatus()) {
            assertTransition(snag.getStatus(), request.getStatus());
            snag.setStatus(request.getStatus());
        }
        return toResponse(snagRepository.save(snag));
    }

    @Transactional
    public SnagResponse patchStatus(Long projectId, UUID uuid, SnagStatusRequest request) {
        requireStaff();
        requireProject(projectId);
        if (request == null || request.getStatus() == null) {
            throw new BadRequestException("status is required");
        }
        Snag snag = requireSnag(uuid, projectId);
        if (request.getStatus() != snag.getStatus()) {
            assertTransition(snag.getStatus(), request.getStatus());
            snag.setStatus(request.getStatus());
        }
        return toResponse(snagRepository.save(snag));
    }

    @Transactional
    public SnagResponse clientApprove(Long projectId, UUID uuid) {
        AuthPrincipal principal = requireClient();
        requireProject(projectId);
        Snag snag = requireClientVisibleSnag(uuid, projectId);
        if (snag.getStatus() != SnagStatus.READY_FOR_INSPECTION && snag.getStatus() != SnagStatus.RESOLVED) {
            throw new BadRequestException("Only snags ready for inspection can be approved by the client");
        }
        assertTransition(snag.getStatus(), SnagStatus.CLOSED);
        snag.setStatus(SnagStatus.CLOSED);
        snag.setClientApprovedAt(OffsetDateTime.now());
        snag.setClientApprovedBy(principal.getAccountId());
        snag.setClientVisible(true);
        return toResponse(snagRepository.save(snag));
    }

    @Transactional
    public SnagResponse uploadPhoto(Long projectId, UUID uuid, MultipartFile file) {
        AuthPrincipal principal = requireAuthenticated();
        Project project = requireProject(projectId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required");
        }
        Snag snag = requireSnag(uuid, projectId);
        boolean isStaff = principal.getRoles() == null
                || principal.getRoles().stream().anyMatch(r -> r != Role.CLIENT);
        if (!isStaff) {
            if (!snag.isClientVisible() || !snag.isRaisedByClient()
                    || !principal.getAccountId().equals(snag.getRaisedBy())) {
                throw new ForbiddenException("Not allowed to upload photos for this snag");
            }
        }
        String relativePath = fileStorageService.store(file, CompanyContext.get(), project.getId(), "snags");
        String existing = snag.getPhotoPaths();
        if (StringUtils.hasText(existing)) {
            snag.setPhotoPaths(existing + "," + relativePath);
        } else {
            snag.setPhotoPaths(relativePath);
        }
        return toResponse(snagRepository.save(snag));
    }

    @Transactional
    public void delete(Long projectId, UUID uuid) {
        requireStaff();
        requireProject(projectId);
        snagRepository.delete(requireSnag(uuid, projectId));
    }

    @Transactional(readOnly = true)
    public List<SnagResponse> listClientVisible(Long projectId) {
        requireAuthenticated();
        Project project = requireProject(projectId);
        return snagRepository
                .findByProjectIdAndCompanyIdAndClientVisibleTrueOrderByCreatedAtDesc(
                        project.getId(), CompanyContext.get())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void applyRoomAndActivity(Snag snag, Long projectId, UUID roomId, UUID activityUuid) {
        if (roomId != null) {
            ProjectRoom room = projectRoomRepository.findById(roomId)
                    .orElseThrow(() -> new BadRequestException("Room not found"));
            if (!projectId.equals(room.getProjectId()) || !CompanyContext.get().equals(room.getCompanyId())) {
                throw new BadRequestException("Room does not belong to this project");
            }
            snag.setProjectRoomId(room.getUuid());
        }
        if (activityUuid != null) {
            ScheduleActivity activity = scheduleActivityRepository.findById(activityUuid)
                    .orElseThrow(() -> new BadRequestException("Activity not found"));
            if (!projectId.equals(activity.getProjectId()) || !CompanyContext.get().equals(activity.getCompanyId())) {
                throw new BadRequestException("Activity does not belong to this project");
            }
            snag.setActivityUuid(activity.getUuid());
            if (activity.getProjectRoomId() != null && snag.getProjectRoomId() == null) {
                snag.setProjectRoomId(activity.getProjectRoomId());
            }
        }
    }

    private void fillLocationFromLinks(Snag snag) {
        if (StringUtils.hasText(snag.getLocation())) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (snag.getProjectRoomId() != null) {
            projectRoomRepository.findById(snag.getProjectRoomId()).ifPresent(room -> {
                if (StringUtils.hasText(room.getFloorLabel())) {
                    sb.append(room.getFloorLabel()).append(" / ");
                }
                sb.append(room.getName());
            });
        }
        if (snag.getActivityUuid() != null) {
            scheduleActivityRepository.findById(snag.getActivityUuid()).ifPresent(activity -> {
                if (!sb.isEmpty()) {
                    sb.append(" · ");
                }
                sb.append(activity.getName());
            });
        }
        if (!sb.isEmpty()) {
            snag.setLocation(sb.toString());
        }
    }

    private void assertTransition(SnagStatus from, SnagStatus to) {
        Set<SnagStatus> allowed = switch (from) {
            case OPEN -> OPEN_TO;
            case IN_PROGRESS -> IN_PROGRESS_TO;
            case READY_FOR_INSPECTION -> READY_FOR_INSPECTION_TO;
            case RESOLVED -> RESOLVED_TO;
            case CLOSED -> CLOSED_TO;
        };
        if (!allowed.contains(to)) {
            throw new BadRequestException("Invalid status transition from " + from + " to " + to);
        }
    }

    private Snag requireSnag(UUID uuid, Long projectId) {
        Snag snag = snagRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Snag not found"));
        if (!snag.getProjectId().equals(projectId)) {
            throw new BadRequestException("Snag does not belong to this project");
        }
        return snag;
    }

    private Snag requireClientVisibleSnag(UUID uuid, Long projectId) {
        Snag snag = requireSnag(uuid, projectId);
        if (!snag.isClientVisible()) {
            throw new ForbiddenException("Snag is not visible to client");
        }
        return snag;
    }

    private SnagResponse toResponse(Snag snag) {
        String roomName = null;
        if (snag.getProjectRoomId() != null) {
            roomName = projectRoomRepository.findById(snag.getProjectRoomId())
                    .map(r -> {
                        if (StringUtils.hasText(r.getFloorLabel())) {
                            return r.getFloorLabel() + " / " + r.getName();
                        }
                        return r.getName();
                    })
                    .orElse(null);
        }
        String activityName = null;
        if (snag.getActivityUuid() != null) {
            activityName = scheduleActivityRepository.findById(snag.getActivityUuid())
                    .map(ScheduleActivity::getName)
                    .orElse(null);
        }
        return SnagResponse.builder()
                .uuid(snag.getUuid())
                .projectId(snag.getProjectId())
                .companyId(snag.getCompanyId())
                .title(snag.getTitle())
                .description(snag.getDescription())
                .location(snag.getLocation())
                .projectRoomId(snag.getProjectRoomId())
                .roomName(roomName)
                .activityUuid(snag.getActivityUuid())
                .activityName(activityName)
                .photoPaths(snag.getPhotoPaths())
                .status(snag.getStatus())
                .severity(snag.getSeverity())
                .dueDate(snag.getDueDate())
                .raisedBy(snag.getRaisedBy())
                .raisedByName(displayName(snag.getRaisedBy()))
                .raisedByClient(snag.isRaisedByClient())
                .assigneeAccountId(snag.getAssigneeAccountId())
                .assigneeName(displayName(snag.getAssigneeAccountId()))
                .clientVisible(snag.isClientVisible())
                .clientApprovedAt(snag.getClientApprovedAt())
                .clientApprovedBy(snag.getClientApprovedBy())
                .clientApprovedByName(displayName(snag.getClientApprovedBy()))
                .createdAt(snag.getCreatedAt())
                .updatedAt(snag.getUpdatedAt())
                .build();
    }

    private String displayName(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findById(accountId)
                .map(this::accountLabel)
                .orElse(null);
    }

    private String accountLabel(Account account) {
        if (StringUtils.hasText(account.getFullName())) {
            return account.getFullName().trim();
        }
        return account.getEmail();
    }

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        UUID companyId = CompanyContext.get();
        if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }
        return project;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        return companyId;
    }

    private AuthPrincipal requireAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        return principal;
    }

    private AuthPrincipal requireStaff() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private AuthPrincipal requireClient() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() == null || principal.getRoles().stream().noneMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Client access required");
        }
        return principal;
    }
}
