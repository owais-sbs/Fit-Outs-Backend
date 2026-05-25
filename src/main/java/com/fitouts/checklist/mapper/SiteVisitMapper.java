package com.fitouts.checklist.mapper;

import org.springframework.stereotype.Component;

import com.fitouts.checklist.domain.ChecklistTemplate;
import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitLocationDetails;
import com.fitouts.checklist.domain.SiteVisitStatus;
import com.fitouts.checklist.dto.SiteVisitCreateRequest;
import com.fitouts.checklist.dto.SiteVisitLocationDetailsRequest;
import com.fitouts.checklist.dto.SiteVisitLocationDetailsResponse;
import com.fitouts.checklist.dto.SiteVisitResponse;

@Component
public class SiteVisitMapper {

    public SiteVisit toEntity(SiteVisitCreateRequest request, ChecklistTemplate template) {
        SiteVisit siteVisit = new SiteVisit();
        siteVisit.setLeadId(request.getLeadId());
        siteVisit.setAssignedTo(request.getAssignedTo());
        siteVisit.setScheduledDate(request.getScheduledDate());
        siteVisit.setScheduledTime(request.getScheduledTime());
        siteVisit.setLatitude(request.getLatitude());
        siteVisit.setLongitude(request.getLongitude());
        siteVisit.setChecklistTemplate(template);
        siteVisit.setNotes(trimNullable(request.getNotes()));
        siteVisit.setCreatedBy(request.getCreatedBy());
        siteVisit.setStatus(SiteVisitStatus.SCHEDULED);
        return siteVisit;
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
        SiteVisitResponse response = SiteVisitResponse.builder()
                .leadId(siteVisit.getLeadId())
                .assignedTo(siteVisit.getAssignedTo())
                .scheduledDate(siteVisit.getScheduledDate())
                .scheduledTime(siteVisit.getScheduledTime())
                .latitude(siteVisit.getLatitude())
                .longitude(siteVisit.getLongitude())
                .status(siteVisit.getStatus())
                .notes(siteVisit.getNotes())
                .createdBy(siteVisit.getCreatedBy())
                .createdAt(siteVisit.getCreatedAt())
                .updatedAt(siteVisit.getUpdatedAt())
                .locationDetails(toLocationResponse(siteVisit.getLocationDetails()))
                .build();
        response.setUuid(siteVisit.getUuid());
        response.setChecklistTemplateUuid(siteVisit.getChecklistTemplate().getUuid());
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

    private String trimNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
