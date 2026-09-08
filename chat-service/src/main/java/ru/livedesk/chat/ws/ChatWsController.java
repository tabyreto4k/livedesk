package ru.livedesk.chat.ws;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import ru.livedesk.chat.auth.model.AuthenticatedUser;
import ru.livedesk.chat.message.dto.SendMessageRequest;
import ru.livedesk.chat.message.service.MessageService;
import ru.livedesk.chat.presence.PresenceClient;

@Controller
public class ChatWsController {

    private final MessageService messageService;
    private final PresenceClient presence;

    public ChatWsController(MessageService messageService, PresenceClient presence) {
        this.messageService = messageService;
        this.presence = presence;
    }

    @MessageMapping("/conversations/{conversationId}/send")
    public void send(
            @DestinationVariable UUID conversationId,
            @Valid @Payload SendMessageRequest request,
            AuthenticatedUser sender) {
        messageService.send(conversationId, sender, request.text());
    }

    /** Пульс идёт по уже открытому сокету: отдельный REST-запрос каждые 10 секунд не нужен. */
    @MessageMapping("/presence/heartbeat")
    public void heartbeat(AuthenticatedUser user) {
        presence.heartbeat(user.id());
    }
}
