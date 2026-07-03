plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.3"
}

group = "dev.minjae.stargate"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.waterdog.dev/releases")
    maven("https://repo.waterdog.dev/snapshots")
}

dependencies {
    compileOnly(project(":"))
}

tasks.test {
    useJUnitPlatform()
}