package com.fitouts.checklist.mapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitLocationDetails;
import com.fitouts.checklist.domain.SiteVisitPropertyType;
import com.fitouts.checklist.domain.SiteVisitStatus;
import com.fitouts.checklist.dto.FloorRoomScopeDto;
import com.fitouts.checklist.dto.RoomScopeDto;
import com.fitouts.checklist.dto.RoomScopeSelectionDto;
import com.fitouts.checklist.dto.SiteVisitChecklistScopeRequest;
import com.fitouts.checklist.dto.SiteVisitCreateRequest;
import com.fitouts.checklist.dto.SiteVisitLocationDetailsRequest;
import com.fitouts.checklist.dto.SiteVisitLocationDetailsResponse;
import com.fitouts.checklist.dto.SiteVisitResponse;
import com.fitouts.employee.domain.Employee;
import com.fitouts.employee.domain.EmployeeRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SiteVisitMapper {

    private final EmployeeRepository employeeRepository;

    public SiteVisit toEntity(SiteVisitCreateRequest request) {
        SiteVisit siteVisit = new SiteVisit();
        siteVisit.setLeadId(request.getLeadId());
        siteVisit.setScheduledDate(request.getScheduledDate());
        siteVisit.setScheduledTime(request.getScheduledTime());
        siteVisit.setLatitude(request.getLatitude());
        siteVisit.setLongitude(request.getLongitude());
        siteVisit.setNotes(trimNullable(request.getNotes()));
        siteVisit.setCreatedBy(request.getCreatedBy());
        siteVisit.setStatus(SiteVisitStatus.SCHEDULED);
        siteVisit.setChecklistTemplateUuid(request.getChecklistTemplateUuid());
        applyChecklistScope(
                siteVisit,
                request.getPropertyType(),
                request.getPropertyTypeCustom(),
                request.getRoomScopes(),
                request.getCategories(),
                request.getRooms());
        return siteVisit;
    }

    public void applyChecklistScope(
            SiteVisit siteVisit,
            SiteVisitPropertyType propertyType,
            String propertyTypeCustom,
            List<RoomScopeDto> roomScopes,
            List<String> fallbackCategories,
            List<String> fallbackRooms) {
        List<RoomScopeDto> cleanedScopes = cleanRoomScopes(roomScopes);
        siteVisit.setPropertyType(propertyType);
        siteVisit.setPropertyTypeCustom(
                propertyType == SiteVisitPropertyType.CUSTOM ? trimNullable(propertyTypeCustom) : null);
        siteVisit.setRoomScopes(cleanedScopes);

        if (!cleanedScopes.isEmpty()) {
            siteVisit.setCategories(deriveCategories(cleanedScopes));
            siteVisit.setRooms(deriveRooms(cleanedScopes));
        } else {
            siteVisit.setCategories(cleanList(fallbackCategories));
            siteVisit.setRooms(cleanList(fallbackRooms));
        }
    }

    public void applyChecklistScope(SiteVisit siteVisit, SiteVisitChecklistScopeRequest request) {
        applyChecklistScope(
                siteVisit,
                request.getPropertyType(),
                request.getPropertyTypeCustom(),
                request.getRoomScopes(),
                List.of(),
                List.of());
    }

    public SiteVisitLocationDetails toLocationEntity(SiteVisitLocationDetailsRequest request) {
        SiteVisitLocationDetails details = new SiteVisitLocationDetails();
        details.setAddressLine1(request.getAddressLine1().trim());
        details.setAddressLine2(trimNullable(request.getAddressLine2()));
        details.setCity(request.getCity().trim());
        details.setState(request.getState().trim());
        details.setCountry(request.getCountry().trim());
        details.setPincode(request.getPincode().trim());
        details.setArea(trimNullable(request.getArea()));
        details.setBuildingName(trimNullable(request.getBuildingName()));
        details.setFloor(trimNullable(request.getFloor()));
        details.setUnitNumber(trimNullable(request.getUnitNumber()));
        details.setLandmark(trimNullable(request.getLandmark()));
        details.setAccessNotes(trimNullable(request.getAccessNotes()));
        return details;
    }

    public SiteVisitResponse toResponse(SiteVisit siteVisit) {
        List<Long> accountIds = siteVisit.getAssignments() == null
                ? List.of()
                : siteVisit.getAssignments()
                    .stream()
                    .filter(a -> a != null && a.getEmployee() != null)
                    .map(a -> a.getEmployee().getId())
                    .toList();

        List<Long> employeeIds = List.of();
        List<String> employeeNames = List.of();
        if (!accountIds.isEmpty()) {
            List<Employee> employees = employeeRepository.findByAccountIdIn(accountIds);
            employeeIds = employees.stream()
                    .map(Employee::getId)
                    .toList();
            employeeNames = employees.stream()
                    .map(Employee::getEmployeeName)
                    .toList();
        }

        SiteVisitResponse response = SiteVisitResponse.builder()
                .leadId(siteVisit.getLeadId())
                .employeeIds(employeeIds)
                .employeeNames(employeeNames)
                .scheduledDate(siteVisit.getScheduledDate())
                .scheduledTime(siteVisit.getScheduledTime())
                .latitude(siteVisit.getLatitude())
                .longitude(siteVisit.getLongitude())
                .status(siteVisit.getStatus())
                .notes(siteVisit.getNotes())
                .createdBy(siteVisit.getCreatedBy())
                .checklistTemplateUuid(siteVisit.getChecklistTemplateUuid())
                .propertyType(siteVisit.getPropertyType())
                .propertyTypeCustom(siteVisit.getPropertyTypeCustom())
                .roomScopes(siteVisit.getRoomScopes() != null
                        ? new ArrayList<>(siteVisit.getRoomScopes())
                        : new ArrayList<>())
                .categories(siteVisit.getCategories() != null
                        ? new ArrayList<>(siteVisit.getCategories())
                        : new ArrayList<>())
                .rooms(siteVisit.getRooms() != null
                        ? new ArrayList<>(siteVisit.getRooms())
                        : new ArrayList<>())
                .createdAt(siteVisit.getCreatedAt())
                .updatedAt(siteVisit.getUpdatedAt())
                .locationDetails(toLocationResponse(siteVisit.getLocationDetails()))
                .build();
        response.setUuid(siteVisit.getUuid());
        return response;
    }

    public SiteVisitLocationDetailsResponse toLocationResponse(SiteVisitLocationDetails details) {
        if (details == null) {
            return null;
        }
        SiteVisitLocationDetailsResponse response = SiteVisitLocationDetailsResponse.builder()
                .addressLine1(details.getAddressLine1())
                .addressLine2(details.getAddressLine2())
                .city(details.getCity())
                .state(details.getState())
                .country(details.getCountry())
                .pincode(details.getPincode())
                .area(details.getArea())
                .buildingName(details.getBuildingName())
                .floor(details.getFloor())
                .unitNumber(details.getUnitNumber())
                .landmark(details.getLandmark())
                .accessNotes(details.getAccessNotes())
                .createdAt(details.getCreatedAt())
                .updatedAt(details.getUpdatedAt())
                .build();
        response.setUuid(details.getUuid());
        return response;
    }

    private List<RoomScopeDto> cleanRoomScopes(List<RoomScopeDto> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return new ArrayList<>();
        }
        List<RoomScopeDto> cleaned = new ArrayList<>();
        for (RoomScopeDto scope : scopes) {
            if (scope == null) {
                continue;
            }

            // Legacy flat shape: roomName + selections → wrap under General floor
            boolean legacyFlat = (scope.getRooms() == null || scope.getRooms().isEmpty())
                    && scope.getRoomName() != null
                    && !scope.getRoomName().isBlank();

            String floorName = trimNullable(scope.getFloorName());
            if (floorName == null) {
                floorName = legacyFlat ? "General" : null;
            }
            if (floorName == null || floorName.isBlank()) {
                continue;
            }

            List<FloorRoomScopeDto> rooms = new ArrayList<>();
            if (legacyFlat) {
                FloorRoomScopeDto room = new FloorRoomScopeDto();
                room.setRoomName(scope.getRoomName().trim());
                room.setSelections(cleanSelections(scope.getSelections()));
                rooms.add(room);
            } else if (scope.getRooms() != null) {
                for (FloorRoomScopeDto room : scope.getRooms()) {
                    if (room == null || room.getRoomName() == null || room.getRoomName().isBlank()) {
                        continue;
                    }
                    FloorRoomScopeDto roomCopy = new FloorRoomScopeDto();
                    roomCopy.setRoomName(room.getRoomName().trim());
                    roomCopy.setSelections(cleanSelections(room.getSelections()));
                    rooms.add(roomCopy);
                }
            }

            RoomScopeDto copy = new RoomScopeDto();
            copy.setFloorName(floorName.trim());
            copy.setRooms(rooms);
            cleaned.add(copy);
        }
        return cleaned;
    }

    private List<RoomScopeSelectionDto> cleanSelections(List<RoomScopeSelectionDto> selections) {
        List<RoomScopeSelectionDto> cleaned = new ArrayList<>();
        if (selections == null) {
            return cleaned;
        }
        for (RoomScopeSelectionDto selection : selections) {
            if (selection == null || selection.getCategory() == null || selection.getCategory().isBlank()) {
                continue;
            }
            List<String> items = cleanList(selection.getItems());
            if (items.isEmpty()) {
                continue;
            }
            RoomScopeSelectionDto selCopy = new RoomScopeSelectionDto();
            selCopy.setCategory(selection.getCategory().trim());
            selCopy.setItems(items);
            cleaned.add(selCopy);
        }
        return cleaned;
    }

    private List<String> deriveCategories(List<RoomScopeDto> scopes) {
        Set<String> categories = new LinkedHashSet<>();
        for (RoomScopeDto floor : scopes) {
            if (floor.getRooms() == null) {
                continue;
            }
            for (FloorRoomScopeDto room : floor.getRooms()) {
                if (room.getSelections() == null) {
                    continue;
                }
                for (RoomScopeSelectionDto selection : room.getSelections()) {
                    if (selection.getCategory() != null && !selection.getCategory().isBlank()) {
                        categories.add(selection.getCategory().trim());
                    }
                }
            }
        }
        return new ArrayList<>(categories);
    }

    private List<String> deriveRooms(List<RoomScopeDto> scopes) {
        Set<String> rooms = new LinkedHashSet<>();
        for (RoomScopeDto floor : scopes) {
            String floorName = floor.getFloorName() != null ? floor.getFloorName().trim() : "General";
            if (floor.getRooms() == null) {
                continue;
            }
            for (FloorRoomScopeDto room : floor.getRooms()) {
                if (room.getRoomName() != null && !room.getRoomName().isBlank()) {
                    rooms.add(floorName + " / " + room.getRoomName().trim());
                }
            }
        }
        return new ArrayList<>(rooms);
    }

    private List<String> cleanList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String trimNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
