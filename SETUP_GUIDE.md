# 🚀 Setup-Anleitung für das Marketplace Projekt

## Voraussetzungen
- **Java 17** oder höher installiert ([Download](https://adoptium.net/))
- **Java Development Kit - JDK** muss installiert sein
- **Git** installiert
- Keine Maven-Installation nötig! (Maven Wrapper ist dabei)

## Quick Start ------------------------------
## 1️⃣ Projekt klonen
git clone <repository-url>
cd Verteilte-Systeme---Marktsimulation

## 2️⃣ Projekt bauen
**Sofern eine JDK installiert ist oder neu wurde, muss die IDE neu geöffnet werden. Danach warten, bis "Java: Ready" erscheint!**
### Windows:
```cmd
build clean install
```
### Mac/Linux:
```bash/cmd?
chmod +x mvnw    # Nur beim ersten Mal nötig!
./mvnw clean install
```

**Beim ersten Mal:** Der Maven Wrapper lädt automatisch Maven herunter (~10MB). Kann kurz dauern, danach sollte alles passen.

## 3️⃣ Projekt ausführen - über Terminal
```bash
# Marketplace starten
java -jar target/marktsimulation-1.0.0-jar-with-dependencies.jar --mode=marketplace --instance=1

# Seller starten (in neuem Terminal)
java -jar target/marktsimulation-1.0.0-jar-with-dependencies.jar --mode=seller --instance=1
```
------------------------------
## 📁 Projektstruktur
```
Verteilte-Systeme---Marktsimulation/
│
├── 📄 pom.xml                          # Maven Konfiguration
├── 📄 README.md                        # Projektbeschreibung
├── 📄 SETUP_GUIDE.md                   # Setup-Anleitung
├── 📄 build.cmd                        # Windows Build-Helfer
├── 📄 start-system.bat                 # System-Start-Skript
├── 📄 mvnw                             # Maven Wrapper (Linux/Mac)
├── 📄 mvnw.cmd                         # Maven Wrapper (Windows)
├── 📄 .gitignore                       # Git Ignore-Datei
│
├── 📁 .mvn/                            # Maven Wrapper Dateien
│   └── 📁 wrapper/
│       └── 📄 maven-wrapper.properties
│
├── 📁 config/                          # Konfigurationsdateien (MUSS ERSTELLT WERDEN!)
│   └── 📄 config.properties            # Standard-Konfiguration
│
├── 📁 src/                             # Source Code
│   └── 📁 main/
│       └── 📁 java/
│           └── 📁 main/                # Package: main
│               │
│               ├── 📄 MainLauncher.java        # Haupt-Einstiegspunkt
│               ├── 📄 Customer.java            # Kunden-Klasse
│               ├── 📄 TestClient.java          # Test-Client
│               │
│               ├── 📁 marketplace/             # Package: main.marketplace
│               │   ├── 📄 MarketplaceApp.java  # Marketplace Hauptklasse
│               │   ├── 📄 OrderProcessor.java  # Bestellverarbeitung
│               │   └── 📄 SagaManager.java     # SAGA-Verwaltung
│               │
│               ├── 📁 seller/                  # Package: main.seller
│               │   ├── 📄 SellerApp.java       # Seller Hauptklasse
│               │   ├── 📄 Inventory.java       # Lagerbestand-Verwaltung
│               │   ├── 📄 Product.java         # Produkt-Klasse
│               │   └── 📄 TestSimpleSeller.java # Einfacher Test-Seller
│               │
│               ├── 📁 messaging/               # Package: main.messaging
│               │   ├── 📄 MessageHandler.java  # JSON-Verarbeitung
│               │   └── 📄 MessageTypes.java    # Nachrichtentypen
│               │
│               └── 📁 simulation/              # Package: main.simulation
│                   ├── 📄 ConfigLoader.java    # Konfiguration laden
│                   ├── 📄 ErrorSimulator.java  # Fehlersimulation
│                   └── 📄 test.properties      # Test-Konfiguration
│
├── 📁 docker/                          # Docker-Dateien
│   ├── 📄 Dockerfile.marketplace
│   ├── 📄 Dockerfile.seller
│   └── 📄 docker-compose.yml
│
├── 📁 docs/                            # Dokumentation (MUSS ERSTELLT WERDEN!)
│   ├── 📄 Architektur.md              # Architekturbeschreibung
│   ├── 📄 Fehlerbehandlung.md         # Fehlerdokumentation
│   └── 📄 Testkonzept.md              # Testdokumentation
│
└── 📁 target/                          # Maven Build-Output (AUTOMATISCH ERSTELLT)
    └── 📄 marktsimulation-1.0.0-jar-with-dependencies.jar

Der target/ Ordner ist der Maven-Arbeitsbereich
diesen NIE committen - steht in .gitignore, wird beim Build automatisch erstellt
```

## 🔧 Entwicklung

### Code ändern und testen
# Nach Änderungen neu bauen

# Windows (mit build.cmd):
build clean compile     # Nur kompilieren (schnell)
build clean install     # Komplett bauen mit JAR

# Windows (alternativ):
mvnw.cmd -Dmaven.multiModuleProjectDirectory=%CD% clean compile

# Mac/Linux:
./mvnw clean compile     # Nur kompilieren
./mvnw clean install     # Komplett bauen

# FAQ
Was macht clean install?

    clean: Löscht alte Build-Dateien
    install: Kompiliert, testet und erstellt die ausführbare JAR

Unterschied zwischen compile und install?

    compile: Nur Code prüfen/kompilieren (schnell)
    install: Vollständiger Build mit JAR-Erstellung

Warum build.cmd auf Windows?

    Vereinfacht den Maven-Aufruf
    Verhindert den "multiModuleProjectDirectory" Fehler

---
**Tipp:** Alle Maven-Befehle funktionieren mit dem Wrapper genauso wie mit normalem Maven, nur mit `mvnw` bzw. `mvnw.cmd` statt `mvn`.