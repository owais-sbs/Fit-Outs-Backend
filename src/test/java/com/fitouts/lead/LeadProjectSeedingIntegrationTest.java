package com.fitouts.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.domain.AuthSessionRecordRepository;
import com.fitouts.auth.domain.PasswordSetupTokenRepository;
import com.fitouts.auth.domain.RememberedDeviceRepository;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;
import com.fitouts.lead.domain.Lead;
import com.fitouts.lead.domain.LeadRepository;
import com.fitouts.lead.domain.LeadStatus;
import com.fitouts.lead.domain.LeadStatusHistoryRepository;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.subscription.domain.SubscriptionPlan;
import com.fitouts.subscription.domain.SubscriptionPlanRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeadProjectSeedingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private LeadStatusHistoryRepository leadStatusHistoryRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AuthSessionRecordRepository authSessionRecordRepository;
    @Autowired private PasswordSetupTokenRepository passwordSetupTokenRepository;
    @Autowired private RememberedDeviceRepository rememberedDeviceRepository;

    private Cookie sessionCookie;
    private UUID companyId;
    private Company company;

    @BeforeEach
    void setUp() throws Exception {
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
        plan.setPlanName("Lead Project Test Plan");
        plan.setMaxUsers(50);
        plan.setModulesIncluded(Set.of("projects", "leads"));
        plan.setPriceMonthly(new BigDecimal("99.00"));
        plan.setPriceAnnual(new BigDecimal("999.00"));
        plan.setIsActive(true);
        plan = subscriptionPlanRepository.save(plan);

        company = new Company();
        company.setCompanyName("Lead Project Test Co");
        company.setSubscriptionPlan(plan);
        company = companyRepository.save(company);
        companyId = company.getUuid();

        Account admin = new Account();
        admin.setFullName("Lead Project Admin");
        admin.setEmail("lead-project-admin@test.com");
        admin.setPassword(passwordEncoder.encode("Password@123"));
        admin.setPhone("9999999991");
        admin.setCompanyName(company.getCompanyName());
        admin.setCompany(company);
        admin.setIsActive(true);
        admin.setRoles(Set.of(Role.ADMIN));
        accountRepository.save(admin);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"lead-project-admin@test.com","password":"Password@123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        sessionCookie = login.getResponse().getCookie("FITOUTS_SESSION");
    }

    @Test
    void convertToClientSeedsProjectWithLeadReference() throws Exception {
        Lead lead = saveLead("client-a@test.com", "Client A", "LEAD-AAA11111");

        mockMvc.perform(post("/api/leads/{id}/convert-to-client", lead.getId()).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLIENT"));

        Project project = projectRepository.findByCompanyIdAndLeadIdAndIsDeletedFalse(companyId, lead.getId())
                .orElseThrow();
        assertThat(project.getLeadId()).isEqualTo(lead.getId());
        assertThat(project.getLeadReferenceNo()).isEqualTo("LEAD-AAA11111");
        assertThat(project.getClientId()).isNotNull();

        mockMvc.perform(get("/api/projects").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.leadReferenceNo == 'LEAD-AAA11111')]").exists());
    }

    @Test
    void repeatConversionForSameLeadIsIdempotent() throws Exception {
        Lead lead = saveLead("client-b@test.com", "Client B", "LEAD-BBB22222");

        mockMvc.perform(post("/api/leads/{id}/convert-to-client", lead.getId()).cookie(sessionCookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/leads/{id}/convert-to-client", lead.getId()).cookie(sessionCookie))
                .andExpect(status().isOk());

        assertThat(projectRepository.findByCompanyIdAndIsDeletedFalse(companyId)).hasSize(1);
    }

    @Test
    void twoLeadsForSameClientEmailCreateSeparateProjects() throws Exception {
        Lead leadOne = saveLead("shared-client@test.com", "Shared Client", "LEAD-CCC33333");
        Lead leadTwo = saveLead("shared-client@test.com", "Shared Client", "LEAD-DDD44444");

        mockMvc.perform(post("/api/leads/{id}/convert-to-client", leadOne.getId()).cookie(sessionCookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/leads/{id}/convert-to-client", leadTwo.getId()).cookie(sessionCookie))
                .andExpect(status().isOk());

        assertThat(projectRepository.findByCompanyIdAndIsDeletedFalse(companyId)).hasSize(2);
        assertThat(projectRepository.findByCompanyIdAndLeadIdAndIsDeletedFalse(companyId, leadOne.getId()))
                .isPresent();
        assertThat(projectRepository.findByCompanyIdAndLeadIdAndIsDeletedFalse(companyId, leadTwo.getId()))
                .isPresent();
    }

    @Test
    void createAccountAlsoSeedsProjectWithLeadReference() throws Exception {
        Lead lead = saveLead("client-c@test.com", "Client C", "LEAD-EEE55555");

        mockMvc.perform(post("/api/leads/{id}/create-account", lead.getId()).cookie(sessionCookie))
                .andExpect(status().isOk());

        Project project = projectRepository.findByCompanyIdAndLeadIdAndIsDeletedFalse(companyId, lead.getId())
                .orElseThrow();
        assertThat(project.getLeadReferenceNo()).isEqualTo("LEAD-EEE55555");
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
}
