#!/bin/bash
# Setup script for gitleaks secret detection

set -e

echo "🔐 CodeJam Security Setup - Gitleaks"
echo "===================================="
echo ""

# Check if gitleaks is already installed
if command -v gitleaks &> /dev/null; then
    echo "✅ Gitleaks is already installed!"
    gitleaks version
else
    echo "📦 Installing gitleaks..."

    # Detect OS and install
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        if command -v brew &> /dev/null; then
            brew install gitleaks
        else
            echo "❌ Homebrew not found. Please install Homebrew first:"
            echo "   /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
            exit 1
        fi
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        echo "Please install gitleaks manually:"
        echo "  wget https://github.com/gitleaks/gitleaks/releases/download/v8.18.0/gitleaks_8.18.0_linux_x64.tar.gz"
        echo "  tar -xzf gitleaks_8.18.0_linux_x64.tar.gz"
        echo "  sudo mv gitleaks /usr/local/bin/"
        exit 1
    else
        echo "❌ Unsupported OS. Please install gitleaks manually:"
        echo "   https://github.com/gitleaks/gitleaks#installing"
        exit 1
    fi
fi

echo ""
echo "🔧 Setting up git hooks..."

# Ensure pre-commit hook is executable
if [ -f ".git/hooks/pre-commit" ]; then
    chmod +x .git/hooks/pre-commit
    echo "✅ Pre-commit hook is active"
else
    echo "⚠️  Pre-commit hook not found at .git/hooks/pre-commit"
fi

echo ""
echo "🧪 Testing gitleaks configuration..."

# Test gitleaks with current repo
gitleaks detect --config=.gitleaks.toml --no-git --verbose || true

echo ""
echo "✅ Setup complete!"
echo ""
echo "Gitleaks will now scan for secrets before every commit."
echo ""
echo "Useful commands:"
echo "  gitleaks detect                    # Scan entire repo"
echo "  gitleaks protect --staged          # Scan staged files"
echo "  gitleaks detect --config=.gitleaks.toml --verbose  # Detailed scan"
echo ""
