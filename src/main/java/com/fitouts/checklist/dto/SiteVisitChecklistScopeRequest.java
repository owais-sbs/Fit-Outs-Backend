package com.fitouts.checklist.dto;

import java.util.ArrayList;
import java.util.List;

import com.fitouts.checklist.domain.SiteVisitPropertyType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SiteVisitChecklistScopeRequest {

    private SiteVisitPropertyType propertyType;
    private String propertyTypeCustom;
    private List<RoomScopeDto> roomScopes = new ArrayList<>();
}
