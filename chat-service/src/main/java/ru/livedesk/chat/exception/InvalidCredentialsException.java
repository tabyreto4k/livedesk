package ru.livedesk.chat.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Неверный адрес или пароль");
    }
}
