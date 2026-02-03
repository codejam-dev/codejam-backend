# CI/CD and Versioning Guide

## Overview

This project uses a **per-service deployment architecture** with **versioned shared library (codejam-commons)**. Each microservice can be deployed independently, and commons updates are explicitly adopted by services.

```
┌─────────────────────────────────────────────────────────────────────┐
│                         GitHub Repository                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  codejam-commons/          → Shared library (versioned: 1.0.0, etc) │
│  auth-service/             → Independent deployment                  │
│  execution-service/        → Independent deployment                  │
│  api-gateway/              → Independent deployment                  │
│  config-server/            → Independent deployment                  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Architecture

### Workflow Files

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `publish-commons.yml` | Tag `commons-v*` | Publish commons to GitHub Packages |
| `deploy-auth-service.yml` | Changes in `auth-service/**` | Build & deploy auth-service |
| `deploy-execution-service.yml` | Changes in `execution-service/**` | Build & deploy execution-service |
| `deploy-api-gateway.yml` | Changes in `api-gateway/**` | Build & deploy api-gateway |
| `deploy-config-server.yml` | Changes in `config-server/**` | Build & deploy config-server |
| `deploy-all.yml` | Changes in `docker-compose-prod.yml` or manual | Deploy all services |

### How Services Get Commons

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   LOCAL DEV      │     │   CI/CD          │     │   PRODUCTION     │
├──────────────────┤     ├──────────────────┤     ├──────────────────┤
│                  │     │                  │     │                  │
│  ./build-all.sh  │     │  settings.xml    │     │  Docker images   │
│       ↓          │     │       ↓          │     │  with commons    │
│  mvn install     │     │  GitHub Packages │     │  baked in        │
│       ↓          │     │       ↓          │     │                  │
│  ~/.m2/repository│     │  Download 1.0.0  │     │                  │
│                  │     │                  │     │                  │
└──────────────────┘     └──────────────────┘     └──────────────────┘
```

---

## Version Management

### Commons Versioning

Commons uses semantic versioning stored in each service's `pom.xml`:

```xml
<properties>
    <codejam-commons.version>1.0.0</codejam-commons.version>
</properties>

<dependency>
    <groupId>com.codejam</groupId>
    <artifactId>codejam-commons</artifactId>
    <version>${codejam-commons.version}</version>
</dependency>
```

### Version Flow

```
1. Developer makes changes to codejam-commons
                    ↓
2. Create and push tag: git tag commons-v1.0.1 && git push origin commons-v1.0.1
                    ↓
3. publish-commons.yml publishes v1.0.1 to GitHub Packages
                    ↓
4. Update service pom.xml: <codejam-commons.version>1.0.1</codejam-commons.version>
                    ↓
5. Push service changes → triggers service deployment with new commons
```

---

## Local Development

### First Time Setup

```bash
cd codejam-backend
./build-all.sh
```

This script:
1. Reads `codejam-commons.version` from `auth-service/pom.xml`
2. Sets that version on `codejam-commons`
3. Installs commons to `~/.m2/repository`
4. Builds all services

### Running Services

```bash
# Build everything
./build-all.sh

# Run specific services
cd auth-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
# etc.

# Or use Docker Compose
docker-compose up
```

### When You Change Commons Locally

Just run `./build-all.sh` again - it reinstalls commons with the current version.

---

## CI/CD Workflows

### Publishing Commons (One-time per version)

**When:** You want to release a new commons version

```bash
# 1. Make changes to codejam-commons
# 2. Commit and push

# 3. Tag and push
git tag commons-v1.0.1
git push origin commons-v1.0.1
```

**What happens:**
- `publish-commons.yml` triggers
- Sets version to `1.0.1` in commons pom.xml
- Runs `mvn deploy` to GitHub Packages
- Commons `1.0.1` now available for CI

### Deploying a Service

**When:** You push changes to a service directory

```bash
# Make changes to auth-service
git add auth-service/
git commit -m "Add new feature"
git push
```

**What happens:**
1. `deploy-auth-service.yml` triggers (path filter: `auth-service/**`)
2. Configures Maven `settings.xml` with GitHub Packages credentials
3. Runs `mvn package` (pulls commons from GitHub Packages)
4. Builds Docker image
5. Pushes to `ghcr.io`
6. SSHs to server and deploys only auth-service

### Deploying All Services

**When:** You need to redeploy everything or docker-compose changed

**Option 1: docker-compose changes**
```bash
# Edit docker-compose-prod.yml
git add docker-compose-prod.yml
git commit -m "Update docker-compose"
git push
# → deploy-all.yml triggers automatically
```

**Option 2: Manual trigger**
1. Go to GitHub Actions
2. Select "Deploy All Services"
3. Click "Run workflow"
4. Check "Force rebuild all service images" if needed

---

## Bumping Commons Version

### Scenario: You fixed a bug in commons and want auth-service to use it

```bash
# 1. Make changes to codejam-commons
cd codejam-commons
# ... make changes ...

# 2. Commit commons changes
git add codejam-commons/
git commit -m "Fix bug in JWT validation"
git push

# 3. Publish new version
git tag commons-v1.0.1
git push origin commons-v1.0.1
# → Wait for publish-commons.yml to complete

# 4. Update auth-service to use new version
# Edit auth-service/pom.xml:
#   <codejam-commons.version>1.0.1</codejam-commons.version>

# 5. Commit and push
git add auth-service/pom.xml
git commit -m "Bump commons to 1.0.1 for JWT fix"
git push
# → deploy-auth-service.yml triggers and deploys
```

### Scenario: Rolling out commons update to all services

```bash
# After publishing commons-v1.0.1...

# Update all services at once
for file in auth-service/pom.xml execution-service/pom.xml api-gateway/pom.xml; do
  sed -i '' 's/<codejam-commons.version>1.0.0/<codejam-commons.version>1.0.1/' $file
done

git add */pom.xml
git commit -m "Bump all services to commons 1.0.1"
git push
# → All service workflows trigger in parallel
```

---

## Benefits of This Architecture

### 1. Independent Deployments
- Change auth-service → only auth-service deploys
- No unnecessary rebuilds of unrelated services

### 2. Controlled Commons Rollout
- Update commons → doesn't auto-break all services
- Each service explicitly opts-in to new version
- Can test new commons on one service before rolling to others

### 3. Easy Rollback
- Service has issue with new commons?
- Just revert the version in pom.xml:
  ```xml
  <codejam-commons.version>1.0.0</codejam-commons.version>
  ```
- Push → deploys with old commons

### 4. Clear Audit Trail
- Git history shows exactly which commons version each service uses
- Easy to track when a service adopted a commons change

### 5. Faster CI/CD
- Services deploy in parallel
- No waiting for unchanged services to build

---

## Troubleshooting

### Local: "Could not resolve com.codejam:codejam-commons"

```bash
# Clear cached failures and rebuild
rm -rf ~/.m2/repository/com/codejam/codejam-commons
./build-all.sh
```

### CI: Service can't find commons version

1. Check if `commons-vX.X.X` tag was pushed
2. Check if `publish-commons.yml` completed successfully
3. Verify version in service pom.xml matches published version

### Want to force rebuild all services

1. Go to GitHub Actions → "Deploy All Services"
2. Run workflow with "Force rebuild" checked

Or manually trigger each service workflow.

---

## Quick Reference

| Task | Command |
|------|---------|
| Build locally | `./build-all.sh` |
| Publish commons | `git tag commons-v1.0.1 && git push origin commons-v1.0.1` |
| Deploy one service | Push changes to service directory |
| Deploy all | Manually run "Deploy All Services" workflow |
| Check current commons version | `grep codejam-commons.version auth-service/pom.xml` |

---

## File Structure

```
codejam-backend/
├── .github/workflows/
│   ├── publish-commons.yml      # Publish commons to GitHub Packages
│   ├── deploy-auth-service.yml  # Deploy auth-service
│   ├── deploy-execution-service.yml
│   ├── deploy-api-gateway.yml
│   ├── deploy-config-server.yml
│   └── deploy-all.yml           # Deploy all services
├── codejam-commons/
│   └── pom.xml                  # Has distributionManagement for GitHub Packages
├── auth-service/
│   └── pom.xml                  # <codejam-commons.version>1.0.0</...>
├── execution-service/
│   └── pom.xml                  # <codejam-commons.version>1.0.0</...>
├── api-gateway/
│   └── pom.xml                  # <codejam-commons.version>1.0.0</...>
├── config-server/
│   └── pom.xml                  # No commons dependency
├── build-all.sh                 # Local build script
└── docker-compose-prod.yml      # Production compose file
```
