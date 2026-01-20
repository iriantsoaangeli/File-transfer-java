#!/bin/bash

# Network File Transfer Application - Startup Script

echo "======================================"
echo "  Network File Transfer Application  "
echo "         miyabi69 Protocol            "
echo "======================================"
echo ""

# Check if nmap is installed
if ! command -v nmap &> /dev/null; then
    echo "ERROR: nmap is not installed!"
    echo "Please install nmap:"
    echo "  Ubuntu/Debian: sudo apt-get install nmap"
    echo "  Fedora/RHEL:   sudo dnf install nmap"
    echo "  macOS:         brew install nmap"
    exit 1
fi

echo "✓ nmap found: $(nmap --version | head -n1)"

# Check if Java 17+ is available
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed!"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "ERROR: Java 17 or higher is required!"
    echo "Current version: $(java -version 2>&1 | head -n1)"
    exit 1
fi

echo "✓ Java found: $(java -version 2>&1 | head -n1)"

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed!"
    exit 1
fi

echo "✓ Maven found: $(mvn --version | head -n1)"
echo ""

# Create necessary directories
mkdir -p logs
mkdir -p mailbox

echo "Starting application..."
echo ""

# Run the application
mvn clean javafx:run
 
