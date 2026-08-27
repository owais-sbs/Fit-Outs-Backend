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
import com.fitouts.resource.api.LabourCrewRequest;
import com.fitouts.resource.api.LabourCrewResponse;
import com.fitouts.resource.domain.LabourCrew;
import com.fitouts.resource.domain.LabourCrewRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LabourCrewService {

    private final LabourCrewRepository repository;

    @Transactional(readOnly = true)
    public List<LabourCrewResponse> list() {
        requireStaff();
        UUID companyId = requireCompany();
        return repository.findByCompanyIdOrderByNameAsc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LabourCrewResponse create(LabourCrewRequest request) {
        requireStaff();
        if (!StringUtils.hasText(request.getName())) {
            throw new BadRequestException("name is required");
        }
        int headcount = request.getHeadcount() != null ? request.getHeadcount() : 1;
        if (headcount < 1) {
            throw new BadRequestException("headcount must be at least 1");
        }
        LabourCrew entity = new LabourCrew();
        entity.setCompanyId(requireCompany());
        entity.setName(request.getName().trim());
        entity.setHeadcount(headcount);
        entity.setActive(request.getActive() == null || request.getActive());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public LabourCrewResponse update(UUID uuid, LabourCrewRequest request) {
        requireStaff();
        LabourCrew entity = repository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Labour crew not found"));
        if (StringUtils.hasText(request.getName())) {
            entity.setName(request.getName().trim());
        }
        if (request.getHeadcount() != null) {
            if (request.getHeadcount() < 1) {
                throw new BadRequestException("headcount must be at least 1");
            }
            entity.setHeadcount(request.getHeadcount());
        }
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(UUID uuid) {
        requireStaff();
        LabourCrew entity = repository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Labour crew not found"));
        entity.setActive(false);
        repository.save(entity);
    }

    private LabourCrewResponse toResponse(LabourCrew e) {
        return LabourCrewResponse.builder()
                .uuid(e.getUuid())
                .name(e.getName())
                .headcount(e.getHeadcount())
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
