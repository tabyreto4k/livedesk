package ru.livedesk.chat.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import ru.livedesk.chat.IntegrationTestSupport;
import ru.livedesk.chat.auth.dto.LoginRequest;
import ru.livedesk.chat.auth.dto.RegisterRequest;
import ru.livedesk.chat.auth.dto.UserResponse;
import ru.livedesk.chat.auth.model.UserRole;

class AuthFlowIT extends IntegrationTestSupport {

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void registersClientAndIssuesTokenWithItsIdAndRole() {
        String email = randomEmail();

        UserResponse registered = client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, CLIENT_PASSWORD))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(Objects.requireNonNull(registered).role()).isEqualTo(UserRole.CLIENT);

        Jwt jwt = jwtDecoder.decode(login(email, CLIENT_PASSWORD));
        assertThat(jwt.getSubject()).isEqualTo(registered.id().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CLIENT");
    }

    @Test
    void rejectsSecondRegistrationOfTheSameEmail() {
        String email = randomEmail();
        registerClient(email);

        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, CLIENT_PASSWORD))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsWrongPassword() {
        String email = randomEmail();
        registerClient(email);

        client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, "wrong-password"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void rejectsShortPasswordWithFieldErrors() {
        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(randomEmail(), "short"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.errors.password")
                .exists();
    }

    /** Оператора заводит миграция V1 — через API роль OPERATOR не выдаётся никому. */
    @Test
    void seededOperatorLogsInWithOperatorRole() {
        Jwt jwt = jwtDecoder.decode(loginOperator());

        assertThat(jwt.getClaimAsString("role")).isEqualTo("OPERATOR");
    }

    @Test
    void rejectsProtectedResourceWithoutToken() {
        client.get().uri("/api/v1/conversations").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void acceptsIssuedTokenOnProtectedResource() {
        String token = registerClient(randomEmail());

        client.get()
                .uri("/api/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus()
                .isOk();
    }
}
