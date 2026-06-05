package com.fitouts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.fitouts")
public class FitOutsApplication {

	public static void main(String[] args) {

		SpringApplication.run(FitOutsApplication.class, args);

	}

}