@echo off
REM Docker Build Script for the Marketplace System

echo =========================================
echo Docker Build for SCB Marketplace System
echo =========================================

REM Check if Docker is running
docker info >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker is not running!
    echo Please start Docker Desktop and try again.
    pause
    exit /b 1
)

REM Prüfe ob JAR existiert
if not exist "target\marktsimulation-1.0.0-jar-with-dependencies.jar" (
    echo ERROR: JAR not found!
    echo Please build the JAR file first.
    pause
    exit /b 1
)

REM Wechsle ins docker Verzeichnis
cd docker

echo.
echo BUidling Docker Images...
echo.

REM Baue Images mit docker-compose
docker-compose build

if %errorlevel% equ 0 (
    echo.
    echo =========================================
    echo Build successful!
    echo.
    echo Starte das System mit:
    echo   cd docker ^&^& docker-compose up
    echo.
    echo Or in the background:
    echo   cd docker ^&^& docker-compose up -d
    echo.
    echo Stop the system with:
    echo   cd docker ^&^& docker-compose down
    echo =========================================
) else (
    echo.
    echo ERROR: Build failed!
    pause
    exit /b 1
)

cd ..
pause