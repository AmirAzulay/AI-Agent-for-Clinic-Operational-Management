package com.clinicagent.clinic.api;

import com.clinicagent.clinic.PostgresIntegrationTest;
import com.clinicagent.clinic.api.dto.Dtos.ClinicStateResponse;
import com.clinicagent.clinic.api.dto.Dtos.DepartmentState;
import com.clinicagent.clinic.api.dto.Dtos.MedicationDto;
import com.clinicagent.clinic.api.dto.Dtos.PatientDto;
import com.clinicagent.clinic.api.dto.Dtos.RoomDto;
import com.clinicagent.clinic.auth.AuthController.LoginRequest;
import com.clinicagent.clinic.auth.AuthController.LoginResponse;
import com.clinicagent.common.model.DepartmentStatus;
import com.clinicagent.common.model.RoomStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The seeded clinic is visible through the read API. */
class ClinicReadControllerTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    HttpEntity<Void> authorized;

    @BeforeEach
    void login() {
        String token = rest.postForEntity("/api/auth/login",
                new LoginRequest("staff", "staff123"), LoginResponse.class).getBody().token();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        authorized = new HttpEntity<>(headers);
    }

    @Test
    void clinicStateReturnsTheSeededClinic() {
        ResponseEntity<ClinicStateResponse> response = rest.exchange(
                "/api/clinic/state", HttpMethod.GET, authorized, ClinicStateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ClinicStateResponse state = response.getBody();
        assertThat(state).isNotNull();
        assertThat(state.departments()).extracting(DepartmentState::name)
                .containsExactly("General Medicine", "Pediatrics", "Orthopedics", "Emergency");
        assertThat(state.departments()).allSatisfy(d -> {
            assertThat(d.status()).isEqualTo(DepartmentStatus.NORMAL);
            assertThat(d.waitingCount()).isZero();
            assertThat(d.roomsAvailable()).isPositive();
            assertThat(d.roomsInactive()).isPositive();
            assertThat(d.doctorsTotal()).isPositive();
        });
        assertThat(state.medications()).hasSizeGreaterThanOrEqualTo(8);
        assertThat(state.activeAlerts()).isEmpty();
    }

    @Test
    void roomsCanBeFilteredByDepartment() {
        ResponseEntity<List<RoomDto>> response = rest.exchange(
                "/api/rooms?departmentId=1", HttpMethod.GET, authorized, new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody()).allMatch(r -> r.departmentId() == 1L);
        assertThat(response.getBody()).extracting(RoomDto::status).contains(RoomStatus.AVAILABLE, RoomStatus.INACTIVE);
    }

    @Test
    void medicationsReportThreshold() {
        ResponseEntity<List<MedicationDto>> response = rest.exchange(
                "/api/medications", HttpMethod.GET, authorized, new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).allMatch(m -> m.threshold() > 0);
        assertThat(response.getBody()).noneMatch(MedicationDto::belowThreshold);
    }

    @Test
    void patientsAreEmptyBeforeTheSimulationRuns() {
        ResponseEntity<List<PatientDto>> response = rest.exchange(
                "/api/patients?status=WAITING", HttpMethod.GET, authorized, new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void unknownDepartmentIs404() {
        ResponseEntity<String> response = rest.exchange(
                "/api/departments/999", HttpMethod.GET, authorized, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
