package com.clinicagent.clinic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 smoke test: the application starts against a real PostgreSQL, Flyway applies the
 * schema and seed data, and the health endpoint reports UP without a token.
 */
class ClinicApplicationSmokeTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestRestTemplate rest;

    @Test
    void flywayCreatesTheInitialSchema() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public' order by table_name",
                String.class);

        assertThat(tables).contains(
                "users", "departments", "treatment_rooms", "doctors", "patients",
                "medications", "restock_requests", "alerts",
                "simulation_runs", "ai_scans", "ai_actions");
    }

    @Test
    void seedDataIsPresent() {
        Integer departments = jdbc.queryForObject("select count(*) from departments", Integer.class);
        Integer users = jdbc.queryForObject("select count(*) from users", Integer.class);

        assertThat(departments).isEqualTo(4);
        assertThat(users).isEqualTo(3);
    }

    @Test
    void healthEndpointIsPublicAndUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
