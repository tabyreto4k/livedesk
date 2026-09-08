package ru.livedesk.chat.presence.dto;

import java.util.Map;
import java.util.UUID;

public record PresenceResponse(Map<UUID, Boolean> statuses) {}
