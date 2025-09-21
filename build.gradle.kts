plugins {
    kotlin("jvm") version "2.2.10"
    id("com.gradleup.shadow") version "9.1.0"
    idea
    application
    `maven-publish`
}

group = "dev.minjae.stargate"
version = "1.1"

repositories {
    mavenCentral()
    maven("https://repo.waterdog.dev/releases")
    maven("https://repo.waterdog.dev/snapshots")
}

dependencies {
    testImplementation(kotlin("test"))
    api("alemiz.stargate:server:2.3-SNAPSHOT")
    api("ch.qos.logback:logback-classic:1.5.18")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.0")
    implementation("com.fasterxml.jackson.module:jackson-module-blackbird:2.20.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.0")
}

tasks.test {
    useJUnitPlatform()
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "minjae-repo"
            url = uri("https://repo.minjae.dev/snapshots")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}


application {
    mainClass.set("dev.minjae.stargate.StarGateLauncher")
}