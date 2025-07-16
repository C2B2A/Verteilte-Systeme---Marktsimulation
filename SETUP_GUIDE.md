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
├── src/main/java/main/    # Java Source Code
│   ├── marketplace/       # Marketplace-Komponenten
│   ├── seller/           # Seller-Komponenten
│   ├── messaging/        # ZeroMQ Kommunikation
│   └── simulation/       # Fehlersimulation
├── config/               # Konfigurationsdateien
├── docker/              # Docker-Dateien (optional)
└── docs/                # Dokumentation

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