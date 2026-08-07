package com.fitouts.checklist.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SiteVisitReportItemRequest {

    private String response;
    private String remarks;
    private String roomName;
    private String sectionName;
    private String question;
    private List<String> photoUrls = new ArrayList<>();
}
