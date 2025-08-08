#!/bin/bash
 
echo "==================================="
echo "SCB Marketplace System Start Script"
echo "==================================="
 
# Path to JAR
JAR="target/marktsimulation-1.0.0-jar-with-dependencies.jar"

# Check if JAR exists
if [ ! -f "$JAR" ]; then
    echo "ERROR: JAR not found!"
    echo "Please run 'mvn clean install' first"
    exit 1
fi
 
echo
echo "Starting 5 Sellers..."
echo
 
# Start Seller S1–S5 on Ports 5556–5560
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
echo "Waiting 3 seconds for Seller to start..."
sleep 3

echo
echo "Starting 2 Marketplaces..."
echo

# Start Marketplaces
java -jar "$JAR" --mode=marketplace --id=M1 &
sleep 1
java -jar "$JAR" --mode=marketplace --id=M2 &
 
echo
echo "Waiting 2 seconds for Marketplace to start..."
sleep 2
 
echo
echo "Starting Customer..."
echo
 
# Start Customer
java -jar "$JAR" --mode=customer --id=C1 &
 
echo
echo "==================================="
echo "System completely started!"
echo
echo "Architecture:"
echo "  1 Customer (C1)"
echo "  2 Marketplaces (M1:5570, M2:5571)"
echo "  5 Seller (S1–S5 on Ports 5556–5560)"
echo
echo "Product Distribution:"
echo "  S1: PA, PB"
echo "  S2: PC, PD"
echo "  S3: PC, PE"
echo "  S4: PD, PE"
echo "  S5: PF, PB"
echo
echo "To exit: Ctrl+C or close window"
echo "==================================="
echo