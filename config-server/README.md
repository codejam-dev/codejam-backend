# Config Server - How It Works

## Overview

Config Server centralizes configuration for all microservices. Instead of each service having its own config files, they all connect to Config Server to get their configurations.

## Two Profiles: DEV and PROD

### 1. DEV Profile (`SPRING_PROFILES_ACTIVE=dev`)

**Config Server Behavior:**
- Reads configs from **local files** in `config-server/src/main/resources/config-repo/`
- No Git needed
- Fast startup, no network required
- Perfect for local development

**Config Files Location:**
```
config-server/src/main/resources/config-repo/
├── auth-service/
│   └── auth-service.properties
└── api-gateway/
    └── api-gateway.properties
```

### 2. PROD Profile (`SPRING_PROFILES_ACTIVE=prod`)

**Config Server Behavior:**
- Reads configs from **Git repository** (https://github.com/codejam-dev/config-repo.git)
- Requires Git credentials (GIT_PASSWORD, GIT_USERNAME)
- Configs are version-controlled
- Changes in Git trigger automatic refresh

**Config Files Location:**
```
Git Repository: https://github.com/codejam-dev/config-repo.git
├── auth-service/
│   └── auth-service.properties
└── api-gateway/
    └── api-gateway.properties
```

## How Services Connect to Config Server

### Step 1: Config Server Starts

```bash
# DEV mode (local files)
SPRING_PROFILES_ACTIVE=dev java -jar config-server.jar

# PROD mode (Git repo)
SPRING_PROFILES_ACTIVE=prod \
  CONFIG_REPO_URL=https://github.com/codejam-dev/config-repo.git \
  GIT_PASSWORD=your-token \
  java -jar config-server.jar
```

Config Server runs on **port 8888**.

### Step 2: Services Connect to Config Server

In `auth-service/src/main/resources/application.yml`:
```yaml
spring:
  config:
    import: "optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}"
```

In `api-gateway/src/main/resources/application.yml`:
```yaml
spring:
  config:
    import: "optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}"
```

### Step 3: Config Server Serves Configs

When `auth-service` starts:
1. It connects to Config Server at `http://localhost:8888`
2. Config Server looks for: `auth-service.properties` (or `auth-service-{profile}.properties`)
3. Config Server returns the config file
4. `auth-service` uses those values

**Example Request:**
```
GET http://localhost:8888/auth-service/default
```

**Response:** Properties file with all configs for auth-service

## Config File Naming Convention

Config Server serves configs based on:
- **Service name**: `auth-service`, `api-gateway`
- **Profile**: `default`, `dev`, `prod`

**File naming:**
- `auth-service.properties` → Default profile
- `auth-service-prod.properties` → When service runs with `prod` profile
- `auth-service-dev.properties` → When service runs with `dev` profile

## Complete Flow Example

### DEV Environment (Local Development)

```
1. Start Config Server:
   SPRING_PROFILES_ACTIVE=dev → Reads from config-repo/ directory

2. Start Auth Service:
   - Connects to Config Server (http://localhost:8888)
   - Config Server reads: config-repo/auth-service/auth-service.properties
   - Returns config to Auth Service
   - Auth Service uses those values

3. Start API Gateway:
   - Connects to Config Server (http://localhost:8888)
   - Config Server reads: config-repo/api-gateway/api-gateway.properties
   - Returns config to API Gateway
   - API Gateway uses those values
```

### PROD Environment (Production)

```
1. Start Config Server:
   SPRING_PROFILES_ACTIVE=prod \
   CONFIG_REPO_URL=https://github.com/codejam-dev/config-repo.git \
   GIT_PASSWORD=token
   → Clones Git repo and reads from there

2. Start Auth Service:
   - Connects to Config Server
   - Config Server reads from Git: auth-service/auth-service.properties
   - Returns config to Auth Service
   - Auth Service uses those values

3. When config changes in Git:
   - Push to GitHub
   - Webhook triggers: POST /webhook/github
   - Config Server pulls latest from Git
   - Services refresh via: POST /actuator/refresh
```

## Benefits

1. **Centralized Config**: All configs in one place
2. **Version Control**: Configs in Git (prod mode)
3. **Environment-Specific**: Different configs for dev/prod
4. **Dynamic Refresh**: Update configs without restarting services
5. **Security**: Secrets not in code, only in Config Server/Git

## Configuration Hierarchy

When a service starts, it loads configs in this order:

1. **application.yml** (in service itself) - Base configs
2. **Config Server** - Overrides/adds configs from central repo
3. **Environment Variables** - Highest priority, overrides everything

Example:
```yaml
# In auth-service/application.yml
jwt:
  secret: ${JWT_SECRET}  # Uses env var if set

# In config-repo/auth-service/auth-service.properties
jwt.secret=default-secret  # Used if env var not set
```

## Summary

- **DEV**: Config Server reads from local `config-repo/` directory
- **PROD**: Config Server reads from Git repository
- **Services**: Connect to Config Server to get their configs
- **Refresh**: Services can refresh configs without restart via `/actuator/refresh`

