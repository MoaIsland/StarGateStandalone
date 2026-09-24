plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.3"
    idea
    application
    `maven-publish`
}

group = "dev.minjae.stargate"
// Fork version. Upstream never published 1.2, so this suffix marks "upstream 1.2 plus
// the MoaIsland startup fixes" and cannot be confused with a future upstream release.
version = "1.2-moa.1"

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

java {
    withJavadocJar()
    withSourcesJar()
}


application {
    mainClass.set("dev.minjae.stargate.StarGateLauncher")
}