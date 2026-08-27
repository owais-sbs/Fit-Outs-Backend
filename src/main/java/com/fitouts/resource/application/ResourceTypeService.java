package com.fitouts.resource.application;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.resource.api.ResourceTypeRequest;
import com.fitouts.resource.api.ResourceTypeResponse;
import com.fitouts.resource.domain.ResourceType;
import com.fitouts.resource.domain.ResourceTypeRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResourceTypeService {

    private final ResourceTypeRepository repository;

    @Transactional(readOnly = true)
    public List<ResourceTypeResponse> list() {
        requireStaff();
        UUID companyId = requireCompany();
        return repository.findByCompanyIdOrderByNameAsc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ResourceTypeResponse create(ResourceTypeRequest request) {
        requireStaff();
        validate(request, true);
        ResourceType entity = new ResourceType();
        entity.setCompanyId(requireCompany());
        entity.setName(request.getName().trim());
        entity.setKind(request.getKind());
        entity.setActive(request.getActive() == null || request.getActive());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public ResourceTypeResponse update(UUID uuid, ResourceTypeRequest request) {
        requireStaff();
        ResourceType entity = repository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Resource type not found"));
        if (StringUtils.hasText(request.getName())) {
            entity.setName(request.getName().trim());
        }
        if (request.getKind() != null) {
            entity.setKind(request.getKind());
        }
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
        if (!StringUtils.hasText(entity.getName()) || entity.getKind() == null) {
            throw new BadRequestException("name and kind are required");
        }
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(UUID uuid) {
        requireStaff();
        ResourceType entity = repository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Resource type not found"));
        entity.setActive(false);
        repository.save(entity);
    }

    private void validate(ResourceTypeRequest request, boolean creating) {
        if (creating && !StringUtils.hasText(request.getName())) {
            throw new BadRequestException("name is required");
        }
        if (creating && request.getKind() == null) {
            throw new BadRequestException("kind is required");
        }
    }

    private ResourceTypeResponse toResponse(ResourceType e) {
        return ResourceTypeResponse.builder()
                .uuid(e.getUuid())
                .name(e.getName())
                .kind(e.getKind())
                .active(e.isActive())
                .build();
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        return companyId;
    }

    private AuthPrincipal requireStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }
}
