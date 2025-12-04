#!/bin/bash
# Export all environment variables from .env file
# Usage: source export-env.sh

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "Error: .env file not found at $ENV_FILE"
    return 1 2>/dev/null || exit 1
fi

echo "Loading environment variables from .env file..."

# Read .env file, ignore comments and empty lines, export variables
while IFS= read -r line || [ -n "$line" ]; do
    # Skip empty lines and comments
    if [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]]; then
        continue
    fi

    # Remove inline comments
    line=$(echo "$line" | sed 's/[[:space:]]*#.*$//')

    # Export the variable
    if [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
        var_name="${BASH_REMATCH[1]}"
        var_value="${BASH_REMATCH[2]}"

        # Remove surrounding quotes if present
        var_value=$(echo "$var_value" | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//")

        export "$var_name=$var_value"
        echo "✓ Exported: $var_name"
    fi
done < "$ENV_FILE"

echo ""
echo "✅ Environment variables loaded successfully!"
echo ""
echo "You can now run your services:"
echo "  cd config-server && mvn spring-boot:run"
echo "  cd auth-service && mvn spring-boot:run"
echo "  cd api-gateway && mvn spring-boot:run"
