package com.fitouts.drawing.api;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.shared.enums.DrawingCategory;
import com.fitouts.shared.enums.DrawingStatus;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectDrawingResponse {
    private UUID id;
    private Long projectId;
    private UUID companyId;
    private DrawingCategory category;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private DrawingStatus status;
    private boolean previewAvailable;
    private String drawingNumber;
    private Integer revisionNo;
    private String revisionCode;
    private java.time.LocalDate revisionDate;
    private Boolean isLatest;
    private Boolean isSuperseded;
    private LocalDateTime supersededAt;
    private UUID supersededById;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
