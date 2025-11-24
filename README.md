# CodeJam Backend

Microservices backend for CodeJam - Real-time collaborative coding platform.

## Architecture

- **auth-service**: Authentication and authorization service
- **codejam-commons**: Shared library for common utilities, DTOs, and exceptions
- **api-gateway**: API Gateway (coming soon)

## Services

### Auth Service
- Port: 8081
- Features:
  - Email/Password authentication
  - Google OAuth with PKCE
  - JWT token generation
  - User management

### CodeJam Commons
- Shared library containing:
  - BaseResponse DTO
  - CustomException
  - RedisService
  - ObjectUtil
  - RedisKeyUtil

## Setup

### Prerequisites
- Java 21+
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 15
- Redis 7

### Build

```bash
# Build commons first (required by other services)
cd codejam-commons
mvn clean install

# Build auth-service
cd ../auth-service
mvn clean install
```

### Run with Docker

```bash
# Start databases
docker-compose up -d codejam-postgres codejam-redis

# Run services individually
cd auth-service
mvn spring-boot:run
```

### Run all services

```bash
docker-compose up
```

## Configuration

Set environment variables:
- `JWT_SECRET`: JWT signing secret
- `GOOGLE_CLIENT_ID`: Google OAuth client ID
- `GOOGLE_CLIENT_SECRET`: Google OAuth client secret

## Project Structure

```
codejam-backend/
├── auth-service/          # Authentication service
│   ├── src/main/java/com/codejam/auth/
│   │   ├── config/        # Security, Redis configs
│   │   ├── controller/    # REST controllers
│   │   ├── service/       # Business logic
│   │   ├── model/         # JPA entities
│   │   ├── repository/    # Data access
│   │   ├── security/      # JWT filter
│   │   ├── handler/       # OAuth handlers
│   │   └── util/          # Service-specific utilities
│   └── pom.xml
├── codejam-commons/       # Shared library
│   ├── src/main/java/com/codejam/commons/
│   │   ├── dto/           # BaseResponse
│   │   ├── exception/     # CustomException
│   │   └── util/          # RedisService, ObjectUtil, etc.
│   └── pom.xml
├── api-gateway/           # API Gateway (coming soon)
└── docker-compose.yml
```

## License

MIT

