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

    clean: deletes old build files
    install: compiles, tests and build the JAR files

Why build.cmd on windows?

    Simplifies Maven call
    Prevents "multiModuleProjectDirectory" error

---
**Tip:** All Maven commands work with the wrapper just like with normal Maven, only with `mvnw` or. `mvnw.cmd` instead of `mvn`.