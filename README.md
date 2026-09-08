# LiveDesk

[![ci](https://github.com/tabyreto4k/livedesk/actions/workflows/ci.yml/badge.svg)](https://github.com/tabyreto4k/livedesk/actions/workflows/ci.yml)
[![codeql](https://github.com/tabyreto4k/livedesk/actions/workflows/codeql.yml/badge.svg)](https://github.com/tabyreto4k/livedesk/actions/workflows/codeql.yml)
[![release](https://github.com/tabyreto4k/livedesk/actions/workflows/release.yml/badge.svg)](https://github.com/tabyreto4k/livedesk/actions/workflows/release.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Чат поддержки «клиент ↔ оператор»: сообщения в реальном времени, история, статусы онлайн
и очередь обращений. Два инстанса чата за nginx обмениваются сообщениями через Redis
pub/sub — горизонтальное масштабирование stateful-соединений видно вживую.

> **Статус:** чат работает на двух инстансах за nginx. Presence и фронт — в работе.

## Стек

Java 21 · Spring Boot 4.1 · STOMP over WebSocket + SockJS · PostgreSQL 16 + Flyway ·
Redis (pub/sub + TTL) · gRPC + protobuf · nginx · JUnit 5 + Testcontainers ·
Gradle (Kotlin DSL) · Docker · GitHub Actions

## Модули

| Модуль | Роль |
|---|---|
| `chat-service` | WebSocket/STOMP, история сообщений, keyset-пагинация |
| `presence-service` | кто онлайн: heartbeat, статусы в Redis с TTL, gRPC наружу |
| `presence-contract` | protobuf-контракт presence и сгенерированные стабы |

## Запуск

```bash
cp .env.example .env      # заполнить JWT_SECRET и OPERATOR_PASSWORD_HASH
docker compose up -d --wait
```

Наружу смотрит только nginx — `http://localhost:8080`. За ним два инстанса
`chat-service`, и какой обслужил запрос, видно и в его логах (`INFO [chat-1]`),
и в логе nginx (`адрес клиента -> адрес инстанса`).

Что чат переживает потерю инстанса, проверяется так:

```bash
docker compose logs -f nginx                 # видно, кто кого обслужил
docker compose stop chat-service-1           # убить инстанс с одним из собеседников
docker compose logs chat-service-2 | tail    # клиент переподключился ко второму
docker compose start chat-service-1
```

Сообщения при этом не теряются: они лежат в PostgreSQL, а после переподключения
клиент дочитывает пропущенное keyset-запросом истории.

Образы каждой ревизии `main` — в GHCR: `ghcr.io/tabyreto4k/livedesk/<сервис>:main`.

## Разработка

```bash
./gradlew build              # компиляция, spotless, checkstyle, unit-тесты, jacoco
./gradlew integrationTest    # Testcontainers, нужен запущенный Docker
./gradlew spotlessApply
```

JDK 21 на машине иметь не обязательно: тулчейн скачает нужный сам.

## Архитектурные решения

Раздел собирается по мере готовности: WebSocket против SSE и long polling; почему здесь
Redis pub/sub, а не Kafka; gRPC против REST между сервисами; что делать с медленным клиентом.

## Лицензия

[MIT](LICENSE)
