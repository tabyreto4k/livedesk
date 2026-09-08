package ru.livedesk.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ru.livedesk.chat.ws.StompTestClient.awaitSubscriptions;
import static ru.livedesk.chat.ws.StompTestClient.receive;
import static ru.livedesk.chat.ws.StompTestClient.subscribe;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import ru.livedesk.chat.IntegrationTestSupport;
import ru.livedesk.chat.conversation.dto.ConversationResponse;
import ru.livedesk.chat.message.dto.MessageResponse;
import ru.livedesk.chat.message.dto.SendMessageRequest;
import ru.livedesk.chat.message.repository.MessageRepository;

class ChatWsIT extends IntegrationTestSupport {

    @Autowired
    private MessageRepository messages;

    @Autowired
    private SimpUserRegistry userRegistry;

    private StompTestClient stomp;

    @BeforeEach
    void startStompClient() {
        stomp = new StompTestClient(port);
    }

    @AfterEach
    void stopStompClient() {
        stomp.close();
    }

    @Test
    void bothSidesOfConversationReceiveMessageAndItLandsInDatabase() throws Exception {
        String clientToken = registerClient(randomEmail());
        String operatorToken = loginOperator();
        ConversationResponse conversation = takenConversation(clientToken, operatorToken, "чат в обе стороны");
        String topic = topic(conversation.id());

        StompSession clientSession = stomp.connect(clientToken);
        StompSession operatorSession = stomp.connect(operatorToken);
        BlockingQueue<MessageResponse> clientInbox = subscribe(clientSession, topic, MessageResponse.class);
        BlockingQueue<MessageResponse> operatorInbox = subscribe(operatorSession, topic, MessageResponse.class);
        awaitSubscriptions(userRegistry, topic, 2);

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
        assertThatThrownBy(() -> stomp.connect(null)).isNotNull();
    }

    @Test
    void connectWithGarbageTokenIsRejected() {
        assertThatThrownBy(() -> stomp.connect("not-a-token")).isNotNull();
    }

    private static String topic(UUID conversationId) {
        return "/topic/conversations/" + conversationId;
    }
}
