#!/bin/bash
# Docker Build Script für das Marketplace System

echo "========================================="
echo "Docker Build für SCB Marketplace System"
echo "========================================="

# Prüfe ob Docker läuft
if ! docker info > /dev/null 2>&1; then
    echo "FEHLER: Docker läuft nicht!"
    echo "Bitte starte Docker Desktop und versuche es erneut."
    exit 1
fi

# Prüfe ob JAR existiert
if [ ! -f "target/marktsimulation-1.0.0-jar-with-dependencies.jar" ]; then
    echo "FEHLER: JAR nicht gefunden!"
    echo "Führe erst './mvnw clean install' aus"
    exit 1
fi

# Wechsle ins docker Verzeichnis
cd docker

echo ""
echo "Baue Docker Images..."
echo ""

# Baue Images mit docker-compose
docker-compose build

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================="
    echo "Build erfolgreich!"
    echo ""
    echo "Starte das System mit:"
    echo "  cd docker && docker-compose up"
    echo ""
    echo "Oder im Hintergrund:"
    echo "  cd docker && docker-compose up -d"
    echo ""
    echo "Stoppe das System mit:"
    echo "  cd docker && docker-compose down"
    echo "========================================="
else
    echo ""
    echo "FEHLER: Build fehlgeschlagen!"
    exit 1
fi