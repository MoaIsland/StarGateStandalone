plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.3"
    idea
    application
    `maven-publish`
}

group = "dev.minjae.stargate"
// Fork version: upstream 1.2 plus the MoaIsland fixes (startup, block-same-names). Upstream
// never published 1.2 itself (repo.minjae.dev stops at 1.1), so the patch number is free to use.
version = "1.2.2"

repositories {
    mavenCentral()
    maven("https://repo.waterdog.dev/releases")
    maven("https://repo.waterdog.dev/snapshots")
}

dependencies {
    testImplementation(kotlin("test"))
    api("alemiz.stargate:server:2.3-SNAPSHOT")
    api("ch.qos.logback:logback-classic:1.5.18")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.0")
    implementation("com.fasterxml.jackson.module:jackson-module-blackbird:2.22.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.22.0")
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
            // GitHub Packages rejects an artifactId containing uppercase letters with a bare
            // 422 Unprocessable Entity, so the published coordinate is lower-cased. The Gradle
            // project keeps its original name; only the published artifactId differs.
            artifactId = "stargate-standalone"
        }
    }
    repositories {
        // Fork publishing target. Consumers need a GitHub token with read:packages,
        // which CI gets for free through GITHUB_TOKEN.
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MoaIsland/StarGateStandalone")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.token").orNull
            }
        }
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

kotlin {
    // Pinned so the produced class files do not depend on whichever JDK Gradle happens to run
    // on. 21 is the current LTS and nothing here needs anything newer; leaving it unset made
    // a CI build emit class file version 68, which then failed to load on a Java 21 server.
    jvmToolchain(21)
}

java {
    withJavadocJar()
    withSourcesJar()
}


application {
    mainClass.set("dev.minjae.stargate.StarGateLauncher")
}