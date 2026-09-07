package ru.livedesk.chat.auth.service;

import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.livedesk.chat.auth.dto.LoginRequest;
import ru.livedesk.chat.auth.dto.RegisterRequest;
import ru.livedesk.chat.auth.dto.TokenResponse;
import ru.livedesk.chat.auth.dto.UserResponse;
import ru.livedesk.chat.auth.model.AuthenticatedUser;
import ru.livedesk.chat.auth.model.User;
import ru.livedesk.chat.auth.model.UserRole;
import ru.livedesk.chat.auth.repository.UserRepository;
import ru.livedesk.chat.config.JwtProperties;
import ru.livedesk.chat.exception.EmailAlreadyUsedException;
import ru.livedesk.chat.exception.InvalidCredentialsException;

@Service
public class AuthService {

    private static final String ISSUER = "livedesk";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    /** Через API регистрируются только клиенты: оператора заводит миграция. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }
        User user = new User(email, passwordEncoder.encode(request.password()), UserRole.CLIENT);
        return UserResponse.of(users.save(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email())).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return new TokenResponse(issueToken(user));
    }

    private String issueToken(User user) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(jwtProperties.ttl()))
                .claim(AuthenticatedUser.ROLE_CLAIM, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
