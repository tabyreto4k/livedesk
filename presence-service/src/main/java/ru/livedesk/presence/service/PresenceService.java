package ru.livedesk.presence.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import ru.livedesk.presence.config.PresenceProperties;

/**
 * «Оффлайн» — это отсутствие heartbeat'а, поэтому статусы живут ключами с TTL: протух ключ —
 * пользователь оффлайн, и фоновому чистильщику работы не остаётся [Р8].
 */
@Service
public class PresenceService {

    private static final String KEY_PREFIX = "presence:";
    private static final String ONLINE = "1";

    private final StringRedisTemplate redis;
    private final PresenceProperties properties;

    public PresenceService(StringRedisTemplate redis, PresenceProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void heartbeat(String userId) {
        redis.opsForValue().set(key(userId), ONLINE, properties.ttl());
    }

    /** Статусы читаются одним MGET: EXISTS по ключу на пользователя — столько же round-trip'ов. */
    public Map<String, Boolean> statuses(List<String> userIds) {
        Map<String, Boolean> statuses = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return statuses;
        }
        List<String> values = redis.opsForValue()
                .multiGet(userIds.stream().map(PresenceService::key).toList());
        for (int i = 0; i < userIds.size(); i++) {
            statuses.put(userIds.get(i), values != null && values.get(i) != null);
        }
        return statuses;
    }

    private static String key(String userId) {
        return KEY_PREFIX + userId;
    }
}
