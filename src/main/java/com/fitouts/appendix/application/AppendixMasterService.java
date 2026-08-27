package com.fitouts.appendix.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.appendix.domain.AppendixMaster;
import com.fitouts.appendix.dto.AppendixMasterRequest;
import com.fitouts.appendix.dto.AppendixMasterResponse;
import com.fitouts.appendix.repository.AppendixMasterRepository;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppendixMasterService {

    private final AppendixMasterRepository repository;
    private final CompanyRepository companyRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<AppendixMasterResponse> listActive() {
        UUID companyId = CompanyContext.get();
        return repository.findByCompany_UuidAndActiveTrueOrderBySortOrderAscTitleAsc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppendixMasterResponse> listAll() {
        UUID companyId = CompanyContext.get();
        return repository.findByCompany_UuidOrderBySortOrderAscTitleAsc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppendixMasterResponse getById(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public AppendixMasterResponse create(AppendixMasterRequest request, MultipartFile image) {
        UUID companyId = CompanyContext.get();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"));

        String path = fileStorageService.store(image, "appendix-masters/" + companyId);

        AppendixMaster master = new AppendixMaster();
        master.setCompany(company);
        master.setTitle(request.getTitle().trim());
        master.setDescription(trim(request.getDescription()));
        master.setImagePath(path);
        master.setCategory(trim(request.getCategory()));
        master.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        master.setActive(request.getActive() == null || request.getActive());

        return toResponse(repository.save(master));
    }

    @Transactional
    public AppendixMasterResponse update(UUID id, AppendixMasterRequest request, MultipartFile image) {
        AppendixMaster master = getEntity(id);
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            master.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            master.setDescription(trim(request.getDescription()));
        }
        if (request.getCategory() != null) {
            master.setCategory(trim(request.getCategory()));
        }
        if (request.getSortOrder() != null) {
            master.setSortOrder(request.getSortOrder());
        }
        if (request.getActive() != null) {
            master.setActive(request.getActive());
        }
        if (image != null && !image.isEmpty()) {
            fileStorageService.deleteIfExists(master.getImagePath());
            master.setImagePath(fileStorageService.store(image, "appendix-masters/" + master.getCompany().getUuid()));
        }
        return toResponse(repository.save(master));
    }

    @Transactional
    public void delete(UUID id) {
        AppendixMaster master = getEntity(id);
        fileStorageService.deleteIfExists(master.getImagePath());
        repository.delete(master);
    }

    @Transactional(readOnly = true)
    public List<AppendixMasterResponse> getByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        UUID companyId = CompanyContext.get();
        return repository.findAllById(ids).stream()
                .filter(m -> m.getCompany().getUuid().equals(companyId))
                .map(this::toResponse)
                .toList();
    }

    private AppendixMaster getEntity(UUID id) {
        UUID companyId = CompanyContext.get();
        AppendixMaster master = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Appendix master not found"));
        if (!master.getCompany().getUuid().equals(companyId)) {
            throw new NotFoundException("Appendix master not found");
        }
        return master;
    }

    private AppendixMasterResponse toResponse(AppendixMaster master) {
        return AppendixMasterResponse.builder()
                .uuid(master.getUuid())
                .title(master.getTitle())
                .description(master.getDescription())
                .imageUrl("/api/files/" + master.getImagePath())
                .category(master.getCategory())
                .sortOrder(master.getSortOrder())
                .active(master.getActive())
                .createdAt(master.getCreatedAt())
                .build();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
