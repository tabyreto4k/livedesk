package ru.livedesk.chat.auth.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthenticatedUserTest {

    @Test
    void readsIdAndRoleFromToken() {
        UUID id = UUID.randomUUID();

        AuthenticatedUser user = AuthenticatedUser.from(jwt(id, "OPERATOR"));

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.role()).isEqualTo(UserRole.OPERATOR);
        assertThat(user.isOperator()).isTrue();
    }

    @Test
    void clientIsNotOperator() {
        AuthenticatedUser user = AuthenticatedUser.from(jwt(UUID.randomUUID(), "CLIENT"));

        assertThat(user.isOperator()).isFalse();
    }

    /** STOMP-сессия хранит `Principal`, а не наш тип: имя должно оставаться идентификатором. */
    @Test
    void principalNameIsUserId() {
        UUID id = UUID.randomUUID();

        assertThat(AuthenticatedUser.from(jwt(id, "CLIENT")).getName()).isEqualTo(id.toString());
    }

    private static Jwt jwt(UUID id, String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(id.toString())
                .claim(AuthenticatedUser.ROLE_CLAIM, role)
                .build();
    }
}
