package ru.livedesk.chat.conversation.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.livedesk.chat.conversation.model.Conversation;
import ru.livedesk.chat.conversation.model.ConversationStatus;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Состязание двух операторов решает БД: условие по статусу входит в сам UPDATE, поэтому
     * строку получит ровно один, а второму вернётся 0. `clearAutomatically` отцепляет сущности
     * от контекста — иначе грязная проверка попыталась бы записать их поверх этого UPDATE.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Conversation c set c.status = ru.livedesk.chat.conversation.model.ConversationStatus.ACTIVE, "
            + "c.operatorId = :operatorId where c.id = :id "
            + "and c.status = ru.livedesk.chat.conversation.model.ConversationStatus.WAITING")
    int takeIfWaiting(@Param("id") UUID id, @Param("operatorId") UUID operatorId);

    List<Conversation> findByStatusOrderByCreatedAtAsc(ConversationStatus status);

    List<Conversation> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    List<Conversation> findByClientIdAndStatusOrderByCreatedAtDesc(UUID clientId, ConversationStatus status);

    List<Conversation> findByOperatorIdOrderByCreatedAtDesc(UUID operatorId);

    List<Conversation> findByOperatorIdAndStatusOrderByCreatedAtDesc(UUID operatorId, ConversationStatus status);
}
