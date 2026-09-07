package ru.livedesk.chat.message.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.livedesk.chat.auth.model.AuthenticatedUser;
import ru.livedesk.chat.conversation.model.Conversation;
import ru.livedesk.chat.conversation.model.ConversationStatus;
import ru.livedesk.chat.conversation.service.ConversationService;
import ru.livedesk.chat.exception.IllegalStateTransitionException;
import ru.livedesk.chat.message.dto.MessageResponse;
import ru.livedesk.chat.message.model.Message;
import ru.livedesk.chat.message.repository.MessageRepository;

@Service
public class MessageService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messages;
    private final ConversationService conversations;

    public MessageService(MessageRepository messages, ConversationService conversations) {
        this.messages = messages;
        this.conversations = conversations;
    }

    @Transactional
    public MessageResponse send(UUID conversationId, AuthenticatedUser sender, String text) {
        Conversation conversation = conversations.requireParticipant(conversationId, sender);
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new IllegalStateTransitionException("Обращение закрыто, писать в него нельзя");
        }
        return MessageResponse.of(messages.save(new Message(conversationId, sender.id(), text)));
    }

    /**
     * Страница истории от свежих к старым. Курсор `before` — id последнего показанного
     * сообщения: вставки между запросами страниц его не сдвигают, в отличие от OFFSET.
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> history(UUID conversationId, AuthenticatedUser user, Long before, int limit) {
        conversations.requireParticipant(conversationId, user);
        Limit page = Limit.of(Math.clamp(limit, 1, MAX_PAGE_SIZE));
        List<Message> found = before == null
                ? messages.findByConversationIdOrderByIdDesc(conversationId, page)
                : messages.findByConversationIdAndIdLessThanOrderByIdDesc(conversationId, before, page);
        return found.stream().map(MessageResponse::of).toList();
    }
}
