package com.fitouts.roomcollab.application;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqDocumentRepository;
import com.fitouts.boq.domain.BoqLine;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.roomcollab.api.*;
import com.fitouts.roomcollab.domain.*;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.shared.error.UnauthorizedException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RoomCollabService {

    private final ProjectService projectService;
    private final ProjectRoomRepository roomRepository;
    private final RoomTaskRepository taskRepository;
    private final RoomTaskFileVersionRepository versionRepository;
    private final RoomTaskMessageRepository taskMessageRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final RoomTaskEventRepository eventRepository;
    private final BoqDocumentRepository boqDocumentRepository;
    private final BoqLineRepository boqLineRepository;
    private final FileStorageService fileStorageService;

    public List<ProjectRoomResponse> listRooms(Long projectId) {
        Project project = requireProjectAccess(projectId);
        List<ProjectRoom> rooms = roomRepository.findByProjectIdOrderBySortOrderAscFloorLabelAscNameAsc(project.getId());
        return rooms.stream().map(this::mapRoom).toList();
    }

    public ProjectRoomResponse createRoom(Long projectId, ProjectRoomCreateRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProjectAccess(projectId);
        String floor = normalizeFloor(request.getFloorLabel());
        String name = request.getName().trim();
        roomRepository.findByProjectIdAndFloorLabelAndName(projectId, floor, name).ifPresent(r -> {
            throw new BadRequestException("Room already exists on this floor");
        });
        ProjectRoom room = new ProjectRoom();
        room.setProjectId(project.getId());
        room.setCompanyId(project.getCompanyId());
        room.setName(name);
        room.setFloorLabel(floor);
        room.setRoomTypeId(request.getRoomTypeId());
        room.setSource(RoomSource.MANUAL);
        room.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        return mapRoom(roomRepository.save(room));
    }

    public ProjectRoomResponse updateRoom(Long projectId, UUID roomId, ProjectRoomCreateRequest request) {
        requireStaff();
        ProjectRoom room = requireRoom(projectId, roomId);
        if (StringUtils.hasText(request.getName())) {
            room.setName(request.getName().trim());
        }
        if (request.getFloorLabel() != null) {
            room.setFloorLabel(normalizeFloor(request.getFloorLabel()));
        }
        if (request.getRoomTypeId() != null) {
            room.setRoomTypeId(request.getRoomTypeId());
        }
        if (request.getSortOrder() != null) {
            room.setSortOrder(request.getSortOrder());
        }
        return mapRoom(roomRepository.save(room));
    }

    public int syncRoomsFromBoq(Long projectId) {
        requireStaff();
        Project project = requireProjectAccess(projectId);
        List<BoqDocument> docs = boqDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        Set<String> seen = new HashSet<>();
        int created = 0;
        int order = 0;
        for (BoqDocument doc : docs) {
            List<BoqLine> lines = boqLineRepository.findByBoqIdOrderBySortOrderAsc(doc.getId());
            for (BoqLine line : lines) {
                String floor = normalizeFloor(line.getFloorLabel());
                String name = StringUtils.hasText(line.getRoomLabel()) ? line.getRoomLabel().trim() : null;
                if (!StringUtils.hasText(name)) continue;
                String key = floor + "|" + name.toLowerCase();
                if (!seen.add(key)) continue;
                var existing = roomRepository.findByProjectIdAndFloorLabelAndName(projectId, floor, name);
                if (existing.isEmpty()) {
                    ProjectRoom room = new ProjectRoom();
                    room.setProjectId(project.getId());
                    room.setCompanyId(project.getCompanyId());
                    room.setName(name);
                    room.setFloorLabel(floor);
                    room.setSource(RoomSource.BOQ);
                    room.setSortOrder(order++);
                    roomRepository.save(room);
                    created++;
                }
            }
        }
        return created;
    }

    /** Called from BoqService after survey/line saves. */
    public void syncRoomsFromBoqLines(Long projectId, UUID companyId, List<BoqLine> lines) {
        if (lines == null || lines.isEmpty()) return;
        Set<String> seen = new HashSet<>();
        int order = (int) roomRepository.findByProjectIdOrderBySortOrderAscFloorLabelAscNameAsc(projectId).size();
        for (BoqLine line : lines) {
            String floor = normalizeFloor(line.getFloorLabel());
            String name = StringUtils.hasText(line.getRoomLabel()) ? line.getRoomLabel().trim() : null;
            if (!StringUtils.hasText(name)) continue;
            String key = floor + "|" + name.toLowerCase();
            if (!seen.add(key)) continue;
            if (roomRepository.findByProjectIdAndFloorLabelAndName(projectId, floor, name).isEmpty()) {
                ProjectRoom room = new ProjectRoom();
                room.setProjectId(projectId);
                room.setCompanyId(companyId);
                room.setName(name);
                room.setFloorLabel(floor);
                room.setSource(RoomSource.BOQ);
                room.setSortOrder(order++);
                roomRepository.save(room);
            }
        }
    }

    public List<RoomTaskResponse> listTasksForRoom(Long projectId, UUID roomId) {
        requireProjectAccess(projectId);
        requireRoom(projectId, roomId);
        return taskRepository.findByProjectRoomIdOrderByCreatedAtDesc(roomId).stream()
                .map(t -> mapTask(t, false))
                .toList();
    }

    public List<RoomTaskResponse> listTasksForProject(Long projectId) {
        requireProjectAccess(projectId);
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(t -> mapTask(t, false))
                .toList();
    }

    public List<RoomTaskResponse> listPendingClientTasks(Long projectId) {
        requireProjectAccess(projectId);
        return taskRepository
                .findByProjectIdAndStatusOrderByUpdatedAtDesc(projectId, RoomTaskStatus.AWAITING_CLIENT)
                .stream()
                .map(t -> mapTask(t, true))
                .toList();
    }

    public RoomTaskResponse getTask(Long projectId, UUID taskId) {
        requireProjectAccess(projectId);
        RoomTask task = requireTask(projectId, taskId);
        return mapTask(task, true);
    }

    public RoomTaskResponse createTask(Long projectId, RoomTaskCreateRequest request) {
        AuthPrincipal principal = requireStaff();
        if (request.getProjectRoomId() == null || !StringUtils.hasText(request.getTitle())) {
            throw new BadRequestException("Room and title are required");
        }
        ProjectRoom room = requireRoom(projectId, request.getProjectRoomId());
        RoomTask task = new RoomTask();
        task.setProjectRoomId(room.getUuid());
        task.setProjectId(projectId);
        task.setCompanyId(room.getCompanyId());
        task.setTitle(request.getTitle().trim());
        task.setTaskType(request.getTaskType() != null ? request.getTaskType() : RoomTaskType.OTHER);
        if (task.getTaskType() == RoomTaskType.CUSTOM) {
            if (!StringUtils.hasText(request.getTypeLabel())) {
                throw new BadRequestException("Custom type label is required");
            }
            task.setTypeLabel(request.getTypeLabel().trim());
        } else if (StringUtils.hasText(request.getTypeLabel())) {
            task.setTypeLabel(request.getTypeLabel().trim());
        }
        task.setClientDeadline(request.getClientDeadline());
        task.setAssigneeAccountId(request.getAssigneeAccountId());
        task.setCreatedBy(principal.getAccountId());
        task.setStatus(RoomTaskStatus.OPEN);
        RoomTask saved = taskRepository.save(task);
        addEvent(saved.getUuid(), RoomTaskEventType.CREATED, principal.getAccountId(),
                "Task created: " + saved.getTitle(), null);
        return mapTask(saved, true);
    }

    public RoomTaskResponse updateTask(Long projectId, UUID taskId, RoomTaskUpdateRequest request) {
        AuthPrincipal principal = requireStaff();
        RoomTask task = requireTask(projectId, taskId);
        if (task.getStatus() == RoomTaskStatus.APPROVED || task.getStatus() == RoomTaskStatus.CLOSED) {
            throw new BadRequestException("Cannot update a closed/approved task");
        }
        if (StringUtils.hasText(request.getTitle())) {
            task.setTitle(request.getTitle().trim());
        }
        if (request.getAssigneeAccountId() != null) {
            task.setAssigneeAccountId(request.getAssigneeAccountId());
        }
        if (request.getClientDeadline() != null) {
            task.setClientDeadline(request.getClientDeadline());
            addEvent(task.getUuid(), RoomTaskEventType.DEADLINE_UPDATED, principal.getAccountId(),
                    "Deadline updated to " + request.getClientDeadline(), null);
        }
        return mapTask(taskRepository.save(task), true);
    }

    public RoomTaskFileVersionResponse uploadVersion(
            Long projectId, UUID taskId, MultipartFile file, String changeNotes) {
        AuthPrincipal principal = requirePrincipal();
        RoomTask task = requireTask(projectId, taskId);
        RoomTaskFileVersion saved = createVersionFromFile(task, projectId, file, changeNotes, principal);
        return mapVersion(saved, projectId);
    }

    public RoomTaskResponse submitToClient(Long projectId, UUID taskId) {
        AuthPrincipal principal = requireStaff();
        RoomTask task = requireTask(projectId, taskId);
        if (task.getStatus() == RoomTaskStatus.APPROVED || task.getStatus() == RoomTaskStatus.CLOSED) {
            throw new BadRequestException("Task is already closed");
        }
        var latest = versionRepository.findFirstByTaskIdOrderByVersionNoDesc(taskId)
                .orElseThrow(() -> new BadRequestException("Upload a file before submitting to client"));
        if (latest.getUploaderRole() != FileUploaderRole.STAFF) {
            throw new BadRequestException("Latest version must be from staff");
        }

        if (task.getFirstSentToClientAt() == null) {
            task.setFirstSentToClientAt(OffsetDateTime.now());
        }
        boolean resubmit = task.getStatus() == RoomTaskStatus.CHANGES_REQUESTED
                || task.getRevisionCount() > 0;
        task.setStatus(RoomTaskStatus.AWAITING_CLIENT);
        taskRepository.save(task);

        addEvent(taskId,
                resubmit ? RoomTaskEventType.RESUBMITTED : RoomTaskEventType.SENT_TO_CLIENT,
                principal.getAccountId(),
                resubmit ? "Resubmitted to client for approval" : "Submitted to client for approval",
                null);

        // Mirror into room chat for discoverability
        RoomMessage link = new RoomMessage();
        link.setProjectRoomId(task.getProjectRoomId());
        link.setSenderAccountId(principal.getAccountId());
        link.setBody("Task \"" + task.getTitle() + "\" sent for your approval.");
        link.setLinkedTaskId(taskId);
        roomMessageRepository.save(link);

        return mapTask(task, true);
    }

    public RoomTaskResponse requestChanges(Long projectId, UUID taskId, ChangeRequestBody body) {
        AuthPrincipal principal = requireClient();
        RoomTask task = requireTask(projectId, taskId);
        assertClientOwnsProject(principal, requireProjectAccess(projectId));
        if (task.getStatus() != RoomTaskStatus.AWAITING_CLIENT) {
            throw new BadRequestException("Task is not awaiting client review");
        }
        String notes = body != null ? body.getNotes() : null;
        task.setStatus(RoomTaskStatus.CHANGES_REQUESTED);
        task.setRevisionCount(task.getRevisionCount() + 1);
        taskRepository.save(task);

        addEvent(taskId, RoomTaskEventType.CHANGE_REQUESTED, principal.getAccountId(),
                StringUtils.hasText(notes) ? notes : "Client requested changes", null);

        if (StringUtils.hasText(notes)) {
            RoomTaskMessage msg = new RoomTaskMessage();
            msg.setTaskId(taskId);
            msg.setSenderAccountId(principal.getAccountId());
            msg.setBody(notes);
            taskMessageRepository.save(msg);
        }
        return mapTask(task, true);
    }

    public RoomTaskResponse approve(Long projectId, UUID taskId) {
        AuthPrincipal principal = requireClient();
        Project project = requireProjectAccess(projectId);
        assertClientOwnsProject(principal, project);
        RoomTask task = requireTask(projectId, taskId);
        if (task.getStatus() != RoomTaskStatus.AWAITING_CLIENT) {
            throw new BadRequestException("Task is not awaiting client review");
        }

        RoomTaskFileVersion latestStaff = versionRepository.findByTaskIdOrderByVersionNoAsc(taskId).stream()
                .filter(v -> v.getUploaderRole() == FileUploaderRole.STAFF)
                .reduce((a, b) -> b)
                .orElseThrow(() -> new BadRequestException("No staff version to approve"));

        for (RoomTaskFileVersion v : versionRepository.findByTaskIdOrderByVersionNoAsc(taskId)) {
            if (!v.getUuid().equals(latestStaff.getUuid()) && v.getStatus() != FileVersionStatus.APPROVED) {
                v.setStatus(FileVersionStatus.SUPERSEDED);
                v.setIsFinal(false);
                versionRepository.save(v);
            }
        }
        latestStaff.setStatus(FileVersionStatus.APPROVED);
        latestStaff.setIsFinal(true);
        versionRepository.save(latestStaff);

        OffsetDateTime now = OffsetDateTime.now();
        task.setStatus(RoomTaskStatus.APPROVED);
        task.setApprovedAt(now);
        if (task.getFirstSentToClientAt() != null) {
            long days = ChronoUnit.DAYS.between(task.getFirstSentToClientAt().toLocalDate(), now.toLocalDate());
            task.setClientApprovalDays((int) Math.max(days, 0));
        }
        taskRepository.save(task);

        addEvent(taskId, RoomTaskEventType.APPROVED, principal.getAccountId(),
                "Client approved final version v" + latestStaff.getVersionNo()
                        + (task.getClientApprovalDays() != null
                        ? " (client took " + task.getClientApprovalDays() + " day(s))"
                        : ""),
                null);

        return mapTask(task, true);
    }

    public RoomTaskResponse closeTask(Long projectId, UUID taskId) {
        AuthPrincipal principal = requireStaff();
        RoomTask task = requireTask(projectId, taskId);
        if (task.getStatus() != RoomTaskStatus.APPROVED) {
            throw new BadRequestException("Only approved tasks can be closed");
        }
        task.setStatus(RoomTaskStatus.CLOSED);
        taskRepository.save(task);
        addEvent(taskId, RoomTaskEventType.CLOSED, principal.getAccountId(), "Task closed", null);
        return mapTask(task, true);
    }

    public List<RoomTaskEventResponse> timeline(Long projectId, UUID taskId) {
        requireProjectAccess(projectId);
        requireTask(projectId, taskId);
        return eventRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::mapEvent)
                .toList();
    }

    public List<RoomMessageResponse> listTaskMessages(Long projectId, UUID taskId) {
        requireProjectAccess(projectId);
        requireTask(projectId, taskId);
        return taskMessageRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(m -> mapTaskMessage(m, projectId))
                .toList();
    }

    public RoomMessageResponse postTaskMessage(
            Long projectId, UUID taskId, String body, MultipartFile file, UUID referencedVersionId) {
        AuthPrincipal principal = requirePrincipal();
        requireProjectAccess(projectId);
        RoomTask task = requireTask(projectId, taskId);
        if (!StringUtils.hasText(body) && (file == null || file.isEmpty()) && referencedVersionId == null) {
            throw new BadRequestException("Message body, attachment, or version reference required");
        }
        RoomTaskMessage msg = new RoomTaskMessage();
        msg.setTaskId(taskId);
        msg.setSenderAccountId(principal.getAccountId());
        msg.setBody(body);

        // Chat file upload also creates a task file version (single upload path).
        if (file != null && !file.isEmpty()) {
            RoomTaskFileVersion created = createVersionFromFile(task, projectId, file, body, principal);
            msg.setReferencedVersionId(created.getUuid());
            msg.setAttachmentPath(created.getFilePath());
            msg.setAttachmentName(created.getOriginalName());
            if (!StringUtils.hasText(body)) {
                msg.setBody("Uploaded v" + created.getVersionNo() + ": " + created.getOriginalName());
            }
        } else if (referencedVersionId != null) {
            RoomTaskFileVersion version = versionRepository.findByUuidAndTaskId(referencedVersionId, taskId)
                    .orElseThrow(() -> new BadRequestException("Referenced version not found on this task"));
            msg.setReferencedVersionId(version.getUuid());
            if (!StringUtils.hasText(body)) {
                msg.setBody("Referring to v" + version.getVersionNo() + ": " + version.getOriginalName());
            }
        }

        RoomTaskMessage saved = taskMessageRepository.save(msg);
        String eventMsg = StringUtils.hasText(saved.getBody()) ? saved.getBody() : "Attachment uploaded";
        if (saved.getReferencedVersionId() != null) {
            eventMsg = eventMsg + " [ref version]";
        }
        addEvent(taskId, RoomTaskEventType.MESSAGE, principal.getAccountId(), eventMsg, null);
        return mapTaskMessage(saved, projectId);
    }

    public List<RoomMessageResponse> listRoomMessages(Long projectId, UUID roomId) {
        requireProjectAccess(projectId);
        requireRoom(projectId, roomId);
        return roomMessageRepository.findByProjectRoomIdOrderByCreatedAtAsc(roomId).stream()
                .map(this::mapRoomMessage)
                .toList();
    }

    public RoomMessageResponse postRoomMessage(
            Long projectId, UUID roomId, String body, UUID linkedTaskId, MultipartFile file) {
        AuthPrincipal principal = requirePrincipal();
        ProjectRoom room = requireRoom(projectId, roomId);
        if (!StringUtils.hasText(body) && (file == null || file.isEmpty())) {
            throw new BadRequestException("Message body or attachment required");
        }
        if (linkedTaskId != null) {
            requireTask(projectId, linkedTaskId);
        }
        RoomMessage msg = new RoomMessage();
        msg.setProjectRoomId(roomId);
        msg.setSenderAccountId(principal.getAccountId());
        msg.setBody(body);
        msg.setLinkedTaskId(linkedTaskId);
        if (file != null && !file.isEmpty()) {
            String path = fileStorageService.store(file, room.getCompanyId(), projectId, "room-chat");
            msg.setAttachmentPath(path);
            msg.setAttachmentName(file.getOriginalFilename());
        }
        return mapRoomMessage(roomMessageRepository.save(msg));
    }

    public FinalReportResponse finalReport(Long projectId) {
        Project project = requireProjectAccess(projectId);
        List<ProjectRoom> rooms = roomRepository.findByProjectIdOrderBySortOrderAscFloorLabelAscNameAsc(projectId);
        List<FinalReportResponse.FinalReportRoom> roomReports = new ArrayList<>();
        for (ProjectRoom room : rooms) {
            List<RoomTask> tasks = taskRepository.findByProjectRoomIdOrderByCreatedAtDesc(room.getUuid());
            List<FinalReportResponse.FinalReportItem> items = new ArrayList<>();
            for (RoomTask task : tasks) {
                if (task.getStatus() != RoomTaskStatus.APPROVED && task.getStatus() != RoomTaskStatus.CLOSED) {
                    continue;
                }
                versionRepository.findByTaskIdAndIsFinalTrue(task.getUuid()).stream().findFirst().ifPresent(v -> {
                    items.add(FinalReportResponse.FinalReportItem.builder()
                            .taskUuid(task.getUuid())
                            .title(task.getTitle())
                            .taskType(task.getTaskType())
                            .approvedAt(task.getApprovedAt())
                            .clientApprovalDays(task.getClientApprovalDays())
                            .revisionCount(task.getRevisionCount())
                            .fileName(v.getOriginalName())
                            .versionUuid(v.getUuid())
                            .downloadUrl("/api/projects/" + projectId + "/room-tasks/" + task.getUuid()
                                    + "/versions/" + v.getUuid() + "/download")
                            .build());
                });
            }
            if (!items.isEmpty()) {
                roomReports.add(FinalReportResponse.FinalReportRoom.builder()
                        .roomUuid(room.getUuid())
                        .floorLabel(room.getFloorLabel())
                        .roomName(room.getName())
                        .items(items)
                        .build());
            }
        }
        return FinalReportResponse.builder()
                .projectId(projectId)
                .projectName(project.getName())
                .rooms(roomReports)
                .build();
    }

    public Resource loadVersionResource(Long projectId, UUID taskId, UUID versionId) {
        requireProjectAccess(projectId);
        requireTask(projectId, taskId);
        RoomTaskFileVersion version = versionRepository.findByUuidAndTaskId(versionId, taskId)
                .orElseThrow(() -> new NotFoundException("Version not found"));
        return fileStorageService.loadAsResource(version.getFilePath());
    }

    public MediaType versionMediaType(Long projectId, UUID taskId, UUID versionId) {
        requireProjectAccess(projectId);
        RoomTaskFileVersion version = versionRepository.findByUuidAndTaskId(versionId, taskId)
                .orElseThrow(() -> new NotFoundException("Version not found"));
        if (StringUtils.hasText(version.getContentType())) {
            try {
                return MediaType.parseMediaType(version.getContentType());
            } catch (Exception ignored) {
                // fall through
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    public String versionFileName(Long projectId, UUID taskId, UUID versionId) {
        requireProjectAccess(projectId);
        return versionRepository.findByUuidAndTaskId(versionId, taskId)
                .map(RoomTaskFileVersion::getOriginalName)
                .orElse("file");
    }

    // --- helpers ---

    private Project requireProjectAccess(Long projectId) {
        Project project = projectService.getById(projectId);
        AuthPrincipal principal = requirePrincipal();
        if (isClient(principal)) {
            assertClientOwnsProject(principal, project);
        }
        return project;
    }

    private void assertClientOwnsProject(AuthPrincipal principal, Project project) {
        if (!Objects.equals(project.getClientId(), principal.getAccountId())) {
            throw new ForbiddenException("Not your project");
        }
    }

    private ProjectRoom requireRoom(Long projectId, UUID roomId) {
        return roomRepository.findByUuidAndProjectId(roomId, projectId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
    }

    private RoomTask requireTask(Long projectId, UUID taskId) {
        return taskRepository.findByUuidAndProjectId(taskId, projectId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
    }

    private AuthPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal;
    }

    private AuthPrincipal requireStaff() {
        AuthPrincipal principal = requirePrincipal();
        if (isClient(principal)) {
            throw new ForbiddenException("Staff only");
        }
        return principal;
    }

    private AuthPrincipal requireClient() {
        AuthPrincipal principal = requirePrincipal();
        if (!isClient(principal)) {
            throw new ForbiddenException("Client only");
        }
        return principal;
    }

    private boolean isClient(AuthPrincipal principal) {
        Set<Role> roles = principal.getRoles();
        if (roles == null || !roles.contains(Role.CLIENT)) {
            return false;
        }
        return roles.stream().allMatch(r -> r == Role.CLIENT);
    }

    private String normalizeFloor(String floor) {
        return StringUtils.hasText(floor) ? floor.trim() : "General";
    }

    private RoomTaskFileVersion createVersionFromFile(
            RoomTask task,
            Long projectId,
            MultipartFile file,
            String changeNotes,
            AuthPrincipal principal) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        boolean client = isClient(principal);
        if (client) {
            if (task.getStatus() != RoomTaskStatus.AWAITING_CLIENT) {
                throw new ForbiddenException("Client can only upload when awaiting review");
            }
        } else {
            if (task.getStatus() == RoomTaskStatus.APPROVED || task.getStatus() == RoomTaskStatus.CLOSED) {
                throw new BadRequestException("Task is closed");
            }
        }

        UUID taskId = task.getUuid();
        int next = versionRepository.findFirstByTaskIdOrderByVersionNoDesc(taskId)
                .map(v -> v.getVersionNo() + 1)
                .orElse(1);

        String path = fileStorageService.store(file, task.getCompanyId(), projectId, "room-tasks");
        RoomTaskFileVersion version = new RoomTaskFileVersion();
        version.setTaskId(taskId);
        version.setVersionNo(next);
        version.setUploadedBy(principal.getAccountId());
        version.setUploaderRole(client ? FileUploaderRole.CLIENT : FileUploaderRole.STAFF);
        version.setFilePath(path);
        version.setOriginalName(file.getOriginalFilename());
        version.setContentType(file.getContentType());
        version.setFileSize(file.getSize());
        version.setChangeNotes(changeNotes);
        version.setStatus(FileVersionStatus.SUBMITTED);
        version.setIsFinal(false);
        RoomTaskFileVersion saved = versionRepository.save(version);

        addEvent(taskId, RoomTaskEventType.FILE_UPLOADED, principal.getAccountId(),
                "Uploaded v" + next + ": " + saved.getOriginalName(), null);

        if (!client && task.getStatus() == RoomTaskStatus.CHANGES_REQUESTED) {
            task.setStatus(RoomTaskStatus.OPEN);
            taskRepository.save(task);
        }

        return saved;
    }

    private void addEvent(UUID taskId, RoomTaskEventType type, Long actorId, String message, String meta) {
        RoomTaskEvent event = new RoomTaskEvent();
        event.setTaskId(taskId);
        event.setEventType(type);
        event.setActorAccountId(actorId);
        event.setMessage(message);
        event.setMetadataJson(meta);
        eventRepository.save(event);
    }

    private ProjectRoomResponse mapRoom(ProjectRoom room) {
        List<RoomTask> tasks = taskRepository.findByProjectRoomIdOrderByCreatedAtDesc(room.getUuid());
        int open = (int) tasks.stream()
                .filter(t -> t.getStatus() != RoomTaskStatus.APPROVED && t.getStatus() != RoomTaskStatus.CLOSED)
                .count();
        return ProjectRoomResponse.builder()
                .uuid(room.getUuid())
                .projectId(room.getProjectId())
                .name(room.getName())
                .floorLabel(room.getFloorLabel())
                .roomTypeId(room.getRoomTypeId())
                .source(room.getSource())
                .sortOrder(room.getSortOrder())
                .taskCount(tasks.size())
                .openTaskCount(open)
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }

    private RoomTaskResponse mapTask(RoomTask task, boolean includeVersions) {
        ProjectRoom room = roomRepository.findById(task.getProjectRoomId()).orElse(null);
        List<RoomTaskFileVersionResponse> versions = includeVersions
                ? versionRepository.findByTaskIdOrderByVersionNoAsc(task.getUuid()).stream()
                .map(v -> mapVersion(v, task.getProjectId()))
                .toList()
                : List.of();
        return RoomTaskResponse.builder()
                .uuid(task.getUuid())
                .projectRoomId(task.getProjectRoomId())
                .projectId(task.getProjectId())
                .roomName(room != null ? room.getName() : null)
                .floorLabel(room != null ? room.getFloorLabel() : null)
                .title(task.getTitle())
                .taskType(task.getTaskType())
                .typeLabel(resolveTypeLabel(task))
                .status(task.getStatus())
                .clientDeadline(task.getClientDeadline())
                .createdBy(task.getCreatedBy())
                .assigneeAccountId(task.getAssigneeAccountId())
                .firstSentToClientAt(task.getFirstSentToClientAt())
                .approvedAt(task.getApprovedAt())
                .clientApprovalDays(task.getClientApprovalDays())
                .revisionCount(task.getRevisionCount())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .versions(versions)
                .build();
    }

    private RoomTaskFileVersionResponse mapVersion(RoomTaskFileVersion v, Long projectId) {
        return RoomTaskFileVersionResponse.builder()
                .uuid(v.getUuid())
                .taskId(v.getTaskId())
                .versionNo(v.getVersionNo())
                .uploadedBy(v.getUploadedBy())
                .uploaderRole(v.getUploaderRole())
                .originalName(v.getOriginalName())
                .contentType(v.getContentType())
                .fileSize(v.getFileSize())
                .changeNotes(v.getChangeNotes())
                .isFinal(v.getIsFinal())
                .status(v.getStatus())
                .createdAt(v.getCreatedAt())
                .downloadUrl("/api/projects/" + projectId + "/room-tasks/" + v.getTaskId()
                        + "/versions/" + v.getUuid() + "/download")
                .build();
    }

    private RoomTaskEventResponse mapEvent(RoomTaskEvent e) {
        return RoomTaskEventResponse.builder()
                .uuid(e.getUuid())
                .taskId(e.getTaskId())
                .eventType(e.getEventType())
                .actorAccountId(e.getActorAccountId())
                .message(e.getMessage())
                .metadataJson(e.getMetadataJson())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private String resolveTypeLabel(RoomTask task) {
        if (StringUtils.hasText(task.getTypeLabel())) {
            return task.getTypeLabel();
        }
        if (task.getTaskType() == null) {
            return "Other";
        }
        return switch (task.getTaskType()) {
            case DESIGN -> "Design";
            case CONCEPT_MOODBOARD -> "Concept / moodboard";
            case LAYOUT_PLAN -> "Layout plan";
            case THREE_D_RENDER -> "3D render";
            case TILE_SELECTION -> "Tile selection";
            case FLOORING_SELECTION -> "Flooring selection";
            case PAINT_COLOR -> "Paint color";
            case WALLPAPER -> "Wallpaper";
            case JOINERY -> "Joinery";
            case KITCHEN -> "Kitchen";
            case WARDROBE -> "Wardrobe";
            case BATHROOM_FITTINGS -> "Bathroom fittings";
            case LIGHTING -> "Lighting";
            case ELECTRICAL_POINTS -> "Electrical points";
            case AC_LOCATION -> "AC location";
            case CURTAINS_BLINDS -> "Curtains / blinds";
            case FURNITURE -> "Furniture";
            case MATERIAL -> "Material";
            case SAMPLE_APPROVAL -> "Sample approval";
            case MEASUREMENT_CONFIRMATION -> "Measurement confirmation";
            case CHANGE_ORDER -> "Change order";
            case CUSTOM -> "Custom";
            case OTHER -> "Other";
        };
    }

    private RoomMessageResponse mapTaskMessage(RoomTaskMessage m, Long projectId) {
        RoomMessageResponse.RoomMessageResponseBuilder builder = RoomMessageResponse.builder()
                .uuid(m.getUuid())
                .taskId(m.getTaskId())
                .senderAccountId(m.getSenderAccountId())
                .body(m.getBody())
                .attachmentName(m.getAttachmentName())
                .referencedVersionId(m.getReferencedVersionId())
                .createdAt(m.getCreatedAt());
        if (m.getReferencedVersionId() != null) {
            versionRepository.findById(m.getReferencedVersionId()).ifPresent(v -> {
                String downloadUrl = "/api/projects/" + projectId + "/room-tasks/" + v.getTaskId()
                        + "/versions/" + v.getUuid() + "/download";
                builder.referencedVersionNo(v.getVersionNo());
                builder.referencedFileName(v.getOriginalName());
                builder.referencedDownloadUrl(downloadUrl);
                // Prefer working version download over broken /api/projects/files path
                builder.attachmentUrl(downloadUrl);
                if (!StringUtils.hasText(m.getAttachmentName())) {
                    builder.attachmentName(v.getOriginalName());
                }
            });
        }
        return builder.build();
    }

    private RoomMessageResponse mapRoomMessage(RoomMessage m) {
        // Room chat attachments have no version download; omit broken /files URL.
        return RoomMessageResponse.builder()
                .uuid(m.getUuid())
                .projectRoomId(m.getProjectRoomId())
                .senderAccountId(m.getSenderAccountId())
                .body(m.getBody())
                .attachmentName(m.getAttachmentName())
                .attachmentUrl(null)
                .linkedTaskId(m.getLinkedTaskId())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
