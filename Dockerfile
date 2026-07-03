FROM debian:trixie-slim AS builder

WORKDIR /app

COPY . .

RUN apt-get update && apt-get install -y \
    openjdk-25-jdk \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN chmod +x /app/gradlew

RUN /app/gradlew clean shadowJar

FROM azul/zulu-openjdk-alpine:25-latest

WORKDIR /app

COPY --from=builder /app/build/libs/*-all.jar /app/app.jar

EXPOSE 47007

VOLUME /app/plugins

ENTRYPOINT ["java", "-jar", "/app/app.jar"]