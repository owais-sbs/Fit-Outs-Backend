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

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.checklist.domain.ChecklistItemType;
import com.fitouts.checklist.domain.ChecklistTemplate;
import com.fitouts.checklist.domain.ChecklistTemplateItem;
import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitStatus;
import com.fitouts.checklist.repository.ChecklistTemplateItemRepository;
import com.fitouts.checklist.repository.ChecklistTemplateRepository;
import com.fitouts.checklist.repository.SiteVisitReportRepository;
import com.fitouts.checklist.repository.SiteVisitRepository;
import com.fitouts.lead.domain.Lead;
import com.fitouts.lead.domain.LeadRepository;
import com.fitouts.lead.domain.LeadStatus;

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

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        reportRepository.deleteAll();
        siteVisitRepository.deleteAll();
        templateRepository.deleteAll();
        leadRepository.deleteAll();
    }

    @Test
    void checklistSchedulingLocationAndReportFlowPersistsPhotoAnswers() throws Exception {
        Lead lead = leadRepository.save(lead("claire@mossinteriors.com", "Claire Moss"));

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
                                  "leadId":%d,
                                  "assignedTo":5,
                                  "scheduledDate":"2026-05-25",
                                  "scheduledTime":"11:00",
                                  "latitude":16.50617452,
                                  "longitude":80.64801532,
                                  "checklistTemplateUuid":"%s",
                                  "notes":"Initial renovation visit"
                                }
                                """.formatted(lead.getId(), template.getUuid())))
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

        mockMvc.perform(post("/api/site-visits/EmployeeSiteVisitByUuid/{uuid}/report", siteVisitUuid)
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
                .andExpect(jsonPath("$.data.items[0].photoUrls.length()").value(3))
                .andExpect(jsonPath("$.data.clientAccountCreated").value(true))
                .andExpect(jsonPath("$.data.clientEmail").value("claire@mossinteriors.com"))
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty());

        mockMvc.perform(get("/api/site-visits/GetSiteVisitByUuid/{uuid}", siteVisitUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.locationDetails.pincode").value("500081"));

        Account clientAccount = accountRepository.findByEmail("claire@mossinteriors.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(clientAccount.getRoles()).contains(Role.CLIENT);
        org.assertj.core.api.Assertions.assertThat(leadRepository.findById(lead.getId()).orElseThrow().getStatus())
                .isEqualTo(LeadStatus.CLIENT);
    }

    @Test
    void reportRejectsMissingRequiredChecklistAnswers() throws Exception {
        Lead lead = leadRepository.save(lead("missing-required@example.com", "Missing Required"));
        ChecklistTemplate template = templateRepository.save(template("Required Checklist", true, true));
        SiteVisit siteVisit = siteVisitRepository.save(siteVisit(template, lead.getId()));
        List<ChecklistTemplateItem> items = orderedItems();

        mockMvc.perform(post("/api/site-visits/EmployeeSiteVisitByUuid/{uuid}/report", siteVisit.getUuid())
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
        Lead lead = leadRepository.save(lead("other-template@example.com", "Other Template"));
        ChecklistTemplate visitTemplate = templateRepository.save(template("Visit Checklist", true));
        ChecklistTemplate otherTemplate = templateRepository.save(template("Other Checklist", false));
        SiteVisit siteVisit = siteVisitRepository.save(siteVisit(visitTemplate, lead.getId()));
        var otherTemplateItemUuid = otherTemplate.getItems().get(0).getUuid();

        mockMvc.perform(post("/api/site-visits/EmployeeSiteVisitByUuid/{uuid}/report", siteVisit.getUuid())
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

    @Test
    void reportCompletionReusesExistingClientAccount() throws Exception {
        Lead lead = leadRepository.save(lead("existing-client@example.com", "Existing Client"));
        Account account = new Account();
        account.setFullName("Existing Client");
        account.setEmail("existing-client@example.com");
        account.setPassword("encoded-password");
        account.setIsActive(true);
        account.setRoles(new java.util.HashSet<>(java.util.Set.of(Role.ADMIN)));
        accountRepository.save(account);

        ChecklistTemplate template = templateRepository.save(template("Reusable Checklist", true));
        SiteVisit siteVisit = siteVisitRepository.save(siteVisit(template, lead.getId()));
        var templateItemUuid = orderedItems().get(0).getUuid();

        mockMvc.perform(post("/api/site-visits/EmployeeSiteVisitByUuid/{uuid}/report", siteVisit.getUuid())
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
                                """.formatted(templateItemUuid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientAccountCreated").value(false))
                .andExpect(jsonPath("$.data.clientEmail").value("existing-client@example.com"))
                .andExpect(jsonPath("$.data.temporaryPassword").value(org.hamcrest.Matchers.nullValue()));

        Account updatedAccount = accountRepository.findByEmail("existing-client@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updatedAccount.getRoles()).contains(Role.ADMIN, Role.CLIENT);
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

    private Lead lead(String email, String clientName) {
        Lead lead = new Lead();
        lead.setClientName(clientName);
        lead.setEmail(email);
        lead.setPhone("9999999999");
        lead.setCompany("Moss Interiors");
        lead.setStatus(LeadStatus.QUALIFIED);
        lead.setIsactive(true);
        lead.setIsdeleted(false);
        return lead;
    }

    private SiteVisit siteVisit(ChecklistTemplate template, Long leadId) {
        SiteVisit siteVisit = new SiteVisit();
        siteVisit.setLeadId(leadId);
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
