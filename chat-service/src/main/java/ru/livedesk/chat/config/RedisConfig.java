package ru.livedesk.chat.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import ru.livedesk.chat.ws.RedisMessageRelay;

@Configuration
public class RedisConfig {

    /** Подписка живёт на отдельном соединении, поэтому инстанс слышит и то, что опубликовал сам. */
    @Bean
    RedisMessageListenerContainer relayListenerContainer(
            RedisConnectionFactory connectionFactory, RedisMessageRelay relay) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                relay,
                List.of(
                        new ChannelTopic(RedisMessageRelay.MESSAGES_CHANNEL),
                        new ChannelTopic(RedisMessageRelay.QUEUE_CHANNEL)));
        return container;
    }
}
