package com.fitouts.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.AuthSessionRecordRepository;
import com.fitouts.auth.domain.PasswordSetupTokenRepository;
import com.fitouts.auth.domain.RememberedDeviceRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitEstimate;
import com.fitouts.checklist.domain.SiteVisitEstimateLine;
import com.fitouts.checklist.domain.SiteVisitEstimateStatus;
import com.fitouts.checklist.domain.SiteVisitStatus;
import com.fitouts.checklist.repository.SiteVisitEstimateRepository;
import com.fitouts.checklist.repository.SiteVisitRepository;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;
import com.fitouts.lead.domain.Lead;
import com.fitouts.lead.domain.LeadRepository;
import com.fitouts.lead.domain.LeadStatus;
import com.fitouts.lead.domain.LeadStatusHistoryRepository;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectQasSurveySeedRepository;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.subscription.domain.SubscriptionPlan;
import com.fitouts.subscription.domain.SubscriptionPlanRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeadQasSurveySeedIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private LeadStatusHistoryRepository leadStatusHistoryRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectQasSurveySeedRepository seedRepository;
    @Autowired private SiteVisitRepository siteVisitRepository;
    @Autowired private SiteVisitEstimateRepository estimateRepository;
    @Autowired private AuthSessionRecordRepository authSessionRecordRepository;
    @Autowired private PasswordSetupTokenRepository passwordSetupTokenRepository;
    @Autowired private RememberedDeviceRepository rememberedDeviceRepository;

    private Cookie sessionCookie;
    private UUID companyId;
    private Company company;

    @BeforeEach
    void setUp() throws Exception {
        seedRepository.deleteAll();
        estimateRepository.deleteAll();
        siteVisitRepository.deleteAll();
        projectRepository.deleteAll();
        leadStatusHistoryRepository.deleteAll();
        leadRepository.deleteAll();
        passwordSetupTokenRepository.deleteAll();
        authSessionRecordRepository.deleteAll();
        rememberedDeviceRepository.deleteAll();
        accountRepository.deleteAll();
        companyRepository.deleteAll();
        subscriptionPlanRepository.deleteAll();

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanName("QAS Seed Test Plan");
        plan.setMaxUsers(50);
        plan.setModulesIncluded(Set.of("projects", "leads"));
        plan.setPriceMonthly(new BigDecimal("99.00"));
        plan.setPriceAnnual(new BigDecimal("999.00"));
        plan.setIsActive(true);
        plan = subscriptionPlanRepository.save(plan);

        company = new Company();
        company.setCompanyName("QAS Seed Test Co");
        company.setSubscriptionPlan(plan);
        company = companyRepository.save(company);
        companyId = company.getUuid();

        Account admin = new Account();
        admin.setFullName("QAS Seed Admin");
        admin.setEmail("qas-seed-admin@test.com");
        admin.setPassword(passwordEncoder.encode("Password@123"));
        admin.setPhone("9999999992");
        admin.setCompanyName(company.getCompanyName());
        admin.setCompany(company);
        admin.setIsActive(true);
        admin.setRoles(Set.of(Role.ADMIN));
        accountRepository.save(admin);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"qas-seed-admin@test.com","password":"Password@123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        sessionCookie = login.getResponse().getCookie("FITOUTS_SESSION");
    }

    @Test
    void convertToClientSeedsQasSurveyFromIssuedEstimate() throws Exception {
        Lead lead = saveLead("qas-seed-client@test.com", "QAS Client", "LEAD-QAS11111");
        SiteVisitEstimate estimate = saveIssuedEstimateWithLines(lead);

        mockMvc.perform(post("/api/leads/{id}/convert-to-client", lead.getId()).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLIENT"));

        Project project = projectRepository.findByCompanyIdAndLeadIdAndIsDeletedFalse(companyId, lead.getId())
                .orElseThrow();

        assertThat(seedRepository.findByProjectIdAndCompanyId(project.getId(), companyId)).isPresent();

        mockMvc.perform(get("/api/projects/{id}/qas-survey-seed", project.getId()).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(project.getId().intValue()))
                .andExpect(jsonPath("$.data.sourceEstimateUuid").value(estimate.getUuid().toString()))
                .andExpect(jsonPath("$.data.floors.length()").value(1))
                .andExpect(jsonPath("$.data.floors[0].name").value("Ground Floor"))
                .andExpect(jsonPath("$.data.rooms.length()").value(1))
                .andExpect(jsonPath("$.data.rooms[0].name").value("Living Room"))
                .andExpect(jsonPath("$.data.rooms[0].savedLines.length()").value(1))
                .andExpect(jsonPath("$.data.rooms[0].savedLines[0].description").value("Paint walls"));
    }

    @Test
    void convertToClientWithoutIssuedEstimateLeavesSurveySeedEmpty() throws Exception {
        Lead lead = saveLead("qas-no-est@test.com", "No Est Client", "LEAD-QAS22222");

        mockMvc.perform(post("/api/leads/{id}/convert-to-client", lead.getId()).cookie(sessionCookie))
                .andExpect(status().isOk());

        Project project = projectRepository.findByCompanyIdAndLeadIdAndIsDeletedFalse(companyId, lead.getId())
                .orElseThrow();

        assertThat(seedRepository.findByProjectIdAndCompanyId(project.getId(), companyId)).isEmpty();

        mockMvc.perform(get("/api/projects/{id}/qas-survey-seed", project.getId()).cookie(sessionCookie))
                .andExpect(status().isNotFound());
    }

    private Lead saveLead(String email, String clientName, String referenceNo) {
        Lead lead = new Lead();
        lead.setClientName(clientName);
        lead.setEmail(email);
        lead.setPhone("9999999999");
        lead.setReferenceNo(referenceNo);
        lead.setStatus(LeadStatus.QUALIFIED);
        lead.setIsactive(true);
        lead.setIsdeleted(false);
        lead.setCreatedAt(LocalDateTime.now());
        lead.setUpdatedAt(LocalDateTime.now());
        lead.setLastActivityDate(LocalDateTime.now());
        lead.setCompanyEntity(company);
        return leadRepository.save(lead);
    }

    private SiteVisitEstimate saveIssuedEstimateWithLines(Lead lead) {
        SiteVisit visit = new SiteVisit();
        visit.setLeadId(lead.getId());
        visit.setScheduledDate(LocalDate.now());
        visit.setScheduledTime(LocalTime.of(10, 0));
        visit.setLatitude(new BigDecimal("25.20480000"));
        visit.setLongitude(new BigDecimal("55.27080000"));
        visit.setStatus(SiteVisitStatus.COMPLETED);
        visit.setCompany(company);
        visit = siteVisitRepository.save(visit);

        SiteVisitEstimate estimate = new SiteVisitEstimate();
        estimate.setSiteVisit(visit);
        estimate.setCompany(company);
        estimate.setStatus(SiteVisitEstimateStatus.ISSUED);
        estimate.setClientName(lead.getClientName());
        estimate.setSubtotal(new BigDecimal("1500.00"));

        SiteVisitEstimateLine line = new SiteVisitEstimateLine();
        line.setFloorName("Ground Floor");
        line.setRoomName("Living Room");
        line.setRoomTypeId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        line.setWorkItemId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        line.setCategory("Painting");
        line.setDescription("Paint walls");
        line.setQty(new BigDecimal("50"));
        line.setUnit("SQM");
        line.setRate(new BigDecimal("30"));
        line.setAmount(new BigDecimal("1500"));
        line.setDisplayOrder(1);
        line.setLineSource("CATALOG");
        estimate.addLine(line);

        return estimateRepository.save(estimate);
    }
}
