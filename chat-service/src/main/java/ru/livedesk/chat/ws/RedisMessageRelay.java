package ru.livedesk.chat.ws;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import ru.livedesk.chat.conversation.dto.ConversationResponse;
import ru.livedesk.chat.message.dto.MessageResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Мост между инстансами [Р3]: инстанс не рассылает своим подписчикам напрямую, а публикует
 * в Redis-канал, откуда сообщение возвращается всем инстансам сразу — включая тот, что его
 * отправил. Поэтому `convertAndSend` вызывается ровно в одном месте, в {@link #onMessage}.
 */
@Component
public class RedisMessageRelay implements MessageListener {

    public static final String MESSAGES_CHANNEL = "livedesk:messages";
    public static final String QUEUE_CHANNEL = "livedesk:queue";

    private static final Logger LOG = LoggerFactory.getLogger(RedisMessageRelay.class);

    private static final String CONVERSATION_TOPIC = "/topic/conversations/";
    private static final String QUEUE_TOPIC = "/topic/queue";

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate broker;
    private final ObjectMapper json;

    public RedisMessageRelay(StringRedisTemplate redis, SimpMessagingTemplate broker, ObjectMapper json) {
        this.redis = redis;
        this.broker = broker;
        this.json = json;
    }

    public void publishMessage(UUID conversationId, MessageResponse message) {
        publish(MESSAGES_CHANNEL, new MessageEvent(conversationId, message));
    }

    public void publishQueueEvent(ConversationResponse conversation) {
        publish(QUEUE_CHANNEL, conversation);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            switch (channel) {
                case MESSAGES_CHANNEL -> {
                    MessageEvent event = json.readValue(body, MessageEvent.class);
                    broker.convertAndSend(CONVERSATION_TOPIC + event.conversationId(), event.message());
                }
                case QUEUE_CHANNEL ->
                    broker.convertAndSend(QUEUE_TOPIC, json.readValue(body, ConversationResponse.class));
                default -> LOG.warn("Пришло сообщение из незнакомого канала {}", channel);
            }
        } catch (RuntimeException e) {
            LOG.warn("Сообщение из канала {} не разобрано: {}", channel, e.toString());
        }
    }

    /**
     * Сбой шины не должен ронять отправку: сообщение уже в PG, и подписчики дочитают
     * пропущенное из истории — это и есть цена at-most-once у pub/sub [Р3].
     */
    private void publish(String channel, Object payload) {
        try {
            redis.convertAndSend(channel, json.writeValueAsString(payload));
        } catch (RuntimeException e) {
            LOG.warn("Не удалось опубликовать в канал {}: {}", channel, e.toString());
        }
    }

    record MessageEvent(UUID conversationId, MessageResponse message) {}
}
