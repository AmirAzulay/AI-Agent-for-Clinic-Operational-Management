package com.clinicagent.history.index;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Installs (or updates) the composable index templates on startup so every index this
 * service writes has an explicit mapping. Templates live in {@code resources/index-templates}.
 */
@Component
public class IndexTemplateInitializer implements ApplicationRunner {

    public static final List<String> TEMPLATE_NAMES = List.of(
            "clinic-state-snapshots",
            "ai-decisions",
            "system-events",
            "evaluation-results");

    private static final Logger log = LoggerFactory.getLogger(IndexTemplateInitializer.class);

    private final RestClient restClient;

    public IndexTemplateInitializer(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        for (String name : TEMPLATE_NAMES) {
            install(name);
        }
    }

    private void install(String name) throws IOException {
        String body = new ClassPathResource("index-templates/" + name + ".json")
                .getContentAsString(StandardCharsets.UTF_8);

        Request request = new Request("PUT", "/_index_template/" + name);
        request.setJsonEntity(body);

        Response response = restClient.performRequest(request);
        log.info("Index template '{}' installed (HTTP {})", name, response.getStatusLine().getStatusCode());
    }
}
