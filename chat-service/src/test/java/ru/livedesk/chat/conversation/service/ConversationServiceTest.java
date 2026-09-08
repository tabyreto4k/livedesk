package ru.livedesk.chat.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.livedesk.chat.auth.model.AuthenticatedUser;
import ru.livedesk.chat.auth.model.UserRole;
import ru.livedesk.chat.conversation.dto.ConversationResponse;
import ru.livedesk.chat.conversation.dto.CreateConversationRequest;
import ru.livedesk.chat.conversation.model.Conversation;
import ru.livedesk.chat.conversation.model.ConversationStatus;
import ru.livedesk.chat.conversation.repository.ConversationRepository;
import ru.livedesk.chat.exception.IllegalStateTransitionException;
import ru.livedesk.chat.exception.NotFoundException;

class ConversationServiceTest {

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationService conversationService = new ConversationService(conversations);

    private final AuthenticatedUser client = new AuthenticatedUser(UUID.randomUUID(), UserRole.CLIENT);
    private final AuthenticatedUser operator = new AuthenticatedUser(UUID.randomUUID(), UserRole.OPERATOR);

    @Test
    void createdConversationBelongsToItsClient() {
        when(conversations.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConversationResponse response = conversationService.create(client, new CreateConversationRequest("тема"));

        assertThat(response.clientId()).isEqualTo(client.id());
        assertThat(response.status()).isEqualTo(ConversationStatus.WAITING);
    }

    @Test
    void takeReturnsConversationAssignedToOperator() {
        Conversation conversation = new Conversation(client.id(), "тема");
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversations.takeIfWaiting(conversation.getId(), operator.id())).thenReturn(1);

        ConversationResponse response = conversationService.take(conversation.getId(), operator);

        assertThat(response.status()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(response.operatorId()).isEqualTo(operator.id());
    }

    /** Ноль обновлённых строк — обращение забрал кто-то другой между чтением и записью. */
    @Test
    void takeRejectsWhenUpdateTouchedNoRows() {
        Conversation conversation = new Conversation(client.id(), "тема");
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversations.takeIfWaiting(conversation.getId(), operator.id())).thenReturn(0);

        assertThatThrownBy(() -> conversationService.take(conversation.getId(), operator))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void takeOfMissingConversationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(conversations.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.take(id, operator)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void closeIsAllowedToParticipant() {
        Conversation conversation = new Conversation(client.id(), "тема");
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        ConversationResponse response = conversationService.close(conversation.getId(), client);

        assertThat(response.status()).isEqualTo(ConversationStatus.CLOSED);
    }

    @Test
    void strangerDoesNotLearnThatConversationExists() {
        Conversation conversation = new Conversation(client.id(), "тема");
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        AuthenticatedUser stranger = new AuthenticatedUser(UUID.randomUUID(), UserRole.CLIENT);

        assertThatThrownBy(() -> conversationService.close(conversation.getId(), stranger))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void operatorWithoutMineSeesWaitingQueue() {
        when(conversations.findByStatusOrderByCreatedAtAsc(ConversationStatus.WAITING))
                .thenReturn(List.of(new Conversation(client.id(), "тема")));

        assertThat(conversationService.list(operator, null, false)).hasSize(1);
        verify(conversations).findByStatusOrderByCreatedAtAsc(ConversationStatus.WAITING);
    }

    @Test
    void operatorWithMineSeesOwnConversations() {
        when(conversations.findByOperatorIdAndStatusOrderByCreatedAtDesc(operator.id(), ConversationStatus.ACTIVE))
                .thenReturn(List.of());

        conversationService.list(operator, ConversationStatus.ACTIVE, true);

        verify(conversations).findByOperatorIdAndStatusOrderByCreatedAtDesc(operator.id(), ConversationStatus.ACTIVE);
    }

    @Test
    void operatorWithMineAndNoStatusSeesAllOwnConversations() {
        conversationService.list(operator, null, true);

        verify(conversations).findByOperatorIdOrderByCreatedAtDesc(operator.id());
    }

    @Test
    void clientSeesOnlyOwnConversations() {
        conversationService.list(client, null, false);
        conversationService.list(client, ConversationStatus.CLOSED, true);

        verify(conversations).findByClientIdOrderByCreatedAtDesc(client.id());
        verify(conversations).findByClientIdAndStatusOrderByCreatedAtDesc(client.id(), ConversationStatus.CLOSED);
    }
}
