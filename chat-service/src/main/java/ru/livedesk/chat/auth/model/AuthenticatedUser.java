package ru.livedesk.chat.auth.model;

import java.security.Principal;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Пользователь за текущим запросом или STOMP-сессией. Один тип на оба транспорта: в REST
 * собирается из `@AuthenticationPrincipal`, в WebSocket кладётся в сессию как `Principal`.
 */
public record AuthenticatedUser(UUID id, UserRole role) implements Principal {

    public static final String ROLE_CLAIM = "role";

    public static AuthenticatedUser from(Jwt jwt) {
        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()), UserRole.valueOf(jwt.getClaimAsString(ROLE_CLAIM)));
    }

    public boolean isOperator() {
        return role == UserRole.OPERATOR;
    }

    @Override
    public String getName() {
        return id.toString();
    }
}
