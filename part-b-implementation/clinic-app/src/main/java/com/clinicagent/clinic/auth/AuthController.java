package com.clinicagent.clinic.auth;

import com.clinicagent.clinic.domain.UserAccount;
import com.clinicagent.clinic.domain.UserAccountRepository;
import com.clinicagent.clinic.model.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Staff login. The email is only a login name: it is never verified and no mail is ever sent.
 * Accounts are created by the clinic manager ({@link StaffAccountController}); there is no sign-up.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Staff login and current user")
public class AuthController {

    private static final String INVALID = "Invalid email or password";

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
    @Operation(summary = "Exchange a staff email and password for a bearer token")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        // Same answer for unknown email, wrong password and deactivated account: no account probing.
        UserAccount user = users.findByEmail(UserAccount.normalizeEmail(request.email()))
                .filter(UserAccount::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID));

        JwtService.IssuedToken issued = jwtService.issue(user);
        return new LoginResponse(issued.token(), issued.expiresAt(), user.getEmail(), user.getDisplayName(),
                user.getRole());
    }

    @GetMapping("/me")
    @Operation(summary = "The staff member the current token belongs to")
    public MeResponse me(Authentication authentication) {
        UserAccount user = currentUser(authentication);
        return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    @Operation(summary = "Change your own password (current password required)")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        UserAccount user = currentUser(authentication);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current password is wrong");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private UserAccount currentUser(Authentication authentication) {
        return users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, String email, String displayName, UserRole role) {
    }

    public record MeResponse(Long id, String email, String displayName, UserRole role) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
