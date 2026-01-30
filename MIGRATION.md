# Architecture Migration: Microservices → Modular Monolith

## Overview

CodeJam backend was migrated from a microservices architecture to a modular monolith to optimize for resource usage and operational simplicity.

## Branches

| Branch | Architecture | Status |
|--------|-------------|--------|
| `main` | Microservices (4 services) | Reference/Archive |
| `master` | Modular Monolith (1 service) | **Active/Default** |

## Why We Migrated

### 1. Resource Constraints

| Metric | Microservices | Monolith | Savings |
|--------|--------------|----------|---------|
| JVM Instances | 4 | 1 | 75% |
| Min RAM Required | ~4GB | ~2GB | 50% |
| Docker Images | 4 | 1 | 75% |
| Startup Time | ~3-4 min (sequential) | ~30-45s | 80% |

**Cost Impact**: Can run on $12/month droplet instead of $24/month.

### 2. Complexity vs. Scale Analysis

| Factor | Our Situation | Microservices Benefit |
|--------|--------------|----------------------|
| Team Size | Solo developer | ❌ No benefit |
| Traffic | Low-moderate | ❌ No benefit |
| Independent Scaling | Not needed | ❌ No benefit |
| Deployment Complexity | Overhead | ❌ Negative impact |
| Debugging | Harder distributed | ❌ Negative impact |

**Conclusion**: Microservices added complexity without providing benefits at our scale.

### 3. Operational Simplicity

**Before (Microservices):**
- 4 services to monitor and debug
- Distributed logging across services
- Inter-service network calls that can fail
- Config server as single point of failure
- Complex deployment orchestration

**After (Monolith):**
- Single application to monitor
- Unified logging
- Direct method calls (no network latency)
- No config server dependency
- Simple deployment: pull and restart

## What Changed

### Architecture

```
BEFORE (Microservices):
┌─────────────┐     ┌──────────────┐     ┌───────────────────┐
│ API Gateway │────▶│ Auth Service │────▶│ Execution Service │
│   :8080     │     │    :8081     │     │      :8082        │
└─────────────┘     └──────────────┘     └───────────────────┘
       │                   │                      │
       └───────────────────┴──────────────────────┘
                           │
                    ┌──────────────┐
                    │Config Server │
                    │    :8888     │
                    └──────────────┘

AFTER (Monolith):
┌─────────────────────────────────────────────────┐
│              CodeJam Application                │
│                    :8080                        │
│  ┌───────────┐ ┌───────────┐ ┌───────────────┐ │
│  │   Auth    │ │ Execution │ │    Gateway    │ │
│  │  Module   │ │   Module  │ │    (Config)   │ │
│  └───────────┘ └───────────┘ └───────────────┘ │
└─────────────────────────────────────────────────┘
```

### Code Structure

```
BEFORE:
codejam-backend/
├── api-gateway/          # Separate Spring Boot app
├── auth-service/         # Separate Spring Boot app
├── execution-service/    # Separate Spring Boot app
├── config-server/        # Separate Spring Boot app
└── codejam-commons/      # Shared library

AFTER:
codejam-backend/
├── src/main/java/com/codejam/
│   ├── CodejamApplication.java   # Single entry point
│   ├── auth/                     # Auth module
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   └── model/
│   ├── execution/                # Execution module
│   │   ├── controller/
│   │   ├── service/
│   │   └── dto/
│   ├── gateway/                  # Gateway module (filters)
│   │   ├── filter/
│   │   └── config/
│   └── config/                   # Shared configs
│       ├── SecurityConfig.java
│       └── WebMvcConfig.java
└── codejam-commons/              # Still used
```

### Docker Compose

```yaml
# BEFORE: 6 containers
services:
  postgres, redis, config-server, auth-service, execution-service, api-gateway

# AFTER: 3 containers
services:
  postgres, redis, codejam
```

### CI/CD Pipeline

```yaml
# BEFORE: Matrix build (4 parallel jobs)
strategy:
  matrix:
    service: [config-server, auth-service, execution-service, api-gateway]

# AFTER: Single build job
steps:
  - run: mvn clean package
  - run: docker build -t codejam .
```

## What Stayed the Same

| Component | Status |
|-----------|--------|
| All API endpoints | ✅ Unchanged (`/v1/api/auth/**`, `/v1/api/execute/**`) |
| JWT authentication | ✅ Same implementation |
| OAuth2 Google login | ✅ Same flow |
| Docker code execution | ✅ Same service |
| Database schema | ✅ Same (`auth.users`) |
| Redis OTP storage | ✅ Same |
| CORS configuration | ✅ Same origins |

## When to Use Each Architecture

### Use Microservices When:
- Multiple teams need independent deployments
- Services have different scaling requirements
- Different technology stacks per service
- High availability with isolated failure domains
- Large organization with service ownership

### Use Monolith When:
- Solo developer or small team
- Uniform scaling needs
- Same technology stack throughout
- Resource-constrained environment
- Rapid iteration needed
- Simple operational requirements

## Migration Checklist

- [x] Create modular package structure
- [x] Merge security configurations
- [x] Merge application.yml configs
- [x] Update Dockerfile for single JAR
- [x] Update docker-compose for single service
- [x] Update CI/CD pipeline
- [x] Test all endpoints
- [x] Create this migration document

## Rollback Plan

If issues arise with the monolith, the microservices architecture is preserved on the `main` branch:

```bash
git checkout main
# Rebuild and deploy microservices version
```

## Performance Comparison

| Metric | Microservices | Monolith |
|--------|--------------|----------|
| Cold Start | ~3-4 minutes | ~30-45 seconds |
| Memory Usage | ~1.5GB | ~400-500MB |
| API Latency | +5-10ms (inter-service) | Baseline |
| Deployment Time | ~5-8 minutes | ~2-3 minutes |

## Lessons Learned

1. **Start simple**: A monolith is not a bad word. Start with what you need.
2. **Microservices have overhead**: Network calls, distributed tracing, config management.
3. **Right-size for your scale**: Solo projects don't need enterprise architecture.
4. **Keep modularity**: Even in a monolith, maintain clean module boundaries.
5. **Document decisions**: Future you will thank present you.

---

*Migration completed: January 2025*
