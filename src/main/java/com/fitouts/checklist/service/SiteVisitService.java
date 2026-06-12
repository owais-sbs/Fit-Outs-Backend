package com.fitouts.checklist.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitLocationDetails;
import com.fitouts.checklist.dto.SiteVisitCreateRequest;
import com.fitouts.checklist.dto.SiteVisitLocationDetailsRequest;
import com.fitouts.checklist.dto.SiteVisitResponse;
import com.fitouts.checklist.mapper.SiteVisitMapper;
import com.fitouts.checklist.repository.SiteVisitLocationDetailsRepository;
import com.fitouts.checklist.repository.SiteVisitRepository;
import com.fitouts.company.application.CompanyService;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SiteVisitService {

    private final SiteVisitRepository repository;
    private final SiteVisitLocationDetailsRepository locationDetailsRepository;
    private final SiteVisitMapper mapper;
    private final CompanyService companyService;

    @Transactional
    public SiteVisitResponse create(SiteVisitCreateRequest request) {
        SiteVisit siteVisit = mapper.toEntity(request);

        UUID companyId = CompanyContext.get();
        if (companyId != null) {
            siteVisit.setCompany(companyService.getCompany(companyId));
        }

        return mapper.toResponse(repository.save(siteVisit));
    }

    @Transactional(readOnly = true)
    public List<SiteVisitResponse> getAll() {
        UUID companyId = CompanyContext.get();
        if (companyId != null) {
            return repository.findByCompanyUuid(companyId).stream()
                    .map(mapper::toResponse)
                    .toList();
        }
        return repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SiteVisitResponse getByUuid(UUID uuid) {
        return mapper.toResponse(getSiteVisit(uuid));
    }

    @Transactional
    public SiteVisitResponse addLocationDetails(UUID uuid, SiteVisitLocationDetailsRequest request) {
        SiteVisit siteVisit = getSiteVisit(uuid);
        if (locationDetailsRepository.existsBySiteVisitUuid(uuid)) {
            throw new ConflictException("Site visit location details already exist");
        }

        SiteVisitLocationDetails details = mapper.toLocationEntity(request);
        siteVisit.setLocationDetails(details);
        return mapper.toResponse(repository.save(siteVisit));
    }

    @Transactional(readOnly = true)
    public SiteVisit getSiteVisit(UUID uuid) {
        return repository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Site visit not found"));
    }
}
