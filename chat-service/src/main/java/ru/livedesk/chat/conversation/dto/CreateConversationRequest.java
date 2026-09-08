package ru.livedesk.chat.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(@NotBlank @Size(max = 200) String topic) {}
