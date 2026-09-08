package ru.livedesk.chat.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import ru.livedesk.chat.IntegrationTestSupport;
import ru.livedesk.chat.conversation.dto.ConversationResponse;
import ru.livedesk.chat.conversation.dto.CreateConversationRequest;
import ru.livedesk.chat.conversation.model.ConversationStatus;

class QueueFlowIT extends IntegrationTestSupport {

    @Test
    void createdConversationWaitsInQueueUntilOperatorTakesIt() {
        String clientToken = registerClient(randomEmail());
        String operatorToken = loginOperator();

        ConversationResponse created = createConversation(clientToken, "не приходит счёт");
        assertThat(created.status()).isEqualTo(ConversationStatus.WAITING);

        List<ConversationResponse> queue = client.get()
                .uri("/api/v1/conversations?status=WAITING")
                .header(HttpHeaders.AUTHORIZATION, bearer(operatorToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new ParameterizedTypeReference<List<ConversationResponse>>() {})
                .returnResult()
                .getResponseBody();
        assertThat(queue).extracting(ConversationResponse::id).contains(created.id());

        ConversationResponse taken = client.post()
                .uri("/api/v1/conversations/{id}/take", created.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(operatorToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ConversationResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(Objects.requireNonNull(taken).status()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(taken.operatorId()).isNotNull();
    }

    /** Ключевая проверка очереди: два оператора не могут забрать одно обращение. */
    @Test
    void concurrentTakeLetsExactlyOneOperatorThrough() throws Exception {
        String clientToken = registerClient(randomEmail());
        ConversationResponse created = createConversation(clientToken, "гонка за обращение");
        String firstOperator = registerOperator();
        String secondOperator = registerOperator();

        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = pool.submit(take(created, firstOperator, barrier));
            Future<Integer> second = pool.submit(take(created, secondOperator, barrier));

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(HttpStatus.OK.value(), HttpStatus.CONFLICT.value());
        }
    }

    @Test
    void clientCannotTakeConversation() {
        String clientToken = registerClient(randomEmail());
        ConversationResponse created = createConversation(clientToken, "тема");

        client.post()
                .uri("/api/v1/conversations/{id}/take", created.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void strangerGetsNotFoundOnClose() {
        ConversationResponse created = createConversation(registerClient(randomEmail()), "тема");
        String strangerToken = registerClient(randomEmail());

        client.post()
                .uri("/api/v1/conversations/{id}/close", created.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(strangerToken))
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void closedConversationCannotBeClosedTwice() {
        String clientToken = registerClient(randomEmail());
        ConversationResponse created = createConversation(clientToken, "тема");

        closeConversation(created, clientToken).expectStatus().isOk();
        closeConversation(created, clientToken).expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    private RestTestClient.ResponseSpec closeConversation(ConversationResponse conversation, String token) {
        return client.post()
                .uri("/api/v1/conversations/{id}/close", conversation.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }

    private ConversationResponse createConversation(String clientToken, String topic) {
        ConversationResponse created = client.post()
                .uri("/api/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateConversationRequest(topic))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(ConversationResponse.class)
                .returnResult()
                .getResponseBody();
        return Objects.requireNonNull(created);
    }

    private Callable<Integer> take(ConversationResponse conversation, String token, CyclicBarrier barrier) {
        RestTestClient ownClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        return () -> {
            barrier.await();
            return ownClient
                    .post()
                    .uri("/api/v1/conversations/{id}/take", conversation.id())
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .exchange()
                    .returnResult()
                    .getStatus()
                    .value();
        };
    }
}
