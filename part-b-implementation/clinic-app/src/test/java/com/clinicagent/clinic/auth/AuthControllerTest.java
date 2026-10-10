package com.clinicagent.clinic.auth;

import com.clinicagent.clinic.PostgresIntegrationTest;
import com.clinicagent.clinic.auth.AuthController.LoginRequest;
import com.clinicagent.clinic.auth.AuthController.LoginResponse;
import com.clinicagent.clinic.auth.AuthController.MeResponse;
import com.clinicagent.common.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Login test cases from the project book (section 9) plus the token round trip. */
class AuthControllerTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void validCredentialsReturnTokenAndRole() {
        ResponseEntity<LoginResponse> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest("manager", "manager123"), LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().role()).isEqualTo(UserRole.CLINIC_MANAGER);
    }

    @Test
    void invalidPasswordIsRejected() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest("manager", "wrong"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownUserIsRejected() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest("nobody", "manager123"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointRequiresToken() {
        ResponseEntity<String> response = rest.getForEntity("/api/clinic/state", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenGrantsAccessAndCarriesRole() {
        String token = rest.postForEntity("/api/auth/login",
                new LoginRequest("reception", "reception123"), LoginResponse.class).getBody().token();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<MeResponse> me = rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(headers), MeResponse.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().username()).isEqualTo("reception");
        assertThat(me.getBody().roles()).containsExactly("RECEPTION");
    }
}
