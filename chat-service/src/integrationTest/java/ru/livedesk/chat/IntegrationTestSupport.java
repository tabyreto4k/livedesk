package ru.livedesk.chat;

import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ru.livedesk.chat.auth.dto.LoginRequest;
import ru.livedesk.chat.auth.dto.RegisterRequest;
import ru.livedesk.chat.auth.dto.TokenResponse;
import ru.livedesk.chat.auth.model.User;
import ru.livedesk.chat.auth.model.UserRole;
import ru.livedesk.chat.auth.repository.UserRepository;

/**
 * Общая обвязка интеграционных тестов: один контейнер Postgres на весь прогон и HTTP-клиент,
 * бьющий в реальный порт приложения.
 */
@ActiveProfiles("it")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestSupport {

    protected static final String OPERATOR_EMAIL = "operator@livedesk.local";
    protected static final String OPERATOR_PASSWORD = "operator-secret";
    protected static final String CLIENT_PASSWORD = "client-secret";

    private static final String OPERATOR_PASSWORD_HASH = new BCryptPasswordEncoder().encode(OPERATOR_PASSWORD);

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @LocalServerPort
    protected int port;

    protected RestTestClient client;

    @BeforeEach
    void bindClient() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @DynamicPropertySource
    static void migrationPlaceholders(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.placeholders.operator_email", () -> OPERATOR_EMAIL);
        registry.add("spring.flyway.placeholders.operator_password_hash", () -> OPERATOR_PASSWORD_HASH);
    }

    protected static String randomEmail() {
        return "client-" + UUID.randomUUID() + "@livedesk.local";
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected String registerClient(String email) {
        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, CLIENT_PASSWORD))
                .exchange()
                .expectStatus()
                .isCreated();
        return login(email, CLIENT_PASSWORD);
    }

    /** Второго оператора через API не завести: роль OPERATOR выдаётся только миграцией. */
    protected String registerOperator() {
        String email = "operator-" + UUID.randomUUID() + "@livedesk.local";
        users.save(new User(email, passwordEncoder.encode(CLIENT_PASSWORD), UserRole.OPERATOR));
        return login(email, CLIENT_PASSWORD);
    }

    protected String loginOperator() {
        return login(OPERATOR_EMAIL, OPERATOR_PASSWORD);
    }

    protected String login(String email, String password) {
        TokenResponse token = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();
        return Objects.requireNonNull(token).accessToken();
    }
}
