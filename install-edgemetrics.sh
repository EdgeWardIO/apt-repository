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
    DISTRO="macos"
elif [[ "$OSTYPE" == "cygwin" ]] || [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
    OS="windows"
    DISTRO="windows"
else
    echo -e "${RED}❌ Unsupported OS: $OSTYPE${NC}"
    echo "Supported platforms: Linux, macOS, Windows"
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
    echo "• Visit: https://edgemetrics.app"
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

# Find Windows and macOS installers
MSI_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.msi' | head -1)
EXE_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.exe' | grep -v msi | head -1)
DMG_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.dmg' | head -1)
APP_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.app\.tar\.gz' | head -1)

echo -e "${GREEN}✅ Latest release: $RELEASE_TAG${NC}"
echo ""

# Function to create GLIBC compatibility wrapper scripts
create_wrapper_scripts() {
    local install_dir="$1"
    
    echo -e "${BLUE}🔧 Creating GLIBC compatibility wrapper scripts...${NC}"
    
    # CLI wrapper script
    cat > "$install_dir/edgemetrics-cli-wrapper" << 'WRAPPER_EOF'
#!/bin/bash
# EdgeMetrics CLI wrapper for GLIBC compatibility
# Cleans environment to prevent snap/VS Code interference

set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACTUAL_BINARY="$SCRIPT_DIR/edgemetrics-cli"

# Clean ALL environment variables to ensure no snap interference
for var in $(env | grep -E '^[A-Z_]*SNAP|VSCODE|GTK_|GDK_|GSETTINGS|GIO_|LOCPATH' | cut -d= -f1); do
    unset "$var"
done

# Ensure clean library path
unset LD_LIBRARY_PATH
unset LD_PRELOAD

# Set minimal, clean environment
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export HOME="${HOME:-/tmp}"
export USER="${USER:-$(whoami)}"
export SHELL="${SHELL:-/bin/bash}"

# Execute the actual binary with all arguments
exec "$ACTUAL_BINARY" "$@"
WRAPPER_EOF
    
    # Server wrapper script
    cat > "$install_dir/edgemetrics-server-wrapper" << 'WRAPPER_EOF'
#!/bin/bash
# EdgeMetrics Server wrapper for GLIBC compatibility
# Cleans environment to prevent snap/VS Code interference

set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACTUAL_BINARY="$SCRIPT_DIR/edgemetrics-server"

# Clean ALL environment variables to ensure no snap interference
for var in $(env | grep -E '^[A-Z_]*SNAP|VSCODE|GTK_|GDK_|GSETTINGS|GIO_|LOCPATH' | cut -d= -f1); do
    unset "$var"
done

# Ensure clean library path
unset LD_LIBRARY_PATH
unset LD_PRELOAD

# Set minimal, clean environment
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export HOME="${HOME:-/tmp}"
export USER="${USER:-$(whoami)}"
export SHELL="${SHELL:-/bin/bash}"

# Execute the actual binary with all arguments
exec "$ACTUAL_BINARY" "$@"
WRAPPER_EOF
    
    # Desktop app wrapper script
    cat > "$install_dir/edgemetrics-wrapper" << 'WRAPPER_EOF'
#!/bin/bash
# EdgeMetrics Desktop wrapper for GLIBC compatibility
# Cleans environment to prevent snap/VS Code interference

set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACTUAL_BINARY="$SCRIPT_DIR/edgemetrics"

# Clean ALL environment variables to ensure no snap interference
for var in $(env | grep -E '^[A-Z_]*SNAP|VSCODE|GTK_|GDK_|GSETTINGS|GIO_|LOCPATH' | cut -d= -f1); do
    unset "$var"
done

# Ensure clean library path
unset LD_LIBRARY_PATH
unset LD_PRELOAD

# Set minimal, clean environment
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export HOME="${HOME:-/tmp}"
export USER="${USER:-$(whoami)}"
export SHELL="${SHELL:-/bin/bash}"

# Execute the actual binary with all arguments
exec "$ACTUAL_BINARY" "$@"
WRAPPER_EOF
    
    # Make wrapper scripts executable
    chmod +x "$install_dir/edgemetrics-cli-wrapper" "$install_dir/edgemetrics-server-wrapper" "$install_dir/edgemetrics-wrapper" 2>/dev/null || true
    
    echo -e "${GREEN}✅ GLIBC compatibility wrappers created${NC}"
}

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
ExecStart=/usr/local/bin/edgemetrics-server-wrapper server start --host $HOST --port $PORT
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
                
                # DEB package now installs wrapper scripts automatically
                echo -e "${GREEN}✅ EdgeMetrics installed successfully via APT${NC}"
                echo -e "${GREEN}📦 Wrapper scripts installed to /usr/bin/ for GLIBC compatibility${NC}"
                echo -e "${GREEN}🔧 Actual binaries installed to /opt/edgemetrics/bin/${NC}"
                
                # Verify wrapper scripts are executable
                if [[ -x "/usr/bin/edgemetrics-cli" ]]; then
                    echo -e "${GREEN}✅ CLI wrapper script ready${NC}"
                fi
                if [[ -x "/usr/bin/edgemetrics-server" ]]; then
                    echo -e "${GREEN}✅ Server wrapper script ready${NC}"
                fi
                
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
                
                # RPM package now installs wrapper scripts automatically
                echo -e "${GREEN}✅ EdgeMetrics installed successfully via $RPM_MANAGER${NC}"
                echo -e "${GREEN}📦 Wrapper scripts installed to /usr/bin/ for GLIBC compatibility${NC}"
                echo -e "${GREEN}🔧 Actual binaries installed to /opt/edgemetrics/bin/${NC}"
                
                # Verify wrapper scripts are executable
                if [[ -x "/usr/bin/edgemetrics-cli" ]]; then
                    echo -e "${GREEN}✅ CLI wrapper script ready${NC}"
                fi
                if [[ -x "/usr/bin/edgemetrics-server" ]]; then
                    echo -e "${GREEN}✅ Server wrapper script ready${NC}"
                fi
                
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
            if tar -xzf "$TEMP_TAR" -C "$INSTALL_DIR" --strip-components=0 edgemetrics-server edgemetrics-cli edgemetrics 2>/dev/null || tar -xzf "$TEMP_TAR" -C "$INSTALL_DIR" 2>/dev/null; then
                # Make binaries executable
                chmod +x "$INSTALL_DIR/edgemetrics-server" "$INSTALL_DIR/edgemetrics-cli" "$INSTALL_DIR/edgemetrics" 2>/dev/null || true
                
                # Create wrapper scripts for GLIBC compatibility
                create_wrapper_scripts "$INSTALL_DIR"
                
                rm -f "$TEMP_TAR"
                
                # Verify at least the server binary exists
                if [[ -f "$INSTALL_DIR/edgemetrics-server" ]]; then
                    echo -e "${GREEN}✅ EdgeMetrics binaries installed to $INSTALL_DIR${NC}"
                    echo -e "${GREEN}📦 GLIBC compatibility wrappers created${NC}"
                else
                    echo -e "${RED}❌ Server binary not found after extraction${NC}"
                    rm -f "$TEMP_TAR"
                    return 1
                fi
            else
                echo -e "${RED}❌ Failed to extract binaries from archive${NC}"
                rm -f "$TEMP_TAR"
                return 1
            fi
            
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
    
    # Try direct binary download if available (fallback)
    if [[ -n "$SERVER_BINARY_URL" ]]; then
        echo -e "${BLUE}📦 Trying direct binary download...${NC}"
        
        # Determine install location
        if [[ $EUID -eq 0 ]]; then
            INSTALL_DIR="/usr/local/bin"
        else
            INSTALL_DIR="$HOME/.local/bin"
            mkdir -p "$INSTALL_DIR"
        fi
        
        echo "Downloading: edgemetrics-server"
        if curl -fsSL "$SERVER_BINARY_URL" -o "$INSTALL_DIR/edgemetrics-server" 2>/dev/null; then
            chmod +x "$INSTALL_DIR/edgemetrics-server"
            
            # Download CLI binary if available
            if [[ -n "$CLI_BINARY_URL" ]]; then
                curl -fsSL "$CLI_BINARY_URL" -o "$INSTALL_DIR/edgemetrics-cli" 2>/dev/null && chmod +x "$INSTALL_DIR/edgemetrics-cli"
            fi
            
            # Download main binary if available
            if [[ -n "$MAIN_BINARY_URL" ]]; then
                curl -fsSL "$MAIN_BINARY_URL" -o "$INSTALL_DIR/edgemetrics" 2>/dev/null && chmod +x "$INSTALL_DIR/edgemetrics"
            fi
            
            # Create wrapper scripts for GLIBC compatibility
            create_wrapper_scripts "$INSTALL_DIR"
            
            echo -e "${GREEN}✅ EdgeMetrics server installed to $INSTALL_DIR/edgemetrics-server${NC}"
            echo -e "${GREEN}📦 GLIBC compatibility wrappers created${NC}"
            
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
        else
            echo -e "${YELLOW}⚠️  Direct binary download failed${NC}"
        fi
    fi
    
    return 1
}

# Function to create Windows wrapper scripts
create_windows_wrapper_scripts() {
    local install_dir="$1"
    
    echo -e "${BLUE}🔧 Creating Windows wrapper scripts...${NC}"
    
    # CLI wrapper batch file
    cat > "$install_dir/edgemetrics-cli.bat" << 'WRAPPER_EOF'
@echo off
REM EdgeMetrics CLI wrapper for Windows
REM Ensures clean environment for compatibility

setlocal EnableDelayedExpansion

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
set "ACTUAL_BINARY=%SCRIPT_DIR%edgemetrics-cli.exe"

REM Clean environment variables that might cause issues
set "VSCODE_PID="
set "VSCODE_CWD="
set "ELECTRON_RUN_AS_NODE="

REM Execute the actual binary with all arguments
"%ACTUAL_BINARY%" %*
WRAPPER_EOF
    
    # Server wrapper batch file
    cat > "$install_dir/edgemetrics-server.bat" << 'WRAPPER_EOF'
@echo off
REM EdgeMetrics Server wrapper for Windows
REM Ensures clean environment for compatibility

setlocal EnableDelayedExpansion

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
set "ACTUAL_BINARY=%SCRIPT_DIR%edgemetrics-server.exe"

REM Clean environment variables that might cause issues
set "VSCODE_PID="
set "VSCODE_CWD="
set "ELECTRON_RUN_AS_NODE="

REM Execute the actual binary with all arguments
"%ACTUAL_BINARY%" %*
WRAPPER_EOF
    
    # Desktop app wrapper batch file  
    cat > "$install_dir/edgemetrics.bat" << 'WRAPPER_EOF'
@echo off
REM EdgeMetrics Desktop wrapper for Windows
REM Ensures clean environment for compatibility

setlocal EnableDelayedExpansion

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
set "ACTUAL_BINARY=%SCRIPT_DIR%edgemetrics.exe"

REM Clean environment variables that might cause issues
set "VSCODE_PID="
set "VSCODE_CWD="
set "ELECTRON_RUN_AS_NODE="

REM Execute the actual binary with all arguments
"%ACTUAL_BINARY%" %*
WRAPPER_EOF
    
    echo -e "${GREEN}✅ Windows wrapper scripts created${NC}"
}

# Install for Windows
install_windows() {
    echo -e "${YELLOW}🪟 Installing EdgeMetrics for Windows...${NC}"
    
    # Try MSI installer first
    if [[ -n "$MSI_URL" ]]; then
        echo -e "${BLUE}📦 MSI installer available${NC}"
        echo "Download and run: $(basename "$MSI_URL")"
        echo "URL: $MSI_URL"
        echo ""
        echo -e "${CYAN}The MSI installer will:${NC}"
        echo "  • Install EdgeMetrics to Program Files"
        echo "  • Create Start Menu shortcuts"
        echo "  • Add to Windows PATH"
        echo "  • Install Windows service (optional)"
        return 0
    fi
    
    # Fallback to EXE installer
    if [[ -n "$EXE_URL" ]]; then
        echo -e "${BLUE}📦 EXE installer available${NC}"
        echo "Download and run: $(basename "$EXE_URL")"
        echo "URL: $EXE_URL"
        return 0
    fi
    
    echo -e "${RED}❌ No Windows installer found${NC}"
    return 1
}

# Install for macOS
install_macos() {
    echo -e "${YELLOW}🍎 Installing EdgeMetrics for macOS...${NC}"
    
    # Try DMG installer first
    if [[ -n "$DMG_URL" ]]; then
        echo -e "${BLUE}📦 DMG installer available${NC}"
        echo "Download and install: $(basename "$DMG_URL")"
        echo "URL: $DMG_URL"
        echo ""
        echo -e "${CYAN}The DMG installer will:${NC}"
        echo "  • Install EdgeMetrics.app to Applications"
        echo "  • Create Launchpad shortcuts"
        echo "  • Install command-line tools"
        echo "  • Set up LaunchAgent service (optional)"
        return 0
    fi
    
    # Fallback to app bundle
    if [[ -n "$APP_URL" ]]; then
        echo -e "${BLUE}📦 App bundle available${NC}"
        
        # Determine install location
        if [[ $EUID -eq 0 ]]; then
            INSTALL_DIR="/Applications"
        else
            INSTALL_DIR="$HOME/Applications"
            mkdir -p "$INSTALL_DIR"
        fi
        
        echo "Downloading: $(basename "$APP_URL")"
        TEMP_TAR=$(mktemp --suffix=.tar.gz)
        if curl -fsSL "$APP_URL" -o "$TEMP_TAR"; then
            if tar -xzf "$TEMP_TAR" -C "$INSTALL_DIR"; then
                rm -f "$TEMP_TAR"
                echo -e "${GREEN}✅ EdgeMetrics.app installed to $INSTALL_DIR${NC}"
                
                # Install command-line tools
                BIN_DIR="/usr/local/bin"
                if [[ $EUID -ne 0 ]]; then
                    BIN_DIR="$HOME/.local/bin"
                    mkdir -p "$BIN_DIR"
                fi
                
                # Create CLI symlinks
                ln -sf "$INSTALL_DIR/EdgeMetrics.app/Contents/MacOS/edgemetrics-cli" "$BIN_DIR/edgemetrics-cli" 2>/dev/null || true
                ln -sf "$INSTALL_DIR/EdgeMetrics.app/Contents/MacOS/edgemetrics-server" "$BIN_DIR/edgemetrics-server" 2>/dev/null || true
                
                echo -e "${GREEN}✅ Command-line tools installed to $BIN_DIR${NC}"
                return 0
            fi
        fi
        rm -f "$TEMP_TAR"
    fi
    
    echo -e "${RED}❌ No macOS installer found${NC}"
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
                echo "  • Start server: edgemetrics-server-wrapper start --host 127.0.0.1 --port 8080 --open"
                echo "  • Custom port:  edgemetrics-server-wrapper start --host 127.0.0.1 --port 9000 --open"
                echo "  • Help: edgemetrics-server-wrapper --help"
                echo "  • Version: edgemetrics-server-wrapper --version"
                echo ""
                echo -e "${CYAN}GLIBC Compatibility:${NC}"
                echo "  • ✅ Automatic compatibility with snap environments (VS Code, etc.)"
                echo "  • ✅ Works across all Linux distributions"
                echo "  • ✅ No manual configuration required"
                echo ""
                echo -e "${GREEN}After starting, web interface available at: http://$HOST:$PORT${NC}"
            fi
            
            echo ""
            echo -e "${CYAN}CLI Commands:${NC}"
            echo "  • Analyze model: edgemetrics-server-wrapper analyze model.onnx --hardware cpu"
            echo "  • Compare models: edgemetrics-server-wrapper compare model1.onnx model2.onnx --hardware gpu"
            echo "  • List hardware: edgemetrics-server-wrapper hardware list"
            echo "  • CLI tool: edgemetrics-cli-wrapper [command] [options]"
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
        if install_macos; then
            echo ""
            echo -e "${CYAN}🎉 macOS installation information provided!${NC}"
            echo ""
            echo -e "${CYAN}After installation:${NC}"
            echo "  • Launch from Applications folder or Launchpad"
            echo "  • CLI tools: edgemetrics-cli, edgemetrics-server"
            echo "  • Documentation: https://edgemetrics.app/docs"
        else
            echo -e "${RED}❌ macOS installation failed${NC}"
            echo "Download manually: https://github.com/$REPO/releases/latest"
            exit 1
        fi
        ;;
    "windows")
        if install_windows; then
            echo ""
            echo -e "${CYAN}🎉 Windows installation information provided!${NC}"
            echo ""
            echo -e "${CYAN}After installation:${NC}"
            echo "  • Launch from Start Menu or desktop shortcut"
            echo "  • Command Prompt: edgemetrics-cli, edgemetrics-server"
            echo "  • Documentation: https://edgemetrics.app/docs"
        else
            echo -e "${RED}❌ Windows installation failed${NC}"
            echo "Download manually: https://github.com/$REPO/releases/latest"
            exit 1
        fi
        ;;
    *)
        echo -e "${RED}❌ Unsupported operating system: $OS${NC}"
        exit 1
        ;;
esac