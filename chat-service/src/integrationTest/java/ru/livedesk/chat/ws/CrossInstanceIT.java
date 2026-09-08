package ru.livedesk.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.livedesk.chat.ws.StompTestClient.awaitSubscriptions;
import static ru.livedesk.chat.ws.StompTestClient.receive;
import static ru.livedesk.chat.ws.StompTestClient.subscribe;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import ru.livedesk.chat.ChatServiceApplication;
import ru.livedesk.chat.IntegrationTestSupport;
import ru.livedesk.chat.conversation.dto.ConversationResponse;
import ru.livedesk.chat.conversation.model.ConversationStatus;
import ru.livedesk.chat.message.dto.MessageResponse;
import ru.livedesk.chat.message.dto.SendMessageRequest;

/**
 * Два инстанса на одних Postgres и Redis: первый поднимает `@SpringBootTest`, второй — руками.
 * Собеседники сидят на разных инстансах, а переписка у них общая — это и проверяем [Р3].
 */
class CrossInstanceIT extends IntegrationTestSupport {

    private static final String QUEUE_TOPIC = "/topic/queue";

    private static ConfigurableApplicationContext secondInstance;

    @Autowired
    private SimpUserRegistry firstRegistry;

    private StompTestClient firstStomp;
    private StompTestClient secondStomp;

    @BeforeAll
    static void startSecondInstance() {
        secondInstance = new SpringApplicationBuilder(ChatServiceApplication.class)
                .profiles("it")
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.data.redis.host=" + REDIS.getHost(),
                        "--spring.data.redis.port=" + REDIS.getFirstMappedPort(),
                        "--spring.flyway.placeholders.operator_email=" + OPERATOR_EMAIL,
                        "--spring.flyway.placeholders.operator_password_hash=" + OPERATOR_PASSWORD_HASH);
    }

    @AfterAll
    static void stopSecondInstance() {
        secondInstance.close();
    }

    @BeforeEach
    void startStompClients() {
        firstStomp = new StompTestClient(port);
        secondStomp = new StompTestClient(
                ((WebServerApplicationContext) secondInstance).getWebServer().getPort());
    }

    @AfterEach
    void stopStompClients() {
        firstStomp.close();
        secondStomp.close();
    }

    @Test
    void messageTravelsBetweenInstancesInBothDirections() throws Exception {
        String clientToken = registerClient(randomEmail());
        String operatorToken = loginOperator();
        ConversationResponse conversation = takenConversation(clientToken, operatorToken, "разные инстансы");
        String topic = topic(conversation.id());

        StompSession clientSession = firstStomp.connect(clientToken);
        StompSession operatorSession = secondStomp.connect(operatorToken);
        BlockingQueue<MessageResponse> clientInbox = subscribe(clientSession, topic, MessageResponse.class);
        BlockingQueue<MessageResponse> operatorInbox = subscribe(operatorSession, topic, MessageResponse.class);
        awaitSubscriptions(firstRegistry, topic, 1);
        awaitSubscriptions(secondRegistry(), topic, 1);

        clientSession.send(sendDestination(conversation.id()), new SendMessageRequest("здравствуйте"));

        assertThat(receive(operatorInbox).text()).isEqualTo("здравствуйте");
        assertThat(receive(clientInbox).text()).isEqualTo("здравствуйте");

        operatorSession.send(sendDestination(conversation.id()), new SendMessageRequest("слушаю вас"));

        assertThat(receive(clientInbox).text()).isEqualTo("слушаю вас");
        assertThat(receive(operatorInbox).text()).isEqualTo("слушаю вас");
    }

    /** Обращение создаётся REST-запросом в первый инстанс, а очередь видит оператор на втором. */
    @Test
    void newConversationReachesOperatorOnAnotherInstance() throws Exception {
        StompSession operatorSession = secondStomp.connect(loginOperator());
        BlockingQueue<ConversationResponse> queueInbox =
                subscribe(operatorSession, QUEUE_TOPIC, ConversationResponse.class);
        awaitSubscriptions(secondRegistry(), QUEUE_TOPIC, 1);

        ConversationResponse created = createConversation(registerClient(randomEmail()), "нужна помощь");

        ConversationResponse announced = receive(queueInbox);
        assertThat(announced.id()).isEqualTo(created.id());
        assertThat(announced.status()).isEqualTo(ConversationStatus.WAITING);
    }

    private static SimpUserRegistry secondRegistry() {
        return secondInstance.getBean(SimpUserRegistry.class);
    }

    private static String topic(UUID conversationId) {
        return "/topic/conversations/" + conversationId;
    }

    private static String sendDestination(UUID conversationId) {
        return "/app/conversations/%s/send".formatted(conversationId);
    }
}
