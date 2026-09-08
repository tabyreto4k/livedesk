package ru.livedesk.presence.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** TTL — три периода heartbeat'а: статус переживает два потерянных пакета подряд [Р8]. */
@Validated
@ConfigurationProperties(prefix = "livedesk.presence")
public record PresenceProperties(@NotNull Duration ttl) {}
