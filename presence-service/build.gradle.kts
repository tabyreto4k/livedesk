plugins {
  id("livedesk.spring-service")
}

dependencies {
  implementation(project(":presence-contract"))
  // web остаётся ради actuator: gRPC слушает свой порт, healthcheck образа ходит по HTTP.
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-grpc-server")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")

  integrationTestImplementation("org.springframework.boot:spring-boot-starter-grpc-server-test")
  integrationTestImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
