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

# Configuration
REPO="EdgeWardIO/apt-repository"
GITHUB_API="https://api.github.com/repos/${REPO}/releases/latest"

echo -e "${CYAN}🚀 EdgeMetrics Installer${NC}"
echo "=========================="
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

# Parse release info
RELEASE_TAG=$(echo "$RELEASE_DATA" | grep -o '"tag_name":[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"tag_name":[[:space:]]*"\([^"]*\)".*/\1/')
VERSION=$(echo "$RELEASE_TAG" | sed 's/^v//')  # Remove 'v' prefix to get version number

# Find package URLs that match the release version
DEB_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.deb' | grep -i "$ARCH" | grep "$VERSION" | head -1)
RPM_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.rpm' | grep -i "$ARCH" | grep "$VERSION" | head -1)
APPIMAGE_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.AppImage' | grep -i "$ARCH" | grep "$VERSION" | head -1)

# Fallback to any package if version-specific not found
if [[ -z "$DEB_URL" ]]; then
    DEB_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.deb' | grep -i "$ARCH" | head -1)
fi
if [[ -z "$RPM_URL" ]]; then
    RPM_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.rpm' | grep -i "$ARCH" | head -1)
fi
if [[ -z "$APPIMAGE_URL" ]]; then
    APPIMAGE_URL=$(echo "$RELEASE_DATA" | grep -o 'https://[^"]*\.AppImage' | grep -i "$ARCH" | head -1)
fi

if [[ -z "$RELEASE_TAG" ]]; then
    echo -e "${RED}❌ Could not parse release information${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Latest release: $RELEASE_TAG${NC}"
echo ""

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
                return 0
            fi
        fi
        rm -f "$TEMP_RPM"
    fi
    
    # Fallback to AppImage (if available)
    if [[ -n "$APPIMAGE_URL" ]]; then
        echo -e "${BLUE}📦 Installing via AppImage (universal)...${NC}"
        
        # Determine install location
        if [[ $EUID -eq 0 ]]; then
            INSTALL_DIR="/usr/local/bin"
            DESKTOP_DIR="/usr/share/applications"
        else
            INSTALL_DIR="$HOME/.local/bin"
            DESKTOP_DIR="$HOME/.local/share/applications"
            mkdir -p "$INSTALL_DIR" "$DESKTOP_DIR"
        fi
        
        echo "Downloading: $(basename "$APPIMAGE_URL")"
        if curl -fsSL "$APPIMAGE_URL" -o "$INSTALL_DIR/edgemetrics"; then
            chmod +x "$INSTALL_DIR/edgemetrics"
            
            # Create desktop entry
            cat > "$DESKTOP_DIR/edgemetrics.desktop" << EOF
[Desktop Entry]
Name=EdgeMetrics
Comment=ML model performance analyzer for edge devices
Exec=$INSTALL_DIR/edgemetrics
Icon=edgemetrics
Type=Application
Categories=Development;Science;
Terminal=false
StartupWMClass=EdgeMetrics
EOF
            
            echo -e "${GREEN}✅ EdgeMetrics AppImage installed to $INSTALL_DIR/edgemetrics${NC}"
            
            # Add to PATH if needed
            if [[ ":$PATH:" != *":$INSTALL_DIR:"* ]] && [[ $EUID -ne 0 ]]; then
                echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$HOME/.bashrc"
                echo -e "${YELLOW}⚠️  Added $INSTALL_DIR to PATH in ~/.bashrc${NC}"
                echo -e "${YELLOW}⚠️  Run 'source ~/.bashrc' or restart terminal${NC}"
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
            echo -e "${CYAN}How to use:${NC}"
            echo "  • Launch: edgemetrics"
            echo "  • Help: edgemetrics --help"
            echo "  • Version: edgemetrics --version"
            echo ""
            echo -e "${CYAN}Documentation:${NC}"
            echo "  • GitHub: https://github.com/EdgeWardIO/EdgeMetrics"
        else
            echo -e "${RED}❌ All installation methods failed${NC}"
            echo ""
            echo -e "${YELLOW}Manual installation options:${NC}"
            echo "1. Download from: https://github.com/$REPO/releases/latest"
            echo "2. Check for AppImage availability (may be >100MB, not on GitHub)"
            echo "3. Use native package managers if available"
            echo "4. Report issues: https://github.com/EdgeWardIO/EdgeMetrics/issues"
            exit 1
        fi
        ;;
    "macos")
        echo -e "${RED}❌ macOS packages not yet available${NC}"
        echo "Download manually: https://github.com/$REPO/releases/latest"
        exit 1
        ;;
    *)
        echo -e "${RED}❌ Unsupported operating system: $OS${NC}"
        exit 1
        ;;
esac