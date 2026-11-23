package com.fitouts.shared.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnWebApplication
@EnableConfigurationProperties(PrometheusScrapeProperties.class)
public class PrometheusActuatorSecurityConfiguration {

    private static final String PROMETHEUS_ROLE = "PROMETHEUS";

    @Bean
    @Order(0)
    SecurityFilterChain actuatorHealthSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/actuator/health", "/actuator/health/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Qualifier("prometheusScrapeUserDetailsService")
    UserDetailsService prometheusScrapeUserDetailsService(PrometheusScrapeProperties properties) {
        return buildPrometheusUserDetailsService(properties);
    }

    @Bean
    @Order(1)
    SecurityFilterChain actuatorPrometheusSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("prometheusScrapeUserDetailsService") UserDetailsService scrapeUsers) throws Exception {
        http.securityMatcher("/actuator/prometheus", "/actuator/info")
                .authenticationProvider(prometheusAuthenticationProvider(scrapeUsers))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(PROMETHEUS_ROLE))
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    private static DaoAuthenticationProvider prometheusAuthenticationProvider(UserDetailsService scrapeUsers) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(scrapeUsers);
        provider.setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());
        return provider;
    }

    private static UserDetailsService buildPrometheusUserDetailsService(PrometheusScrapeProperties properties) {
        if (!StringUtils.hasText(properties.getUsername()) || !StringUtils.hasText(properties.getPassword())) {
            throw new IllegalStateException(
                    "onepath.monitoring.prometheus.username and onepath.monitoring.prometheus.password must be set "
                            + "(env: MANAGEMENT_PROMETHEUS_USERNAME / MANAGEMENT_PROMETHEUS_PASSWORD)");
        }
        return new InMemoryUserDetailsManager(User.builder()
                .username(properties.getUsername())
                .password("{noop}" + properties.getPassword())
                .roles(PROMETHEUS_ROLE)
                .build());
    }
}