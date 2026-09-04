package com.fitouts.schedule;

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
import com.fitouts.auth.domain.Role;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;
import com.fitouts.planning.domain.PlanAreaStatus;
import com.fitouts.planning.domain.ProjectPlanningStatus;
import com.fitouts.planning.domain.ProjectPlanningStatusRepository;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.roomcollab.domain.ProjectRoom;
import com.fitouts.roomcollab.domain.ProjectRoomRepository;
import com.fitouts.roomcollab.domain.RoomTask;
import com.fitouts.roomcollab.domain.RoomTaskRepository;
import com.fitouts.roomcollab.domain.RoomTaskStatus;
import com.fitouts.roomcollab.domain.RoomTaskType;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.schedule.domain.SchedulePublishStatus;
import com.fitouts.subscription.domain.SubscriptionPlan;
import com.fitouts.subscription.domain.SubscriptionPlanRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScheduleModuleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectRoomRepository projectRoomRepository;
    @Autowired private RoomTaskRepository roomTaskRepository;
    @Autowired private ProjectPlanningStatusRepository planningStatusRepository;
    @Autowired private ScheduleActivityRepository scheduleActivityRepository;
    @Autowired private ObjectMapper objectMapper;

    private Cookie sessionCookie;
    private UUID companyId;
    private Long projectId;
    private Long otherProjectId;
    private UUID roomId;
    private UUID roomTaskId;
    private UUID otherProjectTaskId;

    @BeforeEach
    void setUp() throws Exception {
        scheduleActivityRepository.deleteAll();
        roomTaskRepository.deleteAll();
        projectRoomRepository.deleteAll();
        planningStatusRepository.deleteAll();
        projectRepository.deleteAll();
        accountRepository.deleteAll();
        companyRepository.deleteAll();
        subscriptionPlanRepository.deleteAll();

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanName("Test Plan");
        plan.setMaxUsers(50);
        plan.setModulesIncluded(Set.of("projects"));
        plan.setPriceMonthly(new BigDecimal("99.00"));
        plan.setPriceAnnual(new BigDecimal("999.00"));
        plan.setIsActive(true);
        plan = subscriptionPlanRepository.save(plan);

        Company company = new Company();
        company.setCompanyName("Schedule Test Co");
        company.setSubscriptionPlan(plan);
        company = companyRepository.save(company);
        companyId = company.getUuid();

        Account admin = new Account();
        admin.setFullName("Schedule Admin");
        admin.setEmail("schedule-admin@test.com");
        admin.setPassword(passwordEncoder.encode("Password@123"));
        admin.setPhone("9999999991");
        admin.setCompanyName(company.getCompanyName());
        admin.setCompany(company);
        admin.setIsActive(true);
        admin.setRoles(Set.of(Role.ADMIN));
        accountRepository.save(admin);

        Project project = new Project();
        project.setName("Schedule Project A");
        project.setCompanyId(companyId);
        project = projectRepository.save(project);
        projectId = project.getId();

        Project other = new Project();
        other.setName("Schedule Project B");
        other.setCompanyId(companyId);
        other = projectRepository.save(other);
        otherProjectId = other.getId();

        ProjectRoom room = new ProjectRoom();
        room.setProjectId(projectId);
        room.setCompanyId(companyId);
        room.setName("Kitchen");
        room.setFloorLabel("Ground");
        room = projectRoomRepository.save(room);
        roomId = room.getUuid();

        RoomTask task = new RoomTask();
        task.setProjectId(projectId);
        task.setProjectRoomId(roomId);
        task.setCompanyId(companyId);
        task.setTitle("Tile approval");
        task.setTaskType(RoomTaskType.TILE_SELECTION);
        task.setStatus(RoomTaskStatus.OPEN);
        task = roomTaskRepository.save(task);
        roomTaskId = task.getUuid();

        ProjectRoom otherRoom = new ProjectRoom();
        otherRoom.setProjectId(otherProjectId);
        otherRoom.setCompanyId(companyId);
        otherRoom.setName("Lobby");
        otherRoom = projectRoomRepository.save(otherRoom);

        RoomTask otherTask = new RoomTask();
        otherTask.setProjectId(otherProjectId);
        otherTask.setProjectRoomId(otherRoom.getUuid());
        otherTask.setCompanyId(companyId);
        otherTask.setTitle("Other project task");
        otherTask.setTaskType(RoomTaskType.OTHER);
        otherTask.setStatus(RoomTaskStatus.OPEN);
        otherTask = roomTaskRepository.save(otherTask);
        otherProjectTaskId = otherTask.getUuid();

        ProjectPlanningStatus planning = new ProjectPlanningStatus();
        planning.setProjectId(projectId);
        planning.setCompanyId(companyId);
        planning.setPlanningReady(true);
        planning.setGanttPublishAllowed(true);
        planning.setMaterialStatus(PlanAreaStatus.NOT_REQUIRED);
        planning.setResourceStatus(PlanAreaStatus.NOT_REQUIRED);
        planning.setLabourStatus(PlanAreaStatus.NOT_REQUIRED);
        planning.setSubcontractorStatus(PlanAreaStatus.NOT_REQUIRED);
        planningStatusRepository.save(planning);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"schedule-admin@test.com","password":"Password@123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        sessionCookie = login.getResponse().getCookie("FITOUTS_SESSION");
    }

    @Test
    void createActivityWithValidRoomTaskLinkPersistsIds() throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/schedule/activities", projectId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Kitchen tile gate",
                                  "startDate":"2026-06-01",
                                  "endDate":"2026-06-10",
                                  "percentComplete":0,
                                  "roomTaskId":"%s"
                                }
                                """.formatted(roomTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomTaskId").value(roomTaskId.toString()))
                .andExpect(jsonPath("$.data.projectRoomId").value(roomId.toString()))
                .andExpect(jsonPath("$.data.roomTaskTitle").value("Tile approval"));
    }

    @Test
    void createActivityWithTaskFromWrongProjectIsRejected() throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/schedule/activities", projectId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Bad link",
                                  "startDate":"2026-06-01",
                                  "endDate":"2026-06-05",
                                  "roomTaskId":"%s"
                                }
                                """.formatted(otherProjectTaskId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishSetsActivitiesPublishedAndCalendarReturnsThem() throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/schedule/activities", projectId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Published task",
                                  "startDate":"2026-07-01",
                                  "endDate":"2026-07-05",
                                  "percentComplete":10
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/{projectId}/schedule/publish", projectId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activities[0].publishStatus").value("PUBLISHED"));

        mockMvc.perform(get("/api/schedule/calendar-events")
                        .cookie(sessionCookie)
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Published task"));
    }

    @Test
    void calendarEventsExcludeDraftActivities() throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/schedule/activities", projectId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Draft only",
                                  "startDate":"2026-08-01",
                                  "endDate":"2026-08-03"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/schedule/calendar-events")
                        .cookie(sessionCookie)
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void postProgressUpdatesPercentAndCreatesHistory() throws Exception {
        MvcResult create = mockMvc.perform(post("/api/projects/{projectId}/schedule/activities", projectId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Progress task",
                                  "startDate":"2026-09-01",
                                  "endDate":"2026-09-05",
                                  "percentComplete":0
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String body = create.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        String uuid = json.path("data").path("uuid").asText();

        mockMvc.perform(post("/api/schedule/activities/{uuid}/progress", uuid)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"percentComplete":45,"notes":"Half done"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentComplete").value(45));

        mockMvc.perform(get("/api/schedule/activities/{uuid}/progress", uuid)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].percentComplete").value(45));

        var activity = scheduleActivityRepository.findByUuidAndCompanyId(UUID.fromString(uuid), companyId)
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(activity.getPercentComplete()).isEqualTo(45);
        org.assertj.core.api.Assertions.assertThat(activity.getPublishStatus()).isEqualTo(SchedulePublishStatus.DRAFT);
    }

    @Test
    void createActivityFromRoomTaskPrefillsFields() throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/schedule/activities/from-room-task", projectId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomTaskId":"%s"}
                                """.formatted(roomTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Tile approval"))
                .andExpect(jsonPath("$.data.roomTaskId").value(roomTaskId.toString()))
                .andExpect(jsonPath("$.data.projectRoomId").value(roomId.toString()));
    }
}
