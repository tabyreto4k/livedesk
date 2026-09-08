package ru.livedesk.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import ru.livedesk.chat.conversation.dto.ConversationResponse;
import ru.livedesk.chat.conversation.model.ConversationStatus;
import ru.livedesk.chat.message.dto.MessageResponse;
import tools.jackson.databind.ObjectMapper;

class RedisMessageRelayTest {

    private static final Instant SENT_AT = Instant.parse("2026-07-13T10:15:30Z");

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SimpMessagingTemplate broker = mock(SimpMessagingTemplate.class);
    private final RedisMessageRelay relay = new RedisMessageRelay(redis, broker, new ObjectMapper());

    private final UUID conversationId = UUID.randomUUID();
    private final MessageResponse message = new MessageResponse(1L, UUID.randomUUID(), "здравствуйте", SENT_AT);

    /** Инстанс не рассылает своим подписчикам сам: иначе они получили бы сообщение дважды. */
    @Test
    void publishedMessageGoesToTheChannelAndNotStraightToSubscribers() {
        relay.publishMessage(conversationId, message);

        assertThat(publishedTo(RedisMessageRelay.MESSAGES_CHANNEL))
                .contains(conversationId.toString())
                .contains("здравствуйте");
        verifyNoInteractions(broker);
    }

    @Test
    void messageFromTheChannelIsDeliveredToLocalSubscribers() {
        relay.publishMessage(conversationId, message);

        relay.onMessage(
                channelMessage(RedisMessageRelay.MESSAGES_CHANNEL, publishedTo(RedisMessageRelay.MESSAGES_CHANNEL)),
                null);

        verify(broker).convertAndSend("/topic/conversations/" + conversationId, message);
    }

    @Test
    void queueEventFromTheChannelGoesToOperators() {
        ConversationResponse conversation = new ConversationResponse(
                UUID.randomUUID(), UUID.randomUUID(), null, "тема", ConversationStatus.WAITING, SENT_AT);
        relay.publishQueueEvent(conversation);

        relay.onMessage(
                channelMessage(RedisMessageRelay.QUEUE_CHANNEL, publishedTo(RedisMessageRelay.QUEUE_CHANNEL)), null);

        verify(broker).convertAndSend("/topic/queue", conversation);
    }

    /** Сообщение уже в PG: недоступная шина стоит строчки в логе, но не исключения наверх. */
    @Test
    void brokenBusDoesNotBreakSending() {
        doThrow(new IllegalStateException("redis недоступен")).when(redis).convertAndSend(anyString(), any());

        assertThatCode(() -> relay.publishMessage(conversationId, message)).doesNotThrowAnyException();
    }

    @Test
    void garbageFromTheChannelIsNotForwarded() {
        assertThatCode(() -> relay.onMessage(channelMessage(RedisMessageRelay.MESSAGES_CHANNEL, "не json"), null))
                .doesNotThrowAnyException();

        verifyNoInteractions(broker);
    }

    @Test
    void unknownChannelIsIgnored() {
        relay.onMessage(channelMessage("livedesk:unknown", "{}"), null);

        verifyNoInteractions(broker);
    }

    private String publishedTo(String channel) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(channel), body.capture());
        return body.getValue();
    }

    private static DefaultMessage channelMessage(String channel, String body) {
        return new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }
}
