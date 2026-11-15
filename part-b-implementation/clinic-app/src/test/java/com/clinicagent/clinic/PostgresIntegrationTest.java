package com.clinicagent.clinic;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests: one PostgreSQL container for the whole test JVM, so every test
 * class shares the same Spring context and database instead of starting its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "agent.service-token=" + PostgresIntegrationTest.AGENT_TOKEN)
public abstract class PostgresIntegrationTest {

    /** Service token the tests use for the MCP endpoint and the agent-run API. */
    public static final String AGENT_TOKEN = "test-agent-service-token";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
