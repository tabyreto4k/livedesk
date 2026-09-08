package ru.livedesk.chat.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.livedesk.presence.v1.GetStatusesRequest;
import ru.livedesk.presence.v1.GetStatusesResponse;
import ru.livedesk.presence.v1.HeartbeatRequest;
import ru.livedesk.presence.v1.PresenceGrpc;
import ru.livedesk.presence.v1.UserStatus;

class PresenceClientTest {

    private final PresenceGrpc.PresenceBlockingStub stub = mock(PresenceGrpc.PresenceBlockingStub.class);
    private final PresenceClient presence = new PresenceClient(stub);

    private final UUID online = UUID.randomUUID();
    private final UUID offline = UUID.randomUUID();

    @BeforeEach
    void applyDeadlineToItself() {
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
    }

    @Test
    void statusesAreReturnedForEveryRequestedUser() {
        when(stub.getStatuses(any(GetStatusesRequest.class)))
                .thenReturn(GetStatusesResponse.newBuilder()
                        .addStatuses(UserStatus.newBuilder()
                                .setUserId(online.toString())
                                .setOnline(true))
                        .build());

        assertThat(presence.statuses(List.of(online, offline)))
                .containsEntry(online, true)
                .containsEntry(offline, false);
    }

    /** Статусы — не критичный тракт: недоступный сервис гасится в offline, а не в 500. */
    @Test
    void unavailableServiceTurnsEveryoneOffline() {
        when(stub.getStatuses(any(GetStatusesRequest.class))).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThat(presence.statuses(List.of(online, offline))).containsValues(false, false);
    }

    @Test
    void failedHeartbeatDoesNotBreakTheSession() {
        when(stub.heartbeat(any(HeartbeatRequest.class))).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatCode(() -> presence.heartbeat(online)).doesNotThrowAnyException();
    }

    @Test
    void heartbeatCarriesTheUserId() {
        presence.heartbeat(online);

        verify(stub)
                .heartbeat(HeartbeatRequest.newBuilder()
                        .setUserId(online.toString())
                        .build());
    }

    @Test
    void emptyRequestDoesNotReachTheService() {
        assertThat(presence.statuses(List.of())).isEmpty();

        verify(stub, org.mockito.Mockito.never()).getStatuses(any(GetStatusesRequest.class));
    }
}
