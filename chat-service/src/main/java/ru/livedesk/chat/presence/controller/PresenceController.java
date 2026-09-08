package ru.livedesk.chat.presence.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.livedesk.chat.presence.PresenceClient;
import ru.livedesk.chat.presence.dto.PresenceResponse;

/** Фронту нужен статус собеседника при открытии чата и по таймеру — отсюда REST-фасад. */
@RestController
@RequestMapping("/api/v1/presence")
public class PresenceController {

    private final PresenceClient presence;

    public PresenceController(PresenceClient presence) {
        this.presence = presence;
    }

    @GetMapping
    public PresenceResponse statuses(@RequestParam List<UUID> userIds) {
        return new PresenceResponse(presence.statuses(userIds));
    }
}
