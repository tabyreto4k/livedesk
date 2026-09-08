package ru.livedesk.presence.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;
import ru.livedesk.presence.service.PresenceService;
import ru.livedesk.presence.v1.GetStatusesRequest;
import ru.livedesk.presence.v1.GetStatusesResponse;
import ru.livedesk.presence.v1.HeartbeatRequest;
import ru.livedesk.presence.v1.HeartbeatResponse;
import ru.livedesk.presence.v1.PresenceGrpc;
import ru.livedesk.presence.v1.UserStatus;

/** Транспорт: разобрать запрос, позвать сервис, собрать ответ. */
@Service
public class PresenceGrpcService extends PresenceGrpc.PresenceImplBase {

    private final PresenceService presence;

    public PresenceGrpcService(PresenceService presence) {
        this.presence = presence;
    }

    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> observer) {
        if (request.getUserId().isBlank()) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription("user_id обязателен")
                    .asRuntimeException());
            return;
        }
        presence.heartbeat(request.getUserId());
        observer.onNext(HeartbeatResponse.getDefaultInstance());
        observer.onCompleted();
    }

    @Override
    public void getStatuses(GetStatusesRequest request, StreamObserver<GetStatusesResponse> observer) {
        GetStatusesResponse.Builder response = GetStatusesResponse.newBuilder();
        presence.statuses(request.getUserIdsList())
                .forEach((userId, online) -> response.addStatuses(
                        UserStatus.newBuilder().setUserId(userId).setOnline(online)));
        observer.onNext(response.build());
        observer.onCompleted();
    }
}
