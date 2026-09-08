package ru.livedesk.chat.auth.dto;

import java.util.UUID;
import ru.livedesk.chat.auth.model.User;
import ru.livedesk.chat.auth.model.UserRole;

public record UserResponse(UUID id, String email, UserRole role) {

    public static UserResponse of(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
