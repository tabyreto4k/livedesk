package ru.livedesk.chat.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsNotFoundTo404() {
        ProblemDetail problem = handler.handleNotFound(new NotFoundException("Обращение не найдено"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Обращение не найдено");
    }

    @Test
    void mapsTakenEmailTo409() {
        ProblemDetail problem = handler.handleEmailAlreadyUsed(new EmailAlreadyUsedException("client@livedesk.local"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).contains("client@livedesk.local");
    }

    @Test
    void mapsBadCredentialsTo401() {
        ProblemDetail problem = handler.handleInvalidCredentials(new InvalidCredentialsException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void reportsFieldErrorsOnValidationFailure() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("registerRequest", "password", "слишком короткий")));

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getProperties()).containsEntry("errors", Map.of("password", "слишком короткий"));
    }
}
