#!/bin/bash
 
echo "==================================="
echo "SCB Marketplace System Start Script"
echo "==================================="
 
# Pfad zur JAR
JAR="target/marktsimulation-1.0.0-jar-with-dependencies.jar"
 
# Prüfe ob JAR existiert
if [ ! -f "$JAR" ]; then
    echo "FEHLER: JAR nicht gefunden!"
    echo "Bitte zuerst 'mvn clean install' ausführen"
    exit 1
fi
 
echo
echo "Starte 5 Seller..."
echo
 
# Starte Seller S1–S5 auf Ports 5556–5560
java -jar "$JAR" --mode=seller --id=S1 --port=5556 &
sleep 1
java -jar "$JAR" --mode=seller --id=S2 --port=5557 &
sleep 1
java -jar "$JAR" --mode=seller --id=S3 --port=5558 &
sleep 1
java -jar "$JAR" --mode=seller --id=S4 --port=5559 &
sleep 1
java -jar "$JAR" --mode=seller --id=S5 --port=5560 &
 
echo
echo "Warte 3 Sekunden auf Seller-Start..."
sleep 3
 
echo
echo "Starte 2 Marketplaces..."
echo
 
# Starte Marketplaces
java -jar "$JAR" --mode=marketplace --id=M1 &
sleep 1
java -jar "$JAR" --mode=marketplace --id=M2 &
 
echo
echo "Warte 2 Sekunden auf Marketplace-Start..."
sleep 2
 
echo
echo "Starte Customer..."
echo
 
# Starte Customer
java -jar "$JAR" --mode=customer --id=C1 &
 
echo
echo "==================================="
echo "System komplett gestartet!"
echo
echo "Architektur:"
echo "  1 Customer (C1)"
echo "  2 Marketplaces (M1:5570, M2:5571)"
echo "  5 Seller (S1–S5 auf Ports 5556–5560)"
echo
echo "Produktverteilung:"
echo "  S1: PA, PB"
echo "  S2: PC, PD"
echo "  S3: PC, PE"
echo "  S4: PD, PE"
echo "  S5: PF, PB"
echo
echo "Zum Beenden: Ctrl+C oder Fenster schließen"
echo "==================================="
echo