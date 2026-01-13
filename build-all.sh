#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Force Java 21 for Maven
export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
export PATH="$JAVA_HOME/bin:$PATH"

# Store the root directory
ROOT_DIR=$(pwd)

# Function to build a specific service
build_service() {
    local service=$1
    local emoji=$2
    
    echo "$emoji Building $service..."
    cd "$ROOT_DIR/$service" || exit 1
    
    if [ "$service" == "codejam-commons" ]; then
        mvn clean install -DskipTests
    else
        mvn clean package -DskipTests
    fi
    
    echo "✅ $service built successfully"
    echo ""
}

# Function to show usage
show_usage() {
    echo "Usage: ./build-all.sh [service1] [service2] ..."
    echo ""
    echo "Available services:"
    echo "  codejam-commons    - Shared library (always built first)"
    echo "  auth-service       - Authentication service"
    echo "  api-gateway        - API Gateway"
    echo "  execution-service  - Code execution service"
    echo "  config-server      - Configuration server (optional)"
    echo ""
    echo "Examples:"
    echo "  ./build-all.sh                           # Build all services"
    echo "  ./build-all.sh auth-service              # Build only auth-service"
    echo "  ./build-all.sh auth-service api-gateway  # Build multiple services"
    echo ""
}

echo "🏗️  Building CodeJam Microservices..."
echo "📍 Working directory: $ROOT_DIR"
echo "☕ Java version: $(java -version 2>&1 | head -n 1)"
echo ""

# Check if help is requested
if [[ "$1" == "-h" || "$1" == "--help" ]]; then
    show_usage
    exit 0
fi

# Define all services
ALL_SERVICES=("auth-service" "api-gateway" "execution-service" "config-server")

# Determine which services to build
if [ $# -eq 0 ]; then
    # No arguments - build all services
    SERVICES_TO_BUILD=("${ALL_SERVICES[@]}")
    echo "📦 Building all services..."
else
    # Arguments provided - build specific services
    SERVICES_TO_BUILD=("$@")
    echo "📦 Building specific services: $*"
fi

echo ""

# Always build codejam-commons first
build_service "codejam-commons" "📦"

# Build requested services
for service in "${SERVICES_TO_BUILD[@]}"; do
    case $service in
        auth-service)
            build_service "auth-service" "🔐"
            ;;
        api-gateway)
            build_service "api-gateway" "🌐"
            ;;
        execution-service)
            build_service "execution-service" "⚡"
            ;;
        config-server)
            build_service "config-server" "⚙️"
            ;;
        codejam-commons)
            # Already built, skip
            echo "⏭️  codejam-commons already built, skipping..."
            echo ""
            ;;
        *)
            echo "❌ Unknown service: $service"
            echo ""
            show_usage
            exit 1
            ;;
    esac
done

# Return to root directory
cd "$ROOT_DIR"

echo ""
if [ $# -eq 0 ]; then
    echo "✅ All services built successfully!"
else
    echo "✅ Selected services built successfully!"
fi
echo "🚀 Run 'docker-compose up' to start all services"



