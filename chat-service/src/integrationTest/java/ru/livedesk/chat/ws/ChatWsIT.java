package ru.livedesk.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import ru.livedesk.chat.IntegrationTestSupport;
import ru.livedesk.chat.conversation.dto.ConversationResponse;
import ru.livedesk.chat.conversation.dto.CreateConversationRequest;
import ru.livedesk.chat.message.dto.MessageResponse;
import ru.livedesk.chat.message.dto.SendMessageRequest;
import ru.livedesk.chat.message.repository.MessageRepository;

class ChatWsIT extends IntegrationTestSupport {

    private static final int TIMEOUT_SECONDS = 10;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private SimpUserRegistry userRegistry;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void startStompClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void stopStompClient() {
        sessions.forEach(StompSession::disconnect);
        sessions.clear();
        stompClient.stop();
    }

    @Test
    void bothSidesOfConversationReceiveMessageAndItLandsInDatabase() throws Exception {
        String clientToken = registerClient(randomEmail());
        String operatorToken = loginOperator();
        ConversationResponse conversation = takenConversation(clientToken, operatorToken);

        StompSession clientSession = connect(clientToken);
        StompSession operatorSession = connect(operatorToken);
        BlockingQueue<MessageResponse> clientInbox = subscribe(clientSession, conversation.id());
        BlockingQueue<MessageResponse> operatorInbox = subscribe(operatorSession, conversation.id());
        awaitSubscriptions(conversation.id(), 2);

        clientSession.send(
                "/app/conversations/%s/send".formatted(conversation.id()), new SendMessageRequest("здравствуйте"));

        assertThat(receive(clientInbox).text()).isEqualTo("здравствуйте");
        MessageResponse forOperator = receive(operatorInbox);
        assertThat(forOperator.text()).isEqualTo("здравствуйте");
        assertThat(forOperator.senderId()).isEqualTo(conversation.clientId());

        assertThat(messages.findByConversationIdOrderByIdDesc(conversation.id(), Limit.of(10)))
                .singleElement()
                .satisfies(stored -> assertThat(stored.getText()).isEqualTo("здравствуйте"));
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> connect(null)).isNotNull();
    }

    @Test
    void connectWithGarbageTokenIsRejected() {
        assertThatThrownBy(() -> connect("not-a-token")).isNotNull();
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        StompSession session = stompClient
                .connectAsync(
                        "ws://localhost:%d/ws".formatted(port),
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private BlockingQueue<MessageResponse> subscribe(StompSession session, UUID conversationId) {
        BlockingQueue<MessageResponse> inbox = new LinkedBlockingQueue<>();
        session.subscribe(topic(conversationId), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((MessageResponse) payload);
            }
        });
        return inbox;
    }

    /**
     * SUBSCRIBE уходит асинхронно, и простой брокер не отвечает на него receipt'ом. Ждём, пока
     * подписки появятся в реестре сервера, иначе тест гоняется с собственным SEND.
     */
    private void awaitSubscriptions(UUID conversationId, int expected) throws InterruptedException {
        String destination = topic(conversationId);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            long registered = userRegistry
                    .findSubscriptions(subscription -> destination.equals(subscription.getDestination()))
                    .size();
            if (registered >= expected) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("Сервер не зарегистрировал %d подписок на %s".formatted(expected, destination));
    }

    private static String topic(UUID conversationId) {
        return "/topic/conversations/" + conversationId;
    }

    private static MessageResponse receive(BlockingQueue<MessageResponse> inbox) throws InterruptedException {
        return Objects.requireNonNull(inbox.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private ConversationResponse takenConversation(String clientToken, String operatorToken) {
        ConversationResponse created = Objects.requireNonNull(client.post()
                .uri("/api/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateConversationRequest("чат в обе стороны"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(ConversationResponse.class)
                .returnResult()
                .getResponseBody());
        client.post()
                .uri("/api/v1/conversations/{id}/take", created.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(operatorToken))
                .exchange()
                .expectStatus()
                .isOk();
        return created;
    }
}
