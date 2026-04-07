# CodeJam Backend

**Modular monolith:** one JVM (**`codejam-app`**) delivers authentication, OAuth, code execution APIs, and the former **API gateway** behavior (JWT boundary, CORS, OTP rate limiting). **Spring Cloud Config Server** is a **separate** deployable in this repo, used in production compose.

## Modules

| Path | Role |
|------|------|
| **`codejam-app/`** | Runnable Spring Boot app (`com.codejam.CodeJamApplication`) — default port **8080** |
| **`codejam-commons/`** | Shared library: DTOs, `JwtUtil`, exceptions, `RedisService`, etc. |
| **`config-server/`** | Spring Cloud Config Server (port **8888**) — reads from private **Git** `config-repo` in prod |

## Architecture

```
Browser / Frontend
        │
        ▼
┌───────────────────┐
│  codejam-app      │  :8080
│  /v1/api/auth/**  │
│  /v1/api/execution/**
└─────────┬─────────┘
          │
    ┌─────┴─────┬─────────────┐
    ▼           ▼             ▼
 Postgres    Redis      Docker (sandbox runs)
```

**Optional (prod):** `config-server` serves Git-backed properties to `codejam-app` when profile **`prod`** (+ **`cloud-config`**) is active.

## Public HTTP API (unchanged paths)

- **`/v1/api/auth/**`** — register, login, OTP, OAuth, refresh, logout, execution-adjacent auth
- **`/v1/api/execution/**`** — run code, history, health, supported languages

## Tech stack

- Java **21**, Spring Boot **3.4.x**
- PostgreSQL, Redis
- Docker API for isolated execution
- Maven (multi-module parent + standalone `config-server` POM)

## Local development

```bash
cp .env.example .env    # JWT_SECRET as base64, DB, Redis, optional vars
./build-all.sh          # installs commons + builds app
mvn -pl codejam-app spring-boot:run
```

Defaults assume Postgres `codejam_db` / user `codejam` / password `codejam123` (override with `SPRING_DATASOURCE_*`).

### JWT and cookies

- Use a **base64-encoded** `JWT_SECRET` in non-local environments.
- Optional **`JWT_REFRESH_SECRET`** (defaults to access secret if unset).
- Local **HTTP:** set **`REFRESH_COOKIE_SECURE=false`** and **`REFRESH_COOKIE_SAME_SITE=Lax`** so the refresh cookie works (see `.env.example`).

### Auth behavior

- Short-lived **access JWT** (`accessToken` in JSON, claim `token_use=access`).
- **Refresh JWT** in an **HttpOnly** cookie (path **`/v1/api/auth`**).
- Notable routes: `POST .../refresh`, `POST .../logout`, `POST .../logoutAll` (Bearer access for logout-all).

## Spring Cloud Config

| Item | Detail |
|------|--------|
| **Profiles** | `prod` includes **`cloud-config`**, which imports `optional:configserver:${CONFIG_SERVER_URL}` |
| **Local only** | Omit `cloud-config` or point `CONFIG_SERVER_URL` at a local server |
| **Git application name** | **`codejam-app`** → config files under **`codejam-app/`** in your private config repository (see sibling [`config-repo`](../config-repo)) |

## Production deployment

- **[`docker-compose.prod.yml`](docker-compose.prod.yml)** — **postgres**, **redis**, **config-server**, **codejam-app**; only **8080** exposed for the app.
- Fill **`.env`** on the server (`CONFIG_REPO_URL`, Git credentials, `JWT_SECRET`, `FRONTEND_URL`, `GATEWAY_BASE_URL`, email/OAuth keys, etc.).

## Build & CI

- **Monolith:** `mvn clean install` from repo root (builds commons + app).
- **Config server only:** `mvn -f config-server/pom.xml clean package`
- Workflows under **`.github/workflows/`** — e.g. `deploy-all.yml`, `deploy-config-server.yml`.

## Scripts & SQL

- **[`scripts/README.md`](scripts/README.md)** — optional DB bootstrap SQL for Postgres.

## Related

- Frontend: [../codejam-frontend/README.md](../codejam-frontend/README.md)
- Config properties: [../config-repo/README.md](../config-repo/README.md)
- Monorepo overview: [../README.md](../README.md)
