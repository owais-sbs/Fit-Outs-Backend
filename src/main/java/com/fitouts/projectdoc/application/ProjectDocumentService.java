package com.fitouts.projectdoc.application;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.projectdoc.api.ProjectDocumentRequest;
import com.fitouts.projectdoc.api.ProjectDocumentResponse;
import com.fitouts.projectdoc.domain.ProjectDocument;
import com.fitouts.projectdoc.domain.ProjectDocumentRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectDocumentService {

    private final ProjectDocumentRepository documentRepository;
    private final ProjectService projectService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<ProjectDocumentResponse> list(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        return documentRepository
                .findByProjectIdAndCompanyIdAndDeletedFalseOrderByCreatedAtDesc(
                        project.getId(), CompanyContext.get())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDocumentResponse get(Long projectId, UUID uuid) {
        requireStaff();
        requireProject(projectId);
        return toResponse(requireDocument(uuid, projectId));
    }

    @Transactional
    public ProjectDocumentResponse create(Long projectId, ProjectDocumentRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new BadRequestException("title is required");
        }
        if (!StringUtils.hasText(request.getFilePath())) {
            throw new BadRequestException("filePath is required");
        }

        ProjectDocument doc = new ProjectDocument();
        doc.setProjectId(project.getId());
        doc.setCompanyId(CompanyContext.get());
        doc.setTitle(request.getTitle().trim());
        doc.setCategory(trimToNull(request.getCategory()));
        doc.setFilePath(request.getFilePath().trim());
        doc.setUploadedBy(principal.getAccountId());
        doc.setPublishedToClient(Boolean.TRUE.equals(request.getPublishedToClient()));
        applyVersioning(doc, projectId, request.getParentDocumentUuid());
        return toResponse(documentRepository.save(doc));
    }

    @Transactional
    public ProjectDocumentResponse upload(
            Long projectId,
            String title,
            String category,
            MultipartFile file,
            UUID parentDocumentUuid) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        if (!StringUtils.hasText(title)) {
            throw new BadRequestException("title is required");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required");
        }

        UUID companyId = CompanyContext.get();
        String relativePath = fileStorageService.store(file, companyId, project.getId(), "documents");

        ProjectDocument doc = new ProjectDocument();
        doc.setProjectId(project.getId());
        doc.setCompanyId(companyId);
        doc.setTitle(title.trim());
        doc.setCategory(trimToNull(category));
        doc.setFilePath(relativePath);
        doc.setUploadedBy(principal.getAccountId());
        doc.setPublishedToClient(false);
        applyVersioning(doc, projectId, parentDocumentUuid);
        return toResponse(documentRepository.save(doc));
    }

    @Transactional
    public ProjectDocumentResponse update(Long projectId, UUID uuid, ProjectDocumentRequest request) {
        requireStaff();
        requireProject(projectId);
        ProjectDocument doc = requireDocument(uuid, projectId);
        if (request == null) {
            return toResponse(doc);
        }
        if (StringUtils.hasText(request.getTitle())) {
            doc.setTitle(request.getTitle().trim());
        }
        if (request.getCategory() != null) {
            doc.setCategory(trimToNull(request.getCategory()));
        }
        if (StringUtils.hasText(request.getFilePath())) {
            doc.setFilePath(request.getFilePath().trim());
        }
        if (request.getPublishedToClient() != null) {
            doc.setPublishedToClient(request.getPublishedToClient());
        }
        return toResponse(documentRepository.save(doc));
    }

    @Transactional
    public void delete(Long projectId, UUID uuid) {
        requireStaff();
        requireProject(projectId);
        ProjectDocument doc = requireDocument(uuid, projectId);
        doc.setDeleted(true);
        documentRepository.save(doc);
    }

    @Transactional
    public ProjectDocumentResponse publishToClient(Long projectId, UUID uuid) {
        requireStaff();
        requireProject(projectId);
        ProjectDocument doc = requireDocument(uuid, projectId);
        doc.setPublishedToClient(true);
        return toResponse(documentRepository.save(doc));
    }

    @Transactional(readOnly = true)
    public List<ProjectDocumentResponse> listPublished(Long projectId) {
        requireAuthenticated();
        Project project = requireProject(projectId);
        return documentRepository
                .findByProjectIdAndCompanyIdAndPublishedToClientTrueAndDeletedFalseOrderByCreatedAtDesc(
                        project.getId(), CompanyContext.get())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void applyVersioning(ProjectDocument doc, Long projectId, UUID parentDocumentUuid) {
        if (parentDocumentUuid != null) {
            ProjectDocument parent = requireDocument(parentDocumentUuid, projectId);
            UUID rootUuid = parent.getParentDocumentUuid() != null
                    ? parent.getParentDocumentUuid()
                    : parent.getUuid();
            doc.setParentDocumentUuid(rootUuid);
            int nextVersion = documentRepository
                    .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(doc.getProjectId(), CompanyContext.get())
                    .stream()
                    .filter(d -> rootUuid.equals(d.getUuid()) || rootUuid.equals(d.getParentDocumentUuid()))
                    .mapToInt(ProjectDocument::getVersion)
                    .max()
                    .orElse(parent.getVersion()) + 1;
            doc.setVersion(nextVersion);
            if (!StringUtils.hasText(doc.getCategory())) {
                doc.setCategory(parent.getCategory());
            }
        } else {
            doc.setVersion(1);
        }
    }

    private ProjectDocument requireDocument(UUID uuid, Long projectId) {
        ProjectDocument doc = documentRepository.findByUuidAndCompanyIdAndDeletedFalse(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Document not found"));
        if (!doc.getProjectId().equals(projectId)) {
            throw new BadRequestException("Document does not belong to this project");
        }
        return doc;
    }

    private ProjectDocumentResponse toResponse(ProjectDocument doc) {
        return ProjectDocumentResponse.builder()
                .uuid(doc.getUuid())
                .projectId(doc.getProjectId())
                .companyId(doc.getCompanyId())
                .title(doc.getTitle())
                .category(doc.getCategory())
                .filePath(doc.getFilePath())
                .version(doc.getVersion())
                .publishedToClient(doc.isPublishedToClient())
                .uploadedBy(doc.getUploadedBy())
                .parentDocumentUuid(doc.getParentDocumentUuid())
                .deleted(doc.isDeleted())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
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

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
