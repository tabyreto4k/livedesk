package ru.livedesk.chat.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final int MESSAGE_SIZE_LIMIT = 64 * 1024;
    private static final int SEND_BUFFER_SIZE_LIMIT = 512 * 1024;
    private static final int SEND_TIME_LIMIT_MS = 10_000;

    private final StompAuthInterceptor authInterceptor;

    public WebSocketConfig(StompAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * Один и тот же путь регистрируется дважды: браузерный фронт идёт через SockJS-fallback
     * на `/ws/**`, а обычный STOMP-клиент подключается нативным WebSocket прямо к `/ws`.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    /**
     * Медленный клиент не должен копить сообщения в памяти сервера: при переполнении буфера
     * сессия закрывается, клиент переподключается и дочитывает пропущенное из истории.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setSendTimeLimit(SEND_TIME_LIMIT_MS)
                .setSendBufferSizeLimit(SEND_BUFFER_SIZE_LIMIT)
                .setMessageSizeLimit(MESSAGE_SIZE_LIMIT);
    }
}
