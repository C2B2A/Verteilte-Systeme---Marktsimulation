# 🚀 Setup-Anleitung für das SCB Marketplace Projekt

## Voraussetzungen
- **Java 17** oder höher installiert ([Download](https://adoptium.net/))
- **Git** installiert
- Keine Maven-Installation nötig! (Maven Wrapper ist dabei)

## 1️⃣ Projekt klonen
```bash
git clone <repository-url>
cd Verteilte-Systeme---Marktsimulation
```

## 2️⃣ Projekt bauen

### Windows:
```bash
mvnw.cmd clean install
```

### Mac/Linux:
```bash
chmod +x mvnw    # Nur beim ersten Mal nötig!
./mvnw clean install
```

**Beim ersten Mal:** Der Maven Wrapper lädt automatisch Maven herunter (~10MB). Kann kurz dauern, danach sollte alles passen.

## 3️⃣ Projekt ausführen
```bash
# Marketplace starten
java -jar target/marktsimulation-1.0.0-jar-with-dependencies.jar --mode=marketplace --instance=1

# Seller starten (in neuem Terminal)
java -jar target/marktsimulation-1.0.0-jar-with-dependencies.jar --mode=seller --instance=1
```

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
```

## 🔧 Entwicklung

### Code ändern und testen
```bash
# Nach Änderungen neu bauen
mvnw.cmd clean compile   # Windows
./mvnw clean compile     # Mac/Linux

# Tests ausführen (wenn vorhanden)
mvnw.cmd test           # Windows
./mvnw test             # Mac/Linux
```

---
**Tipp:** Alle Maven-Befehle funktionieren mit dem Wrapper genauso wie mit normalem Maven, nur mit `mvnw` bzw. `mvnw.cmd` statt `mvn`.