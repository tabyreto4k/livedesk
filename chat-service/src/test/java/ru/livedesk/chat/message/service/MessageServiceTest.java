package ru.livedesk.chat.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import ru.livedesk.chat.auth.model.AuthenticatedUser;
import ru.livedesk.chat.auth.model.UserRole;
import ru.livedesk.chat.conversation.model.Conversation;
import ru.livedesk.chat.conversation.service.ConversationService;
import ru.livedesk.chat.exception.IllegalStateTransitionException;
import ru.livedesk.chat.exception.NotFoundException;
import ru.livedesk.chat.message.dto.MessageResponse;
import ru.livedesk.chat.message.model.Message;
import ru.livedesk.chat.message.repository.MessageRepository;

class MessageServiceTest {

    private final MessageRepository messages = mock(MessageRepository.class);
    private final ConversationService conversations = mock(ConversationService.class);
    private final MessageService messageService = new MessageService(messages, conversations);

    private final AuthenticatedUser sender = new AuthenticatedUser(UUID.randomUUID(), UserRole.CLIENT);
    private final UUID conversationId = UUID.randomUUID();

    @Test
    void savesMessageOfConversationParticipant() {
        when(conversations.requireParticipant(conversationId, sender))
                .thenReturn(new Conversation(sender.id(), "тема"));
        when(messages.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MessageResponse response = messageService.send(conversationId, sender, "здравствуйте");

        assertThat(response.text()).isEqualTo("здравствуйте");
        assertThat(response.senderId()).isEqualTo(sender.id());
    }

    @Test
    void refusesToWriteIntoClosedConversation() {
        Conversation conversation = new Conversation(sender.id(), "тема");
        conversation.close();
        when(conversations.requireParticipant(conversationId, sender)).thenReturn(conversation);

        assertThatThrownBy(() -> messageService.send(conversationId, sender, "ещё вопрос"))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void strangerCannotSendMessage() {
        when(conversations.requireParticipant(conversationId, sender)).thenThrow(new NotFoundException("нет такого"));

        assertThatThrownBy(() -> messageService.send(conversationId, sender, "привет"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void firstPageIsReadWithoutCursor() {
        messageService.history(conversationId, sender, null, 50);

        verify(messages).findByConversationIdOrderByIdDesc(conversationId, Limit.of(50));
    }

    @Test
    void nextPageIsReadByCursor() {
        messageService.history(conversationId, sender, 500L, 20);

        verify(messages).findByConversationIdAndIdLessThanOrderByIdDesc(conversationId, 500L, Limit.of(20));
    }

    @Test
    void pageSizeIsClamped() {
        messageService.history(conversationId, sender, null, 5000);
        messageService.history(conversationId, sender, null, 0);

        ArgumentCaptor<Limit> limits = ArgumentCaptor.forClass(Limit.class);
        verify(messages, times(2)).findByConversationIdOrderByIdDesc(eq(conversationId), limits.capture());
        assertThat(limits.getAllValues()).extracting(Limit::max).containsExactly(100, 1);
    }

    @Test
    void historyIsRefusedToStranger() {
        when(conversations.requireParticipant(conversationId, sender)).thenThrow(new NotFoundException("нет такого"));

        assertThatThrownBy(() -> messageService.history(conversationId, sender, null, 50))
                .isInstanceOf(NotFoundException.class);
    }
}
