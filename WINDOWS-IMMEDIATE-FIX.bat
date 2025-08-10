@echo off
echo 🔧 IMMEDIATE WINDOWS FIX for etcd path issue
echo.

echo ✅ Step 1: Copying etcd.exe to system location...
if exist "etcd.exe" (
    copy etcd.exe C:\Windows\System32\
    if %errorlevel% equ 0 (
        echo ✅ etcd.exe copied to C:\Windows\System32\
    ) else (
        echo ⚠️  Admin rights needed for system copy, trying local solution...
    )
) else (
    echo ❌ etcd.exe not found in current directory
    echo Please ensure etcd.exe is in the same folder
    pause
    exit /b 1
)

echo.
echo ✅ Step 2: Setting environment variables...
set ETCD_PATH=%CD%\etcd.exe
set PATH=%PATH%;%CD%

echo.
echo ✅ Step 3: Testing etcd availability...
where etcd
if %errorlevel% neq 0 (
    echo ⚠️  etcd not in PATH, using direct path...
    set ETCD_BINARY=%CD%\etcd.exe
) else (
    echo ✅ etcd found in PATH
)

echo.
echo 🚀 Step 4: Starting application with explicit etcd path...
java -Detcd.binary.path="%CD%\etcd.exe" -jar multisite-sequential-poc.jar

pause