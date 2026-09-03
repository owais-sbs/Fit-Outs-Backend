package com.fitouts.project.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.checklist.domain.SiteVisitEstimate;
import com.fitouts.checklist.domain.SiteVisitEstimateLine;
import com.fitouts.checklist.domain.SiteVisitEstimateStatus;
import com.fitouts.checklist.repository.SiteVisitEstimateRepository;
import com.fitouts.project.api.ProjectQasSurveySeedResponse;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectQasSurveySeed;
import com.fitouts.project.domain.ProjectQasSurveySeedRepository;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectQasSurveySeedService {

    private static final Logger log = LoggerFactory.getLogger(ProjectQasSurveySeedService.class);

    private final ProjectQasSurveySeedRepository seedRepository;
    private final ProjectRepository projectRepository;
    private final SiteVisitEstimateRepository estimateRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void seedFromLatestIssuedEstimate(Project project, Long leadId) {
        if (project == null || project.getId() == null || leadId == null) {
            return;
        }
        try {
            Optional<SiteVisitEstimate> issued = estimateRepository
                    .findByLeadIdAndStatusOrderByUpdatedAtDesc(leadId, SiteVisitEstimateStatus.ISSUED)
                    .stream()
                    .filter(e -> e.getLines() != null && !e.getLines().isEmpty())
                    .findFirst();

            if (issued.isEmpty()) {
                log.debug("No issued estimate with lines for lead {} — skipping QAS survey seed", leadId);
                return;
            }

            SiteVisitEstimate estimate = issued.get();
            Hibernate.initialize(estimate.getLines());

            SurveyShell shell = buildSurveyShell(estimate.getLines());
            if (shell.floors().isEmpty()) {
                return;
            }

            UUID companyId = project.getCompanyId() != null ? project.getCompanyId() : CompanyContext.get();
            ProjectQasSurveySeed seed = seedRepository
                    .findByProjectIdAndCompanyId(project.getId(), companyId)
                    .orElseGet(ProjectQasSurveySeed::new);

            seed.setProjectId(project.getId());
            seed.setCompanyId(companyId);
            seed.setSourceEstimateUuid(estimate.getUuid());
            seed.setFloorsJson(objectMapper.writeValueAsString(shell.floors()));
            seed.setRoomsJson(objectMapper.writeValueAsString(shell.rooms()));
            seedRepository.save(seed);
            log.info("Seeded QAS survey for project {} from estimate {}", project.getId(), estimate.getUuid());
        } catch (Exception ex) {
            log.warn("Failed to seed QAS survey for project {} / lead {}: {}",
                    project.getId(), leadId, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ProjectQasSurveySeedResponse getForProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        if (project.isDeleted()) {
            throw new NotFoundException("Project not found");
        }
        UUID companyId = CompanyContext.get();
        if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }

        ProjectQasSurveySeed seed = seedRepository.findByProjectIdAndCompanyId(projectId, companyId)
                .orElseThrow(() -> new NotFoundException("QAS survey seed not found"));

        try {
            List<Object> floors = objectMapper.readValue(seed.getFloorsJson(), new TypeReference<>() {});
            List<Object> rooms = objectMapper.readValue(seed.getRoomsJson(), new TypeReference<>() {});
            return ProjectQasSurveySeedResponse.builder()
                    .projectId(projectId)
                    .sourceEstimateUuid(seed.getSourceEstimateUuid())
                    .floors(floors)
                    .rooms(rooms)
                    .build();
        } catch (Exception ex) {
            throw new NotFoundException("QAS survey seed is corrupt");
        }
    }

    private SurveyShell buildSurveyShell(List<SiteVisitEstimateLine> lines) {
        List<Map<String, Object>> floors = new ArrayList<>();
        List<Map<String, Object>> rooms = new ArrayList<>();
        Map<String, String> floorMap = new LinkedHashMap<>();
        Map<String, String> roomMap = new LinkedHashMap<>();
        Map<String, Map<String, Object>> roomById = new LinkedHashMap<>();

        for (SiteVisitEstimateLine line : lines) {
            String floorName = blankToDefault(line.getFloorName(), "General");
            String roomName = blankToDefault(line.getRoomName(), "Room");

            if (!floorMap.containsKey(floorName)) {
                String floorId = "floor-" + (floorMap.size() + 1);
                floorMap.put(floorName, floorId);
                Map<String, Object> floor = new LinkedHashMap<>();
                floor.put("id", floorId);
                floor.put("name", floorName);
                floors.add(floor);
            }
            String floorId = floorMap.get(floorName);
            String roomKey = floorName + "::" + roomName;

            if (!roomMap.containsKey(roomKey)) {
                String roomId = "room-" + (roomMap.size() + 1);
                roomMap.put(roomKey, roomId);
                Map<String, Object> room = new LinkedHashMap<>();
                room.put("id", roomId);
                room.put("floorId", floorId);
                room.put("name", roomName);
                room.put("roomTypeId", line.getRoomTypeId() != null ? line.getRoomTypeId().toString() : "");
                room.put("roomTypeName", "");
                room.put("length", "");
                room.put("width", "");
                room.put("height", "3");
                room.put("selections", List.of());
                room.put("savedLines", new ArrayList<>());
                rooms.add(room);
                roomById.put(roomId, room);
            }

            String roomId = roomMap.get(roomKey);
            Map<String, Object> room = roomById.get(roomId);
            if (room != null) {
                Object existingType = room.get("roomTypeId");
                if ((existingType == null || String.valueOf(existingType).isBlank()) && line.getRoomTypeId() != null) {
                    room.put("roomTypeId", line.getRoomTypeId().toString());
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> savedLines = (List<Map<String, Object>>) room.get("savedLines");
                savedLines.add(toSavedLine(line));
            }
        }

        return new SurveyShell(floors, rooms);
    }

    private Map<String, Object> toSavedLine(SiteVisitEstimateLine line) {
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("workItemId", line.getWorkItemId() != null ? line.getWorkItemId().toString() : null);
        saved.put("roomTypeId", line.getRoomTypeId() != null ? line.getRoomTypeId().toString() : null);
        saved.put("floorName", line.getFloorName());
        saved.put("roomName", line.getRoomName());
        saved.put("category", line.getCategory());
        saved.put("description", line.getDescription());
        saved.put("qty", line.getQty());
        saved.put("unit", line.getUnit());
        saved.put("rate", line.getRate());
        saved.put("amount", line.getAmount());
        saved.put("displayOrder", line.getDisplayOrder());
        saved.put("lineSource", line.getLineSource());
        saved.put("scopeRef", line.getScopeRef());
        return saved;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record SurveyShell(List<Map<String, Object>> floors, List<Map<String, Object>> rooms) {}
}
