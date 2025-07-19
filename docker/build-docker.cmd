@echo off
REM Docker Build Script für das Marketplace System

echo =========================================
echo Docker Build für SCB Marketplace System
echo =========================================

REM Prüfe ob Docker läuft
docker info >nul 2>&1
if errorlevel 1 (
    echo FEHLER: Docker läuft nicht!
    echo Bitte starte Docker Desktop und versuche es erneut.
    pause
    exit /b 1
)

REM Prüfe ob JAR existiert
if not exist "target\marktsimulation-1.0.0-jar-with-dependencies.jar" (
    echo FEHLER: JAR nicht gefunden!
    echo Führe erst 'build clean install' aus
    pause
    exit /b 1
)

REM Wechsle ins docker Verzeichnis
cd docker

echo.
echo Baue Docker Images...
echo.

REM Baue Images mit docker-compose
docker-compose build

if %errorlevel% equ 0 (
    echo.
    echo =========================================
    echo Build erfolgreich!
    echo.
    echo Starte das System mit:
    echo   cd docker ^&^& docker-compose up
    echo.
    echo Oder im Hintergrund:
    echo   cd docker ^&^& docker-compose up -d
    echo.
    echo Stoppe das System mit:
    echo   cd docker ^&^& docker-compose down
    echo =========================================
) else (
    echo.
    echo FEHLER: Build fehlgeschlagen!
    pause
    exit /b 1
)

cd ..
pause