# User manual for the marketplace

## Requirements
- **Java 17** or higher installed ([Download](https://adoptium.net/))
- **Java Development Kit - JDK** has to be installed
- **Git** installed
- No Maven installation needed (Maven wrappers are implemented)

## Quick Start ------------------------------
## Clone project
git clone <repository-url>
cd Verteilte-Systeme---Marktsimulation

## Build project
**If a JDK is installed or just has been installed the IDE has to be restartet. Then wait until "Java: Ready" appears!**
### Windows:
```cmd
build clean install
```
### Mac/Linux:
```bash/cmd?
chmod +x mvnw     #only necessary the first time
./mvnw clean install
```

**First time:** The Maven wrapper automatically installs everything. Can take a short time, after this everything should be set.

## Execute project over terminal
--> start-system.bat
or
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

The target folder is the maven directory.
Never commit this - is in .gitignore, is automatically built
```

## Development

### Change and test code
# rebuild after changes

# Windows (mit build.cmd):
build clean compile     # only compile (fast)
build clean install     # complete build with JAR

# Windows (alternatively):
mvnw.cmd -Dmaven.multiModuleProjectDirectory=%CD% clean compile

# Mac/Linux:
./mvnw clean compile     # only compile
./mvnw clean install     # build completely

# FAQ
What does clean install do?

    clean: Löscht alte Build-Dateien
    install: Kompiliert, testet und erstellt die ausführbare JAR

Why build.cmd on windows?

    Simplifies Maven call
    Prevents "multiModuleProjectDirectory" error

---
**Tip:** All Maven commands work with the wrapper just like with normal Maven, only with `mvnw` or. `mvnw.cmd` instead of `mvn`.