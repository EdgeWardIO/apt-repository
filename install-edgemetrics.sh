#!/bin/bash

# EdgeMetrics One-Command Installer
# Downloads and installs the latest EdgeMetrics release from GitHub
# Usage: curl -fsSL https://raw.githubusercontent.com/EdgeWardIO/EdgeMetrics/main/install-edgemetrics.sh | bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Installation mode
MODE="${EDGEMETRICS_MODE:-single}"  # single, service, or ask
PORT="${EDGEMETRICS_PORT:-8080}"     # Default port for web interface
HOST="${EDGEMETRICS_HOST:-127.0.0.1}"  # Default host

# Configuration
REPO="EdgeWardIO/apt-repository"
GITHUB_API="https://api.github.com/repos/${REPO}/releases/latest"

echo -e "${CYAN}🚀 EdgeMetrics Server Installer${NC}"
echo "============================"
echo ""
echo -e "${BLUE}Installing EdgeMetrics web server with API${NC}"
echo -e "${BLUE}Mode: $MODE | Port: $PORT | Host: $HOST${NC}"
echo ""

# Detect system
OS="unknown"
ARCH=$(uname -m)

if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    OS="linux"
    if [[ -f /etc/os-release ]]; then
        . /etc/os-release
        DISTRO=$ID
    fi
elif [[ "$OSTYPE" == "darwin"* ]]; then
    OS="macos"
else
    echo -e "${RED}❌ Unsupported OS: $OSTYPE${NC}"
    exit 1
fi

# Normalize architecture
case $ARCH in
    x86_64|amd64) ARCH="amd64" ;;
    aarch64|arm64) ARCH="arm64" ;;
    *) 
        echo -e "${RED}❌ Unsupported architecture: $ARCH${NC}"
        exit 1
        ;;
esac

echo -e "${BLUE}System: $OS ($DISTRO)${NC}"
echo -e "${BLUE}Architecture: $ARCH${NC}"
echo ""

# Check dependencies
if ! command -v curl &> /dev/null; then
    echo -e "${RED}❌ curl is required but not installed${NC}"
    exit 1
fi

# Fetch latest release
echo -e "${YELLOW}📡 Fetching latest release...${NC}"
RELEASE_DATA=$(curl -s "$GITHUB_API")

if [[ $? -ne 0 ]]; then
    echo -e "${RED}❌ Failed to fetch release information${NC}"
    exit 1
fi

# Check if the API response contains an error (no releases available)
if echo "$RELEASE_DATA" | grep -q '"message":[[:space:]]*"Not Found"'; then
    echo -e "${RED}❌ No releases are available yet${NC}"
    echo ""
    echo -e "${YELLOW}EdgeMetrics releases are not yet published to GitHub.${NC}"
    echo ""
    echo -e "${CYAN}Alternative installation options:${NC}"
    echo "1. Check back later for official releases"
    echo "2. Visit https://github.com/EdgeWardIO/EdgeMetrics for updates"
    echo "3. Follow @EdgeWardIO for release announcements"
    echo "4. Contact support if you need immediate access"
    echo ""
    echo -e "${BLUE}For development builds or early access:${NC}"
    echo "• Visit: https://edgewardstudios.com"
    echo "• Email: support@edgemetrics.app"
    exit 1
fi

# Check for other API errors
if echo "$RELEASE_DATA" | grep -q '"message"'; then
    ERROR_MSG=$(echo "$RELEASE_DATA" | grep -o '"message":[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"message":[[:space:]]*"\([^"]*\)".*/\1/')
    echo -e "${RED}❌ GitHub API error: $ERROR_MSG${NC}"
    echo ""
    echo -e "${YELLOW}Please try again later or check:${NC}"
    echo "• Repository: https://github.com/$REPO"
    echo "• Network connectivity"
    echo "• GitHub status: https://www.githubstatus.com"
    exit 1
fi

# Parse release info
RELEASE_TAG=$(echo "$RELEASE_DATA" | grep -o '"tag_name":[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"tag_name":[[:space:]]*"\([^"]*\)".*/\1/')

if [[ -z "$RELEASE_TAG" ]]; then
    echo -e "${RED}❌ Could not parse release information${NC}"
    echo ""
    echo -e "${YELLOW}The GitHub API response was unexpected.${NC}"
    echo "This might indicate:"
    echo "• A temporary GitHub API issue"
    echo "• Changes in the repository structure"
    echo "• Network connectivity problems"
    echo ""
    echo -e "${CYAN}Please try again later or contact support.${NC}"
    exit 1
fi

VERSION=$(echo "$RELEASE_TAG" | sed 's/^v//')  # Remove 'v' prefix to get version number

# Find package URLs that match the release version (exact match)
# Use literal string matching with -F flag to avoid regex interpretation
DEB_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.deb' | grep -i "$ARCH" | head -1)
RPM_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.rpm' | grep -i "$ARCH" | head -1)
TARGZ_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.tar\.gz' | grep -i "$ARCH" | head -1)

# Find server binary URL from tar.gz archive
SERVER_BINARY_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*edgemetrics-server' | head -1)
CLI_BINARY_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*edgemetrics-cli' | head -1)
MAIN_BINARY_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*edgemetrics[^-]' | head -1)

echo -e "${GREEN}✅ Latest release: $RELEASE_TAG${NC}"
echo ""

# Function to ask for deployment mode
select_deployment_mode() {
    if [[ "$MODE" == "ask" ]]; then
        echo -e "${CYAN}Deployment Mode Selection:${NC}"
        echo "1. Single Binary (manual start/stop)"
        echo "2. Service Mode (systemd service)"
        echo ""
        while true; do
            read -p "Select mode (1-2): " choice
            case $choice in
                1) MODE="single"; break ;;
                2) MODE="service"; break ;;
                *) echo "Please enter 1 or 2" ;;
            esac
        done
    fi
    
    echo -e "${BLUE}Selected mode: $MODE${NC}"
}

# Function to install systemd service
install_systemd_service() {
    echo -e "${BLUE}🔧 Installing systemd service...${NC}"
    
    # Create system user if not exists
    if ! id -u edgemetrics &>/dev/null; then
        sudo useradd -r -s /bin/false -d /var/lib/edgemetrics edgemetrics
        sudo mkdir -p /var/lib/edgemetrics
        sudo chown edgemetrics:edgemetrics /var/lib/edgemetrics
    fi
    
    # Create service file
    sudo tee /etc/systemd/system/edgemetrics.service > /dev/null << EOF
[Unit]
Description=EdgeMetrics Web Server
After=network.target

[Service]
Type=simple
User=edgemetrics
Group=edgemetrics
WorkingDirectory=/var/lib/edgemetrics
ExecStart=/usr/local/bin/edgemetrics-server server start --host $HOST --port $PORT
Restart=always
RestartSec=10
Environment=RUST_LOG=info

[Install]
WantedBy=multi-user.target
EOF
    
    # Enable and start service
    sudo systemctl daemon-reload
    sudo systemctl enable edgemetrics
    sudo systemctl start edgemetrics
    
    echo -e "${GREEN}✅ EdgeMetrics service installed and started${NC}"
    echo -e "${CYAN}Service commands:${NC}"
    echo "  Status: sudo systemctl status edgemetrics"
    echo "  Start:  sudo systemctl start edgemetrics"
    echo "  Stop:   sudo systemctl stop edgemetrics"
    echo "  Logs:   sudo journalctl -u edgemetrics -f"
}

# Install based on Linux distribution
install_linux() {
    echo -e "${YELLOW}🐧 Installing EdgeMetrics for Linux...${NC}"
    
    # Try APT first (Ubuntu/Debian)
    if command -v apt &> /dev/null && [[ -n "$DEB_URL" ]]; then
        echo -e "${BLUE}📦 Installing via APT (DEB package)...${NC}"
        
        TEMP_DEB=$(mktemp --suffix=.deb)
        echo "Downloading: $(basename "$DEB_URL")"
        
        if curl -fsSL "$DEB_URL" -o "$TEMP_DEB"; then
            echo "Installing DEB package..."
            if sudo dpkg -i "$TEMP_DEB" 2>/dev/null || sudo apt-get install -f -y; then
                rm -f "$TEMP_DEB"
                echo -e "${GREEN}✅ EdgeMetrics installed successfully via APT${NC}"
                
                # Post-install configuration
                select_deployment_mode
                if [[ "$MODE" == "service" ]]; then
                    install_systemd_service
                fi
                
                return 0
            fi
        fi
        rm -f "$TEMP_DEB"
    fi
    
    # Try DNF/YUM (Fedora/RHEL)
    if (command -v dnf &> /dev/null || command -v yum &> /dev/null) && [[ -n "$RPM_URL" ]]; then
        echo -e "${BLUE}📦 Installing via RPM package...${NC}"
        
        TEMP_RPM=$(mktemp --suffix=.rpm)
        echo "Downloading: $(basename "$RPM_URL")"
        
        if curl -fsSL "$RPM_URL" -o "$TEMP_RPM"; then
            echo "Installing RPM package..."
            RPM_MANAGER="dnf"
            command -v dnf &> /dev/null || RPM_MANAGER="yum"
            
            if sudo $RPM_MANAGER install -y "$TEMP_RPM"; then
                rm -f "$TEMP_RPM"
                echo -e "${GREEN}✅ EdgeMetrics installed successfully via $RPM_MANAGER${NC}"
                
                # Post-install configuration
                select_deployment_mode
                if [[ "$MODE" == "service" ]]; then
                    install_systemd_service
                fi
                
                return 0
            fi
        fi
        rm -f "$TEMP_RPM"
    fi
    
    # Fallback to binary download from tar.gz archive
    if [[ -n "$TARGZ_URL" ]]; then
        echo -e "${BLUE}📦 Installing via binary download...${NC}"
        
        # Determine install location
        if [[ $EUID -eq 0 ]]; then
            INSTALL_DIR="/usr/local/bin"
        else
            INSTALL_DIR="$HOME/.local/bin"
            mkdir -p "$INSTALL_DIR"
        fi
        
        echo "Downloading: $(basename "$TARGZ_URL")"
        TEMP_TAR=$(mktemp --suffix=.tar.gz)
        if curl -fsSL "$TARGZ_URL" -o "$TEMP_TAR"; then
            # Extract binaries from tar.gz
            tar -xzf "$TEMP_TAR" -C "$INSTALL_DIR" --strip-components=0 edgemetrics-server edgemetrics-cli edgemetrics 2>/dev/null || {
                # Try without strip-components if structure is different
                tar -xzf "$TEMP_TAR" -C "$INSTALL_DIR" 2>/dev/null
            }
            
            # Make binaries executable
            chmod +x "$INSTALL_DIR/edgemetrics-server" "$INSTALL_DIR/edgemetrics-cli" "$INSTALL_DIR/edgemetrics" 2>/dev/null || true
            
            rm -f "$TEMP_TAR"
            
            echo -e "${GREEN}✅ EdgeMetrics binaries installed to $INSTALL_DIR${NC}"
            
            # Add to PATH if needed
            if [[ ":$PATH:" != *":$INSTALL_DIR:"* ]] && [[ $EUID -ne 0 ]]; then
                echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$HOME/.bashrc"
                echo -e "${YELLOW}⚠️  Added $INSTALL_DIR to PATH in ~/.bashrc${NC}"
                echo -e "${YELLOW}⚠️  Run 'source ~/.bashrc' or restart terminal${NC}"
            fi
            
            # Post-install configuration
            select_deployment_mode
            if [[ "$MODE" == "service" ]]; then
                install_systemd_service
            fi
            
            return 0
        fi
        rm -f "$TEMP_TAR"
    fi
    
    # Try direct binary download if available
    if [[ -n "$SERVER_BINARY_URL" ]]; then
        echo -e "${BLUE}📦 Installing server binary directly...${NC}"
        
        # Determine install location
        if [[ $EUID -eq 0 ]]; then
            INSTALL_DIR="/usr/local/bin"
        else
            INSTALL_DIR="$HOME/.local/bin"
            mkdir -p "$INSTALL_DIR"
        fi
        
        echo "Downloading: edgemetrics-server"
        if curl -fsSL "$SERVER_BINARY_URL" -o "$INSTALL_DIR/edgemetrics-server"; then
            chmod +x "$INSTALL_DIR/edgemetrics-server"
            
            # Download CLI binary if available
            if [[ -n "$CLI_BINARY_URL" ]]; then
                curl -fsSL "$CLI_BINARY_URL" -o "$INSTALL_DIR/edgemetrics-cli" && chmod +x "$INSTALL_DIR/edgemetrics-cli"
            fi
            
            # Download main binary if available
            if [[ -n "$MAIN_BINARY_URL" ]]; then
                curl -fsSL "$MAIN_BINARY_URL" -o "$INSTALL_DIR/edgemetrics" && chmod +x "$INSTALL_DIR/edgemetrics"
            fi
            
            echo -e "${GREEN}✅ EdgeMetrics server installed to $INSTALL_DIR/edgemetrics-server${NC}"
            
            # Add to PATH if needed
            if [[ ":$PATH:" != *":$INSTALL_DIR:"* ]] && [[ $EUID -ne 0 ]]; then
                echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$HOME/.bashrc"
                echo -e "${YELLOW}⚠️  Added $INSTALL_DIR to PATH in ~/.bashrc${NC}"
                echo -e "${YELLOW}⚠️  Run 'source ~/.bashrc' or restart terminal${NC}"
            fi
            
            # Post-install configuration
            select_deployment_mode
            if [[ "$MODE" == "service" ]]; then
                install_systemd_service
            fi
            
            return 0
        fi
    fi
    
    return 1
}

# Install based on OS
case "$OS" in
    "linux")
        if install_linux; then
            echo ""
            echo -e "${CYAN}🎉 Installation completed successfully!${NC}"
            echo ""
            
            if [[ "$MODE" == "service" ]]; then
                echo -e "${CYAN}Service Mode - EdgeMetrics is running as a system service${NC}"
                echo -e "${GREEN}✅ Web interface: http://$HOST:$PORT${NC}"
                echo ""
                echo -e "${CYAN}Service Management:${NC}"
                echo "  • Status: sudo systemctl status edgemetrics"
                echo "  • Start:  sudo systemctl start edgemetrics"
                echo "  • Stop:   sudo systemctl stop edgemetrics"
                echo "  • Logs:   sudo journalctl -u edgemetrics -f"
            else
                echo -e "${CYAN}Single Binary Mode - Manual start/stop${NC}"
                echo -e "${CYAN}How to use:${NC}"
                echo "  • Start server: edgemetrics-server server start"
                echo "  • Custom port:  edgemetrics-server server start --port 9000"
                echo "  • Help: edgemetrics-server --help"
                echo "  • Version: edgemetrics-server --version"
                echo ""
                echo -e "${GREEN}After starting, web interface available at: http://$HOST:$PORT${NC}"
            fi
            
            echo ""
            echo -e "${CYAN}CLI Commands:${NC}"
            echo "  • Analyze model: edgemetrics-server analyze model.onnx --hardware cpu"
            echo "  • Compare models: edgemetrics-server compare model1.onnx model2.onnx --hardware gpu"
            echo "  • List hardware: edgemetrics-server hardware list"
            echo ""
            echo -e "${CYAN}Documentation:${NC}"
            echo "  • Website: https://edgemetrics.app"
            echo "  • API Docs: http://$HOST:$PORT/docs (when server is running)"
            echo "  • Support: support@edgemetrics.app"
        else
            echo -e "${RED}❌ All installation methods failed${NC}"
            echo ""
            echo -e "${YELLOW}Manual installation options:${NC}"
            echo "1. Download from: https://github.com/$REPO/releases/latest"
            echo "2. Extract tar.gz archive manually"
            echo "3. Use native package managers if available"
            echo "4. Contact support: support@edgemetrics.app"
            exit 1
        fi
        ;;
    "macos")
        echo -e "${RED}❌ macOS packages not yet available${NC}"
        echo "Download manually: https://github.com/$REPO/releases/latest"
        echo "Contact support: support@edgemetrics.app"
        exit 1
        ;;
    *)
        echo -e "${RED}❌ Unsupported operating system: $OS${NC}"
        exit 1
        ;;
esac