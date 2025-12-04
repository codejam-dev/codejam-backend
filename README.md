# CodeJam Backend

Microservices backend for CodeJam - Real-time collaborative coding platform with secure authentication and authorization.

## Architecture

The backend follows a microservices architecture with the following components:

- **api-gateway**: API Gateway (Spring Cloud Gateway) - Single entry point, handles JWT validation and routing
- **auth-service**: Authentication and authorization service - User management, OAuth, JWT generation
- **config-server**: Configuration server - Centralized configuration management (optional)
- **codejam-commons**: Shared library - Common utilities, DTOs, exceptions, Redis service

## Services

### API Gateway
- **Port**: 8080
- **Responsibilities**:
  - JWT token validation and extraction
  - Route requests to appropriate services
  - CORS handling
  - User info extraction from JWT and passing as headers to downstream services
  - PKCE code_challenge validation for OAuth flows

### Auth Service
- **Port**: 8081
- **Responsibilities**:
  - User registration and login
  - Email verification via OTP
  - Google OAuth with PKCE
  - JWT token generation (temp tokens for unverified users, full tokens for verified)
  - User management

### Config Server (Optional)
- **Port**: 8888
- **Responsibilities**:
  - Centralized configuration management
  - Supports Git-based or native file-based configuration
  - Dynamic configuration refresh

## Authentication Flow

### Registration Flow
```
1. POST /v1/api/auth/register
   → Creates user with enabled=false
   → Returns temp token (15 min expiry, isEnabled=false)

2. POST /v1/api/auth/generateOtp
   Header: Authorization: Bearer <TEMP_TOKEN>
   → Extracts email from token
   → Generates OTP, stores in Redis (5 min expiry)
   → Returns transactionId

3. POST /v1/api/auth/validateOtp
   Header: Authorization: Bearer <TEMP_TOKEN>
   Body: { otp, transactionId }
   → Validates OTP from Redis
   → Sets user.enabled = true
   → Returns full token (7 days expiry, isEnabled=true)
```

### Login Flow
```
1. POST /v1/api/auth/login
   Body: { email, password }
   → If user.enabled=false:
     Returns temp token (15 min expiry)
     Frontend redirects to OTP verification
   → If user.enabled=true:
     Returns full token (7 days expiry)
     Frontend redirects to dashboard
```

### OAuth Flow (Google with PKCE)
```
1. Frontend generates PKCE code_verifier and code_challenge
2. GET /v1/api/auth/oauth2/authorization/google?code_challenge=...&code_challenge_method=S256
   → Backend stores code_challenge in session
   → Redirects to Google OAuth
3. Google redirects back with code
4. Backend validates, creates/updates user, generates OAuth code
5. Frontend exchanges code with code_verifier
   POST /v1/api/auth/oauth/exchange
   Body: { code, codeVerifier }
   → Backend validates PKCE and returns JWT token
```

## Setup

### Prerequisites
- Java 21+
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 15
- Redis 7

### Initial Setup: Security (Gitleaks)

**First time only** - Install gitleaks to prevent committing secrets:

```bash
./setup-gitleaks.sh
```

This installs gitleaks and sets up a pre-commit hook that automatically scans for secrets before every commit.

### Environment Variables

Create a `.env` file (already gitignored) and configure all required variables:

```bash
# JWT Configuration
JWT_SECRET=your-jwt-secret-key-base64-encoded
JWT_EXPIRATION=86400000

# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/codejam_db
SPRING_DATASOURCE_USERNAME=your-db-username
SPRING_DATASOURCE_PASSWORD=your-db-password

# Google OAuth
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret

# Email (for OTP)
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# Frontend URLs
FRONTEND_URL=http://localhost:3000
OAUTH_SUCCESS_REDIRECT=http://localhost:3000/auth/callback
GATEWAY_BASE_URL=http://localhost:8080
```

See `.env` file for complete list of environment variables.

### Build

```bash
# Build commons first (required by other services)
cd codejam-commons
mvn clean install

# Build auth-service
cd ../auth-service
mvn clean install

# Build api-gateway
cd ../api-gateway
mvn clean install
```

### Run with Docker Compose

```bash
# Start all services (PostgreSQL, Redis, Auth Service)
docker-compose up -d

# Or start only databases
docker-compose up -d codejam-postgres codejam-redis
```

### Run Services Locally

```bash
# Start databases
docker-compose up -d codejam-postgres codejam-redis

# Run auth-service
cd auth-service
mvn spring-boot:run

# Run api-gateway (in another terminal)
cd api-gateway
mvn spring-boot:run
```

## API Endpoints

### Public Endpoints
- `POST /v1/api/auth/register` - Register new user
- `POST /v1/api/auth/login` - Login with email/password
- `GET /v1/api/auth/oauth2/authorization/google` - Initiate Google OAuth
- `GET /v1/api/auth/oauth2/callback/google` - OAuth callback
- `POST /v1/api/auth/oauth/exchange` - Exchange OAuth code for token

### Protected Endpoints (Require JWT)
- `POST /v1/api/auth/generateOtp` - Generate OTP (temp token only)
- `POST /v1/api/auth/validateOtp` - Validate OTP (temp token only)
- `GET /v1/api/auth/health` - Health check

## Security Features

### JWT Token Types
- **Temp Token**: 15 minutes expiry, `isEnabled=false`, only allows `/generateOtp` and `/validateOtp`
- **Full Token**: 7 days expiry, `isEnabled=true`, grants `ROLE_USER` authority

### PKCE for OAuth
- Frontend generates `code_verifier` and `code_challenge` (S256)
- Backend validates PKCE during code exchange
- Prevents authorization code interception attacks

### Email Normalization
- All emails are normalized to lowercase at request level (DTO setters)
- Prevents duplicate accounts with different email casing

## Project Structure

```
codejam-backend/
├── api-gateway/              # API Gateway service
│   ├── src/main/java/com/codejam/gateway/
│   │   ├── filter/          # JWT authentication filter
│   │   ├── service/         # JWT service
│   │   └── controller/      # Gateway controllers
│   └── pom.xml
├── auth-service/            # Authentication service
│   ├── src/main/java/com/codejam/auth/
│   │   ├── config/          # Security, OAuth, Redis configs
│   │   ├── controller/       # REST controllers
│   │   ├── service/         # Business logic
│   │   ├── model/           # JPA entities
│   │   ├── repository/      # Data access
│   │   ├── security/        # JWT filter (redundant, gateway handles it)
│   │   ├── handler/         # OAuth handlers
│   │   └── dto/             # Request/Response DTOs
│   └── pom.xml
├── codejam-commons/         # Shared library
│   ├── src/main/java/com/codejam/commons/
│   │   ├── dto/             # BaseResponse
│   │   ├── exception/       # CustomException
│   │   └── util/            # RedisService, ObjectUtil, etc.
│   └── pom.xml
├── config-server/          # Config Server (optional)
├── docker-compose.yml       # Docker services
├── .env                     # Environment variables (not committed)
└── README.md
```

## Key Features

- **Email/Password Authentication**: Registration with email verification
- **OTP Verification**: 6-digit OTP sent via email (or static for development)
- **Google OAuth**: Secure OAuth flow with PKCE
- **JWT Tokens**: Temp tokens for unverified users, full tokens for verified
- **Email Normalization**: Case-insensitive email handling
- **Centralized JWT Validation**: All validation at API Gateway level
- **Redis Integration**: OTP storage and OAuth code management

## Development

### Running Tests
```bash
mvn test
```

### Code Quality
- Java 21
- Spring Boot 3.4.10
- Maven for dependency management

## License

MIT
