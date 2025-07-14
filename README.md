# EdgeMetrics APT Repository

Official APT repository for EdgeMetrics - ML model performance analyzer for edge devices.

## 🚀 Quick Installation

```bash
# Add repository key
curl -fsSL https://EdgeWardIO.github.io/apt-repository/edgemetrics-apt-key.asc | sudo gpg --dearmor -o /usr/share/keyrings/edgemetrics.gpg

# Add repository
echo "deb [signed-by=/usr/share/keyrings/edgemetrics.gpg] https://EdgeWardIO.github.io/apt-repository stable main" | sudo tee /etc/apt/sources.list.d/edgemetrics.list

# Update package list
sudo apt update

# Install EdgeMetrics
sudo apt install edgemetrics
```

## 📦 Available Packages

- **edgemetrics** - Main EdgeMetrics application
  - Supports: amd64, arm64, armhf
  - Dependencies: Automatically managed

## 🔐 Repository Signing

This repository is signed with GPG key: `apt@edgemetrics.app`

To verify packages manually:
```bash
gpg --verify /var/lib/apt/lists/edgewardio.github.io_apt-repository_dists_stable_Release.gpg
```

## 🛠️ For Developers

This repository is automatically updated when new releases are published to the main EdgeMetrics repository.

### Manual Package Installation

If you prefer to download packages directly:
1. Visit [GitHub Releases](https://github.com/EdgeWardIO/EdgeMetrics/releases)
2. Download the appropriate `.deb` file for your architecture
3. Install with: `sudo dpkg -i edgemetrics_*.deb`

## 📊 Repository Statistics

- **Architecture Support**: amd64, arm64, armhf
- **Update Frequency**: Automatic on release
- **Signature**: GPG signed for security

## 🔗 Links

- **Main Project**: [EdgeMetrics](https://github.com/EdgeWardIO/EdgeMetrics)
- **Website**: [edgemetrics.app](https://edgemetrics.app)
- **Documentation**: [docs.edgemetrics.app](https://docs.edgemetrics.app)
- **Issues**: [GitHub Issues](https://github.com/EdgeWardIO/EdgeMetrics/issues)

---

**Note**: This repository will be populated with packages once the first EdgeMetrics release is published.
