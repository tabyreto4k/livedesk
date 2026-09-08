package ru.livedesk.presence.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.livedesk.presence.service.PresenceService;
import ru.livedesk.presence.v1.GetStatusesRequest;
import ru.livedesk.presence.v1.GetStatusesResponse;
import ru.livedesk.presence.v1.HeartbeatRequest;
import ru.livedesk.presence.v1.HeartbeatResponse;
import ru.livedesk.presence.v1.UserStatus;

class PresenceGrpcServiceTest {

    private final PresenceService presence = mock(PresenceService.class);
    private final PresenceGrpcService grpc = new PresenceGrpcService(presence);

    @Test
    void heartbeatIsPassedToTheService() {
        StreamObserver<HeartbeatResponse> observer = mock(StreamObserver.class);

        grpc.heartbeat(HeartbeatRequest.newBuilder().setUserId("user-1").build(), observer);

        verify(presence).heartbeat("user-1");
        verify(observer).onNext(HeartbeatResponse.getDefaultInstance());
        verify(observer).onCompleted();
    }

    @Test
    void heartbeatWithoutUserIdIsRejected() {
        StreamObserver<HeartbeatResponse> observer = mock(StreamObserver.class);

        grpc.heartbeat(HeartbeatRequest.getDefaultInstance(), observer);

        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        assertThat(Status.fromThrowable(error.getValue()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verifyNoInteractions(presence);
    }

    @Test
    void statusesAreReturnedForEveryRequestedUser() {
        Map<String, Boolean> statuses = new LinkedHashMap<>();
        statuses.put("user-1", true);
        statuses.put("user-2", false);
        when(presence.statuses(List.of("user-1", "user-2"))).thenReturn(statuses);
        StreamObserver<GetStatusesResponse> observer = mock(StreamObserver.class);

        grpc.getStatuses(
                GetStatusesRequest.newBuilder()
                        .addUserIds("user-1")
                        .addUserIds("user-2")
                        .build(),
                observer);

        ArgumentCaptor<GetStatusesResponse> response = ArgumentCaptor.forClass(GetStatusesResponse.class);
        verify(observer).onNext(response.capture());
        assertThat(response.getValue().getStatusesList())
                .extracting(UserStatus::getUserId, UserStatus::getOnline)
                .containsExactly(tuple("user-1", true), tuple("user-2", false));
        verify(observer).onCompleted();
    }
}
