@echo off
echo ===================================
echo SCB Marketplace System Start Script
echo ===================================

REM Pfad zur JAR
set JAR=target\marktsimulation-1.0.0-jar-with-dependencies.jar

REM Prüfe ob JAR existiert
if not exist %JAR% (
    echo FEHLER: JAR nicht gefunden!
    echo Bitte erst 'build clean install' ausführen
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
echo Starte 2 Marketplaces...
echo.

REM Starte Marketplace 1-2
start "Marketplace M1" cmd /k java -jar %JAR% --mode=marketplace --id=M1
timeout /t 1 /nobreak > nul

start "Marketplace M2" cmd /k java -jar %JAR% --mode=marketplace --id=M2

echo.
echo Warte 2 Sekunden auf Marketplace-Start...
timeout /t 2 /nobreak > nul

echo.
echo Starte Customer...
echo.

REM Starte einen Customer
start "Customer C1" cmd /k java -jar %JAR% --mode=customer --id=C1

echo.
echo ===================================
echo System komplett gestartet!
echo.
echo Architektur:
echo   1 Customer (C1)
echo   2 Marketplaces (M1:5570, M2:5571)
echo   5 Seller (S1-S5 auf Ports 5556-5560)
echo.
echo Produktverteilung:
echo   S1: PA, PB
echo   S2: PC, PD
echo   S3: PC, PE
echo   S4: PD, PE
echo   S5: PF, PB
echo.
echo Zum Beenden alle Fenster schließen
echo oder Ctrl+C in den Fenstern
echo ===================================
echo.
pause