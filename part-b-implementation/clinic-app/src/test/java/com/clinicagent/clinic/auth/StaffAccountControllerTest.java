package com.clinicagent.clinic.auth;

import com.clinicagent.clinic.PostgresIntegrationTest;
import com.clinicagent.clinic.auth.AuthController.ChangePasswordRequest;
import com.clinicagent.clinic.auth.AuthController.LoginRequest;
import com.clinicagent.clinic.auth.AuthController.LoginResponse;
import com.clinicagent.clinic.auth.StaffAccountController.CreateStaffAccountRequest;
import com.clinicagent.clinic.auth.StaffAccountController.StaffAccountDto;
import com.clinicagent.clinic.auth.StaffAccountController.UpdateStaffAccountRequest;
import com.clinicagent.clinic.model.UserRole;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Staff account management end to end. Every test creates its own accounts with unique emails. */
class StaffAccountControllerTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    String managerToken;

    @BeforeEach
    void setUp() {
        // PATCH needs an HTTP client that supports it.
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        managerToken = login("manager@clinic.local", "manager123").getBody().token();
    }

    private ResponseEntity<LoginResponse> login(String email, String password) {
        return rest.postForEntity("/api/auth/login", new LoginRequest(email, password), LoginResponse.class);
    }

    private HttpEntity<Object> as(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private static String uniqueEmail() {
        return "nurse-" + UUID.randomUUID().toString().substring(0, 8) + "@clinic.local";
    }

    private StaffAccountDto create(String email, UserRole role) {
        ResponseEntity<StaffAccountDto> created = rest.exchange("/api/users", HttpMethod.POST,
                as(managerToken, new CreateStaffAccountRequest(email, "Nurse Dana", "secret-pass-1", role)),
                StaffAccountDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody();
    }

    private ResponseEntity<StaffAccountDto> patch(String token, long id, UpdateStaffAccountRequest body) {
        return rest.exchange("/api/users/" + id, HttpMethod.PATCH, as(token, body), StaffAccountDto.class);
    }

    @Test
    void managerCreatesAStaffAccountThatCanLogInRightAway() {
        String email = uniqueEmail();

        StaffAccountDto account = create(email.toUpperCase(), UserRole.MEDICAL_STAFF);

        assertThat(account.email()).isEqualTo(email);
        assertThat(account.active()).isTrue();
        ResponseEntity<LoginResponse> login = login(email, "secret-pass-1");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().role()).isEqualTo(UserRole.MEDICAL_STAFF);
    }

    @Test
    void listingNeverExposesPasswordHashes() {
        String body = rest.exchange("/api/users", HttpMethod.GET, as(managerToken, null), String.class).getBody();

        assertThat(body).contains("manager@clinic.local").doesNotContainIgnoringCase("password");
    }

    @Test
    void onlyTheManagerCanManageAccounts() {
        String staffToken = login("staff@clinic.local", "staff123").getBody().token();

        assertThat(rest.exchange("/api/users", HttpMethod.GET, as(staffToken, null), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange("/api/users", HttpMethod.POST,
                as(staffToken, new CreateStaffAccountRequest(uniqueEmail(), "X", "secret-pass-1", UserRole.CLINIC_MANAGER)),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.getForEntity("/api/users", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void duplicateEmailsAndInvalidInputAreRejected() {
        String email = uniqueEmail();
        create(email, UserRole.RECEPTION);

        assertThat(rest.exchange("/api/users", HttpMethod.POST,
                as(managerToken, new CreateStaffAccountRequest(email, "Again", "secret-pass-1", UserRole.RECEPTION)),
                String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rest.exchange("/api/users", HttpMethod.POST,
                as(managerToken, new CreateStaffAccountRequest("not-an-email", "X", "secret-pass-1", UserRole.RECEPTION)),
                String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.exchange("/api/users", HttpMethod.POST,
                as(managerToken, new CreateStaffAccountRequest(uniqueEmail(), "X", "short", UserRole.RECEPTION)),
                String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deactivationLocksTheAccountOutImmediatelyEvenWithAValidToken() {
        String email = uniqueEmail();
        StaffAccountDto account = create(email, UserRole.MEDICAL_STAFF);
        String nurseToken = login(email, "secret-pass-1").getBody().token();

        patch(managerToken, account.id(), new UpdateStaffAccountRequest(null, null, false, null));

        assertThat(rest.exchange("/api/clinic/state", HttpMethod.GET, as(nurseToken, null), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, "secret-pass-1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        patch(managerToken, account.id(), new UpdateStaffAccountRequest(null, null, true, null));
        assertThat(login(email, "secret-pass-1").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aRoleChangeAppliesToTheNextRequest() {
        String email = uniqueEmail();
        StaffAccountDto account = create(email, UserRole.MEDICAL_STAFF);
        String token = login(email, "secret-pass-1").getBody().token();
        assertThat(rest.exchange("/api/users", HttpMethod.GET, as(token, null), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        patch(managerToken, account.id(), new UpdateStaffAccountRequest(null, UserRole.CLINIC_MANAGER, null, null));

        assertThat(rest.exchange("/api/users", HttpMethod.GET, as(token, null), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void managerResetsAPasswordAndTheOldOneStopsWorking() {
        String email = uniqueEmail();
        StaffAccountDto account = create(email, UserRole.RECEPTION);

        patch(managerToken, account.id(), new UpdateStaffAccountRequest(null, null, null, "brand-new-pass"));

        assertThat(login(email, "secret-pass-1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, "brand-new-pass").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void staffChangeTheirOwnPasswordWithTheCurrentOne() {
        String email = uniqueEmail();
        create(email, UserRole.MEDICAL_STAFF);
        String token = login(email, "secret-pass-1").getBody().token();

        assertThat(rest.exchange("/api/auth/password", HttpMethod.POST,
                as(token, new ChangePasswordRequest("wrong-current", "another-pass-2")), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange("/api/auth/password", HttpMethod.POST,
                as(token, new ChangePasswordRequest("secret-pass-1", "another-pass-2")), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login(email, "another-pass-2").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theManagerCannotLockThemselvesOut() {
        long managerId = rest.exchange("/api/users", HttpMethod.GET, as(managerToken, null),
                        new ParameterizedTypeReference<List<StaffAccountDto>>() {}).getBody().stream()
                .filter(a -> a.email().equals("manager@clinic.local")).findFirst().orElseThrow().id();

        assertThat(patch(managerToken, managerId, new UpdateStaffAccountRequest(null, null, false, null)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(patch(managerToken, managerId, new UpdateStaffAccountRequest(null, UserRole.RECEPTION, null, null))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
