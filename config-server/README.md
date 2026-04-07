# Config Server (CodeJam)

Spring Cloud **Config Server** — serves centralized configuration from **Git** (production) or from the **filesystem** (local native profile). Clients (notably **`codejam-app`**) pull properties at startup and can refresh via **`/actuator/refresh`** when wired in CI or ops.

This module lives under **`codejam-backend/`** and builds its own JAR (`mvn -f config-server/pom.xml package`). Docker image is built from this directory; see root **`docker-compose.prod.yml`**.

## What this is not

- It does **not** store production secrets in the public backend repo — those live in env vars or a **private** Git config repo.
- It is **not** part of the monolith JAR; it runs as a **separate** process (port **8888** by default).

## Config sources

### Git profile (production)

Activate with **`SPRING_PROFILES_ACTIVE=git`** (as in Docker Compose). Set:

- **`CONFIG_REPO_URL`** — HTTPS Git URL of your private **`config-repo`**
- **`CONFIG_GIT_BRANCH`** — branch (e.g. `main`)
- **`GIT_USERNAME` / `GIT_PASSWORD`** — PAT or credential for private clone

See [`application-git.yml`](src/main/resources/application-git.yml). Config is **cached** after clone; refresh via **`/actuator/refresh`** on the config server (and then on clients) when Git changes — often driven by GitHub Actions.

### Native profile (local)

Activate with **`SPRING_PROFILES_ACTIVE=native`** and point **`spring.cloud.config.server.native.search-locations`** at a local clone of `config-repo` (see `application-native.yml`). Useful when you cannot reach Git from your laptop.

## Client: codejam-app

The monolith uses **`spring.application.name=codejam-app`**. Config Server exposes:

```http
GET http://localhost:8888/codejam-app/default
GET http://localhost:8888/codejam-app/prod
```

Files in Git should follow:

- `codejam-app/codejam-app.properties`
- `codejam-app/codejam-app-prod.properties` (for profile `prod`)

The app imports the server when profile **`cloud-config`** is active (included in **`prod`** in `codejam-app`’s `application.yml`) and **`CONFIG_SERVER_URL`** is set.

## Build & run (quick)

```bash
cd config-server
mvn clean package -DskipTests
java -jar target/config-server-*.jar
```

With Git:

```bash
export SPRING_PROFILES_ACTIVE=git
export CONFIG_REPO_URL=https://github.com/your-org/config-repo.git
export GIT_PASSWORD=...
java -jar target/config-server-*.jar
```

## Related

- Property files: [../../config-repo/README.md](../../config-repo/README.md)  
- Backend overview: [../README.md](../README.md)
