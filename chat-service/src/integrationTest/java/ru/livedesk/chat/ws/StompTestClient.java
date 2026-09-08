package ru.livedesk.chat.ws;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** STOMP-клиент, привязанный к порту одного инстанса: тестам про два инстанса их нужно два. */
final class StompTestClient implements AutoCloseable {

    private static final int TIMEOUT_SECONDS = 10;

    private final WebSocketStompClient stompClient;
    private final String url;
    private final List<StompSession> sessions = new ArrayList<>();

    StompTestClient(int port) {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        url = "ws://localhost:%d/ws".formatted(port);
    }

    StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        StompSession session = stompClient
                .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    static <T> BlockingQueue<T> subscribe(StompSession session, String destination, Class<T> payloadType) {
        BlockingQueue<T> inbox = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add(payloadType.cast(payload));
            }
        });
        return inbox;
    }

    static <T> T receive(BlockingQueue<T> inbox) throws InterruptedException {
        return Objects.requireNonNull(inbox.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Сообщение не пришло");
    }

    /**
     * SUBSCRIBE уходит асинхронно, и простой брокер не отвечает на него receipt'ом. Ждём, пока
     * подписки появятся в реестре нужного инстанса, иначе тест гоняется с собственным SEND.
     */
    static void awaitSubscriptions(SimpUserRegistry registry, String destination, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            long registered = registry.findSubscriptions(
                            subscription -> destination.equals(subscription.getDestination()))
                    .size();
            if (registered >= expected) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("Инстанс не зарегистрировал %d подписок на %s".formatted(expected, destination));
    }

    @Override
    public void close() {
        sessions.forEach(StompSession::disconnect);
        sessions.clear();
        stompClient.stop();
    }
}
