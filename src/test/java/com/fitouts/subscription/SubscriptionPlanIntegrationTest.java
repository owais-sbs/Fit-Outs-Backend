package com.fitouts.subscription;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fitouts.shared.error.ConflictException;
import com.fitouts.subscription.application.SubscriptionPlanService;
import com.fitouts.subscription.domain.SubscriptionPlan;
import com.fitouts.subscription.domain.SubscriptionPlanRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionPlanIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubscriptionPlanRepository repository;

    @Autowired
    private SubscriptionPlanService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCanCreateUpdateAndDeactivatePlan() throws Exception {
        mockMvc.perform(post("/api/subscription-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planName":"Growth",
                                  "maxUsers":25,
                                  "modulesIncluded":["projects","finance"],
                                  "priceMonthly":199.00,
                                  "priceAnnual":1999.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uuid").isString())
                .andExpect(jsonPath("$.data.planName").value("Growth"))
                .andExpect(jsonPath("$.data.maxUsers").value(25))
                .andExpect(jsonPath("$.data.modulesIncluded.length()").value(2))
                .andExpect(jsonPath("$.data.priceMonthly").value(199.0))
                .andExpect(jsonPath("$.data.active").value(true));

        UUID uuid = repository.findByPlanNameIgnoreCase("Growth").orElseThrow().getUuid();

        mockMvc.perform(put("/api/subscription-plans/{uuid}", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planName":"Growth Plus",
                                  "maxUsers":50,
                                  "modulesIncluded":["projects","finance","sales"],
                                  "priceMonthly":299.00,
                                  "priceAnnual":2999.00,
                                  "active":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planName").value("Growth Plus"))
                .andExpect(jsonPath("$.data.maxUsers").value(50))
                .andExpect(jsonPath("$.data.modulesIncluded.length()").value(3));

        mockMvc.perform(delete("/api/subscription-plans/{uuid}", uuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Subscription plan deactivated successfully"));

        mockMvc.perform(get("/api/subscription-plans/{uuid}", uuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicatePlanNameIsRejectedIgnoringCase() throws Exception {
        repository.save(plan("Starter", true));

        mockMvc.perform(post("/api/subscription-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planName":"starter",
                                  "maxUsers":10,
                                  "modulesIncluded":["projects"],
                                  "priceMonthly":49.00,
                                  "priceAnnual":499.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Subscription plan name already exists"));
    }

    @Test
    void plansCanBeCreatedWithoutLogin() throws Exception {
        mockMvc.perform(post("/api/subscription-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planName":"Designer Plan",
                                  "maxUsers":10,
                                  "modulesIncluded":["projects"],
                                  "priceMonthly":49.00,
                                  "priceAnnual":499.00
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void plansCanBeReadWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/subscription-plans"))
                .andExpect(status().isOk());
    }

    @Test
    void inactivePlanCannotBeSelectedForNewTenantAssignment() {
        SubscriptionPlan inactivePlan = repository.save(plan("Legacy", false));

        assertThatThrownBy(() -> service.getAssignablePlan(inactivePlan.getUuid()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Inactive subscription plans cannot be assigned to new tenants");
    }

    private SubscriptionPlan plan(String name, boolean active) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanName(name);
        plan.setMaxUsers(10);
        plan.setModulesIncluded(Set.of("projects"));
        plan.setPriceMonthly(new BigDecimal("49.00"));
        plan.setPriceAnnual(new BigDecimal("499.00"));
        plan.setIsActive(active);
        return plan;
    }
}
