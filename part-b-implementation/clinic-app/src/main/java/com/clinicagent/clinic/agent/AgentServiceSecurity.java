package com.clinicagent.clinic.agent;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * The agent service's door into clinic-app: the MCP endpoint (/mcp) and the agent-run endpoints
 * (/api/agent/service/**). They accept only {@code Authorization: Bearer <AGENT_SERVICE_TOKEN>}.
 * Staff JWTs do not open them, and if no token is configured nobody gets in: Spring AI's MCP
 * endpoint is unauthenticated by default, so it must never be left open.
 */
@Configuration
public class AgentServiceSecurity {

    public static final String ROLE = "AGENT";

    @Bean
    @Order(1)
    SecurityFilterChain agentServiceChain(HttpSecurity http,
                                          @Value("${agent.service-token:}") String serviceToken) throws Exception {
        http
                .securityMatcher("/mcp", "/mcp/**", "/api/agent/service/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ServiceTokenFilter(serviceToken), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // MCP replies stream asynchronously; the async (and error) re-dispatch of an
                        // already-authorized request carries no token, so it must not be checked again.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .anyRequest().hasRole(ROLE));
        return http.build();
    }

    /** Authenticates a request whose bearer token equals the configured service token (constant-time compare). */
    static final class ServiceTokenFilter extends OncePerRequestFilter {

        private final byte[] expected;

        ServiceTokenFilter(String token) {
            this.expected = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (expected != null && header != null && header.startsWith("Bearer ")) {
                byte[] given = header.substring(7).strip().getBytes(StandardCharsets.UTF_8);
                if (MessageDigest.isEqual(given, expected)) {
                    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            "agent-service", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
                }
            }
            chain.doFilter(request, response);
        }
    }
}
