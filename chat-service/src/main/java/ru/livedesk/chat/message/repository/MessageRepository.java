package ru.livedesk.chat.message.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.livedesk.chat.message.model.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Первая страница истории: свежие сообщения обращения. */
    List<Message> findByConversationIdOrderByIdDesc(UUID conversationId, Limit limit);

    /** Следующая страница — keyset по курсору, а не OFFSET: индекс (conversation_id, id desc). */
    List<Message> findByConversationIdAndIdLessThanOrderByIdDesc(UUID conversationId, Long before, Limit limit);
}
