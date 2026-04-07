# CI/CD and Versioning Guide

## Overview

**Current default (monolith branch):** one runnable app **`codejam-app`** plus shared **`codejam-commons`** (Maven reactor) and a separate **`config-server`** image. Production **`docker-compose.prod.yml`** runs **postgres**, **redis**, **config-server**, and **codejam-app** (single API port **8080**).

**Legacy:** Older branches may still have **auth-service**, **execution-service**, and **api-gateway** as separate images; workflows named `deploy-auth-service.yml`, `deploy-api-gateway.yml`, `deploy-execution-service.yml` may remain in `.github/workflows/` for those branches — they are **not** the primary path for the modular monolith.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    codejam-backend (monolith layout)                 │
├─────────────────────────────────────────────────────────────────────┤
│  codejam-commons/     → Shared library (local mvn install / GH PKG) │
│  codejam-app/         → Fat JAR + Docker image (main API)           │
│  config-server/       → Separate Config Server image                 │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Architecture

### Workflow Files (verify against repo)

| Workflow | Typical trigger | Purpose |
|----------|-----------------|--------|
| `deploy-all.yml` | Push to `codejam-app/**`, `codejam-commons/**`, `config-server/**`, compose file | Build & push **codejam-app** + **config-server** images; deploy compose to droplet |
| `deploy-config-server.yml` | Changes in `config-server/**` | Build & push config-server only |
| `publish-commons.yml` | Tag `commons-v*` | Publish commons to GitHub Packages (if still used) |
| `deploy-auth-service.yml` etc. | _(legacy)_ | **Obsolete** for monolith — remove or ignore on default branch |

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

This script runs **`mvn clean install`** from the **parent POM**, building **codejam-commons** and **codejam-app** (and installing the reactor artifacts).

### Running Services

```bash
# Build everything
./build-all.sh

# Run the monolith locally
mvn -pl codejam-app spring-boot:run

# Production-like stack (see docker-compose.prod.yml)
# cp .env.example .env && fill values
docker compose -f docker-compose.prod.yml up -d
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

### Deploying the monolith

**When:** You push changes under `codejam-app/**`, `codejam-commons/**`, `config-server/**`, or `docker-compose.prod.yml`

**What happens (typical):**
1. **`deploy-all.yml`** runs on `push` (see workflow `paths` and `if` conditions).
2. Matrix build: **`codejam-app`** image (parent `mvn install` + Docker build from `codejam-app/`) and **`config-server`** image (`mvn package` in `config-server/` + Docker build).
3. Images push to **GHCR**; deploy job copies compose and runs `docker compose pull && up` on the droplet.

**Config server only:** `deploy-config-server.yml` on `config-server/**` changes.

**Legacy per-service workflows** (`deploy-auth-service.yml`, etc.): ignore on monolith branch unless you still maintain old service directories.

### Deploying all containers

**Option 1 — Push**  
Push to `main`/`master` with changes under the paths watched by `deploy-all.yml`.

**Option 2 — Manual dispatch**  
Actions → **Deploy All Services** → Run workflow; use **force rebuild** when you need fresh images without a matching path change (see workflow inputs).

---

## Bumping Commons Version

### Scenario: You fixed a bug in commons (monolith uses reactor parent)

With the **parent POM** linking **`codejam-app`** → **`codejam-commons`** as `${project.version}`, a local **`./build-all.sh`** picks up commons changes immediately. For **GitHub Packages** versioning (if you still publish commons separately):

```bash
# 1. Make changes to codejam-commons
# 2. Tag commons-v* and push if using publish-commons.yml
# 3. If any consumer used external version property, bump it — monolith reactor usually does not need this

# 4. Commit and push app + commons together (reactor build)
git add codejam-commons codejam-app pom.xml
git commit -m "Fix commons / app"
git push
# → deploy-all.yml (or your chosen workflow) rebuilds the monolith image
```

### Scenario: Rolling out commons (legacy multi-service layout)

_If you still maintain separate service POMs on another branch:_ bump `codejam-commons.version` in each service and deploy each workflow. **On the monolith branch**, commons and app ship in **one** build from the parent POM.

---

## Benefits (legacy: per-service + versioned commons)

The following applied when **auth**, **execution**, and **gateway** were **separate** deployables. The **monolith** simplifies this: one image includes commons; trade-offs are **simpler ops** vs **less independent scaling** per concern.

### 1. Independent Deployments (legacy)
- Per-service pushes could deploy only that service.

### 2. Controlled commons rollout (legacy)
- Explicit version bumps per service before multi-service adoption.

### 3. Easy rollback
- Revert Git / image tag / compose version for **codejam-app** (and **config-server** if needed).

### 4. Audit trail
- Git history + image tags still apply.

### 5. CI/CD
- **deploy-all** matrix can still build **codejam-app** and **config-server** in parallel.

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
