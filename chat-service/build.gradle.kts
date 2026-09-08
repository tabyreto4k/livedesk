plugins {
  id("livedesk.spring-service")
}

dependencies {
  implementation(project(":presence-contract"))
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-websocket")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("org.springframework.boot:spring-boot-starter-security")
  // Свои токены выпускаем и проверяем Nimbus'ом из Spring Security: сторонняя JWT-библиотека
  // не нужна, версии приезжают из BOM.
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-grpc-client")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  integrationTestImplementation("org.testcontainers:testcontainers-postgresql")
}
