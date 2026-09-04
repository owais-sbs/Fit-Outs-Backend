package com.fitouts.drawing.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.drawing.api.ProjectDrawingResponse;
import com.fitouts.drawing.domain.ProjectDrawing;
import com.fitouts.drawing.domain.ProjectDrawingRepository;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.projectdoc.application.ProjectDocumentService;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.enums.DrawingCategory;
import com.fitouts.shared.enums.DrawingStatus;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectDrawingService {

    private final ProjectDrawingRepository drawingRepository;
    private final ProjectService projectService;
    private final FileStorageService fileStorageService;
    private final DwgConversionService dwgConversionService;
    private final ProjectDocumentService projectDocumentService;

    public ProjectDrawingResponse upload(Long projectId, DrawingCategory category, MultipartFile file) {
        UUID companyId = CompanyContext.get();
        Project project = projectService.getById(projectId);

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            throw new BadRequestException("File name required");
        }
        String lower = originalName.toLowerCase(Locale.ROOT);
        boolean isPdf = lower.endsWith(".pdf");
        boolean isDwg = lower.endsWith(".dwg");
        if (!isPdf && !isDwg) {
            throw new BadRequestException("Only PDF and DWG files are supported");
        }

        String storedPath = fileStorageService.store(file, companyId, projectId, "drawings");
        ProjectDrawing drawing = ProjectDrawing.builder()
                .project(project)
                .companyId(companyId)
                .category(category)
                .fileName(originalName)
                .originalPath(storedPath)
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .status(DrawingStatus.UPLOADED)
                .build();

        if (isPdf) {
            drawing.setPreviewPdfPath(storedPath);
            drawing.setStatus(DrawingStatus.READY);
            ProjectDrawing saved = drawingRepository.save(drawing);
            projectDocumentService.registerFromDrawing(saved);
            return mapToResponse(saved);
        }

        drawing.setStatus(DrawingStatus.CONVERTING);
        ProjectDrawing saved = drawingRepository.save(drawing);
        String previewPath = dwgConversionService.convertToPreview(storedPath);
        if (previewPath != null) {
            saved.setPreviewPdfPath(previewPath);
            saved.setStatus(DrawingStatus.READY);
        } else {
            saved.setStatus(DrawingStatus.FAILED);
        }
        ProjectDrawing finalDrawing = drawingRepository.save(saved);
        projectDocumentService.registerFromDrawing(finalDrawing);
        return mapToResponse(finalDrawing);
    }

    public ProjectDrawingResponse reconvert(UUID id) {
        ProjectDrawing drawing = find(id);
        String originalPath = drawing.getOriginalPath();
        if (originalPath == null || !originalPath.toLowerCase(Locale.ROOT).endsWith(".dwg")) {
            throw new BadRequestException("Only DWG drawings can be reconverted");
        }
        if (!dwgConversionService.isConverterAvailable()) {
            throw new BadRequestException("DWG converter is not configured on the server");
        }

        drawing.setStatus(DrawingStatus.CONVERTING);
        drawingRepository.save(drawing);

        String previewPath = dwgConversionService.convertToPreview(originalPath);
        if (previewPath != null) {
            drawing.setPreviewPdfPath(previewPath);
            drawing.setStatus(DrawingStatus.READY);
        } else {
            drawing.setStatus(DrawingStatus.FAILED);
        }
        return mapToResponse(drawingRepository.save(drawing));
    }

    @Transactional(readOnly = true)
    public List<ProjectDrawingResponse> listByProject(Long projectId) {
        return drawingRepository.findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(projectId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectDrawingResponse getById(UUID id) {
        return mapToResponse(find(id));
    }

    @Transactional(readOnly = true)
    public Resource getPreviewResource(UUID id) {
        ProjectDrawing drawing = find(id);
        String path = drawing.getPreviewPdfPath();
        if (path == null || path.isBlank()) {
            throw new BadRequestException("Preview not available for this drawing");
        }
        return fileStorageService.loadAsResource(path);
    }

    @Transactional(readOnly = true)
    public MediaType getPreviewMediaType(UUID id) {
        String path = find(id).getPreviewPdfPath();
        if (path == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".svg")) {
            return MediaType.valueOf("image/svg+xml");
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".dxf")) {
            return MediaType.valueOf("application/dxf");
        }
        return MediaType.APPLICATION_PDF;
    }

    public void delete(UUID id) {
        ProjectDrawing drawing = find(id);
        drawing.setDeleted(true);
        drawing.setStatus(DrawingStatus.FAILED);
        drawingRepository.save(drawing);
        projectDocumentService.softDeleteByDrawingSource(drawing.getId(), drawing.getCompanyId());
    }

    public ProjectDrawing find(UUID id) {
        return drawingRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Drawing not found"));
    }

    private ProjectDrawingResponse mapToResponse(ProjectDrawing d) {
        return ProjectDrawingResponse.builder()
                .id(d.getId())
                .projectId(d.getProject().getId())
                .companyId(d.getCompanyId())
                .category(d.getCategory())
                .fileName(d.getFileName())
                .mimeType(d.getMimeType())
                .fileSize(d.getFileSize())
                .status(d.getStatus())
                .previewAvailable(d.getPreviewPdfPath() != null && !d.getPreviewPdfPath().isBlank())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
