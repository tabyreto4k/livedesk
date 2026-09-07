package ru.livedesk.chat.conversation.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.livedesk.chat.auth.model.AuthenticatedUser;
import ru.livedesk.chat.conversation.dto.ConversationResponse;
import ru.livedesk.chat.conversation.dto.CreateConversationRequest;
import ru.livedesk.chat.conversation.model.ConversationStatus;
import ru.livedesk.chat.conversation.service.ConversationService;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateConversationRequest request) {
        return conversationService.create(AuthenticatedUser.from(jwt), request);
    }

    @GetMapping
    public List<ConversationResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ConversationStatus status,
            @RequestParam(defaultValue = "false") boolean mine) {
        return conversationService.list(AuthenticatedUser.from(jwt), status, mine);
    }

    @PostMapping("/{id}/take")
    public ConversationResponse take(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return conversationService.take(id, AuthenticatedUser.from(jwt));
    }

    @PostMapping("/{id}/close")
    public ConversationResponse close(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return conversationService.close(id, AuthenticatedUser.from(jwt));
    }
}
