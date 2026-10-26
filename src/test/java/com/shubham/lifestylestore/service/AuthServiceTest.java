package com.shubham.lifestylestore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shubham.lifestylestore.config.AppProperties;
import com.shubham.lifestylestore.domain.UserAccount;
import com.shubham.lifestylestore.repo.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AuthServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); // cheap cost for tests
    private AuthService service;
    private UserAccount alice;

    @BeforeEach
    void setUp() {
        var secret = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        var props = new AppProperties(new AppProperties.Security("0123456789abcdef0123456789abcdef", 60, 10),
                new AppProperties.Assistant("", "m", "http://localhost", 5));
        service = new AuthService(users, encoder, new NimbusJwtEncoder(new ImmutableSecret<>(secret)), props);
        alice = new UserAccount("u1", "Alice", "alice@example.com", encoder.encode("correct-horse-battery"),
                "9999999999", "Pune", "1 Main St", 0, null, Instant.now());
        when(users.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void successfulLoginReturnsSignedToken() {
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        var token = service.login("  Alice@Example.com ", "correct-horse-battery");

        assertThat(token.accessToken().split("\\.")).hasSize(3);
        assertThat(token.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void unknownEmailAndWrongPasswordLookIdentical() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> service.login("ghost@example.com", "whatever-password"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).isEqualTo("Invalid email or password");
                });
        assertThatThrownBy(() -> service.login("alice@example.com", "wrong-password-1"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("Invalid email or password"));
    }

    @Test
    void accountLocksAfterFiveFailures() {
        var almostLocked = alice.withLoginState(4, null);
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(almostLocked));

        assertThatThrownBy(() -> service.login("alice@example.com", "wrong-password-1"))
                .isInstanceOf(ApiException.class);

        var saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().lockedUntil()).isAfter(Instant.now());
    }

    @Test
    void lockedAccountRejectsEvenCorrectPassword() {
        var locked = alice.withLoginState(0, Instant.now().plusSeconds(600));
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.login("alice@example.com", "correct-horse-battery"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void registrationStoresHashNotPlaintext() {
        service.register(new AuthService.Registration("Bob", "BOB@example.com", "super-secret-pass", "9999999999",
                "Delhi", "2 Side St"));

        var saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().email()).isEqualTo("bob@example.com");
        assertThat(saved.getValue().passwordHash()).startsWith("$2").doesNotContain("super-secret-pass");
    }

    @Test
    void duplicateEmailGivesVagueConflict() {
        when(users.save(any(UserAccount.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.register(new AuthService.Registration("A", "a@b.co", "super-secret-pass",
                "9999999999", "X", "Y")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).doesNotContain("a@b.co");
                });
    }
}
