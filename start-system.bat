@echo off
echo ===================================
echo SCB Marketplace System Start Script
echo ===================================

REM Path to JAR
set JAR=target\marktsimulation-1.0.0-jar-with-dependencies.jar

REM Check if JAR exists
if not exist %JAR% (
    echo ERROR: JAR not found!
    echo Please run 'build clean install' first
    pause
    exit /b 1
)

echo.
echo Starte 5 Seller...
echo.

REM Starte Seller 1-5
start "Seller S1" cmd /k java -jar %JAR% --mode=seller --id=S1 --port=5556
timeout /t 1 /nobreak > nul

start "Seller S2" cmd /k java -jar %JAR% --mode=seller --id=S2 --port=5557
timeout /t 1 /nobreak > nul

start "Seller S3" cmd /k java -jar %JAR% --mode=seller --id=S3 --port=5558
timeout /t 1 /nobreak > nul

start "Seller S4" cmd /k java -jar %JAR% --mode=seller --id=S4 --port=5559
timeout /t 1 /nobreak > nul

start "Seller S5" cmd /k java -jar %JAR% --mode=seller --id=S5 --port=5560

echo.
echo Warte 3 Sekunden auf Seller-Start...
timeout /t 3 /nobreak > nul

echo.
echo Start 2 Marketplaces...
echo.

REM Start Marketplace 1-2
start "Marketplace M1" cmd /k java -jar %JAR% --mode=marketplace --id=M1
timeout /t 1 /nobreak > nul

start "Marketplace M2" cmd /k java -jar %JAR% --mode=marketplace --id=M2

echo.
echo Warte 2 Sekunden auf Marketplace-Start...
timeout /t 2 /nobreak > nul

echo.
echo Start a Customer...
echo.

REM Start a Customer
start "Customer C1" cmd /k java -jar %JAR% --mode=customer --id=C1

echo.
echo ===================================
echo System completely started!
echo.
echo Architecture:
echo   1 Customer (C1)
echo   2 Marketplaces (M1:5570, M2:5571)
echo   5 Seller (S1-S5 auf Ports 5556-5560)
echo.
echo Product Distribution:
echo   S1: PA, PB
echo   S2: PC, PD
echo   S3: PC, PE
echo   S4: PD, PE
echo   S5: PF, PB
echo.
echo To exit, close all windows
echo or press Ctrl+C in the windows
echo ===================================
echo.
pause