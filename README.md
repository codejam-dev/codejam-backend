# CodeJam Backend

Microservices backend for CodeJam - a real-time collaborative coding platform with secure code execution, authentication, and centralized configuration.

## Architecture

```
                    ┌─────────────────┐
                    │   Frontend      │
                    │  (Next.js)      │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  API Gateway    │ :8080
                    │  (JWT + CORS)   │
                    └──┬──────────┬───┘
                       │          │
              ┌────────▼──┐  ┌───▼──────────┐
              │ Auth      │  │ Execution    │
              │ Service   │  │ Service      │
              │ :8081     │  │ :8082        │
              └──┬────┬───┘  └──┬───────────┘
                 │    │         │
           ┌─────▼┐ ┌▼─────┐ ┌─▼──────┐
           │Postgres│ │Redis │ │Docker  │
           │ :5432  │ │:6379 │ │Engine  │
           └───────┘ └──────┘ └────────┘

           ┌──────────────────────────┐
           │   Config Server :8888    │
           │  (Spring Cloud Config)   │
           └──────────────────────────┘
```

## Services

| Service | Port | Description |
|---------|------|-------------|
| **api-gateway** | 8080 | Entry point - JWT validation, routing, CORS |
| **auth-service** | 8081 | Authentication, OAuth, email verification |
| **execution-service** | 8082 | Code execution in isolated Docker containers |
| **config-server** | 8888 | Centralized config management (optional locally) |
| **codejam-commons** | - | Shared library (DTOs, exceptions, JWT utils, Redis) |

## Tech Stack

- Java 21, Spring Boot 3.4.10, Spring Cloud 2024.0.0
- PostgreSQL 15, Redis 7
- Docker (code execution sandboxing)
- Maven, GitHub Actions, GitHub Packages (GHCR)

## Local Development

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker & Docker Compose

### Quick Start

```bash
# 1. Start Postgres + Redis
docker compose up -d

# 2. Build commons + all services
./build-all.sh

# 3. Run services from IntelliJ (or terminal)
cd auth-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
cd execution-service && mvn spring-boot:run
```

All services have safe local defaults - no env vars needed for basic local dev:
- DB: `codejam/codejam123` on `localhost:5432/codejam_db`
- Redis: `localhost:6379`
- JWT: local dev key auto-configured
- Config Server: optional (`optional:configserver:`)

### Build Script

```bash
./build-all.sh                           # Build all
./build-all.sh auth-service api-gateway  # Build specific services
```

The script auto-reads the commons version from `auth-service/pom.xml`, sets it on `codejam-commons`, and installs it to the local Maven repo before building services.

### Optional: Set env vars for full features

For Google OAuth and email to work locally, set these in your IntelliJ run config:

- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` - OAuth login
- `RESEND_API_KEY` - Email (OTP verification)
- `JWT_SECRET` - Custom JWT key (has a default for dev)

## Authentication Flow

### Registration
```
POST /v1/api/auth/register → temp token (15 min)
POST /v1/api/auth/generateOtp → OTP sent to email
POST /v1/api/auth/validateOtp → full token (7 days)
```

### Login
```
POST /v1/api/auth/login → full token (if verified) or temp token (if not)
```

### OAuth (Google with PKCE)
```
1. Frontend generates PKCE code_verifier + code_challenge
2. GET /v1/api/auth/oauth2/authorization/google?code_challenge=...
3. Google OAuth flow → callback with code
4. POST /v1/api/auth/oauth/exchange { code, codeVerifier } → JWT token
```

## Code Execution

The execution service runs user code in isolated Docker containers:

- **Languages**: JavaScript, Python, Java (C++, Rust planned)
- **Sandboxing**: No network, read-only volumes, no privilege escalation
- **Limits**: 256MB memory, 0.5 CPU, 30s timeout, 50 max processes
- **Output**: stdout/stderr captured (max 1MB), exit code, execution time

## API Endpoints

### Public
- `POST /v1/api/auth/register` - Register
- `POST /v1/api/auth/login` - Login
- `GET /v1/api/auth/oauth2/authorization/google` - Google OAuth
- `POST /v1/api/auth/oauth/exchange` - Exchange OAuth code

### Protected (JWT required)
- `POST /v1/api/auth/generateOtp` - Generate OTP
- `POST /v1/api/auth/validateOtp` - Validate OTP
- `POST /v1/api/execution/run` - Execute code
- `GET /v1/api/execution/supported-languages` - List languages

## Project Structure

```
codejam-backend/
├── api-gateway/                # Spring Cloud Gateway
│   └── src/.../gateway/
│       ├── filter/             # JWT auth filter
│       ├── service/            # JWT service
│       └── controller/
├── auth-service/               # Auth microservice
│   └── src/.../auth/
│       ├── config/             # Security, OAuth, Redis
│       ├── controller/         # REST endpoints
│       ├── service/            # Business logic
│       ├── model/              # JPA entities
│       ├── repository/         # Data access
│       ├── handler/            # OAuth handlers
│       └── dto/                # Request/Response DTOs
├── execution-service/          # Code execution
│   └── src/.../execution/
│       ├── config/             # Docker, executor config
│       ├── controller/         # Execution endpoint
│       ├── service/            # DockerExecutor, CodeExecutor
│       └── dto/                # CodeSubmission, ExecutionResult
├── config-server/              # Spring Cloud Config
├── codejam-commons/            # Shared library
│   └── src/.../commons/
│       ├── dto/                # BaseResponse, ErrorResponse
│       ├── exception/          # CustomException, GlobalHandler
│       ├── util/               # JwtUtil, RedisService
│       └── config/             # Security, Redis config
├── .github/workflows/          # CI/CD pipelines
├── docker-compose.yml          # Local dev (Postgres + Redis) - gitignored
├── docker-compose.prod.yml     # Production (all services)
├── build-all.sh                # Build script
├── setup-gitleaks.sh           # Secret detection setup
└── .env                        # Secrets (gitignored)
```

## CI/CD

Per-service GitHub Actions workflows - only the changed service gets built and deployed:

| Workflow | Trigger | Action |
|----------|---------|--------|
| `deploy-auth-service.yml` | `auth-service/**` changes on main | Build + deploy auth-service |
| `deploy-execution-service.yml` | `execution-service/**` changes | Build + deploy execution-service |
| `deploy-api-gateway.yml` | `api-gateway/**` changes | Build + deploy api-gateway |
| `deploy-config-server.yml` | `config-server/**` changes | Build + deploy config-server |
| `deploy-all.yml` | `docker-compose.prod.yml` changes or manual | Build + deploy all services |
| `publish-commons.yml` | `commons-v*` tag or manual | Publish commons to GitHub Packages |

### Pipeline Flow
1. Maven build with Java 21
2. Docker image push to GHCR (`ghcr.io/<owner>/codejam-<service>`)
3. SSH deploy to DigitalOcean Droplet
4. `docker compose pull && up -d` (only affected service)

### Updating Commons Version
```bash
# 1. Update version in auth-service/pom.xml (codejam-commons.version property)
# 2. Update same version in execution-service/pom.xml and api-gateway/pom.xml
# 3. Tag and push: git tag commons-v1.1.0 && git push origin commons-v1.1.0
```

## Security

- **Gitleaks**: Pre-commit hook for secret detection (`./setup-gitleaks.sh`)
- **JWT**: Validated at gateway level, passed as headers to downstream
- **PKCE**: OAuth code exchange with S256 challenge
- **Docker Isolation**: Code runs in sandboxed containers with no network
- **Email Normalization**: Case-insensitive to prevent duplicates

## License

MIT
