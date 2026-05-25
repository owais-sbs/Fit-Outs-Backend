package com.fitouts.tenant;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fitouts.account.domain.AccountRepository;
import com.fitouts.subscription.domain.SubscriptionPlan;
import com.fitouts.subscription.domain.SubscriptionPlanRepository;
import com.fitouts.tenant.domain.TenantRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        tenantRepository.deleteAll();
        subscriptionPlanRepository.deleteAll();
    }

    @Test
    void tenantCanBeCreatedSuspendedAndTerminated() throws Exception {
        UUID planUuid = subscriptionPlanRepository.save(plan("Growth")).getUuid();

        mockMvc.perform(post("/api/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName":"Acme Interiors",
                                  "logo":"https://assets.example/acme.png",
                                  "domainSlug":"acme-interiors",
                                  "subscriptionPlanUuid":"%s",
                                  "status":"ACTIVE"
                                }
                                """.formatted(planUuid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.uuid").isString())
                .andExpect(jsonPath("$.data.companyName").value("Acme Interiors"))
                .andExpect(jsonPath("$.data.domainSlug").value("acme-interiors"))
                .andExpect(jsonPath("$.data.subscriptionPlanUuid").value(planUuid.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        UUID tenantUuid = tenantRepository.findByDomainSlugIgnoreCase("acme-interiors").orElseThrow().getUuid();

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Acme Admin",
                                  "email":"admin@acme.test",
                                  "password":"Password@123",
                                  "phone":"9999999999",
                                  "companyName":"Acme Interiors",
                                  "tenantUuid":"%s",
                                  "roles":["ADMIN"]
                                }
                                """.formatted(tenantUuid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantUuid").value(tenantUuid.toString()));

        mockMvc.perform(post("/api/tenants/{uuid}/suspend", tenantUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        mockMvc.perform(post("/api/tenants/{uuid}/terminate", tenantUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TERMINATED"));
    }

    @Test
    void duplicateTenantSlugIsRejected() throws Exception {
        UUID planUuid = subscriptionPlanRepository.save(plan("Starter")).getUuid();
        tenantRepository.save(tenant("Starter Client", "starter-client", planUuid));

        mockMvc.perform(post("/api/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName":"Another Starter",
                                  "domainSlug":"starter-client",
                                  "subscriptionPlanUuid":"%s"
                                }
                                """.formatted(planUuid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Tenant domain slug already exists"));
    }

    private SubscriptionPlan plan(String name) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanName(name);
        plan.setMaxUsers(25);
        plan.setModulesIncluded(Set.of("projects"));
        plan.setPriceMonthly(new BigDecimal("199.00"));
        plan.setPriceAnnual(new BigDecimal("1999.00"));
        plan.setIsActive(true);
        return plan;
    }

    private com.fitouts.tenant.domain.Tenant tenant(String companyName, String domainSlug, UUID planUuid) {
        com.fitouts.tenant.domain.Tenant tenant = new com.fitouts.tenant.domain.Tenant();
        tenant.setCompanyName(companyName);
        tenant.setDomainSlug(domainSlug);
        tenant.setSubscriptionPlan(subscriptionPlanRepository.findById(planUuid).orElseThrow());
        return tenant;
    }
}
