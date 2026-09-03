package com.fitouts.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import com.fitouts.auth.security.AuthPrincipal;

@Configuration
public class SessionJdbcConfig {

    private static final Logger log = LoggerFactory.getLogger(SessionJdbcConfig.class);

    /**
     * JDBC sessions use Java serialization. DevTools loads AuthPrincipal in a
     * RestartClassLoader, while the default deserializer uses the base loader, so
     * {@code instanceof AuthPrincipal} fails and company-scoped lists come back empty.
     */
    @Bean
    public SessionRepositoryCustomizer<JdbcIndexedSessionRepository> jdbcSessionLenientSerialization() {
        ClassLoader classLoader = AuthPrincipal.class.getClassLoader();
        GenericConversionService conversionService = new GenericConversionService();
        conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
        DeserializingConverter deserializingConverter = new DeserializingConverter(classLoader);
        conversionService.addConverter(byte[].class, Object.class, source -> {
            try {
                return deserializingConverter.convert(source);
            } catch (RuntimeException ex) {
                log.warn("Dropping unreadable session attribute (stale serialize after deploy): {}",
                        ex.getMessage());
                return null;
            }
        });
        return repository -> repository.setConversionService(conversionService);
    }
}
