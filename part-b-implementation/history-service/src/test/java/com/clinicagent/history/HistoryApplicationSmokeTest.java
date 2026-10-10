package com.clinicagent.history;

import com.clinicagent.history.index.IndexTemplateInitializer;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 smoke test: the application starts against a real Elasticsearch, installs the four
 * index templates, and the health endpoint reports UP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HistoryApplicationSmokeTest {

    @Container
    @ServiceConnection
    static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.18.8"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @Autowired
    RestClient restClient;

    @Autowired
    TestRestTemplate rest;

    @Test
    void indexTemplatesAreInstalledOnStartup() throws IOException {
        for (String name : IndexTemplateInitializer.TEMPLATE_NAMES) {
            Response response = restClient.performRequest(new Request("GET", "/_index_template/" + name));
            assertThat(response.getStatusLine().getStatusCode())
                    .as("index template %s", name)
                    .isEqualTo(200);
        }
    }

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
