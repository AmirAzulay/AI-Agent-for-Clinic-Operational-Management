package com.clinicagent.clinic.auth;

import com.clinicagent.clinic.domain.UserAccount;
import com.clinicagent.clinic.domain.UserAccountRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Turns a valid token into an authenticated staff member, checking the account on every request:
 * a deactivated or deleted account is rejected immediately (not only when its token expires), and
 * the role comes from the database, so a role change applies to the next request.
 */
@Component
public class StaffJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserAccountRepository users;

    public StaffJwtAuthenticationConverter(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UserAccount user = parseId(jwt.getSubject())
                .flatMap(users::findById)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new InvalidBearerTokenException("Account is not active"));
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                user.getEmail());
    }

    private static Optional<Long> parseId(String subject) {
        try {
            return Optional.of(Long.parseLong(subject));
        } catch (NumberFormatException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
