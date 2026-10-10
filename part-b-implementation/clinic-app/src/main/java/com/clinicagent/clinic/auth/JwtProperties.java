package com.clinicagent.clinic.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings. The secret must be at least 32 bytes (HS256). The default in application.yml is
 * for local development only; set JWT_SECRET in the environment anywhere else.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, Duration ttl) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("security.jwt.secret must be at least 32 bytes");
        }
        if (ttl == null) {
            ttl = Duration.ofHours(8);
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
