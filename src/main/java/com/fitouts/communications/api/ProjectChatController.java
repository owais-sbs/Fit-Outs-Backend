package com.fitouts.communications.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.communications.application.ProjectChatService;
import com.fitouts.communications.dto.CreateProjectDirectRequest;
import com.fitouts.communications.dto.CreateProjectGroupRequest;
import com.fitouts.communications.dto.UpdateProjectGroupMembersRequest;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ProjectChatController extends BaseController {

    private final ProjectChatService projectChatService;

    @GetMapping("/api/projects/{projectId}/chat/candidates")
    public Object candidates(@PathVariable Long projectId) {
        try {
            return successResponse(projectChatService.listCandidates(projectId));
        } catch (Exception e) {
            return failureResponse("Unable to load people for this project", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/chat/direct/candidates")
    public Object directCandidates(@PathVariable Long projectId) {
        try {
            return successResponse(projectChatService.listDirectCandidates(projectId));
        } catch (Exception e) {
            return failureResponse("Unable to load people for this project", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/chat/direct")
    public Object startDirect(@PathVariable Long projectId, @RequestBody CreateProjectDirectRequest request) {
        try {
            return successResponse("Chat started", projectChatService.startDirect(projectId, request));
        } catch (Exception e) {
            return failureResponse("Unable to start chat", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/chat/groups")
    public Object groups(@PathVariable Long projectId) {
        try {
            return successResponse(projectChatService.listGroups(projectId));
        } catch (Exception e) {
            return failureResponse("Unable to load project chat", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/chat/groups")
    public Object create(@PathVariable Long projectId, @RequestBody CreateProjectGroupRequest request) {
        try {
            return successResponse("Group created", projectChatService.createGroup(projectId, request));
        } catch (Exception e) {
            return failureResponse("Unable to create group", e.getMessage());
        }
    }

    @PutMapping("/api/projects/{projectId}/chat/groups/{channelUuid}/members")
    public Object updateMembers(
            @PathVariable Long projectId,
            @PathVariable UUID channelUuid,
            @RequestBody UpdateProjectGroupMembersRequest request) {
        try {
            return successResponse("Group updated", projectChatService.updateMembers(projectId, channelUuid, request));
        } catch (Exception e) {
            return failureResponse("Unable to update group", e.getMessage());
        }
    }
}
