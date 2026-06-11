package com.fitouts.lead.application;

import com.fitouts.company.application.CompanyService;
import com.fitouts.lead.domain.*;
import com.fitouts.shared.context.CompanyContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class LeadSourceService {

    private final LeadSourceRepository repository;

    private final CompanyService companyService;

    public LeadSourceService(LeadSourceRepository repository,
                             CompanyService companyService) {
        this.repository = repository;
        this.companyService = companyService;
    }

    public LeadSource create(LeadSource request) {

        request.setId(null);

        request.setIsactive(true);

        request.setIsdeleted(false);

        UUID companyId = CompanyContext.get();
        if (companyId != null) {
            request.setCompany(companyService.getCompany(companyId));
        }

        return repository.save(request);
    }

    public List<LeadSource> getAll() {

        UUID companyId = CompanyContext.get();
        if (companyId != null) {
            return repository.findByCompanyUuidAndIsdeletedFalseAndIsactiveTrue(companyId);
        }
        return repository.findByIsdeletedFalseAndIsactiveTrue();
    }
}