package ru.livedesk.chat.message.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private static final String CONVERSATION_TOPIC = "/topic/conversations/";

    private final MessageRepository messages;
    private final ConversationService conversations;
    private final SimpMessagingTemplate broker;

    public MessageService(MessageRepository messages, ConversationService conversations, SimpMessagingTemplate broker) {
        this.messages = messages;
        this.conversations = conversations;
        this.broker = broker;
    }

    /**
     * Без общей транзакции намеренно: подписчики получают сообщение уже после того, как
     * `save` зафиксировал строку, — иначе клиент увидел бы то, чего в истории ещё нет.
     * В Заходе 3 прямая рассылка сменится публикацией в Redis, порядок останется тот же.
     */
    public MessageResponse send(UUID conversationId, AuthenticatedUser sender, String text) {
        Conversation conversation = conversations.requireParticipant(conversationId, sender);
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new IllegalStateTransitionException("Обращение закрыто, писать в него нельзя");
        }
        MessageResponse response = MessageResponse.of(messages.save(new Message(conversationId, sender.id(), text)));
        broker.convertAndSend(CONVERSATION_TOPIC + conversationId, response);
        return response;
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
