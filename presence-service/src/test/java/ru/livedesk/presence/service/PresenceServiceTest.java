package ru.livedesk.presence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import ru.livedesk.presence.config.PresenceProperties;

class PresenceServiceTest {

    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final PresenceService presence = new PresenceService(redis, new PresenceProperties(TTL));

    @Test
    void heartbeatWritesKeyThatExpiresOnItsOwn() {
        when(redis.opsForValue()).thenReturn(values);

        presence.heartbeat("user-1");

        verify(values).set("presence:user-1", "1", TTL);
    }

    @Test
    void userWithLiveKeyIsOnlineAndTheRestAreNot() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(List.of("presence:user-1", "presence:user-2"))).thenReturn(Arrays.asList("1", null));

        assertThat(presence.statuses(List.of("user-1", "user-2")))
                .containsExactly(Map.entry("user-1", true), Map.entry("user-2", false));
    }

    /** Redis отвечает `null` на MGET без ключей — спрашивать его о пустом списке незачем. */
    @Test
    void emptyRequestDoesNotReachRedis() {
        assertThat(presence.statuses(List.of())).isEmpty();

        verifyNoInteractions(redis);
    }
}
