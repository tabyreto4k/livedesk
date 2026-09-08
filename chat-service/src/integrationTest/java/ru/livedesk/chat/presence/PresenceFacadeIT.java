package ru.livedesk.chat.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import ru.livedesk.chat.IntegrationTestSupport;
import ru.livedesk.chat.presence.dto.PresenceResponse;

/** presence-service не поднят: чат обязан ответить статусами offline, а не пятисоткой. */
class PresenceFacadeIT extends IntegrationTestSupport {

    @Test
    void statusesFallBackToOfflineWhenPresenceServiceIsDown() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        PresenceResponse response = Objects.requireNonNull(client.get()
                .uri("/api/v1/presence?userIds={first},{second}", first, second)
                .header(HttpHeaders.AUTHORIZATION, bearer(registerClient(randomEmail())))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PresenceResponse.class)
                .returnResult()
                .getResponseBody());

        assertThat(response.statuses()).containsEntry(first, false).containsEntry(second, false);
    }

    @Test
    void statusesAreNotPublic() {
        client.get()
                .uri("/api/v1/presence?userIds={id}", UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
