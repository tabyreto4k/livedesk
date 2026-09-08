package ru.livedesk.chat.conversation.dto;

import java.time.Instant;
import java.util.UUID;
import ru.livedesk.chat.conversation.model.Conversation;
import ru.livedesk.chat.conversation.model.ConversationStatus;

public record ConversationResponse(
        UUID id, UUID clientId, UUID operatorId, String topic, ConversationStatus status, Instant createdAt) {

    public static ConversationResponse of(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getClientId(),
                conversation.getOperatorId(),
                conversation.getTopic(),
                conversation.getStatus(),
                conversation.getCreatedAt());
    }
}
