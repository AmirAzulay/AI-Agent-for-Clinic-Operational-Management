package com.clinicagent.clinic.agent;

import com.clinicagent.clinic.PostgresIntegrationTest;
import com.clinicagent.clinic.auth.AuthController.LoginRequest;
import com.clinicagent.clinic.auth.AuthController.LoginResponse;
import com.clinicagent.clinic.simulation.SimulationEngine;
import com.clinicagent.clinic.simulation.SimulationEngineTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The MCP endpoint over real HTTP: who may connect, and the protocol round trip an agent makes. */
class McpEndpointTest extends PostgresIntegrationTest {

    private static final String INIT = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
             "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Autowired
    SimulationEngine engine;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @AfterEach
    void cleanUp() {
        SimulationEngineTest.cleanSimulationData(engine, jdbc);
    }

    private HttpResponse<String> post(String token, String sessionId, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (sessionId != null) request.header("Mcp-Session-Id", sessionId).header("MCP-Protocol-Version", "2025-06-18");
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** A response is plain JSON or a server-sent event whose data line holds the JSON. */
    private JsonNode body(HttpResponse<String> response) throws Exception {
        String text = response.body().strip();
        if (!text.startsWith("{")) {
            text = text.lines().filter(l -> l.startsWith("data:")).map(l -> l.substring(5).strip()).findFirst().orElseThrow();
        }
        return json.readTree(text);
    }

    private String session() throws Exception {
        HttpResponse<String> init = post(AGENT_TOKEN, null, INIT);
        assertThat(init.statusCode()).isEqualTo(200);
        String sessionId = init.headers().firstValue("Mcp-Session-Id").orElseThrow();
        // A notification has no answer (202, empty body); the JDK client can trip over the empty
        // chunked reply, so send it without reading one.
        http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Authorization", "Bearer " + AGENT_TOKEN)
                .header("Mcp-Session-Id", sessionId)
                .header("MCP-Protocol-Version", "2025-06-18")
                .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .build(), HttpResponse.BodyHandlers.discarding());
        return sessionId;
    }

    @Test
    void onlyTheServiceTokenOpensTheMcpEndpoint() throws Exception {
        String staffJwt = rest.postForEntity("/api/auth/login", new LoginRequest("manager@clinic.local", "manager123"),
                LoginResponse.class).getBody().token();

        assertThat(post(null, null, INIT).statusCode()).isEqualTo(401);
        assertThat(post("wrong-token", null, INIT).statusCode()).isEqualTo(401);
        assertThat(post(staffJwt, null, INIT).statusCode()).as("a staff login is not the agent").isEqualTo(401);
        assertThat(post(AGENT_TOKEN, null, INIT).statusCode()).isEqualTo(200);
    }

    @Test
    void theAgentListsTheSixToolsAndCallsThem() throws Exception {
        String sessionId = session();

        JsonNode tools = body(post(AGENT_TOKEN, sessionId, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"))
                .path("result").path("tools");
        List<String> names = new ArrayList<>();
        tools.forEach(t -> names.add(t.path("name").asText()));
        assertThat(names).containsExactlyInAnyOrder("get_clinic_state", "open_room", "create_alert",
                "escalate_department_status", "request_medication_restock", "write_summary");

        JsonNode state = body(post(AGENT_TOKEN, sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_clinic_state","arguments":{}}}"""));
        JsonNode view = json.readTree(state.path("result").path("content").get(0).path("text").asText());
        assertThat(view.path("departments")).hasSize(4);

        JsonNode rejected = body(post(AGENT_TOKEN, sessionId, """
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"open_room",
                 "arguments":{"departmentId":1,"reason":"testing the endpoint with no simulation"}}}"""));
        assertThat(rejected.path("result").path("content").get(0).path("text").asText())
                .contains("\"executed\":false").contains("no simulation is running");
    }

    @Test
    void agentRunEndpointsNeedTheServiceTokenToo() {
        assertThat(rest.postForEntity("/api/agent/service/runs", java.util.Map.of("skill", "x"), String.class)
                .getStatusCode().value()).isEqualTo(401);
    }
}
