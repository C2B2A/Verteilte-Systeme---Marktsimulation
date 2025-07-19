# Docker Setup for Marketplace System

## Requirements
- Docker Desktop installed and startet
- Project has to have been built with Maven prior

## Quick Start

### 1. Build project (if not already built)
```bash
# Windows
build clean install

# Linux/Mac
./mvnw clean install
```

### 2. Build docker images
```bash
# Windows
build-docker.cmd

# Linux/Mac
chmod +x build-docker.sh
./build-docker.sh
```

### 3. Start system
```bash
cd docker
docker-compose up
```

Or in background
```bash
cd docker
docker-compose up -d
```

### 4. Stop system
```bash
cd docker
docker-compose down
```

## Docker architecture

System starts automatically:
- 5 sellers (S1-S5) on ports 5556-5560
- 2 marketplaces (M1:5570, M2:5571)
- 1 customer (C1)

All containers are in the same docker network and can communicate with each other.

## Container-Overview

| Container | Service | Port | Description |
|-----------|---------|------|--------------|
| seller-s1 | Seller S1 | 5556 | products: PA, PB |
| seller-s2 | Seller S2 | 5557 | products: PC, PD |
| seller-s3 | Seller S3 | 5558 | products: PC, PE |
| seller-s4 | Seller S4 | 5559 | products: PD, PE |
| seller-s5 | Seller S5 | 5560 | products: PF, PB |
| marketplace-m1 | Marketplace M1 | 5570 | receives Customer-orders|
| marketplace-m2 | Marketplace M2 | 5571 | receives Customer-orders |
| customer-c1 | Customer C1 | - | sends orders |

## Show Logs

All Logs:
```bash
docker-compose logs -f
```

Only one service
```bash
docker-compose logs -f seller1
docker-compose logs -f marketplace1
docker-compose logs -f customer1
```

## Start individual containers
```bash
docker-compose restart seller1
docker-compose restart marketplace1
```

## Change configuration

The configuration is loaded from `config/config.properties`.
After changes the containers must be restartet:

```bash
docker-compose restart
```

## Troubleshooting

**Problem:** Port already used
```
Error: bind: address already in use
```
**Solution:** Stop lokal Java version or change the ports in docker-compose.yml

**Problem:** Container don't start
```bash
# Zeige Status
docker-compose ps

# Zeige Logs
docker-compose logs [service-name]
```