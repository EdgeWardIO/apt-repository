@echo off
setlocal enabledelayedexpansion
title Sequential Number HA POC - Startup
color 0A

echo.
echo    ████████╗███████╗██████╗ ██╗   ██╗███████╗███╗   ██╗ ██████╗███████╗
echo    ██╔════╝██╔════╝██╔══██╗██║   ██║██╔════╝████╗  ██║██╔════╝██╔════╝
echo    ███████╗█████╗  ██████╔╝██║   ██║█████╗  ██╔██╗ ██║██║     █████╗  
echo    ╚════██║██╔══╝  ██╔═══╝ ██║   ██║██╔══╝  ██║╚██╗██║██║     ██╔══╝  
echo    ███████║███████╗██║     ╚██████╔╝███████╗██║ ╚████║╚██████╗███████╗
echo    ╚══════╝╚══════╝╚═╝      ╚═════╝ ╚══════╝╚═╝  ╚═══╝ ╚═════╝╚══════╝
echo.
echo           Global Sequential Invoice Number Generation POC
echo                     High Availability Demonstration
echo                           Portable Edition v1.0
echo.
echo ==================================================================

:: Set environment variables
set JAVA_OPTS=-Xms512m -Xmx2g -Dfile.encoding=UTF-8

:: Check prerequisites
echo [1/5] Checking prerequisites...

:: Check Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Java is NOT installed or not in PATH
    echo.
    echo Please install Java 17 or higher from:
    echo https://adoptium.net/temurin/releases/
    echo.
    pause
    exit /b 1
)
echo ✅ Java found

:: Check Maven
call mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Maven is NOT installed or not in PATH
    echo.
    echo Please install Apache Maven from:
    echo https://maven.apache.org/download.cgi
    echo.
    pause
    exit /b 1
)
echo ✅ Maven found

:: Create data directories
echo.
echo [2/5] Preparing data directories...
if not exist "data" mkdir "data"
if not exist "data\logs" mkdir "data\logs"
if not exist "data\etcd-cluster" mkdir "data\etcd-cluster"
echo ✅ Data directories ready

:: Check ports
echo.
echo [3/5] Checking port availability...
netstat -an | findstr ":8080" >nul
if %errorlevel% equ 0 (
    echo ❌ Port 8080 is already in use
    echo Please close the application using port 8080
    pause
    exit /b 1
)
echo ✅ Port 8080 is available

:: Build application
echo.
echo [4/5] Building application...
echo This may take a few minutes on first run (downloading dependencies)...
call mvn clean compile -q
if %errorlevel% neq 0 (
    echo ❌ Build failed
    echo Please check the Maven output above for errors
    pause
    exit /b 1
)
echo ✅ Application built successfully

:: Start application
echo.
echo [5/5] Starting Sequential Number Generation POC...
echo.

echo Starting Spring Boot application...
echo Profile: POC (single-node etcd)
echo Dashboard: http://localhost:8080/dashboard
echo API Base: http://localhost:8080/api/v1
echo.

start "Sequential Number POC" cmd /k "title Sequential Number POC [RUNNING] && color 0B && call mvn spring-boot:run -Dspring.profiles.active=poc"

:: Wait for application to start
echo Waiting for application to start...
timeout /t 15 /nobreak >nul

:: Test if application is running
echo Testing application startup...
for /l %%i in (1,1,30) do (
    curl -s http://localhost:8080/actuator/health >nul 2>&1
    if !errorlevel! equ 0 (
        echo ✅ Application is running!
        goto :app_started
    )
    timeout /t 2 /nobreak >nul
    echo Waiting for startup... (%%i/30)
)

echo ⚠️ Application may still be starting...
:app_started

echo.
echo ==================================================================
echo 🎉 SEQUENTIAL NUMBER GENERATION POC IS READY! 🎉
echo ==================================================================
echo.
echo 🌐 ACCESS POINTS:
echo    Main Dashboard:     http://localhost:8080/dashboard
echo    API Documentation:  http://localhost:8080/actuator
echo    Health Check:       http://localhost:8080/actuator/health
echo    System Statistics:  http://localhost:8080/api/v1/sequence/stats
echo.
echo 🚀 QUICK START EXAMPLES:
echo.
echo    Generate Sequence:
echo    curl "http://localhost:8080/api/v1/sequence/next?siteId=site-1&partitionId=partition-a&invoiceType=on-cycle"
echo.
echo    View Statistics:
echo    curl "http://localhost:8080/api/v1/sequence/stats"
echo.
echo    Run Basic Demo:
echo    curl -X POST "http://localhost:8080/api/v1/demo/basic"
echo.
echo 🎭 DEMONSTRATION OPTIONS:
echo    [1] Open Dashboard in Browser
echo    [2] Run Basic Demo
echo    [3] Run Concurrent Test
echo    [4] Run Gap Management Demo
echo    [5] Run Load Test
echo    [6] Just keep running
echo.
set /p choice="Select option (1-6): "

if "%choice%"=="1" (
    echo Opening dashboard...
    start http://localhost:8080/dashboard
)
if "%choice%"=="2" (
    echo Running basic demo...
    curl -X POST "http://localhost:8080/api/v1/demo/basic"
    start http://localhost:8080/dashboard
)
if "%choice%"=="3" (
    echo Running concurrent test...
    curl -X POST "http://localhost:8080/api/v1/demo/concurrent"
    start http://localhost:8080/dashboard
)
if "%choice%"=="4" (
    echo Running gap management demo...
    curl -X POST "http://localhost:8080/api/v1/demo/gaps"
    start http://localhost:8080/dashboard
)
if "%choice%"=="5" (
    echo Running load test...
    curl -X POST "http://localhost:8080/api/v1/demo/load-test"
    start http://localhost:8080/dashboard
)

if not "%choice%"=="6" (
    timeout /t 3 /nobreak >nul
    start http://localhost:8080/dashboard
)

echo.
echo ==================================================================
echo  📊 The POC is now running and accessible via web browser
echo  📈 Monitor real-time sequence generation on the dashboard
echo  🔧 Use the web interface to generate sequences and run demos
echo  🛑 Close the application window to stop the POC
echo ==================================================================
echo.
echo Application is running... Press any key to open dashboard
pause >nul
start http://localhost:8080/dashboard

echo.
echo Thanks for trying the Sequential Number Generation POC! 👋
echo The application will continue running in the background.
echo Close the "Sequential Number POC [RUNNING]" window to stop it.
echo.
pause