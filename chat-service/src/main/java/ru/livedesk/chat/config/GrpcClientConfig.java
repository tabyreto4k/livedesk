package ru.livedesk.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import ru.livedesk.presence.v1.PresenceGrpc;

@Configuration
public class GrpcClientConfig {

    /** Канал ленивый: соединение поднимается на первом вызове, а не на старте chat-service. */
    @Bean
    PresenceGrpc.PresenceBlockingStub presenceStub(GrpcChannelFactory channels) {
        return PresenceGrpc.newBlockingStub(channels.createChannel("presence"));
    }
}
