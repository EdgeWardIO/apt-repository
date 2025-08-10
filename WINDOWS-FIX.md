# 🔧 Windows Deployment Fix

## Problem
The JAR requires `etcd` binary which is not available on Windows by default.

## 🚀 **QUICK SOLUTION** (Recommended)

### Download etcd for Windows
```cmd
# Download etcd Windows binary
curl -L -o etcd-v3.5.9-windows-amd64.zip https://github.com/etcd-io/etcd/releases/download/v3.5.9/etcd-v3.5.9-windows-amd64.zip

# Extract it
powershell -command "Expand-Archive etcd-v3.5.9-windows-amd64.zip ."

# Add to current directory PATH
set PATH=%PATH%;%CD%\etcd-v3.5.9-windows-amd64

# Verify etcd is available
etcd --version

# Now run the JAR
java -jar multisite-sequential-poc.jar
```

## 🛠️ **Alternative Solutions**

### Option 1: Use Chocolatey (if available)
```cmd
choco install etcd
java -jar multisite-sequential-poc.jar
```

### Option 2: Manual Binary Download
1. Go to: https://github.com/etcd-io/etcd/releases/tag/v3.5.9
2. Download: `etcd-v3.5.9-windows-amd64.zip`
3. Extract `etcd.exe` to same folder as JAR
4. Run: `java -jar multisite-sequential-poc.jar`

### Option 3: Use WSL (Windows Subsystem for Linux)
```bash
# In WSL
wget https://github.com/etcd-io/etcd/releases/download/v3.5.9/etcd-v3.5.9-linux-amd64.tar.gz
tar -xzf etcd-v3.5.9-linux-amd64.tar.gz
sudo cp etcd-v3.5.9-linux-amd64/etcd /usr/local/bin/
java -jar multisite-sequential-poc.jar
```

## ✅ **After Fix - Expected Output**
```
Starting Multi-Site Sequential Number Generation POC...
🔄 Starting external etcd cluster (3 nodes)...
✅ etcd cluster started successfully
🚀 Starting Site-A Formatter on port 8081...
🚀 Starting Site-B Formatter on port 8082...
🚀 Starting Site-A Sequence Service on port 8083...
🚀 Starting Site-B Sequence Service on port 8084...
🚀 Starting Dashboard on port 8080...
🎉 Multi-Site Sequential Number POC Started Successfully!
📊 Dashboard: http://localhost:8080
```

## 🔍 **Verify Everything Works**
```cmd
# Check dashboard
curl http://localhost:8080/health

# Check services
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health

# Test sequence generation
curl -X POST http://localhost:8081/api/generate-invoices -H "Content-Type: application/json" -d "{\"partitionId\":\"CORP-A\",\"count\":3,\"invoiceType\":\"periodic\",\"customerType\":\"STANDARD\"}"
```

## 📱 **For Demo Presentation**
Once running, use the Web Dashboard at http://localhost:8080 for:
- Live sequence generation testing
- Cross-site coordination demonstration  
- Gap filling validation
- Real-time monitoring