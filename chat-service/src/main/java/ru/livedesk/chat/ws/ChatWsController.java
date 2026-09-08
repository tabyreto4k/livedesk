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

@Controller
public class ChatWsController {

    private final MessageService messageService;

    public ChatWsController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/conversations/{conversationId}/send")
    public void send(
            @DestinationVariable UUID conversationId,
            @Valid @Payload SendMessageRequest request,
            AuthenticatedUser sender) {
        messageService.send(conversationId, sender, request.text());
    }
}
