package com.clinicagent.clinic.auth;

import com.clinicagent.clinic.domain.UserAccountRepository;
import com.clinicagent.clinic.domain.UserAccount;
import com.clinicagent.common.model.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Login and current user")
public class AuthController {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserAccountRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Exchange username and password for a bearer token")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = users.findByUsername(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        JwtService.IssuedToken issued = jwtService.issue(user);
        return new LoginResponse(issued.token(), issued.expiresAt(), user.getUsername(), user.getRole());
    }

    @GetMapping("/me")
    @Operation(summary = "Who the current token belongs to")
    public MeResponse me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .toList();
        return new MeResponse(authentication.getName(), roles);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, String username, UserRole role) {
    }

    public record MeResponse(String username, List<String> roles) {
    }
}
