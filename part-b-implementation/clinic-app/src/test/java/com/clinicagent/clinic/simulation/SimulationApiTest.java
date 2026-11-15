package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.PostgresIntegrationTest;
import com.clinicagent.clinic.auth.AuthController.LoginRequest;
import com.clinicagent.clinic.auth.AuthController.LoginResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Simulation and history over HTTP, including who may do what. */
class SimulationApiTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    SimulationEngine engine;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        SimulationEngineTest.cleanSimulationData(engine, jdbc);
    }

    private HttpEntity<Object> as(String email, String password, Object body) {
        String token = rest.postForEntity("/api/auth/login", new LoginRequest(email, password), LoginResponse.class)
                .getBody().token();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Object> manager(Object body) {
        return as("manager@clinic.local", "manager123", body);
    }

    private HttpEntity<Object> staff() {
        return as("staff@clinic.local", "staff123", null);
    }

    @Test
    void theManagerStartsStepsAndStopsARun() {
        ResponseEntity<SimulationController.SimulationStatus> started = rest.exchange("/api/simulation/start",
                HttpMethod.POST, manager(Map.of("scenario", "RUSH", "seed", 5, "autoTick", false)),
                SimulationController.SimulationStatus.class);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(started.getBody().running()).isTrue();
        assertThat(started.getBody().scenario()).isEqualTo("RUSH");
        assertThat(started.getBody().simClock()).isEqualTo("08:00");

        ResponseEntity<List<TickService.TickResult>> stepped = rest.exchange("/api/simulation/step?ticks=90",
                HttpMethod.POST, manager(null), new ParameterizedTypeReference<>() {});
        assertThat(stepped.getBody()).hasSize(90);

        SimulationController.SimulationStatus status = rest.exchange("/api/simulation/status", HttpMethod.GET,
                staff(), SimulationController.SimulationStatus.class).getBody();
        assertThat(status.tick()).isEqualTo(90);
        assertThat(status.simClock()).isEqualTo("09:30");

        ResponseEntity<SimulationController.SimulationStatus> stopped = rest.exchange("/api/simulation/stop",
                HttpMethod.POST, manager(null), SimulationController.SimulationStatus.class);
        assertThat(stopped.getBody().running()).isFalse();
        assertThat(stopped.getBody().stopReason()).hasToString("STOPPED");
    }

    @Test
    void onlyTheManagerControlsTheSimulation() {
        assertThat(rest.exchange("/api/simulation/start", HttpMethod.POST, staff(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange("/api/simulation/stop", HttpMethod.POST, staff(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange("/api/simulation/step", HttpMethod.POST, staff(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.getForEntity("/api/simulation/status", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void conflictsAndBadInputAreRejected() {
        assertThat(rest.exchange("/api/simulation/stop", HttpMethod.POST, manager(null), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(rest.exchange("/api/simulation/start", HttpMethod.POST,
                manager(Map.of("tickIntervalSeconds", 0)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        rest.exchange("/api/simulation/start", HttpMethod.POST, manager(Map.of("autoTick", false)), String.class);
        assertThat(rest.exchange("/api/simulation/start", HttpMethod.POST, manager(null), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void historyServesSnapshotsAndTrendsForTheLatestRun() {
        rest.exchange("/api/simulation/start", HttpMethod.POST,
                manager(Map.of("seed", 11, "autoTick", false)), String.class);
        rest.exchange("/api/simulation/step?ticks=60", HttpMethod.POST, manager(null), String.class);

        Map<String, Object> series = rest.exchange("/api/history/snapshots?lastMinutes=30", HttpMethod.GET, staff(),
                new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        List<?> departments = (List<?>) series.get("departments");
        List<?> medications = (List<?>) series.get("medications");
        assertThat(departments).hasSize(7 * 4);   // ticks 30, 35, …, 60 for 4 departments
        assertThat(medications).hasSize(7 * 8);

        Map<String, Object> trends = rest.exchange("/api/history/trends?minutes=15", HttpMethod.GET, staff(),
                new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        assertThat((List<?>) trends.get("departments")).hasSize(4);
        assertThat((List<?>) trends.get("medications")).hasSize(8);
    }

    @Test
    void theClinicViewUsesTheSimulatedClock() {
        rest.exchange("/api/simulation/start", HttpMethod.POST, manager(Map.of("seed", 11, "autoTick", false)), String.class);
        rest.exchange("/api/simulation/step?ticks=120", HttpMethod.POST, manager(null), String.class);

        Map<String, Object> status = rest.exchange("/api/simulation/status", HttpMethod.GET, staff(),
                new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        Map<String, Object> state = rest.exchange("/api/clinic/state", HttpMethod.GET, staff(),
                new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();

        assertThat(state.get("generatedAt")).isEqualTo(status.get("simNow"));
    }
}
