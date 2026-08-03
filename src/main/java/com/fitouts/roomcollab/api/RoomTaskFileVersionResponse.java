package com.fitouts.roomcollab.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.roomcollab.domain.FileUploaderRole;
import com.fitouts.roomcollab.domain.FileVersionStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoomTaskFileVersionResponse {
    private UUID uuid;
    private UUID taskId;
    private Integer versionNo;
    private Long uploadedBy;
    private FileUploaderRole uploaderRole;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String changeNotes;
    private Boolean isFinal;
    private FileVersionStatus status;
    private OffsetDateTime createdAt;
    private String downloadUrl;
}
