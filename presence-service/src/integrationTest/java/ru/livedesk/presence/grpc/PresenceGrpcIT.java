package ru.livedesk.presence.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import ru.livedesk.presence.v1.GetStatusesRequest;
import ru.livedesk.presence.v1.HeartbeatRequest;
import ru.livedesk.presence.v1.PresenceGrpc;
import ru.livedesk.presence.v1.UserStatus;

/** TTL укорочен до двух секунд: протухание ключа — это и есть переход в оффлайн [Р8]. */
@SpringBootTest(properties = {"spring.grpc.server.port=0", "livedesk.presence.ttl=2s"})
class PresenceGrpcIT {

    private static final int REDIS_PORT = 6379;
    private static final int TIMEOUT_SECONDS = 10;

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    static {
        REDIS.start();
    }

    @LocalGrpcServerPort
    private int grpcPort;

    private ManagedChannel channel;
    private PresenceGrpc.PresenceBlockingStub presence;

    @BeforeEach
    void openChannel() {
        channel = ManagedChannelBuilder.forAddress("localhost", grpcPort)
                .usePlaintext()
                .build();
        presence = PresenceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void closeChannel() throws InterruptedException {
        channel.shutdownNow().awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void heartbeatPutsUserOnlineAndSilenceTakesHimOffline() {
        String userId = "user-" + System.nanoTime();
        presence.heartbeat(HeartbeatRequest.newBuilder().setUserId(userId).build());

        assertThat(statuses(userId)).containsEntry(userId, true);

        awaitOffline(userId);
    }

    @Test
    void userWhoNeverSaidHelloIsOffline() {
        String stranger = "stranger-" + System.nanoTime();

        assertThat(statuses(stranger)).containsEntry(stranger, false);
    }

    private Map<String, Boolean> statuses(String... userIds) {
        return presence
                .getStatuses(GetStatusesRequest.newBuilder()
                        .addAllUserIds(List.of(userIds))
                        .build())
                .getStatusesList()
                .stream()
                .collect(java.util.stream.Collectors.toMap(UserStatus::getUserId, UserStatus::getOnline));
    }

    private void awaitOffline(String userId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (!statuses(userId).get(userId)) {
                return;
            }
            sleep();
        }
        throw new AssertionError("Ключ %s не протух за %d секунд".formatted(userId, TIMEOUT_SECONDS));
    }

    private static void sleep() {
        try {
            TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
