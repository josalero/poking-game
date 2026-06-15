# syntax=docker/dockerfile:1.7

FROM node:25-bookworm-slim AS node-runtime

FROM eclipse-temurin:25-jdk AS build
COPY --from=node-runtime /usr/local /usr/local

ENV PATH="/usr/local/bin:${PATH}"
ENV JAVA_HOME="/opt/java/openjdk"

WORKDIR /workspace

RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 \
    && rm -rf /var/lib/apt/lists/*

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY backend ./backend
COPY frontend ./frontend
COPY docs ./docs

RUN ./gradlew :backend:bootJar --no-daemon -Dorg.gradle.java.installations.paths="${JAVA_HOME}"

FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

RUN useradd --system --create-home --uid 10001 appuser

COPY --from=build /workspace/backend/build/libs/*.jar /app/app.jar

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ENV SERVER_PORT=8089
ENV SCRUM_POKING_ROOM_INACTIVITY_HOURS=1
ENV JAVA_OPTS=""

EXPOSE 8089

USER appuser

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
