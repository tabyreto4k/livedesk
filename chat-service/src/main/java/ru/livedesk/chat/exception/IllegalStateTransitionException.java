package ru.livedesk.chat.exception;

public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
