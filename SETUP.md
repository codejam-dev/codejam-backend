# CodeJam Backend - Environment Setup Guide

## Configuration Architecture

CodeJam follows a clean 3-tier configuration approach:

1. **Config Server** (`config-repo/`) - Application configuration (features, timeouts, URLs)
2. **Environment Variables** (`.env`) - Secrets and environment-specific values
3. **Application Files** (`application.yml`) - Minimal bootstrap config only

### Why This Approach?

✅ **Security**: Secrets never committed to git
✅ **Flexibility**: Change app config without code changes
✅ **Clean Separation**: Config vs Secrets vs Bootstrap
✅ **12-Factor App**: Follows industry best practices

---

## Initial Setup

### Security: Install Gitleaks

**First time only** - Install gitleaks to prevent committing secrets:

```bash
./setup-gitleaks.sh
```

This installs gitleaks and sets up a pre-commit hook that automatically scans for secrets before every commit.

---

## Quick Start

### 1. Local Development (IntelliJ)

Your IntelliJ is already configured to read `.env` file automatically.

**Run services:**
1. Start Config Server first
2. Start Auth Service
3. Start API Gateway

All secrets will be loaded from `.env` automatically.

### 2. Local Development (Terminal)

```bash
# Load environment variables
source export-env.sh

# Start Config Server
cd config-server && mvn spring-boot:run

# In another terminal
source export-env.sh
cd auth-service && mvn spring-boot:run

# In another terminal
source export-env.sh
cd api-gateway && mvn spring-boot:run
```

### 3. Docker Compose

```bash
# Start all services (automatically loads .env)
./docker-up.sh

# Or manually
docker-compose up --build

# Stop services
docker-compose down
```

---

## Configuration Files Overview

### What Goes Where?

| Type | Location | Committed? | Example |
|------|----------|------------|---------|
| **Secrets** | `.env` | ❌ NO | JWT_SECRET, passwords |
| **App Config** | `config-repo/` | ✅ YES | OTP settings, URLs |
| **Bootstrap** | `application.yml` | ✅ YES | Server port, app name |
| **Dev Overrides** | `application-dev.yml` | ❌ NO | Local credentials |

### Files You SHOULD Commit:

```
✅ config-repo/auth-service/auth-service.properties
✅ config-repo/api-gateway/api-gateway.properties
✅ auth-service/src/main/resources/application.yml
✅ api-gateway/src/main/resources/application.yml
✅ config-server/src/main/resources/application*.yml
```

### Files You MUST NOT Commit:

```
❌ .env
❌ **/*-dev.yml
❌ **/*-dev.properties
```

---

## Environment Variables Reference

### Required for All Environments

```bash
# JWT Configuration
JWT_SECRET=<base64-encoded-secret>
JWT_EXPIRATION=86400000

# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/codejam_db
SPRING_DATASOURCE_USERNAME=codejam
SPRING_DATASOURCE_PASSWORD=<secret>

# OAuth2
GOOGLE_CLIENT_ID=<client-id>
GOOGLE_CLIENT_SECRET=<secret>

# Email
MAIL_USERNAME=<email>
MAIL_PASSWORD=<app-password>
```

### Config Server Specific

```bash
# For native mode (local)
SPRING_PROFILES_ACTIVE=native
CONFIG_REPO_PATH=file:///path/to/config-repo/{application}

# For git mode (production)
SPRING_PROFILES_ACTIVE=git
CONFIG_REPO_URL=https://github.com/org/config-repo.git
GIT_USERNAME=<username>
GIT_PASSWORD=<pat>
```

---

## Dynamic Configuration Refresh

After changing config in `config-repo/`:

```bash
# Refresh specific service
curl -X POST http://localhost:8081/actuator/refresh

# Or for API Gateway
curl -X POST http://localhost:8080/actuator/refresh
```

Only properties annotated with `@RefreshScope` or `@ConfigurationProperties` will refresh.

---

## Production Deployment

### 1. Generate Secrets

```bash
# JWT Secret (base64, 64 bytes)
openssl rand -base64 64

# Webhook Secret
openssl rand -hex 32
```

### 2. Set Environment Variables

Use your cloud provider's secret management:
- **AWS**: Systems Manager Parameter Store / Secrets Manager
- **GCP**: Secret Manager
- **Azure**: Key Vault
- **Kubernetes**: Sealed Secrets / External Secrets

### 3. Update Config Server Mode

```bash
# Set profile to git
export SPRING_PROFILES_ACTIVE=git

# Configure Git repository
export CONFIG_REPO_URL=https://github.com/your-org/config-repo.git
export GIT_USERNAME=your-username
export GIT_PASSWORD=your-pat-token
```

### 4. Security Checklist

- [ ] All JWT_SECRET values match across services
- [ ] Strong database passwords
- [ ] OAuth credentials for production app
- [ ] CORS origins restricted to production domains
- [ ] Rate limiting enabled
- [ ] OTP_ENABLE_DYNAMIC=true
- [ ] hibernate.ddl-auto=validate (not update!)

---

## Troubleshooting

### Config not refreshing?

1. Check if property is in Config Server:
   ```bash
   curl http://localhost:8888/auth-service/default
   ```

2. Check if service loaded it:
   ```bash
   curl http://localhost:8081/actuator/env/app.otp.enable-dynamic
   ```

3. Ensure no local override in `application-dev.yml`

### Config Server not connecting?

1. Check Config Server is running on port 8888
2. Verify `spring.config.import` in application.yml
3. Check logs for connection errors

### Secrets not loading?

1. Verify `.env` file exists
2. Check environment variables: `echo $JWT_SECRET`
3. For IntelliJ: Verify EnvFile plugin is enabled
4. For Docker: Ensure `.env` is in same directory as docker-compose.yml

---

## Scripts Reference

### `export-env.sh`
Exports all variables from `.env` to current shell session.

**Usage:**
```bash
source export-env.sh
```

### `docker-up.sh`
Starts all services with Docker Compose, validates required env vars.

**Usage:**
```bash
./docker-up.sh           # Start in foreground
./docker-up.sh -d        # Start in background
```

---

## Best Practices

1. ✅ **Never commit** `.env` or `*-dev.yml` files
2. ✅ **Use Config Server** for all application configuration
3. ✅ **Use Environment Variables** for all secrets
4. ✅ **Keep** `application.yml` minimal (bootstrap only)
5. ✅ **Document** all required environment variables
6. ✅ **Validate** environment variables before startup
7. ✅ **Use different secrets** for dev/staging/prod
8. ✅ **Rotate secrets** regularly in production

---

## Support

For issues or questions, check:
1. This SETUP.md file
2. `.env.example` for required variables
3. Individual service README files
4. Project documentation
