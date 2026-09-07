import com.google.protobuf.gradle.id

// Модуль не берёт livedesk.java-conventions намеренно: своего Java-кода здесь нет,
// а checkstyle и spotless на сгенерированных стабах — шум и правки в чужой код.
plugins {
  `java-library`
  alias(libs.plugins.protobuf)
}

group = "ru.livedesk"

version = "0.1.0"

java {
  toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

dependencies {
  // api: стабы — часть контракта модуля, потребителям нужны эти типы в компиляции.
  api(libs.protobuf.java)
  api(libs.grpc.protobuf)
  api(libs.grpc.stub)
  compileOnly(libs.tomcat.annotations)
}

protobuf {
  protoc { artifact = libs.protoc.get().toString() }
  plugins {
    id("grpc") {
      artifact =
        libs.grpc.codegen
          .get()
          .toString()
    }
  }
  generateProtoTasks {
    all().configureEach { plugins { id("grpc") } }
  }
}
