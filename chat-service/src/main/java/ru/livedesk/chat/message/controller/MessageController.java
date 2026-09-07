package ru.livedesk.chat.message.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.livedesk.chat.auth.model.AuthenticatedUser;
import ru.livedesk.chat.message.dto.MessageResponse;
import ru.livedesk.chat.message.service.MessageService;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public List<MessageResponse> history(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "" + MessageService.DEFAULT_PAGE_SIZE) int limit) {
        return messageService.history(conversationId, AuthenticatedUser.from(jwt), before, limit);
    }
}
