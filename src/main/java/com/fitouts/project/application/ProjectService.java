package com.fitouts.project.application;

import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.shared.context.CompanyContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project create(Project request) {
        request.setId(null);
        request.setActive(true);
        request.setDeleted(false);
        if (request.getCompanyId() == null) {
            request.setCompanyId(CompanyContext.get());
        }
        return projectRepository.save(request);
    }

    public List<Project> getAll() {
        UUID companyId = CompanyContext.get();
        return projectRepository.findByCompanyIdAndIsDeletedFalse(companyId);
    }

    public Project getById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));
    }

    public Project update(Long id, Project request) {
        Project project = getById(id);
        project.setName(request.getName());
        if (request.getClientId() != null) {
            project.setClientId(request.getClientId());
        }
        return projectRepository.save(project);
    }

    public Project delete(Long id) {
        Project project = getById(id);
        project.setDeleted(true);
        project.setActive(false);
        return projectRepository.save(project);
    }
}
