# Config Server Local Repository

This directory contains **local fallback configurations** for Config Server.

## Purpose

These configs are used when:
1. Git repository is unavailable
2. As fallback during development
3. For testing without Git access

## Structure

```
config-repo/
├── auth-service/
│   ├── auth-service.properties          # Default profile (local dev)
│   └── auth-service-render.properties    # Render profile (production)
├── api-gateway/
│   ├── api-gateway.properties
│   └── api-gateway-render.properties
```

## How It Works

1. **Primary Source**: Git repository (configured in `application.yml`)
2. **Fallback Source**: This local `config-repo` directory
3. Config Server tries Git first, falls back to local if Git fails

## Profile-Based Loading

- **Default profile**: `auth-service.properties`
- **Render profile**: `auth-service-render.properties` (when `SPRING_PROFILES_ACTIVE=render`)

## Note

⚠️ **These are fallback configs only!** 
- For production, use Git repository
- These files are for local development and testing
- Git repository is the source of truth

## Updating Configs

1. **For Git repo**: Update files in your GitHub repository
2. **For local fallback**: Update files here (only for testing)

