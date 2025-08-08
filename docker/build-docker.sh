#!/bin/bash
# Docker Build Script for das Marketplace System

echo "========================================="
echo "Docker Build for SCB Marketplace System"
echo "========================================="


# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "ERROR: Docker is not running!"
    echo "Please start Docker Desktop and try again."
    exit 1
fi

# Check if JAR exists
if [ ! -f "target/marktsimulation-1.0.0-jar-with-dependencies.jar" ]; then
    echo "ERROR: JAR not found!"
    echo "First run './mvnw clean install'"
    exit 1
fi

# Wechsle ins docker Verzeichnis
cd docker

echo ""
echo "Building Docker Images..."
echo ""

# Baue Images mit docker-compose
docker-compose build

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================="
    echo "Build successful!"
    echo ""
    echo "Start the system with:"
    echo "  cd docker && docker-compose up"
    echo ""
    echo "Or in the background:"
    echo "  cd docker && docker-compose up -d"
    echo ""
    echo "Stop the system with:"
    echo "  cd docker && docker-compose down"
    echo "========================================="
else
    echo ""
    echo "ERROR: Build failed!"
    exit 1
fi