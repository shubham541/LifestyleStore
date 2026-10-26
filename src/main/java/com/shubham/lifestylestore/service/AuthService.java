package com.shubham.lifestylestore.service;

import com.shubham.lifestylestore.config.AppProperties;
import com.shubham.lifestylestore.config.SecurityConfig;
import com.shubham.lifestylestore.domain.UserAccount;
import com.shubham.lifestylestore.repo.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AppProperties props;
    /** Hash compared against when the email is unknown, so response time does not reveal valid accounts. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtEncoder jwtEncoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public record Registration(String name, String email, String password, String contact, String city,
                               String address) {
    }

    public record Token(String accessToken, long expiresInSeconds) {
    }

    public UserAccount register(Registration r) {
        String email = normalise(r.email());
        var user = new UserAccount(UUID.randomUUID().toString(), r.name().trim(), email, encoder.encode(r.password()),
                r.contact(), r.city(), r.address(), 0, null, Instant.now());
        try {
            return users.save(user);
        } catch (DuplicateKeyException e) {
            // Deliberately vague: do not confirm which emails are registered.
            throw ApiException.conflict("Unable to register with these details");
        }
    }

    public Token login(String email, String password) {
        UserAccount user = users.findByEmail(normalise(email)).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash);
            throw invalidCredentials();
        }
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(Instant.now())) {
            log.warn("Login rejected for locked account {}", user.id());
            throw invalidCredentials();
        }
        if (!encoder.matches(password, user.passwordHash())) {
            int attempts = user.failedAttempts() + 1;
            Instant lock = attempts >= MAX_FAILED_ATTEMPTS ? Instant.now().plus(LOCK_DURATION) : null;
            users.save(user.withLoginState(lock != null ? 0 : attempts, lock));
            log.warn("Failed login for account {} (attempt {})", user.id(), attempts);
            throw invalidCredentials();
        }
        if (user.failedAttempts() > 0 || user.lockedUntil() != null) {
            users.save(user.withLoginState(0, null));
        }
        return issueToken(user);
    }

    public void changePassword(String userId, String oldPassword, String newPassword) {
        UserAccount user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!encoder.matches(oldPassword, user.passwordHash())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Current password is incorrect");
        }
        users.save(user.withPasswordHash(encoder.encode(newPassword)));
        log.info("Password changed for account {}", userId);
    }

    public UserAccount profile(String userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }

    private Token issueToken(UserAccount user) {
        Instant now = Instant.now();
        Duration ttl = Duration.ofMinutes(props.security().jwtTtlMinutes());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.ISSUER)
                .subject(user.id())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("roles", List.of("USER"))
                .build();
        String jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Token(jwt, ttl.toSeconds());
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
