#!/bin/bash
set -e

# CodeJam Backend Deployment Script for DigitalOcean Droplet
# Uses GitHub Container Registry (GHCR) images built by GitHub Actions
# Usage: ./deploy.sh [setup|login|deploy|pull|logs|status|stop|restart]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Check if .env file exists and load it
check_env() {
    if [ ! -f .env ]; then
        log_error ".env file not found!"
        log_info "Copy .env.example to .env and configure your secrets:"
        log_info "  cp .env.example .env"
        log_info "  nano .env"
        exit 1
    fi
    source .env

    # Validate required variables
    if [ -z "$JWT_SECRET" ]; then
        log_error "JWT_SECRET is not set in .env"
        exit 1
    fi
    if [ -z "$POSTGRES_PASSWORD" ]; then
        log_error "POSTGRES_PASSWORD is not set in .env"
        exit 1
    fi
    if [ -z "$GHCR_OWNER" ]; then
        log_error "GHCR_OWNER is not set in .env (your GitHub username)"
        exit 1
    fi
}

# Login to GitHub Container Registry
ghcr_login() {
    log_info "Logging into GitHub Container Registry..."
    if [ -z "$GHCR_TOKEN" ]; then
        log_warn "GHCR_TOKEN not set. Enter your GitHub Personal Access Token (with read:packages scope):"
        read -s GHCR_TOKEN
    fi
    echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_OWNER" --password-stdin
    log_info "Logged in to GHCR successfully!"
}

# Pull latest images from GHCR
pull_images() {
    check_env
    log_info "Pulling latest images from GHCR..."

    docker pull ghcr.io/${GHCR_OWNER}/codejam-auth-service:${IMAGE_TAG:-latest}
    docker pull ghcr.io/${GHCR_OWNER}/codejam-execution-service:${IMAGE_TAG:-latest}
    docker pull ghcr.io/${GHCR_OWNER}/codejam-api-gateway:${IMAGE_TAG:-latest}

    log_info "All images pulled successfully!"
}

# Pull code execution Docker images (for execution-service)
pull_executor_images() {
    log_info "Pulling code execution runtime images..."
    docker pull eclipse-temurin:21-jdk
    docker pull python:3.11-slim
    docker pull node:20-slim
    log_info "Executor images pulled successfully!"
}

# Deploy with docker-compose (pull and start)
deploy() {
    check_env
    log_info "Deploying CodeJam backend services..."

    # Pull latest images and start services
    docker compose -f docker-compose-prod.yml pull
    docker compose -f docker-compose-prod.yml up -d

    log_info "Waiting for services to be healthy..."
    sleep 10

    # Check service status
    docker compose -f docker-compose-prod.yml ps

    log_info "Deployment complete!"
    log_info "API Gateway is available at http://localhost:8080"
}

# Update: pull new images and restart
update() {
    check_env
    log_info "Updating CodeJam backend services..."

    # Pull latest images
    docker compose -f docker-compose-prod.yml pull

    # Recreate containers with new images
    docker compose -f docker-compose-prod.yml up -d --force-recreate

    log_info "Update complete!"
    status
}

# View logs
logs() {
    local service=${1:-}
    if [ -n "$service" ]; then
        docker compose -f docker-compose-prod.yml logs -f "$service"
    else
        docker compose -f docker-compose-prod.yml logs -f
    fi
}

# Check status
status() {
    log_info "Service Status:"
    docker compose -f docker-compose-prod.yml ps
    echo ""
    log_info "Health Checks:"
    for container in api-gateway auth-service execution-service codejam-postgres codejam-redis; do
        health=$(docker inspect --format='{{.State.Health.Status}}' $container 2>/dev/null || echo "not found")
        state=$(docker inspect --format='{{.State.Status}}' $container 2>/dev/null || echo "not found")
        echo "  $container: $state (health: $health)"
    done
}

# Stop services
stop() {
    log_info "Stopping services..."
    docker compose -f docker-compose-prod.yml down
    log_info "Services stopped"
}

# Restart services
restart() {
    log_info "Restarting services..."
    docker compose -f docker-compose-prod.yml restart
    log_info "Services restarted"
}

# Initial setup for a fresh droplet
setup() {
    log_info "Setting up DigitalOcean droplet for CodeJam..."

    # Update system
    sudo apt-get update && sudo apt-get upgrade -y

    # Install Docker if not present
    if ! command -v docker &> /dev/null; then
        log_info "Installing Docker..."
        curl -fsSL https://get.docker.com -o get-docker.sh
        sudo sh get-docker.sh
        sudo usermod -aG docker $USER
        rm get-docker.sh
        log_warn "Docker installed. Please log out and back in, then re-run this script."
        exit 0
    fi

    # Install Docker Compose plugin if not present
    if ! docker compose version &> /dev/null; then
        log_info "Installing Docker Compose plugin..."
        sudo apt-get install docker-compose-plugin -y
    fi

    # Pull executor images
    pull_executor_images

    log_info ""
    log_info "Setup complete!"
    log_info ""
    log_info "Next steps:"
    log_info "  1. Copy .env.example to .env:     cp .env.example .env"
    log_info "  2. Edit .env with your secrets:   nano .env"
    log_info "  3. Login to GHCR:                 ./deploy.sh login"
    log_info "  4. Deploy services:               ./deploy.sh deploy"
}

# Show usage
usage() {
    echo "CodeJam Backend Deployment Script (GHCR Edition)"
    echo ""
    echo "Usage: $0 <command>"
    echo ""
    echo "Commands:"
    echo "  setup     - Initial droplet setup (install Docker, pull executor images)"
    echo "  login     - Login to GitHub Container Registry"
    echo "  deploy    - Pull images and start all services"
    echo "  update    - Pull latest images and recreate containers"
    echo "  pull      - Pull latest images only (no restart)"
    echo "  logs      - View logs (optionally: logs <service-name>)"
    echo "  status    - Check status of all services"
    echo "  stop      - Stop all services"
    echo "  restart   - Restart all services"
    echo ""
    echo "First-time deployment:"
    echo "  $0 setup              # Install Docker"
    echo "  cp .env.example .env  # Configure secrets"
    echo "  nano .env             # Edit configuration"
    echo "  $0 login              # Login to GHCR"
    echo "  $0 deploy             # Start services"
    echo ""
    echo "Update deployment:"
    echo "  $0 update             # Pull new images and restart"
}

# Main
case "${1:-}" in
    setup)
        setup
        ;;
    login)
        check_env
        ghcr_login
        ;;
    pull)
        pull_images
        ;;
    deploy)
        deploy
        ;;
    update)
        update
        ;;
    logs)
        logs "$2"
        ;;
    status)
        status
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    *)
        usage
        ;;
esac
