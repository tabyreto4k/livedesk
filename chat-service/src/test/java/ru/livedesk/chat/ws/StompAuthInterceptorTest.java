package ru.livedesk.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import ru.livedesk.chat.auth.model.AuthenticatedUser;
import ru.livedesk.chat.auth.model.UserRole;

class StompAuthInterceptorTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes";

    private final JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key()));
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(NimbusJwtDecoder.withSecretKey(key())
            .macAlgorithm(MacAlgorithm.HS256)
            .build());
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void connectWithValidTokenGetsPrincipal() {
        UUID userId = UUID.randomUUID();
        StompHeaderAccessor accessor =
                connectFrame("Bearer " + token(userId, Instant.now().plusSeconds(60)));

        interceptor.preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);

        assertThat(accessor.getUser()).isEqualTo(new AuthenticatedUser(userId, UserRole.CLIENT));
    }

    @Test
    void connectWithoutTokenIsRejected() {
        StompHeaderAccessor accessor = connectFrame(null);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel)).isInstanceOf(MessagingException.class);
    }

    @Test
    void connectWithGarbageTokenIsRejected() {
        StompHeaderAccessor accessor = connectFrame("Bearer not-a-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel)).isInstanceOf(MessagingException.class);
    }

    @Test
    void connectWithExpiredTokenIsRejected() {
        String expired = token(UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.HOURS));
        StompHeaderAccessor accessor = connectFrame("Bearer " + expired);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel)).isInstanceOf(MessagingException.class);
    }

    /** Токен проверяется один раз на CONNECT: остальные фреймы идут в контексте сессии. */
    @Test
    void otherFramesPassThroughUntouched() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        assertThat(accessor.getUser()).isNull();
    }

    private StompHeaderAccessor connectFrame(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return accessor;
    }

    private String token(UUID userId, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(expiresAt.minusSeconds(600))
                .expiresAt(expiresAt)
                .claim(AuthenticatedUser.ROLE_CLAIM, UserRole.CLIENT.name())
                .build();
        return jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static SecretKeySpec key() {
        return new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
