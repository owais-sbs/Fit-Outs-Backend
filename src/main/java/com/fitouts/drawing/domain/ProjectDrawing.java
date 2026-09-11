package com.fitouts.drawing.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.project.domain.Project;
import com.fitouts.shared.enums.DrawingCategory;
import com.fitouts.shared.enums.DrawingStatus;
import java.time.*;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "project_drawings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectDrawing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DrawingCategory category;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "original_path", nullable = false, length = 1000)
    private String originalPath;

    @Column(name = "preview_pdf_path", length = 1000)
    private String previewPdfPath;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DrawingStatus status = DrawingStatus.UPLOADED;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
    @Column(name = "drawing_number", length = 128)
    private String drawingNumber;

    @Column(name = "revision_no")
    @Builder.Default
    private Integer revisionNo = 1;

    @Column(name = "revision_code", length = 32)
    private String revisionCode;

    @Column(name = "revision_date")
    private LocalDate revisionDate;

    @Column(name = "is_latest")
    @Builder.Default
    private Boolean isLatest = true;

    @Column(name = "is_superseded")
    @Builder.Default
    private Boolean isSuperseded = false;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @Column(name = "superseded_by_id")
    private UUID supersededById;

    public Integer getRevisionNo() {
        return revisionNo != null ? revisionNo : 1;
    }

    public Boolean getIsLatest() {
        return isLatest != null ? isLatest : true;
    }

    public Boolean getIsSuperseded() {
        return isSuperseded != null ? isSuperseded : false;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
