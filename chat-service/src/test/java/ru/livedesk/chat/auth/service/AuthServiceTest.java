package ru.livedesk.chat.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import ru.livedesk.chat.auth.dto.LoginRequest;
import ru.livedesk.chat.auth.dto.RegisterRequest;
import ru.livedesk.chat.auth.dto.TokenResponse;
import ru.livedesk.chat.auth.dto.UserResponse;
import ru.livedesk.chat.auth.model.User;
import ru.livedesk.chat.auth.model.UserRole;
import ru.livedesk.chat.auth.repository.UserRepository;
import ru.livedesk.chat.config.JwtProperties;
import ru.livedesk.chat.exception.EmailAlreadyUsedException;
import ru.livedesk.chat.exception.InvalidCredentialsException;

class AuthServiceTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes";
    private static final String PASSWORD = "very-secret";

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key())
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    private final JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key()));
    private final AuthService authService =
            new AuthService(users, passwordEncoder, jwtEncoder, new JwtProperties(SECRET, Duration.ofHours(1)));

    private static SecretKeySpec key() {
        return new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Test
    void registersClientWithHashedPassword() {
        when(users.existsByEmail("client@livedesk.local")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.register(new RegisterRequest("Client@Livedesk.local", PASSWORD));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash()))
                .isTrue();
        assertThat(response.email()).isEqualTo("client@livedesk.local");
        assertThat(response.role()).isEqualTo(UserRole.CLIENT);
    }

    @Test
    void rejectsRegistrationOfTakenEmail() {
        when(users.existsByEmail("client@livedesk.local")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("client@livedesk.local", PASSWORD)))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(users, never()).save(any());
    }

    @Test
    void issuesTokenWithUserIdAndRole() {
        User operator = new User("operator@livedesk.local", passwordEncoder.encode(PASSWORD), UserRole.OPERATOR);
        when(users.findByEmail("operator@livedesk.local")).thenReturn(Optional.of(operator));

        TokenResponse response = authService.login(new LoginRequest("operator@livedesk.local", PASSWORD));

        var jwt = jwtDecoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(operator.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("OPERATOR");
        assertThat(jwt.getExpiresAt()).isNotNull();
    }

    @Test
    void rejectsWrongPassword() {
        User user = new User("client@livedesk.local", passwordEncoder.encode(PASSWORD), UserRole.CLIENT);
        when(users.findByEmail("client@livedesk.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("client@livedesk.local", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejectsUnknownEmail() {
        when(users.findByEmail("nobody@livedesk.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@livedesk.local", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
