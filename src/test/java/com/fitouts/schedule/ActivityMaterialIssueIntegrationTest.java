package com.fitouts.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.AuthSessionRecordRepository;
import com.fitouts.auth.domain.RememberedDeviceRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;
import com.fitouts.materialplan.domain.MaterialPlanStatus;
import com.fitouts.materialplan.domain.ProjectMaterialPlan;
import com.fitouts.materialplan.domain.ProjectMaterialPlanLine;
import com.fitouts.materialplan.domain.ProjectMaterialPlanLineRepository;
import com.fitouts.materialplan.domain.ProjectMaterialPlanRepository;
import com.fitouts.planning.domain.PlanAreaStatus;
import com.fitouts.planning.domain.ProjectPlanningStatus;
import com.fitouts.planning.domain.ProjectPlanningStatusRepository;
import com.fitouts.procurement.domain.Material;
import com.fitouts.procurement.domain.MaterialRepository;
import com.fitouts.procurement.domain.MaterialStock;
import com.fitouts.procurement.domain.MaterialStockRepository;
import com.fitouts.procurement.domain.StockMovementRepository;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.schedule.domain.ActivityMaterialIssueRepository;
import com.fitouts.schedule.domain.ActivityMaterialIssueStatus;
import com.fitouts.schedule.domain.ActivityProgressUpdateRepository;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.shared.enums.UnitType;
import com.fitouts.subscription.domain.SubscriptionPlan;
import com.fitouts.subscription.domain.SubscriptionPlanRepository;
import com.fitouts.validation.domain.ProgressValidationRepository;
import com.fitouts.validation.domain.ProgressValidationStatus;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActivityMaterialIssueIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AuthSessionRecordRepository authSessionRecordRepository;
    @Autowired private RememberedDeviceRepository rememberedDeviceRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectPlanningStatusRepository planningStatusRepository;
    @Autowired private ScheduleActivityRepository scheduleActivityRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private MaterialStockRepository materialStockRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private ProjectMaterialPlanRepository planRepository;
    @Autowired private ProjectMaterialPlanLineRepository planLineRepository;
    @Autowired private ActivityMaterialIssueRepository issueRepository;
    @Autowired private ActivityProgressUpdateRepository progressUpdateRepository;
    @Autowired private ProgressValidationRepository validationRepository;
    @Autowired private ObjectMapper objectMapper;

    private Cookie sessionCookie;
    private UUID companyId;
    private Long projectId;
    private Company company;
    private Material material;
    private UUID planLineUuid;

    @BeforeEach
    void setUp() throws Exception {
        issueRepository.deleteAll();
        validationRepository.deleteAll();
        progressUpdateRepository.deleteAll();
        scheduleActivityRepository.deleteAll();
        planLineRepository.deleteAll();
        planRepository.deleteAll();
        stockMovementRepository.deleteAll();
        materialStockRepository.deleteAll();
        materialRepository.deleteAll();
        planningStatusRepository.deleteAll();
        projectRepository.deleteAll();
        authSessionRecordRepository.deleteAll();
        rememberedDeviceRepository.deleteAll();
        accountRepository.deleteAll();
        companyRepository.deleteAll();
        subscriptionPlanRepository.deleteAll();

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanName("Material Issue Plan");
        plan.setMaxUsers(50);
        plan.setModulesIncluded(Set.of("projects"));
        plan.setPriceMonthly(new BigDecimal("99.00"));
        plan.setPriceAnnual(new BigDecimal("999.00"));
        plan.setIsActive(true);
        plan = subscriptionPlanRepository.save(plan);

        company = new Company();
        company.setCompanyName("Material Issue Co");
        company.setSubscriptionPlan(plan);
        company = companyRepository.save(company);
        companyId = company.getUuid();

        Account admin = new Account();
        admin.setFullName("Material Admin");
        admin.setEmail("material-admin@test.com");
        admin.setPassword(passwordEncoder.encode("Password@123"));
        admin.setPhone("9999999992");
        admin.setCompanyName(company.getCompanyName());
        admin.setCompany(company);
        admin.setIsActive(true);
        admin.setRoles(Set.of(Role.ADMIN));
        accountRepository.save(admin);

        Project project = new Project();
        project.setName("Material Issue Project");
        project.setCompanyId(companyId);
        project = projectRepository.save(project);
        projectId = project.getId();

        ProjectPlanningStatus planning = new ProjectPlanningStatus();
        planning.setProjectId(projectId);
        planning.setCompanyId(companyId);
        planning.setPlanningReady(true);
        planning.setGanttPublishAllowed(true);
        planning.setMaterialStatus(PlanAreaStatus.READY);
        planning.setResourceStatus(PlanAreaStatus.NOT_REQUIRED);
        planning.setLabourStatus(PlanAreaStatus.NOT_REQUIRED);
        planning.setSubcontractorStatus(PlanAreaStatus.NOT_REQUIRED);
        planningStatusRepository.save(planning);

        material = Material.builder()
                .company(company)
                .materialName("Tile Adhesive")
                .materialCode("ADH-001")
                .unitType(UnitType.BAG)
                .costPrice(new BigDecimal("10.00"))
                .active(true)
                .deleted(false)
                .build();
        material = materialRepository.save(material);

        MaterialStock stock = MaterialStock.builder()
                .company(company)
                .material(material)
                .quantityOnHand(new BigDecimal("100"))
                .quantityReserved(BigDecimal.ZERO)
                .build();
        materialStockRepository.save(stock);

        ProjectMaterialPlan materialPlan = new ProjectMaterialPlan();
        materialPlan.setProjectId(projectId);
        materialPlan.setCompanyId(companyId);
        materialPlan.setStatus(MaterialPlanStatus.READY);
        materialPlan = planRepository.save(materialPlan);

        ProjectMaterialPlanLine line = new ProjectMaterialPlanLine();
        line.setPlanUuid(materialPlan.getUuid());
        line.setMaterialId(material.getId());
        line.setMaterialName(material.getMaterialName());
        line.setPlannedQty(new BigDecimal("50"));
        line.setStockQtySnapshot(new BigDecimal("100"));
        line.setUnit("BAG");
        line.setShortageFlag(false);
        line.setReservedQty(BigDecimal.ZERO);
        line.setSortOrder(0);
        line = planLineRepository.save(line);
        planLineUuid = line.getUuid();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"material-admin@test.com","password":"Password@123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        sessionCookie = login.getResponse().getCookie("FITOUTS_SESSION");
    }

    private String createActivity() throws Exception {
        MvcResult create = mockMvc.perform(post("/api/projects/{projectId}/schedule/activities", projectId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Tiling task",
                                  "startDate":"2026-09-01",
                                  "endDate":"2026-09-05",
                                  "percentComplete":0
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(create.getResponse().getContentAsString())
                .path("data").path("uuid").asText();
    }

    @Test
    void progressWithoutMaterialIssuesUnchanged() throws Exception {
        String activityUuid = createActivity();

        mockMvc.perform(post("/api/schedule/activities/{uuid}/progress", activityUuid)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"percentComplete":30,"notes":"No materials"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentComplete").value(30))
                .andExpect(jsonPath("$.data.materialIssues").isEmpty());

        assertThat(issueRepository.findAll()).isEmpty();
        assertThat(materialStockRepository.findAll().get(0).getQuantityOnHand())
                .isEqualByComparingTo("100");
        assertThat(scheduleActivityRepository.findByUuidAndCompanyId(UUID.fromString(activityUuid), companyId)
                .orElseThrow().getPercentComplete()).isEqualTo(0);
    }

    @Test
    void declareIssuesStayPendingUntilApprovePostsStockOnce() throws Exception {
        String activityUuid = createActivity();

        MvcResult progress = mockMvc.perform(post("/api/schedule/activities/{uuid}/progress", activityUuid)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "percentComplete":40,
                                  "materialIssues":[
                                    {"materialId":"%s","qty":12,"planLineUuid":"%s"}
                                  ]
                                }
                                """.formatted(material.getId(), planLineUuid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materialIssues[0].qty").value(12))
                .andExpect(jsonPath("$.data.materialIssues[0].status").value("DECLARED"))
                .andExpect(jsonPath("$.data.materialIssues[0].plannedQty").value(50))
                .andReturn();

        String progressUuid = objectMapper.readTree(progress.getResponse().getContentAsString())
                .path("data").path("uuid").asText();

        assertThat(materialStockRepository.findAll().get(0).getQuantityOnHand())
                .isEqualByComparingTo("100");
        assertThat(scheduleActivityRepository.findByUuidAndCompanyId(UUID.fromString(activityUuid), companyId)
                .orElseThrow().getPercentComplete()).isEqualTo(0);

        UUID validationUuid = validationRepository.findByProgressUpdateUuid(UUID.fromString(progressUuid))
                .orElseThrow().getUuid();

        mockMvc.perform(post("/api/validation/{uuid}/approve", validationUuid)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertThat(scheduleActivityRepository.findByUuidAndCompanyId(UUID.fromString(activityUuid), companyId)
                .orElseThrow().getPercentComplete()).isEqualTo(40);
        assertThat(materialStockRepository.findAll().get(0).getQuantityOnHand())
                .isEqualByComparingTo("88");
        assertThat(issueRepository.findByProgressUpdateUuidOrderByCreatedAtAsc(UUID.fromString(progressUuid)))
                .allMatch(i -> i.getStatus() == ActivityMaterialIssueStatus.POSTED_TO_STOCK);

        // Second approve should fail because validation is no longer PENDING
        mockMvc.perform(post("/api/validation/{uuid}/approve", validationUuid)
                        .cookie(sessionCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));

        assertThat(materialStockRepository.findAll().get(0).getQuantityOnHand())
                .isEqualByComparingTo("88");
    }

    @Test
    void rejectVoidsIssuesWithoutStockMovement() throws Exception {
        String activityUuid = createActivity();

        MvcResult progress = mockMvc.perform(post("/api/schedule/activities/{uuid}/progress", activityUuid)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "percentComplete":25,
                                  "materialIssues":[{"materialId":"%s","qty":5}]
                                }
                                """.formatted(material.getId())))
                .andExpect(status().isOk())
                .andReturn();

        String progressUuid = objectMapper.readTree(progress.getResponse().getContentAsString())
                .path("data").path("uuid").asText();
        UUID validationUuid = validationRepository.findByProgressUpdateUuid(UUID.fromString(progressUuid))
                .orElseThrow().getUuid();

        mockMvc.perform(post("/api/validation/{uuid}/reject", validationUuid)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Photos unclear"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(issueRepository.findByProgressUpdateUuidOrderByCreatedAtAsc(UUID.fromString(progressUuid)))
                .allMatch(i -> i.getStatus() == ActivityMaterialIssueStatus.VOID);
        assertThat(materialStockRepository.findAll().get(0).getQuantityOnHand())
                .isEqualByComparingTo("100");
        assertThat(scheduleActivityRepository.findByUuidAndCompanyId(UUID.fromString(activityUuid), companyId)
                .orElseThrow().getPercentComplete()).isEqualTo(0);
        assertThat(validationRepository.findById(validationUuid).orElseThrow().getStatus())
                .isEqualTo(ProgressValidationStatus.REJECTED);
    }

    @Test
    void approveWithInsufficientStockLeavesPendingAndPercentUnchanged() throws Exception {
        MaterialStock stock = materialStockRepository.findAll().get(0);
        stock.setQuantityOnHand(new BigDecimal("3"));
        materialStockRepository.save(stock);

        String activityUuid = createActivity();

        MvcResult progress = mockMvc.perform(post("/api/schedule/activities/{uuid}/progress", activityUuid)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "percentComplete":50,
                                  "materialIssues":[{"materialId":"%s","qty":10}]
                                }
                                """.formatted(material.getId())))
                .andExpect(status().isOk())
                .andReturn();

        String progressUuid = objectMapper.readTree(progress.getResponse().getContentAsString())
                .path("data").path("uuid").asText();
        UUID validationUuid = validationRepository.findByProgressUpdateUuid(UUID.fromString(progressUuid))
                .orElseThrow().getUuid();

        mockMvc.perform(post("/api/validation/{uuid}/approve", validationUuid)
                        .cookie(sessionCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));

        assertThat(validationRepository.findById(validationUuid).orElseThrow().getStatus())
                .isEqualTo(ProgressValidationStatus.PENDING);
        assertThat(scheduleActivityRepository.findByUuidAndCompanyId(UUID.fromString(activityUuid), companyId)
                .orElseThrow().getPercentComplete()).isEqualTo(0);
        assertThat(materialStockRepository.findAll().get(0).getQuantityOnHand())
                .isEqualByComparingTo("3");
        assertThat(issueRepository.findByProgressUpdateUuidOrderByCreatedAtAsc(UUID.fromString(progressUuid)))
                .allMatch(i -> i.getStatus() == ActivityMaterialIssueStatus.DECLARED);
    }

    @Test
    void materialSummaryAndReportIncludeVariance() throws Exception {
        String activityUuid = createActivity();

        MvcResult progress = mockMvc.perform(post("/api/schedule/activities/{uuid}/progress", activityUuid)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "percentComplete":60,
                                  "materialIssues":[{"materialId":"%s","qty":8}]
                                }
                                """.formatted(material.getId())))
                .andExpect(status().isOk())
                .andReturn();

        String progressUuid = objectMapper.readTree(progress.getResponse().getContentAsString())
                .path("data").path("uuid").asText();
        UUID validationUuid = validationRepository.findByProgressUpdateUuid(UUID.fromString(progressUuid))
                .orElseThrow().getUuid();

        mockMvc.perform(get("/api/schedule/activities/{uuid}/material-summary", activityUuid)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].declaredQty").value(8))
                .andExpect(jsonPath("$.data[0].issuedQty").value(0))
                .andExpect(jsonPath("$.data[0].plannedQty").value(50));

        mockMvc.perform(post("/api/validation/{uuid}/approve", validationUuid)
                        .cookie(sessionCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedule/activities/{uuid}/material-summary", activityUuid)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].issuedQty").value(8))
                .andExpect(jsonPath("$.data[0].declaredQty").value(0))
                .andExpect(jsonPath("$.data[0].remainingQty").value(42));

        mockMvc.perform(get("/api/projects/{projectId}/progress-report", projectId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weightedCompletionPercent").value(60.0))
                .andExpect(jsonPath("$.data.materialVariance[0].issuedQty").value(8))
                .andExpect(jsonPath("$.data.materialVariance[0].plannedQty").value(50));
    }
}
