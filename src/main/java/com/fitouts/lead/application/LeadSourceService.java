package com.fitouts.lead.application;

import com.fitouts.lead.domain.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeadSourceService {

    private final LeadSourceRepository repository;

    public LeadSourceService(LeadSourceRepository repository) {
        this.repository = repository;
    }

    public LeadSource create(LeadSource request) {

        request.setId(null);

        request.setIsactive(true);

        request.setIsdeleted(false);

        return repository.save(request);
    }

    public List<LeadSource> getAll() {

        return repository.findByIsdeletedFalseAndIsactiveTrue();
    }
}