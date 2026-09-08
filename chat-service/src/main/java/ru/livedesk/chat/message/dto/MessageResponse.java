package ru.livedesk.chat.message.dto;

import java.time.Instant;
import java.util.UUID;
import ru.livedesk.chat.message.model.Message;

public record MessageResponse(Long id, UUID senderId, String text, Instant sentAt) {

    public static MessageResponse of(Message message) {
        return new MessageResponse(message.getId(), message.getSenderId(), message.getText(), message.getSentAt());
    }
}
