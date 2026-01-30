# CodeJam Backend

Modular monolith backend for CodeJam - Real-time collaborative coding platform with secure authentication, code execution, and authorization.

> **Note**: This project was migrated from microservices to a modular monolith. See [MIGRATION.md](MIGRATION.md) for details. The original microservices architecture is preserved on the `main` branch.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   CodeJam Application                       │
│                        :8080                                │
│  ┌─────────────┐  ┌───────────────┐  ┌─────────────────┐   │
│  │    Auth     │  │   Execution   │  │     Gateway     │   │
│  │   Module    │  │    Module     │  │    (Config)     │   │
│  └─────────────┘  └───────────────┘  └─────────────────┘   │
└─────────────────────────────────────────────────────────────┘
         │                  │                    │
    ┌────┴────┐       ┌────┴────┐          ┌────┴────┐
    │PostgreSQL│       │ Docker  │          │  Redis  │
    └──────────┘       │ Engine  │          └─────────┘
                       └─────────┘
```

### Modules

| Module | Responsibility |
|--------|---------------|
| `auth` | User registration, login, OAuth2 (Google), JWT, OTP verification |
| `execution` | Docker-based code execution for multiple languages |
| `gateway` | JWT filter, CORS, rate limiting configuration |
| `config` | Shared security and web configurations |

## Features

- **Email/Password Authentication** with OTP email verification
- **Google OAuth** with PKCE flow
- **JWT Tokens** (temp tokens for unverified users, full tokens for verified)
- **Code Execution** via Docker containers (Python, Java, JavaScript, C++, Go, Rust)
- **Redis** for OTP storage and caching
- **CORS** configured for frontend

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 15
- Redis 7

### 1. Setup Environment

```bash
cp .env.example .env
# Edit .env with your values
```

### 2. Build

```bash
# Build commons library first
cd codejam-commons && mvn clean install -DskipTests && cd ..

# Build application
mvn clean package -DskipTests
```

### 3. Run Locally

```bash
# Start databases
docker-compose up -d postgres redis

# Run application
mvn spring-boot:run
# Or: java -jar target/codejam-0.0.1-SNAPSHOT.jar
```

### 4. Run with Docker Compose (Production)

```bash
docker-compose -f docker-compose-prod.yml up -d
```

## API Endpoints

### Auth Endpoints (`/v1/api/auth`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/register` | Register new user | No |
| POST | `/login` | Login with email/password | No |
| POST | `/generateOtp` | Generate OTP for email verification | Temp JWT |
| POST | `/validateOtp` | Validate OTP and get full token | Temp JWT |
| GET | `/oauth2/authorization/google` | Initiate Google OAuth | No |
| POST | `/oauth/exchange` | Exchange OAuth code for token | No |

### Execution Endpoints (`/v1/api/execute`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/run` | Execute code | JWT |

### Health Check

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Health status |

## Authentication Flows

### Registration Flow
```
1. POST /v1/api/auth/register → Returns temp token (15 min)
2. POST /v1/api/auth/generateOtp → Sends OTP email
3. POST /v1/api/auth/validateOtp → Returns full token (7 days)
```

### Login Flow
```
POST /v1/api/auth/login
  → If unverified: Returns temp token → OTP verification required
  → If verified: Returns full token
```

### OAuth Flow (Google with PKCE)
```
1. Frontend generates code_verifier + code_challenge
2. GET /oauth2/authorization/google?code_challenge=...
3. Google OAuth consent → callback
4. POST /oauth/exchange with code + code_verifier → JWT token
```

## Environment Variables

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/codejam_db
SPRING_DATASOURCE_USERNAME=codejam
SPRING_DATASOURCE_PASSWORD=your-password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT (generate with: openssl rand -base64 64)
JWT_SECRET=your-base64-encoded-secret

# Google OAuth
GOOGLE_CLIENT_ID=your-client-id
GOOGLE_CLIENT_SECRET=your-client-secret

# Email (for OTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# URLs
FRONTEND_URL=http://localhost:3000
GATEWAY_BASE_URL=http://localhost:8080
```

See `.env.example` for complete list.

## Project Structure

```
codejam-backend/
├── src/main/java/com/codejam/
│   ├── CodejamApplication.java    # Entry point
│   ├── auth/                      # Auth module
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── model/
│   │   └── dto/
│   ├── execution/                 # Code execution module
│   │   ├── controller/
│   │   ├── service/
│   │   └── dto/
│   ├── gateway/                   # Gateway config module
│   │   ├── filter/
│   │   └── config/
│   └── config/                    # Shared configs
│       ├── SecurityConfig.java
│       └── WebMvcConfig.java
├── codejam-commons/               # Shared library
├── Dockerfile
├── docker-compose.yml             # Local development
├── docker-compose-prod.yml        # Production
├── MIGRATION.md                   # Architecture migration docs
└── .env.example
```

## Deployment

### GitHub Actions CI/CD

Push to `master` triggers automatic deployment:
1. Builds JAR with Maven
2. Builds Docker image
3. Pushes to GitHub Container Registry (GHCR)
4. Deploys to DigitalOcean droplet

### Required GitHub Secrets

| Secret | Description |
|--------|-------------|
| `DROPLET_HOST` | Droplet IP address |
| `DROPLET_USER` | SSH username |
| `DROPLET_SSH_KEY` | SSH private key |
| `GHCR_TOKEN` | GitHub PAT for GHCR |

## Security

- **Gitleaks**: Pre-commit hook prevents committing secrets
- **JWT**: Stateless authentication with temp/full token types
- **PKCE**: Secure OAuth flow
- **CORS**: Configured for specific frontend origins

### Setup Gitleaks (First Time)

```bash
./setup-gitleaks.sh
```

## Branch Strategy

| Branch | Description |
|--------|-------------|
| `master` | **Default** - Modular monolith (active) |
| `main` | Microservices architecture (archived/reference) |

## License

MIT
