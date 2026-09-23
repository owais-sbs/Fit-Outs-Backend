package com.fitouts.shared.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer hibernateModuleCustomizer() {
        return (Jackson2ObjectMapperBuilder builder) -> {
            Hibernate6Module hibernateModule = new Hibernate6Module();
            // Allow API responses to include JPA @Transient enrichment fields
            // (e.g. Project.commercialStage). Without this, Hibernate6Module strips them.
            hibernateModule.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION);
            builder.modules(hibernateModule, new JavaTimeModule());
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}
