package com.clinicagent.clinic.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApiConfig {

    /** One clock for the whole app so tests and the simulation can control time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Adds the "Authorize" button to Swagger UI so a JWT from /api/auth/login can be used there. */
    @Bean
    public OpenAPI openAPI() {
        String scheme = "bearer-jwt";
        return new OpenAPI()
                .info(new Info()
                        .title("clinic-app API")
                        .version("0.1.0")
                        .description("AI Agent for Clinic Operational Management – clinic state, simulation, agent"))
                .components(new Components().addSecuritySchemes(scheme, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
