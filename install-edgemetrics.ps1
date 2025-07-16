# EdgeMetrics One-Command Windows Installer
# Downloads and installs the latest EdgeMetrics release from GitHub
# Usage: iwr -useb https://raw.githubusercontent.com/EdgeWardIO/EdgeMetrics/main/install-edgemetrics.ps1 | iex

param(
    [switch]$Force,
    [string]$InstallDir,
    [switch]$NoDesktopShortcut
)

$ErrorActionPreference = "Stop"

# Configuration
$REPO = "EdgeWardIO/apt-repository"
$GITHUB_API = "https://api.github.com/repos/$REPO/releases/latest"

# Color functions
function Write-ColorHost {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    
    switch ($Color) {
        "Success" { Write-Host $Message -ForegroundColor Green }
        "Warning" { Write-Host $Message -ForegroundColor Yellow }
        "Error" { Write-Host $Message -ForegroundColor Red }
        "Info" { Write-Host $Message -ForegroundColor Blue }
        "Header" { Write-Host $Message -ForegroundColor Cyan }
        default { Write-Host $Message -ForegroundColor $Color }
    }
}

Write-ColorHost "🚀 EdgeMetrics Windows Installer" -Color Header
Write-ColorHost "==================================" -Color Header
Write-Host ""

# Detect system
$arch = $env:PROCESSOR_ARCHITECTURE
$windowsVersion = [System.Environment]::OSVersion.Version

Write-ColorHost "System: Windows $($windowsVersion.Major).$($windowsVersion.Minor)" -Color Info
Write-ColorHost "Architecture: $arch" -Color Info
Write-Host ""

# Check if already installed (unless forced)
if (-not $Force) {
    $existingInstall = Get-WmiObject -Class Win32_Product -ErrorAction SilentlyContinue | 
                      Where-Object { $_.Name -like "*EdgeMetrics*" }
    if ($existingInstall) {
        Write-ColorHost "EdgeMetrics is already installed" -Color Warning
        Write-Host "Use -Force parameter to reinstall" -ForegroundColor Gray
        exit 0
    }
}

# Fetch latest release
Write-ColorHost "📡 Fetching latest release..." -Color Warning

try {
    $response = Invoke-RestMethod -Uri $GITHUB_API -UseBasicParsing
    $releaseTag = $response.tag_name
    
    Write-ColorHost "✅ Latest release: $releaseTag" -Color Success
    Write-Host ""
} catch {
    Write-ColorHost "❌ Failed to fetch release information: $($_.Exception.Message)" -Color Error
    exit 1
}

# Find Windows installers
$msiAsset = $response.assets | Where-Object { $_.name -like "*.msi" } | Select-Object -First 1
$exeAsset = $response.assets | Where-Object { $_.name -like "*.exe" -and $_.name -notlike "*troubleshoot*" } | Select-Object -First 1

if (-not $msiAsset -and -not $exeAsset) {
    Write-ColorHost "❌ No Windows installers found in release" -Color Error
    Write-Host "Download manually: $($response.html_url)" -ForegroundColor Gray
    exit 1
}

# Function to download file
function Download-File {
    param($Url, $OutputPath, $Description)
    
    $fileName = Split-Path $Url -Leaf
    Write-ColorHost "📥 Downloading $Description ($fileName)..." -Color Warning
    
    try {
        $directory = Split-Path $OutputPath -Parent
        if (-not (Test-Path $directory)) {
            New-Item -ItemType Directory -Path $directory -Force | Out-Null
        }
        
        Invoke-WebRequest -Uri $Url -OutFile $OutputPath -UseBasicParsing
        
        if (-not (Test-Path $OutputPath)) {
            throw "Download failed - file not found"
        }
        
        $fileSize = (Get-Item $OutputPath).Length / 1MB
        Write-ColorHost "✅ Downloaded $fileName ($($fileSize.ToString('F1')) MB)" -Color Success
        return $true
    }
    catch {
        Write-ColorHost "❌ Download failed: $($_.Exception.Message)" -Color Error
        return $false
    }
}

# Function to select deployment mode
function Select-DeploymentMode {
    if ($Mode -eq "ask") {
        Write-ColorHost "Deployment Mode Selection:" -Color Header
        Write-Host "1. Single Binary (manual start/stop)"
        Write-Host "2. Service Mode (Windows Service)"
        Write-Host ""
        
        do {
            $choice = Read-Host "Select mode (1-2)"
            switch ($choice) {
                "1" { $script:Mode = "single"; break }
                "2" { $script:Mode = "service"; break }
                default { Write-Host "Please enter 1 or 2" }
            }
        } while ($choice -notin @("1", "2"))
    }
    
    Write-ColorHost "Selected mode: $Mode" -Color Info
}

# Function to install Windows Service
function Install-WindowsService {
    param($BinaryPath)
    
    Write-ColorHost "🔧 Installing Windows Service..." -Color Info
    
    try {
        # Check if service already exists
        $existingService = Get-Service -Name "EdgeMetrics" -ErrorAction SilentlyContinue
        if ($existingService) {
            Write-ColorHost "Stopping existing service..." -Color Warning
            Stop-Service -Name "EdgeMetrics" -Force -ErrorAction SilentlyContinue
            
            Write-ColorHost "Removing existing service..." -Color Warning
            sc.exe delete "EdgeMetrics" | Out-Null
            Start-Sleep -Seconds 2
        }
        
        # Create the service
        $serviceArgs = "server start --host $Host --port $Port"
        $servicePath = "`"$BinaryPath`" $serviceArgs"
        
        Write-ColorHost "Creating EdgeMetrics service..." -Color Warning
        $result = sc.exe create "EdgeMetrics" binPath= $servicePath start= auto DisplayName= "EdgeMetrics Web Server"
        
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create service: $result"
        }
        
        # Set service description
        sc.exe description "EdgeMetrics" "ML model performance analyzer for edge devices" | Out-Null
        
        # Start the service
        Write-ColorHost "Starting EdgeMetrics service..." -Color Warning
        Start-Service -Name "EdgeMetrics"
        
        Write-ColorHost "✅ EdgeMetrics service installed and started" -Color Success
        Write-ColorHost "Service commands:" -Color Header
        Write-Host "  Status: Get-Service -Name EdgeMetrics"
        Write-Host "  Start:  Start-Service -Name EdgeMetrics"
        Write-Host "  Stop:   Stop-Service -Name EdgeMetrics"
        Write-Host "  Logs:   Get-EventLog -LogName Application -Source EdgeMetrics"
        
        return $true
    }
    catch {
        Write-ColorHost "❌ Service installation failed: $($_.Exception.Message)" -Color Error
        return $false
    }
}

# Function to install via MSI
function Install-ViaMSI {
    param($Asset)
    
    Write-ColorHost "📦 Installing via MSI package..." -Color Info
    
    $tempMsi = Join-Path $env:TEMP $Asset.name
    
    if (-not (Download-File -Url $Asset.browser_download_url -OutputPath $tempMsi -Description "MSI installer")) {
        return $false
    }
    
    Write-ColorHost "🔧 Installing MSI package..." -Color Warning
    Write-Host "  This may take a few minutes and will install WebView2 if needed" -ForegroundColor Gray
    
    try {
        $msiArgs = @(
            "/i", "`"$tempMsi`""
            "/quiet"
            "/norestart"
            "/l*v", "`"$env:TEMP\EdgeMetrics-install.log`""
        )
        
        if ($InstallDir) {
            $msiArgs += "INSTALLDIR=`"$InstallDir`""
        }
        
        $process = Start-Process -FilePath "msiexec.exe" -ArgumentList $msiArgs -Wait -PassThru -NoNewWindow
        
        if ($process.ExitCode -eq 0 -or $process.ExitCode -eq 3010) {
            Write-ColorHost "✅ MSI installation completed successfully!" -Color Success
            
            # Post-install configuration
            Select-DeploymentMode
            
            # Find the installed binary
            $possiblePaths = @(
                "${env:ProgramFiles}\EdgeMetrics\edgemetrics-server.exe",
                "${env:ProgramFiles(x86)}\EdgeMetrics\edgemetrics-server.exe",
                "${env:LOCALAPPDATA}\Programs\EdgeMetrics\edgemetrics-server.exe",
                "${env:ProgramFiles}\EdgeMetrics\edgemetrics.exe",
                "${env:ProgramFiles(x86)}\EdgeMetrics\edgemetrics.exe",
                "${env:LOCALAPPDATA}\Programs\EdgeMetrics\edgemetrics.exe"
            )
            $binaryPath = $possiblePaths | Where-Object { Test-Path $_ } | Select-Object -First 1
            
            if ($Mode -eq "service" -and $binaryPath) {
                Install-WindowsService -BinaryPath $binaryPath
            }
            
            return $true
        } else {
            Write-ColorHost "❌ MSI installation failed with exit code: $($process.ExitCode)" -Color Error
            Write-Host "Check log at: $env:TEMP\EdgeMetrics-install.log" -ForegroundColor Gray
            return $false
        }
    }
    catch {
        Write-ColorHost "❌ MSI installation error: $($_.Exception.Message)" -Color Error
        return $false
    }
    finally {
        if (Test-Path $tempMsi) {
            Remove-Item $tempMsi -Force -ErrorAction SilentlyContinue
        }
    }
}

# Function to install via EXE
function Install-ViaEXE {
    param($Asset)
    
    Write-ColorHost "📦 Installing via NSIS installer..." -Color Info
    
    $tempExe = Join-Path $env:TEMP $Asset.name
    
    if (-not (Download-File -Url $Asset.browser_download_url -OutputPath $tempExe -Description "NSIS installer")) {
        return $false
    }
    
    Write-ColorHost "🔧 Installing NSIS package..." -Color Warning
    
    try {
        $exeArgs = @("/S")  # Silent install
        
        if ($InstallDir) {
            $exeArgs += "/D=`"$InstallDir`""
        }
        
        $process = Start-Process -FilePath $tempExe -ArgumentList $exeArgs -Wait -PassThru -NoNewWindow
        
        if ($process.ExitCode -eq 0) {
            Write-ColorHost "✅ NSIS installation completed successfully!" -Color Success
            
            # Post-install configuration
            Select-DeploymentMode
            
            # Find the installed binary
            $possiblePaths = @(
                "${env:ProgramFiles}\EdgeMetrics\edgemetrics-server.exe",
                "${env:ProgramFiles(x86)}\EdgeMetrics\edgemetrics-server.exe",
                "${env:LOCALAPPDATA}\Programs\EdgeMetrics\edgemetrics-server.exe",
                "${env:ProgramFiles}\EdgeMetrics\edgemetrics.exe",
                "${env:ProgramFiles(x86)}\EdgeMetrics\edgemetrics.exe",
                "${env:LOCALAPPDATA}\Programs\EdgeMetrics\edgemetrics.exe"
            )
            $binaryPath = $possiblePaths | Where-Object { Test-Path $_ } | Select-Object -First 1
            
            if ($Mode -eq "service" -and $binaryPath) {
                Install-WindowsService -BinaryPath $binaryPath
            }
            
            return $true
        } else {
            Write-ColorHost "❌ NSIS installation failed with exit code: $($process.ExitCode)" -Color Error
            return $false
        }
    }
    catch {
        Write-ColorHost "❌ NSIS installation error: $($_.Exception.Message)" -Color Error
        return $false
    }
    finally {
        if (Test-Path $tempExe) {
            Remove-Item $tempExe -Force -ErrorAction SilentlyContinue
        }
    }
}

# Function to create desktop shortcut
function New-DesktopShortcut {
    if ($NoDesktopShortcut) {
        return
    }
    
    Write-ColorHost "🔗 Creating desktop shortcut..." -Color Warning
    
    $desktopPath = [Environment]::GetFolderPath("Desktop")
    $shortcutPath = Join-Path $desktopPath "EdgeMetrics.lnk"
    
    $possiblePaths = @(
        "${env:ProgramFiles}\EdgeMetrics\edgemetrics-server.exe",
        "${env:ProgramFiles(x86)}\EdgeMetrics\edgemetrics-server.exe",
        "${env:LOCALAPPDATA}\Programs\EdgeMetrics\edgemetrics-server.exe",
        "${env:ProgramFiles}\EdgeMetrics\edgemetrics.exe",
        "${env:ProgramFiles(x86)}\EdgeMetrics\edgemetrics.exe",
        "${env:LOCALAPPDATA}\Programs\EdgeMetrics\edgemetrics.exe"
    )
    
    $executablePath = $possiblePaths | Where-Object { Test-Path $_ } | Select-Object -First 1
    
    if ($executablePath) {
        try {
            $shell = New-Object -ComObject WScript.Shell
            $shortcut = $shell.CreateShortcut($shortcutPath)
            $shortcut.TargetPath = $executablePath
            $shortcut.Description = "ML model performance analyzer for edge devices"
            $shortcut.WorkingDirectory = Split-Path $executablePath -Parent
            $shortcut.Save()
            Write-ColorHost "✅ Created desktop shortcut" -Color Success
        }
        catch {
            Write-ColorHost "⚠️  Could not create desktop shortcut: $($_.Exception.Message)" -Color Warning
        }
    }
}

# Function to verify installation
function Test-Installation {
    Write-ColorHost "🔍 Verifying installation..." -Color Warning
    
    $possiblePaths = @(
        "${env:ProgramFiles}\EdgeMetrics\edgemetrics-server.exe",
        "${env:ProgramFiles(x86)}\EdgeMetrics\edgemetrics-server.exe",
        "${env:LOCALAPPDATA}\Programs\EdgeMetrics\edgemetrics-server.exe",
        "${env:ProgramFiles}\EdgeMetrics\edgemetrics.exe",
        "${env:ProgramFiles(x86)}\EdgeMetrics\edgemetrics.exe",
        "${env:LOCALAPPDATA}\Programs\EdgeMetrics\edgemetrics.exe"
    )
    
    $installedPath = $possiblePaths | Where-Object { Test-Path $_ } | Select-Object -First 1
    
    if ($installedPath) {
        Write-ColorHost "✅ Installation verified: $installedPath" -Color Success
        return $true
    } else {
        try {
            $registryPaths = @(
                "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*",
                "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*"
            )
            
            foreach ($path in $registryPaths) {
                $installed = Get-ItemProperty $path -ErrorAction SilentlyContinue | 
                             Where-Object { $_.DisplayName -like "*EdgeMetrics*" }
                if ($installed) {
                    Write-ColorHost "✅ Installation verified via registry" -Color Success
                    return $true
                }
            }
        } catch {
            # Registry check failed, continue
        }
        
        Write-ColorHost "⚠️  Installation verification failed - executable not found" -Color Warning
        return $false
    }
}

# Main installation logic
Write-ColorHost "🔧 Starting EdgeMetrics installation..." -Color Info
Write-Host ""

$installSuccess = $false
$installMethod = ""

# Try MSI first (preferred)
if ($msiAsset -and (Install-ViaMSI -Asset $msiAsset)) {
    $installMethod = "MSI Package"
    $installSuccess = $true
}
# Fallback to EXE
elseif ($exeAsset -and (Install-ViaEXE -Asset $exeAsset)) {
    $installMethod = "NSIS Installer"
    $installSuccess = $true
}
else {
    Write-Host ""
    Write-ColorHost "❌ All installation methods failed!" -Color Error
    Write-Host ""
    Write-Host "Manual installation options:" -ForegroundColor Yellow
    Write-Host "1. Download from: $($response.html_url)" -ForegroundColor Gray
    Write-Host "2. Run as Administrator if you haven't" -ForegroundColor Gray
    Write-Host "3. Check Windows Event Viewer for detailed errors" -ForegroundColor Gray
    Write-Host "4. Report issue: https://github.com/EdgeWardIO/EdgeMetrics/issues" -ForegroundColor Gray
    exit 1
}

# Verify installation
Test-Installation | Out-Null

# Create desktop shortcut
New-DesktopShortcut

# Success message
Write-Host ""
Write-ColorHost "🎉 Installation completed successfully!" -Color Success
Write-ColorHost "Installation method: $installMethod" -Color Info
Write-Host ""

if ($Mode -eq "service") {
    Write-ColorHost "Service Mode - EdgeMetrics is running as a Windows service" -Color Header
    Write-ColorHost "✅ Web interface: http://$Host`:$Port" -Color Success
    Write-Host ""
    Write-ColorHost "Service Management:" -Color Header
    Write-Host "  • Status: Get-Service -Name EdgeMetrics" -ForegroundColor White
    Write-Host "  • Start:  Start-Service -Name EdgeMetrics" -ForegroundColor White
    Write-Host "  • Stop:   Stop-Service -Name EdgeMetrics" -ForegroundColor White
    Write-Host "  • Logs:   Get-EventLog -LogName Application -Source EdgeMetrics" -ForegroundColor White
} else {
    Write-ColorHost "Single Binary Mode - Manual start/stop" -Color Header
    Write-ColorHost "How to use EdgeMetrics:" -Color Header
    Write-Host "  • Start server: edgemetrics-server server start" -ForegroundColor White
    Write-Host "  • Custom port:  edgemetrics-server server start --port 9000" -ForegroundColor White
    Write-Host "  • Help: edgemetrics-server --help" -ForegroundColor White
    Write-Host "  • Version: edgemetrics-server --version" -ForegroundColor White
    Write-Host ""
    Write-ColorHost "After starting, web interface available at: http://$Host`:$Port" -Color Success
}

Write-Host ""
Write-ColorHost "CLI Commands:" -Color Header
Write-Host "  • Analyze model: edgemetrics-server analyze model.onnx --hardware cpu" -ForegroundColor White
Write-Host "  • Compare models: edgemetrics-server compare model1.onnx model2.onnx --hardware gpu" -ForegroundColor White
Write-Host "  • List hardware: edgemetrics-server hardware list" -ForegroundColor White
Write-Host ""
Write-ColorHost "Documentation:" -Color Header
Write-Host "  • GitHub: https://github.com/EdgeWardIO/EdgeMetrics" -ForegroundColor White
Write-Host "  • API Docs: http://$Host`:$Port/docs (when server is running)" -ForegroundColor White
Write-Host ""
Write-ColorHost "Support:" -Color Header
Write-Host "  • Issues: https://github.com/EdgeWardIO/EdgeMetrics/issues" -ForegroundColor White