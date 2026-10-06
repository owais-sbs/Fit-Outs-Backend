package com.fitouts.communications.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectChatCandidateResponse {

    private Long accountId;
    private String displayName;
    private String email;
    private String roleLabel;
    /** True when this person is the project's client. */
    private boolean projectClient;
    /** True when this person may sit in a group that includes the client. */
    private boolean canJoinClientGroup;
}
