@echo off
chcp 65001 >nul 2>&1
title TradingX Services Launcher

set PROJECT_DIR=D:\myProject\tradingX\hy_docker_app1-main
set JAVA_HOME=D:\tools\jdk-21.0.1
set NODE_DIR=D:\Program Files\nodejs
set PYTHON_DIR=D:\Python312

echo ============================================
echo    TradingX Services Launcher
echo ============================================
echo.


echo [2/3] Starting Backend (port 8080)...
start "Backend" cmd /k "cd /d %PROJECT_DIR%\backend && %JAVA_HOME%\bin\java.exe -jar target\backend-0.0.1-SNAPSHOT.jar"
timeout /t 5 /nobreak >nul

echo [3/3] Starting Frontend (port 3000)...
start "Frontend" cmd /k "cd /d %PROJECT_DIR%\web && set PATH=%NODE_DIR%;%PATH% && npm run dev"

echo.
echo ============================================
echo    All services started!
echo    - Backend:   http://localhost:8080
echo    - Frontend:  http://localhost:3000
echo ============================================
echo.
echo Press any key to exit this window (services will keep running)...
pause >nul
