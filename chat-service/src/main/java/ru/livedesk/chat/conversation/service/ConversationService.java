package ru.livedesk.chat.conversation.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.livedesk.chat.auth.model.AuthenticatedUser;
import ru.livedesk.chat.conversation.dto.ConversationResponse;
import ru.livedesk.chat.conversation.dto.CreateConversationRequest;
import ru.livedesk.chat.conversation.model.Conversation;
import ru.livedesk.chat.conversation.model.ConversationStatus;
import ru.livedesk.chat.conversation.repository.ConversationRepository;
import ru.livedesk.chat.exception.IllegalStateTransitionException;
import ru.livedesk.chat.exception.NotFoundException;

@Service
public class ConversationService {

    private final ConversationRepository conversations;

    public ConversationService(ConversationRepository conversations) {
        this.conversations = conversations;
    }

    @Transactional
    public ConversationResponse create(AuthenticatedUser client, CreateConversationRequest request) {
        Conversation conversation = new Conversation(client.id(), request.topic());
        return ConversationResponse.of(conversations.save(conversation));
    }

    /** Оператор без `mine` видит очередь, с `mine` — свои обращения; клиент — только свои. */
    @Transactional(readOnly = true)
    public List<ConversationResponse> list(AuthenticatedUser user, ConversationStatus status, boolean mine) {
        List<Conversation> found;
        if (user.isOperator() && !mine) {
            found = conversations.findByStatusOrderByCreatedAtAsc(status == null ? ConversationStatus.WAITING : status);
        } else if (user.isOperator()) {
            found = status == null
                    ? conversations.findByOperatorIdOrderByCreatedAtDesc(user.id())
                    : conversations.findByOperatorIdAndStatusOrderByCreatedAtDesc(user.id(), status);
        } else {
            found = status == null
                    ? conversations.findByClientIdOrderByCreatedAtDesc(user.id())
                    : conversations.findByClientIdAndStatusOrderByCreatedAtDesc(user.id(), status);
        }
        return found.stream().map(ConversationResponse::of).toList();
    }

    @Transactional
    public ConversationResponse take(UUID id, AuthenticatedUser operator) {
        Conversation conversation = find(id);
        if (conversations.takeIfWaiting(id, operator.id()) == 0) {
            throw new IllegalStateTransitionException("Обращение уже забрано или закрыто");
        }
        // Строку уже поменял UPDATE, сущность об этом не знает — приводим её в тот же вид,
        // чтобы ответ не показывал устаревший статус.
        conversation.take(operator.id());
        return ConversationResponse.of(conversation);
    }

    @Transactional
    public ConversationResponse close(UUID id, AuthenticatedUser user) {
        Conversation conversation = requireParticipant(id, user);
        conversation.close();
        return ConversationResponse.of(conversation);
    }

    /** Не участник не должен даже узнать, что обращение существует. */
    @Transactional(readOnly = true)
    public Conversation requireParticipant(UUID id, AuthenticatedUser user) {
        Conversation conversation = find(id);
        if (!conversation.hasParticipant(user.id())) {
            throw new NotFoundException("Обращение %s не найдено".formatted(id));
        }
        return conversation;
    }

    private Conversation find(UUID id) {
        return conversations
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Обращение %s не найдено".formatted(id)));
    }
}
