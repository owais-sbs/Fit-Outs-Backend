package com.fitouts.communications.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectChatMemberResponse {

    private Long accountId;
    private String displayName;
    private String roleLabel;
}
