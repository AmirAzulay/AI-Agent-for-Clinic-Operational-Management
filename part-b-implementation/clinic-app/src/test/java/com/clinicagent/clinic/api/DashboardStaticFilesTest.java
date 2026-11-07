package com.clinicagent.clinic.api;

import com.clinicagent.clinic.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In the Docker image clinic-app serves the built dashboard from classpath:/static. The page
 * itself is public (it shows the login form); everything it loads from /api still needs a token.
 */
class DashboardStaticFilesTest extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void theDashboardPageIsServedWithoutALogin() {
        ResponseEntity<String> page = rest.getForEntity("/", String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("test dashboard");
    }

    @Test
    void theDataBehindItStillNeedsALogin() {
        assertThat(rest.getForEntity("/api/clinic/state", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/api/users", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
