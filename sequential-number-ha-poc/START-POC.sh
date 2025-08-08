#!/bin/bash

# Sequential Number Generation HA POC - Linux Startup Script
# Portable cross-platform demonstration

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# Print banner
echo -e "${CYAN}"
cat << "EOF"
   ████████╗███████╗██████╗ ██╗   ██╗███████╗███╗   ██╗ ██████╗███████╗
   ██╔════╝██╔════╝██╔══██╗██║   ██║██╔════╝████╗  ██║██╔════╝██╔════╝
   ███████╗█████╗  ██████╔╝██║   ██║█████╗  ██╔██╗ ██║██║     █████╗  
   ╚════██║██╔══╝  ██╔═══╝ ██║   ██║██╔══╝  ██║╚██╗██║██║     ██╔══╝  
   ███████║███████╗██║     ╚██████╔╝███████╗██║ ╚████║╚██████╗███████╗
   ╚══════╝╚══════╝╚═╝      ╚═════╝ ╚══════╝╚═╝  ╚═══╝ ╚═════╝╚══════╝

          Global Sequential Invoice Number Generation POC
                    High Availability Demonstration
                          Portable Edition v1.0
EOF
echo -e "${NC}"
echo "=================================================================="

# Set environment variables
export JAVA_OPTS="-Xms512m -Xmx2g -Dfile.encoding=UTF-8"

# Functions
check_command() {
    if ! command -v "$1" &> /dev/null; then
        echo -e "${RED}❌ $1 is not installed or not in PATH${NC}"
        return 1
    else
        echo -e "${GREEN}✅ $1 found${NC}"
        return 0
    fi
}

check_port() {
    if lsof -Pi :$1 -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo -e "${RED}❌ Port $1 is already in use${NC}"
        return 1
    else
        echo -e "${GREEN}✅ Port $1 is available${NC}"
        return 0
    fi
}

# Step 1: Check prerequisites
echo -e "${BLUE}[1/5] Checking prerequisites...${NC}"

if ! check_command "java"; then
    echo
    echo "Please install Java 17 or higher from:"
    echo "https://adoptium.net/temurin/releases/"
    exit 1
fi

if ! check_command "mvn"; then
    echo
    echo "Please install Apache Maven from:"
    echo "https://maven.apache.org/download.cgi"
    exit 1
fi

# Optional: Check for curl
if ! command -v curl &> /dev/null; then
    echo -e "${YELLOW}⚠️  curl not found - you can still use the web dashboard${NC}"
    CURL_AVAILABLE=false
else
    echo -e "${GREEN}✅ curl found${NC}"
    CURL_AVAILABLE=true
fi

# Step 2: Prepare directories
echo
echo -e "${BLUE}[2/5] Preparing data directories...${NC}"
mkdir -p data/logs
mkdir -p data/etcd-cluster
echo -e "${GREEN}✅ Data directories ready${NC}"

# Step 3: Check ports
echo
echo -e "${BLUE}[3/5] Checking port availability...${NC}"
if ! check_port 8080; then
    echo "Please close the application using port 8080"
    exit 1
fi

# Step 4: Build application
echo
echo -e "${BLUE}[4/5] Building application...${NC}"
echo "This may take a few minutes on first run (downloading dependencies)..."

if ! mvn clean compile -q; then
    echo -e "${RED}❌ Build failed${NC}"
    echo "Please check the Maven output above for errors"
    exit 1
fi
echo -e "${GREEN}✅ Application built successfully${NC}"

# Step 5: Start application
echo
echo -e "${BLUE}[5/5] Starting Sequential Number Generation POC...${NC}"
echo

echo "Starting Spring Boot application..."
echo "Profile: POC (single-node etcd)"
echo "Dashboard: http://localhost:8080/dashboard"
echo "API Base: http://localhost:8080/api/v1"
echo

# Start application in background
mvn spring-boot:run -Dspring.profiles.active=poc &
APP_PID=$!

# Wait for application to start
echo "Waiting for application to start..."
sleep 15

# Test if application is running
echo "Testing application startup..."
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Application is running!${NC}"
        APP_STARTED=true
        break
    fi
    sleep 2
    echo "Waiting for startup... ($i/30)"
done

if [ "$APP_STARTED" != "true" ]; then
    echo -e "${YELLOW}⚠️  Application may still be starting...${NC}"
fi

echo
echo "=================================================================="
echo -e "${GREEN}🎉 SEQUENTIAL NUMBER GENERATION POC IS READY! 🎉${NC}"
echo "=================================================================="
echo
echo -e "${CYAN}🌐 ACCESS POINTS:${NC}"
echo "   Main Dashboard:     http://localhost:8080/dashboard"
echo "   API Documentation:  http://localhost:8080/actuator"
echo "   Health Check:       http://localhost:8080/actuator/health"
echo "   System Statistics:  http://localhost:8080/api/v1/sequence/stats"
echo

if [ "$CURL_AVAILABLE" = true ]; then
    echo -e "${CYAN}🚀 QUICK START EXAMPLES:${NC}"
    echo
    echo "   Generate Sequence:"
    echo "   curl \"http://localhost:8080/api/v1/sequence/next?siteId=site-1&partitionId=partition-a&invoiceType=on-cycle\""
    echo
    echo "   View Statistics:"
    echo "   curl \"http://localhost:8080/api/v1/sequence/stats\""
    echo
    echo "   Run Basic Demo:"
    echo "   curl -X POST \"http://localhost:8080/api/v1/demo/basic\""
    echo
fi

echo -e "${PURPLE}🎭 DEMONSTRATION OPTIONS:${NC}"
echo "   [1] Run Basic Demo"
echo "   [2] Run Concurrent Test" 
echo "   [3] Run Gap Management Demo"
echo "   [4] Run Load Test"
echo "   [5] Just keep running"
echo

read -p "Select option (1-5): " choice

case $choice in
    1)
        echo "Running basic demo..."
        if [ "$CURL_AVAILABLE" = true ]; then
            curl -X POST "http://localhost:8080/api/v1/demo/basic"
        fi
        ;;
    2)
        echo "Running concurrent test..."
        if [ "$CURL_AVAILABLE" = true ]; then
            curl -X POST "http://localhost:8080/api/v1/demo/concurrent"
        fi
        ;;
    3)
        echo "Running gap management demo..."
        if [ "$CURL_AVAILABLE" = true ]; then
            curl -X POST "http://localhost:8080/api/v1/demo/gaps"
        fi
        ;;
    4)
        echo "Running load test..."
        if [ "$CURL_AVAILABLE" = true ]; then
            curl -X POST "http://localhost:8080/api/v1/demo/load-test"
        fi
        ;;
    5)
        echo "Keeping application running..."
        ;;
    *)
        echo "Invalid choice, keeping application running..."
        ;;
esac

if command -v xdg-open &> /dev/null; then
    echo
    echo "Opening dashboard in browser..."
    xdg-open http://localhost:8080/dashboard &> /dev/null &
elif command -v open &> /dev/null; then
    echo
    echo "Opening dashboard in browser..."
    open http://localhost:8080/dashboard &> /dev/null &
else
    echo
    echo "Please open http://localhost:8080/dashboard in your web browser"
fi

echo
echo "=================================================================="
echo -e "${GREEN} 📊 The POC is now running and accessible via web browser${NC}"
echo -e "${GREEN} 📈 Monitor real-time sequence generation on the dashboard${NC}"
echo -e "${GREEN} 🔧 Use the web interface to generate sequences and run demos${NC}"
echo -e "${YELLOW} 🛑 Press Ctrl+C to stop the application${NC}"
echo "=================================================================="
echo

# Function to cleanup on exit
cleanup() {
    echo
    echo -e "${YELLOW}Shutting down application...${NC}"
    if [ ! -z "$APP_PID" ]; then
        kill $APP_PID 2>/dev/null || true
        wait $APP_PID 2>/dev/null || true
    fi
    echo -e "${GREEN}Application stopped. Thanks for trying the Sequential Number Generation POC! 👋${NC}"
    exit 0
}

# Trap signals for cleanup
trap cleanup SIGINT SIGTERM

echo "Application is running with PID: $APP_PID"
echo "Press Ctrl+C to stop..."
echo

# Wait for the application to finish
wait $APP_PID