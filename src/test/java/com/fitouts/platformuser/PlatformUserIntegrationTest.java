package com.fitouts.platformuser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Set;

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
import com.fitouts.company.domain.CompanyStatus;
import com.fitouts.platformuser.application.AccountPurgeScheduler;
import com.fitouts.subscription.domain.SubscriptionPayment;
import com.fitouts.subscription.domain.SubscriptionPaymentRepository;
import com.fitouts.subscription.domain.SubscriptionPaymentStatus;
import com.fitouts.subscription.domain.SubscriptionPlan;
import com.fitouts.subscription.domain.SubscriptionPlanRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformUserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private SubscriptionPaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountPurgeScheduler accountPurgeScheduler;

    @Autowired
    private AuthSessionRecordRepository authSessionRecordRepository;

    @Autowired
    private RememberedDeviceRepository rememberedDeviceRepository;

    private Cookie superSession;

    @BeforeEach
    void setUp() throws Exception {
        authSessionRecordRepository.deleteAll();
        rememberedDeviceRepository.deleteAll();
        paymentRepository.deleteAll();
        accountRepository.deleteAll();
        companyRepository.deleteAll();
        planRepository.deleteAll();

        accountRepository.save(account("super@fitouts.com", "Super Admin", Set.of(Role.SUPER_ADMIN)));
        accountRepository.save(account("admin@fitouts.com", "Landing Admin", Set.of(Role.ADMIN)));
        accountRepository.save(account("admin@fitouts.demo", "Demo Admin", Set.of(Role.ADMIN)));
        accountRepository.save(account("director@fitouts.demo", "Demo Director", Set.of(Role.BUSINESS_OWNER)));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"super@fitouts.com","password":"Password@123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        superSession = loginResult.getResponse().getCookie("FITOUTS_SESSION");
    }

    @Test
    void listsActiveAdminsAndSupportsDeletionLifecycle() throws Exception {
        mockMvc.perform(get("/api/platform-users?filter=all").cookie(superSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.com')]").exists());

        Account admin = accountRepository.findByEmail("admin@fitouts.com").orElseThrow();

        mockMvc.perform(post("/api/platform-users/" + admin.getId() + "/schedule-deletion")
                        .cookie(superSession))
                .andExpect(status().isOk());

        Account scheduled = accountRepository.findById(admin.getId()).orElseThrow();
        assertThat(scheduled.getPurgeAt()).isNotNull();
        assertThat(scheduled.getIsActive()).isFalse();

        mockMvc.perform(get("/api/platform-users?filter=deletion_queue").cookie(superSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.com')]").exists());

        mockMvc.perform(post("/api/platform-users/" + admin.getId() + "/reactivate")
                        .cookie(superSession))
                .andExpect(status().isOk());

        Account reactivated = accountRepository.findById(admin.getId()).orElseThrow();
        assertThat(reactivated.getPurgeAt()).isNull();
        assertThat(reactivated.getIsActive()).isTrue();
    }

    @Test
    void subscriberFilterRequiresPayment() throws Exception {
        Company company = new Company();
        company.setCompanyName("Subscriber Co");
        company.setDomainSlug("subscriber-co");
        company.setStatus(CompanyStatus.TRIAL);
        company = companyRepository.save(company);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanName("Basic");
        plan.setMaxUsers(10);
        plan.setPriceMonthly(BigDecimal.TEN);
        plan.setPriceAnnual(BigDecimal.valueOf(100));
        plan.setIsActive(true);
        plan = planRepository.save(plan);

        Account admin = accountRepository.findByEmail("admin@fitouts.com").orElseThrow();
        admin.setCompany(company);
        admin.setCompanyName(company.getCompanyName());
        accountRepository.save(admin);

        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setCompany(company);
        payment.setPlan(plan);
        payment.setAmount(BigDecimal.TEN);
        payment.setPaymentMethod("Bank transfer");
        payment.setStatus(SubscriptionPaymentStatus.PENDING);
        paymentRepository.save(payment);

        mockMvc.perform(get("/api/platform-users?filter=subscribers").cookie(superSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.com')]").exists());
    }

    @Test
    void deletionQueueRequiresProperDeletionSchedule() throws Exception {
        Account admin = accountRepository.findByEmail("admin@fitouts.com").orElseThrow();
        admin.setPurgeAt(java.time.OffsetDateTime.now().plusDays(3));
        accountRepository.save(admin);

        mockMvc.perform(get("/api/platform-users?filter=deletion_queue").cookie(superSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.com')]").doesNotExist());

        admin.setDeletionScheduledAt(java.time.OffsetDateTime.now());
        accountRepository.save(admin);

        mockMvc.perform(get("/api/platform-users?filter=deletion_queue").cookie(superSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.com')]").exists())
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.com')].deletionScheduledAt").exists())
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.com')].daysUntilPurge").exists());
    }

    @Test
    void developerSeedFilterListsCoreSeedsAndExcludesThemFromAll() throws Exception {
        mockMvc.perform(get("/api/platform-users?filter=all").cookie(superSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.com')]").exists())
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.demo')]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@.email=='director@fitouts.demo')]").doesNotExist());

        mockMvc.perform(get("/api/platform-users?filter=developer_seed").cookie(superSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.demo')]").exists())
                .andExpect(jsonPath("$.data[?(@.email=='director@fitouts.demo')]").exists())
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.demo')].roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data[?(@.email=='director@fitouts.demo')].roles[0]").value("BUSINESS_OWNER"))
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.demo')].deletable").value(true))
                .andExpect(jsonPath("$.data[?(@.email=='director@fitouts.demo')].deletable").value(false));
    }

    @Test
    void subscriberFilterExcludesDeveloperSeeds() throws Exception {
        Company company = new Company();
        company.setCompanyName("Demo Co");
        company.setDomainSlug("demo-co");
        company.setStatus(CompanyStatus.TRIAL);
        company = companyRepository.save(company);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanName("Basic");
        plan.setMaxUsers(10);
        plan.setPriceMonthly(BigDecimal.TEN);
        plan.setPriceAnnual(BigDecimal.valueOf(100));
        plan.setIsActive(true);
        plan = planRepository.save(plan);

        Account seedAdmin = accountRepository.findByEmail("admin@fitouts.demo").orElseThrow();
        seedAdmin.setCompany(company);
        seedAdmin.setCompanyName(company.getCompanyName());
        accountRepository.save(seedAdmin);

        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setCompany(company);
        payment.setPlan(plan);
        payment.setAmount(BigDecimal.TEN);
        payment.setPaymentMethod("Bank transfer");
        payment.setStatus(SubscriptionPaymentStatus.PENDING);
        paymentRepository.save(payment);

        mockMvc.perform(get("/api/platform-users?filter=subscribers").cookie(superSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email=='admin@fitouts.demo')]").doesNotExist());
    }

    @Test
    void purgeSchedulerRemovesStandaloneAccountAfterGracePeriod() {
        Account admin = accountRepository.findByEmail("admin@fitouts.com").orElseThrow();
        admin.setDeletionScheduledAt(java.time.OffsetDateTime.now().minusDays(8));
        admin.setPurgeAt(java.time.OffsetDateTime.now().minusDays(1));
        admin.setIsActive(false);
        accountRepository.save(admin);

        accountPurgeScheduler.purgeDueAccounts();

        assertThat(accountRepository.findById(admin.getId())).isEmpty();
    }

    private Account account(String email, String name, Set<Role> roles) {
        Account account = new Account();
        account.setEmail(email);
        account.setFullName(name);
        account.setPassword(passwordEncoder.encode("Password@123"));
        account.setIsActive(true);
        account.setRoles(roles);
        return account;
    }
}
