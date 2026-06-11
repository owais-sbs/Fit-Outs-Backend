package com.fitouts.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.fitouts.auth.domain.OtpChallengeRepository;
import com.fitouts.auth.domain.RememberedDeviceRepository;
import com.fitouts.auth.domain.Role;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OtpChallengeRepository otpChallengeRepository;

    @Autowired
    private RememberedDeviceRepository deviceRepository;

    @Autowired
    private AuthSessionRecordRepository authSessionRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        authSessionRecordRepository.deleteAll();
        otpChallengeRepository.deleteAll();
        deviceRepository.deleteAll();
        accountRepository.deleteAll();

        accountRepository.save(account("admin@fitouts.com", "Admin User", Set.of(Role.ADMIN)));
        accountRepository.save(account("super@fitouts.com", "Super Admin", Set.of(Role.SUPER_ADMIN)));
        accountRepository.save(account("designer@fitouts.com", "Designer User", Set.of(Role.DESIGNER)));
    }

    @Test
    void adminLoginCreatesSessionAndAllowsAdminEndpoint() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@fitouts.com","password":"Password@123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AUTHENTICATED"))
                .andExpect(cookie().exists("FITOUTS_SESSION"))
                .andExpect(cookie().exists("FITOUTS_DEVICE"))
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("FITOUTS_SESSION");

        mockMvc.perform(get("/api/accounts").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }


    @Test
    void authenticatedDesignerCanAccessAccountsWhileSecurityIsOpen() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"designer@fitouts.com","password":"Password@123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("FITOUTS_SESSION");

        mockMvc.perform(get("/api/accounts").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void sessionsEndpointReturnsCurrentSessionAndRevocationLogsOutCurrentSession() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@fitouts.com","password":"Password@123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("FITOUTS_SESSION");

        MvcResult sessionsResult = mockMvc.perform(get("/api/auth/sessions").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].current").value(true))
                .andReturn();

        JsonNode sessionsJson = objectMapper.readTree(sessionsResult.getResponse().getContentAsString());
        String sessionId = sessionsJson.get("data").get(0).get("sessionId").asText();

        mockMvc.perform(delete("/api/auth/sessions/{sessionId}", sessionId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Session revoked successfully"));

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isBadRequest());
    }

    private Account account(String email, String fullName, Set<Role> roles) {
        Account account = new Account();
        account.setFullName(fullName);
        account.setEmail(email);
        account.setPassword(passwordEncoder.encode("Password@123"));
        account.setPhone("9999999999");
        account.setCompanyName("Fitouts");
        account.setIsActive(true);
        account.setRoles(roles);
        return account;
    }
}
