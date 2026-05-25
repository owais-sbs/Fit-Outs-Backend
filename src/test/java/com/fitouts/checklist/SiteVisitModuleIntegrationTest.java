package com.fitouts.checklist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fitouts.checklist.domain.ChecklistItemType;
import com.fitouts.checklist.domain.ChecklistTemplate;
import com.fitouts.checklist.domain.ChecklistTemplateItem;
import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitStatus;
import com.fitouts.checklist.repository.ChecklistTemplateItemRepository;
import com.fitouts.checklist.repository.ChecklistTemplateRepository;
import com.fitouts.checklist.repository.SiteVisitReportRepository;
import com.fitouts.checklist.repository.SiteVisitRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SiteVisitModuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChecklistTemplateRepository templateRepository;

    @Autowired
    private ChecklistTemplateItemRepository itemRepository;

    @Autowired
    private SiteVisitRepository siteVisitRepository;

    @Autowired
    private SiteVisitReportRepository reportRepository;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        siteVisitRepository.deleteAll();
        templateRepository.deleteAll();
    }

    @Test
    void checklistSchedulingLocationAndReportFlowPersistsPhotoAnswers() throws Exception {
        mockMvc.perform(post("/api/checklist-templates/CreateCheckList")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Renovation Checklist",
                                  "description":"Villa renovation checklist",
                                  "items":[
                                    {
                                      "sectionName":"Flooring",
                                      "question":"Retain Existing Flooring?",
                                      "type":"YES_NO",
                                      "isRequired":true,
                                      "displayOrder":1
                                    },
                                    {
                                      "sectionName":"Ceiling",
                                      "question":"New Ceiling Required?",
                                      "type":"YES_NO",
                                      "isRequired":true,
                                      "displayOrder":2
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uuid").isString())
                .andExpect(jsonPath("$.data.items.length()").value(2));

        ChecklistTemplate template = templateRepository.findAll().get(0);
        List<ChecklistTemplateItem> items = orderedItems();

        mockMvc.perform(post("/api/site-visits/CreateSite-Visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId":1,
                                  "assignedTo":5,
                                  "scheduledDate":"2026-05-25",
                                  "scheduledTime":"11:00",
                                  "latitude":16.50617452,
                                  "longitude":80.64801532,
                                  "checklistTemplateUuid":"%s",
                                  "notes":"Initial renovation visit"
                                }
                                """.formatted(template.getUuid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.checklistTemplateUuid").value(template.getUuid().toString()));

        var siteVisitUuid = siteVisitRepository.findAll().get(0).getUuid();

        mockMvc.perform(post("/api/site-visits/Site/{uuid}/location-details", siteVisitUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressLine1":"Flat 203",
                                  "addressLine2":"Near Metro Station",
                                  "city":"Hyderabad",
                                  "state":"Telangana",
                                  "country":"India",
                                  "pincode":"500081",
                                  "area":"Madhapur",
                                  "buildingName":"Sky Heights",
                                  "floor":"3",
                                  "unitNumber":"302",
                                  "landmark":"Opposite Mall",
                                  "accessNotes":"Call security before entry"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationDetails.city").value("Hyderabad"));

        mockMvc.perform(post("/api/site-visits/GetSiteVistByUuid/{uuid}/report", siteVisitUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcome":"QUALIFIED",
                                  "notes":"Site inspection completed",
                                  "items":[
                                    {
                                      "templateItemUuid":"%s",
                                      "response":"YES",
                                      "remarks":"Flooring is reusable",
                                      "photoUrls":["url1","url2","url3"]
                                    },
                                    {
                                      "templateItemUuid":"%s",
                                      "response":"NO",
                                      "remarks":"Ceiling damaged",
                                      "photoUrls":["url4"]
                                    }
                                  ]
                                }
                                """.formatted(items.get(0).getUuid(), items.get(1).getUuid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].photoUrls.length()").value(3));

        mockMvc.perform(get("/api/site-visits/GetSiteVisitByUuid/{uuid}", siteVisitUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.locationDetails.pincode").value("500081"));
    }

    @Test
    void reportRejectsMissingRequiredChecklistAnswers() throws Exception {
        ChecklistTemplate template = templateRepository.save(template("Required Checklist", true, true));
        SiteVisit siteVisit = siteVisitRepository.save(siteVisit(template));
        List<ChecklistTemplateItem> items = orderedItems();

        mockMvc.perform(post("/api/site-visits/GetSiteVistByUuid/{uuid}/report", siteVisit.getUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcome":"QUALIFIED",
                                  "items":[
                                    {
                                      "templateItemUuid":"%s",
                                      "response":"YES"
                                    }
                                  ]
                                }
                                """.formatted(items.get(0).getUuid())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("All required checklist items must be answered"));
    }

    @Test
    void reportRejectsChecklistItemsFromAnotherTemplate() throws Exception {
        ChecklistTemplate visitTemplate = templateRepository.save(template("Visit Checklist", true));
        ChecklistTemplate otherTemplate = templateRepository.save(template("Other Checklist", false));
        SiteVisit siteVisit = siteVisitRepository.save(siteVisit(visitTemplate));
        var otherTemplateItemUuid = otherTemplate.getItems().get(0).getUuid();

        mockMvc.perform(post("/api/site-visits/GetSiteVistByUuid/{uuid}/report", siteVisit.getUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcome":"QUALIFIED",
                                  "items":[
                                    {
                                      "templateItemUuid":"%s",
                                      "response":"YES"
                                    }
                                  ]
                                }
                                """.formatted(otherTemplateItemUuid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Report item does not belong to the site visit checklist template"));
    }

    private List<ChecklistTemplateItem> orderedItems() {
        return itemRepository.findAll().stream()
                .sorted(Comparator.comparing(ChecklistTemplateItem::getDisplayOrder))
                .toList();
    }

    private ChecklistTemplate template(String name, boolean... requiredFlags) {
        ChecklistTemplate template = new ChecklistTemplate();
        template.setName(name);
        for (int index = 0; index < requiredFlags.length; index++) {
            ChecklistTemplateItem item = new ChecklistTemplateItem();
            item.setSectionName("Section " + index);
            item.setQuestion("Question " + index);
            item.setType(ChecklistItemType.YES_NO);
            item.setIsRequired(requiredFlags[index]);
            item.setDisplayOrder(index + 1);
            template.addItem(item);
        }
        return template;
    }

    private SiteVisit siteVisit(ChecklistTemplate template) {
        SiteVisit siteVisit = new SiteVisit();
        siteVisit.setLeadId(1L);
        siteVisit.setAssignedTo(5L);
        siteVisit.setScheduledDate(LocalDate.of(2026, 5, 25));
        siteVisit.setScheduledTime(LocalTime.of(11, 0));
        siteVisit.setLatitude(new BigDecimal("16.50617452"));
        siteVisit.setLongitude(new BigDecimal("80.64801532"));
        siteVisit.setChecklistTemplate(template);
        siteVisit.setStatus(SiteVisitStatus.SCHEDULED);
        return siteVisit;
    }
}
