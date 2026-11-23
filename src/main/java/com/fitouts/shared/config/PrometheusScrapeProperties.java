package com.fitouts.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "onepath.monitoring.prometheus")
@Getter
@Setter
public class PrometheusScrapeProperties {

    private String username = "";
    private String password = "";
}