# Scrum Poking Game

Spring Boot 4.0.6 and React/Vite planning poker app.

## Requirements

- Java 25
- Node.js 20.19 or newer
- npm

The Gradle build uses a Java 25 toolchain. On macOS with Homebrew OpenJDK, `gradle.properties` points Gradle at the common Homebrew JDK locations. If your JDK lives somewhere else, set `JAVA_HOME` to that Java 25 install before running Gradle.

## Build

```bash
./gradlew build
```

## Docker Compose

Local development:

```bash
docker compose up --build
```

The local compose setup reads `.env` and exposes the app at `http://localhost:8089` by default.

Coolify production: use compose file `docker-compose.coolify.yml`. Map your domain to service `app` on port `8089`. Set `ROOM_INACTIVITY_HOURS` (default `1`) and optional `JAVA_OPTS` in Coolify env. Rooms are in-memory and clear on restart.

## Run

```bash
./gradlew :backend:bootRun
```

The backend runs on `8089` by default.

In a second terminal, start the Vite dev server:

```bash
cd frontend
npm run dev
```

Vite may choose `5174` if `5173` is already busy; that is fine. The dev server proxies `/api`, `/healthz`, and `/ws` to `http://localhost:8089`.

If you run the backend on a different port, point the Vite proxy at it:

```bash
SCRUM_POKING_BACKEND_URL=http://localhost:18080 npm run dev
```
