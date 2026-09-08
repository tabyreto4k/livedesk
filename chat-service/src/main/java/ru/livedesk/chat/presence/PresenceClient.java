package ru.livedesk.chat.presence;

import io.grpc.StatusRuntimeException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.livedesk.presence.v1.GetStatusesRequest;
import ru.livedesk.presence.v1.HeartbeatRequest;
import ru.livedesk.presence.v1.PresenceGrpc;
import ru.livedesk.presence.v1.UserStatus;

/**
 * Единственное место, где виден gRPC-стаб: наружу торчат только UUID и boolean. Presence —
 * не критичный тракт, поэтому недоступный сервис не роняет запрос, а гасит статусы в offline.
 */
@Component
public class PresenceClient {

    private static final Logger LOG = LoggerFactory.getLogger(PresenceClient.class);
    private static final long DEADLINE_MS = 500;

    private final PresenceGrpc.PresenceBlockingStub presence;

    public PresenceClient(PresenceGrpc.PresenceBlockingStub presence) {
        this.presence = presence;
    }

    public void heartbeat(UUID userId) {
        try {
            withDeadline()
                    .heartbeat(HeartbeatRequest.newBuilder()
                            .setUserId(userId.toString())
                            .build());
        } catch (StatusRuntimeException failure) {
            LOG.warn("Heartbeat {} не доехал до presence-service: {}", userId, failure.getStatus());
        }
    }

    /** Ответ покрывает ровно то, о чём спросили: о ком presence-service промолчал — тот offline. */
    public Map<UUID, Boolean> statuses(List<UUID> userIds) {
        List<UUID> asked = userIds.stream().distinct().toList();
        if (asked.isEmpty()) {
            return Map.of();
        }
        Map<String, Boolean> answered = fetch(asked);
        Map<UUID, Boolean> statuses = new LinkedHashMap<>();
        asked.forEach(userId -> statuses.put(userId, answered.getOrDefault(userId.toString(), false)));
        return statuses;
    }

    private Map<String, Boolean> fetch(List<UUID> asked) {
        try {
            return withDeadline()
                    .getStatuses(GetStatusesRequest.newBuilder()
                            .addAllUserIds(asked.stream().map(UUID::toString).toList())
                            .build())
                    .getStatusesList()
                    .stream()
                    .collect(Collectors.toMap(UserStatus::getUserId, UserStatus::getOnline, (first, second) -> first));
        } catch (StatusRuntimeException failure) {
            LOG.warn("presence-service недоступен, статусы отданы как offline: {}", failure.getStatus());
            return Map.of();
        }
    }

    private PresenceGrpc.PresenceBlockingStub withDeadline() {
        return presence.withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS);
    }
}
