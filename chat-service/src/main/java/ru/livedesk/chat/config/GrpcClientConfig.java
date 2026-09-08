package ru.livedesk.chat.config;

import io.grpc.ManagedChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import ru.livedesk.presence.v1.PresenceGrpc;

@Configuration
public class GrpcClientConfig {

    @Bean
    PresenceGrpc.PresenceBlockingStub presenceStub(GrpcChannelFactory channels) {
        ManagedChannel channel = channels.createChannel("presence");
        // Канал ленивый, и первый вызов иначе платит за резолв имени и установку соединения
        // из своего дедлайна в 500 мс — на стенде это стоило DEADLINE_EXCEEDED.
        channel.getState(true);
        return PresenceGrpc.newBlockingStub(channel);
    }
}
