package ru.livedesk.chat.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.livedesk.chat.IntegrationTestSupport;
import ru.livedesk.chat.conversation.dto.ConversationResponse;
import ru.livedesk.chat.conversation.dto.CreateConversationRequest;
import ru.livedesk.chat.message.dto.MessageResponse;
import ru.livedesk.chat.message.model.Message;
import ru.livedesk.chat.message.repository.MessageRepository;

class MessageKeysetIT extends IntegrationTestSupport {

    @Autowired
    private MessageRepository messages;

    @Test
    void historyIsPagedByCursorWithoutGapsOrDuplicates() {
        String token = registerClient(randomEmail());
        ConversationResponse conversation = createConversation(token);
        fill(conversation.id(), conversation.clientId(), 120);

        List<MessageResponse> first = history(token, conversation.id(), null, 50);
        List<MessageResponse> second = history(token, conversation.id(), cursor(first), 50);
        List<MessageResponse> third = history(token, conversation.id(), cursor(second), 50);

        assertThat(first).hasSize(50);
        assertThat(second).hasSize(50);
        assertThat(third).hasSize(20);

        List<Long> ids = concatIds(first, second, third);
        assertThat(ids).doesNotHaveDuplicates().isSortedAccordingTo((left, right) -> Long.compare(right, left));
        assertThat(ids).hasSize(120);
    }

    /** Ради этого и keyset: OFFSET после вставки сдвинул бы страницу и продублировал строку. */
    @Test
    void messagesArrivingBetweenPagesDoNotShiftTheCursor() {
        String token = registerClient(randomEmail());
        ConversationResponse conversation = createConversation(token);
        fill(conversation.id(), conversation.clientId(), 60);

        List<MessageResponse> first = history(token, conversation.id(), null, 50);
        fill(conversation.id(), conversation.clientId(), 10);
        List<MessageResponse> second = history(token, conversation.id(), cursor(first), 50);

        assertThat(second).hasSize(10);
        assertThat(concatIds(first, second)).doesNotHaveDuplicates().hasSize(60);
    }

    @Test
    void strangerCannotReadHistory() {
        ConversationResponse conversation = createConversation(registerClient(randomEmail()));
        String strangerToken = registerClient(randomEmail());

        client.get()
                .uri("/api/v1/conversations/{id}/messages", conversation.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(strangerToken))
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    private void fill(UUID conversationId, UUID senderId, int count) {
        for (int i = 0; i < count; i++) {
            messages.save(new Message(conversationId, senderId, "сообщение " + i));
        }
    }

    private static Long cursor(List<MessageResponse> page) {
        return page.getLast().id();
    }

    @SafeVarargs
    private static List<Long> concatIds(List<MessageResponse>... pages) {
        return List.of(pages).stream()
                .flatMap(List::stream)
                .map(MessageResponse::id)
                .toList();
    }

    private List<MessageResponse> history(String token, UUID conversationId, Long before, int limit) {
        String uri = before == null
                ? "/api/v1/conversations/%s/messages?limit=%d".formatted(conversationId, limit)
                : "/api/v1/conversations/%s/messages?before=%d&limit=%d".formatted(conversationId, before, limit);
        return Objects.requireNonNull(client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new ParameterizedTypeReference<List<MessageResponse>>() {})
                .returnResult()
                .getResponseBody());
    }

    private ConversationResponse createConversation(String token) {
        return Objects.requireNonNull(client.post()
                .uri("/api/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateConversationRequest("история"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(ConversationResponse.class)
                .returnResult()
                .getResponseBody());
    }
}
