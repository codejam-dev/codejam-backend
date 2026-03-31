# CodeJam Backend

Modular **monolith** (single JVM): authentication, OAuth, code execution, and the former API-gateway concerns (JWT boundary, CORS, OTP rate limiting) in one Spring Boot app.

## Layout

| Module | Description |
|--------|-------------|
| **codejam-app** | Runnable Spring Boot app (`CodeJamApplication`) — port **8080** by default |
| **codejam-commons** | Shared DTOs, `JwtUtil`, `GlobalExceptionHandler`, Redis helpers |

## Architecture

```
Frontend (Next.js)
        │
        ▼
┌───────────────────┐
│  codejam-app      │ :8080  — /v1/api/auth/**, /v1/api/execution/**
└─────────┬─────────┘
          │
    ┌─────┴─────┬─────────┐
    ▼           ▼         ▼
 Postgres    Redis    Docker (code runs)
```

Public API paths are unchanged from the previous gateway + services setup: **`/v1/api/auth/**`** and **`/v1/api/execution/**`**.

## Tech Stack

- Java 21, Spring Boot 3.4.x
- PostgreSQL, Redis, Docker (execution sandbox)
- Maven

## Local development

```bash
docker compose up -d   # Postgres + Redis if you use the repo compose file
./build-all.sh
cd codejam-app && mvn spring-boot:run
```

Or from the repo root:

```bash
mvn -pl codejam-app spring-boot:run
```

Defaults match typical local Postgres (`codejam` / `codejam123` on `codejam_db`). Set `JWT_SECRET` to a strong base64 value in non-local environments.

## Spring Cloud Config

Optional: activate profile **`config-server`** and set `CONFIG_SERVER_URL` to import from a config server (see `application.yml`).

## Legacy microservices

The previous multi-service layout is preserved on the **`main`** branch / history; this **`master`** branch is the monolith rewrite.

## External config repo

If you still use [`config-repo`](../config-repo) with Spring Cloud Config, run the app with profile **`config-server`** and `CONFIG_SERVER_URL` set. The default path uses local `application.yml` only.
