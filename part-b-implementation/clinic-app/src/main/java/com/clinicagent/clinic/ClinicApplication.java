package com.clinicagent.clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * clinic-app: owns PostgreSQL and everything transactional – the clinic domain, the
 * simulation engine, the AI agent and the six validated tools.
 */
@SpringBootApplication
public class ClinicApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicApplication.class, args);
    }
}
