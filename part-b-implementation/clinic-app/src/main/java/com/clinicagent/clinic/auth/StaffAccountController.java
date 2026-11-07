package com.clinicagent.clinic.auth;

import com.clinicagent.clinic.domain.UserAccount;
import com.clinicagent.clinic.domain.UserAccountRepository;
import com.clinicagent.clinic.model.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Staff accounts, managed by the clinic manager only. Accounts are deactivated, never deleted, so
 * history (who did what) keeps pointing at a real account. No email is sent at any point.
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('CLINIC_MANAGER')")
@Tag(name = "Staff accounts", description = "Create and manage staff logins (clinic manager only)")
public class StaffAccountController {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public StaffAccountController(UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    @Operation(summary = "List staff accounts")
    public List<StaffAccountDto> list() {
        return users.findAllByOrderByIdAsc().stream().map(StaffAccountDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Create a staff account; the email is a login name only and is not verified")
    public StaffAccountDto create(@Valid @RequestBody CreateStaffAccountRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        UserAccount user = new UserAccount(email, request.displayName(),
                passwordEncoder.encode(request.password()), request.role());
        return StaffAccountDto.from(users.save(user));
    }

    @PatchMapping("/{id}")
    @Transactional
    @Operation(summary = "Change name, role, active flag or password; only the fields sent are changed")
    public StaffAccountDto update(@PathVariable long id, @Valid @RequestBody UpdateStaffAccountRequest request,
                                  Authentication authentication) {
        UserAccount user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account " + id + " not found"));
        boolean self = Objects.equals(user.getEmail(), authentication.getName());
        if (self && Boolean.FALSE.equals(request.active())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot deactivate your own account");
        }
        if (self && request.role() != null && request.role() != UserRole.CLINIC_MANAGER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot remove your own manager role");
        }
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return StaffAccountDto.from(user);
    }

    public record CreateStaffAccountRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String displayName,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull UserRole role) {
    }

    /** Every field is optional; null means "leave unchanged". */
    public record UpdateStaffAccountRequest(
            @Size(min = 1, max = 128) String displayName,
            UserRole role,
            Boolean active,
            @Size(min = 8, max = 72) String password) {
    }

    /** Never includes the password hash. */
    public record StaffAccountDto(Long id, String email, String displayName, UserRole role, boolean active,
                                 Instant createdAt) {
        static StaffAccountDto from(UserAccount u) {
            return new StaffAccountDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.isActive(),
                    u.getCreatedAt());
        }
    }
}
