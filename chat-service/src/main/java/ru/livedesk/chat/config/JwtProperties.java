package ru.livedesk.chat.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Секрет — только из окружения; HS256 требует ключ не короче 256 бит. */
@Validated
@ConfigurationProperties(prefix = "livedesk.jwt")
public record JwtProperties(@Size(min = 32) String secret, @NotNull Duration ttl) {}
