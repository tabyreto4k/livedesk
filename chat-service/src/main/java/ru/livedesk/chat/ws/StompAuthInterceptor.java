package ru.livedesk.chat.ws;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import ru.livedesk.chat.auth.model.AuthenticatedUser;

/**
 * Токен приезжает заголовком CONNECT-фрейма, а не query-параметром: query уезжает в
 * access-логи nginx и сервера, заголовок STOMP — нет [Р4]. Проверяется один раз на CONNECT,
 * дальше все фреймы сессии идут от найденного пользователя.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public StompAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || StompCommand.CONNECT != accessor.getCommand()) {
            return message;
        }
        accessor.setUser(authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
        return message;
    }

    private AuthenticatedUser authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            throw new MessagingException("CONNECT без токена отклонён");
        }
        try {
            return AuthenticatedUser.from(jwtDecoder.decode(authorization.substring(BEARER.length())));
        } catch (JwtException | IllegalArgumentException exception) {
            throw new MessagingException("CONNECT с непригодным токеном отклонён");
        }
    }
}
