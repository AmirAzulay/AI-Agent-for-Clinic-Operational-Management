package com.clinicagent.clinic.auth;

import com.clinicagent.clinic.PostgresIntegrationTest;
import com.clinicagent.clinic.auth.AuthController.LoginRequest;
import com.clinicagent.clinic.auth.AuthController.LoginResponse;
import com.clinicagent.clinic.auth.AuthController.MeResponse;
import com.clinicagent.clinic.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Login test cases from the project book (section 9) plus the token round trip, with email logins. */
class AuthControllerTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void validCredentialsReturnTokenAndRole() {
        ResponseEntity<LoginResponse> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest("manager@clinic.local", "manager123"), LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().email()).isEqualTo("manager@clinic.local");
        assertThat(response.getBody().displayName()).isEqualTo("Clinic Manager");
        assertThat(response.getBody().role()).isEqualTo(UserRole.CLINIC_MANAGER);
    }

    @Test
    void emailIsCaseAndSpaceInsensitive() {
        ResponseEntity<LoginResponse> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest("  Manager@Clinic.LOCAL ", "manager123"), LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void invalidPasswordIsRejected() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest("manager@clinic.local", "wrong"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownUserIsRejected() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest("nobody@clinic.local", "manager123"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void blankFieldsAreRejected() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/login", Map.of("email", "", "password", "x"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void protectedEndpointRequiresToken() {
        ResponseEntity<String> response = rest.getForEntity("/api/clinic/state", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenGrantsAccessAndCarriesRole() {
        String token = rest.postForEntity("/api/auth/login",
                new LoginRequest("reception@clinic.local", "reception123"), LoginResponse.class).getBody().token();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<MeResponse> me = rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(headers), MeResponse.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().email()).isEqualTo("reception@clinic.local");
        assertThat(me.getBody().role()).isEqualTo(UserRole.RECEPTION);
    }

    @Test
    void anonymousHealthShowsStatusButNoDetails() {
        String body = rest.getForObject("/actuator/health", String.class);

        assertThat(body).contains("\"status\":\"UP\"").doesNotContain("sizeMb").doesNotContain("components");
    }
}
