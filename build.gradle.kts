plugins {
  alias(libs.plugins.spotless)
}

group = "ru.livedesk"
version = "0.1.0"

repositories { mavenCentral() }

spotless {
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("**/build/**")
    ktlint()
  }
}
