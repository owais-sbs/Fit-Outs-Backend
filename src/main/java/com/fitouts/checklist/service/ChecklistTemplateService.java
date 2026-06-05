package com.fitouts.checklist.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.checklist.domain.ChecklistTemplate;
import com.fitouts.checklist.dto.ChecklistTemplateRequest;
import com.fitouts.checklist.dto.ChecklistTemplateResponse;
import com.fitouts.checklist.mapper.ChecklistTemplateMapper;
import com.fitouts.checklist.repository.ChecklistTemplateRepository;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChecklistTemplateService {

    private final ChecklistTemplateRepository repository;
    private final ChecklistTemplateMapper mapper;

    @Transactional
    public ChecklistTemplateResponse create(ChecklistTemplateRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public List<ChecklistTemplateResponse> getAll() {
        return repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChecklistTemplateResponse getByUuid(UUID uuid) {
        return mapper.toResponse(getTemplate(uuid));
    }

    @Transactional(readOnly = true)
    public ChecklistTemplate getTemplate(UUID uuid) {
        return repository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Checklist template not found"));
    }
}
