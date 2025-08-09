# User manual and Setup Guide for the marketplace simulation

## Requirements
- **Java 17** or higher installed ([Download](https://adoptium.net/))
- **Java Development Kit - JDK** has to be installed and the IDE restarted afterwards until Java is ready
- **NO Maven installation needed** (Maven wrappers are implemented)

This marketplace simulation was developed and tested using **VSCode**.
**First time:** The Maven wrapper automatically installs everything. It can take a short time, after this everything should be set and the IDE / VSCode restartet until *Java:Ready* is displayed.

## User Manual ------------------------------
This project uses Maven to build. After every change in code it has to be rebuilt using the commands below.
To start the simulation follow these steps (either for Windows or for Mac):
1. build the project (see commands below for details)
2. use the start script to automatically start the simulation (works best with Windows)
    or:
    use the executable JAR files to manually start the simulation (works best with Mac)

There are two files, which support manual change to change how the simulation reacts: 
- config.properties (in the config folder): holds general parameters
- CustomerOrdersConfig: uses a "main switch" to change between generated and predefined orders in the statement:
     **private static final boolean GENERATE_ORDERS = true;** --> true for generated orders and false for predefined orders

## Building the project ---------------------------
**If a JDK is installed or just has been installed the IDE has to be restartet. Then wait until "Java: Ready" appears!**
### Windows:
**IMPORTANT NOTE:**  use **cmd** as terminal - NOT POWERSHELL since this might lead to administrative errors
build clean install
    (is used to compile and build the project - is to be used after every codechange)
## Executing the project AUTOATICALLY via the terminal on Windows
start-system.bat

### Mac (using the standard terminal)
1. optional if the other steps after step 2 don't work directly:
brew install maven 
    (installes Maven lokally)
2. might need restart of the IDE / VSCode
mvn clean install 
    --> BUILD SUCCESS
**After:**
Use maven wrapper (then supposedly Maven doesn't have to be globally installed on the system)
1. Use:
mvn -N io.takari:maven:wrapper
2. Use:
./mvnw clean install
    (this is the command to build the project)
--> BUILD SUCCESS
## Executing the project AUTOMATICALLY after the build on Mac
1. Making the script executable with: 
chmod +x start-system.sh
2. Execute with:
./start-system.sh
3. All processes can be stopped with:
pkill -f marktsimulation 
    it's possible that Ctrl+C is not sufficient

### Executing the project MANUALLY
**To do this the following executable JARs have to be started *seperately* in an own terminal each.**
## 1. Starting the sellers
java -jar target/marktsimulation-1.0.0-jar-with-dependencies.jar --mode=seller --id=S1 --port=5556
 
java -jar target/marktsimulation-1.0.0-jar-with-dependencies.jar --mode=seller --id=S2 --port=5557
 
java -jar target/marktsimulation-1.0.0-jar-with-dependencies.jar --mode=seller --id=S3 --port=5558
 
java -jar target/marktsimulation-1.0.0-jar-with-dependencies.jar --mode=seller --id=S4 --port=5559
 
java -jar target/marktsimulation-1.0.0-jar-with-dependencies.jar --mode=seller --id=S5 --port=5560

## 2. Starting the marketplaces
java -jar target/marktsimulation-1.0.0-jar-with-dependencies.jar --mode=marketplace --id=M1
 
java -jar target/marktsimulation-1.0.0-jar-with-dependencies.jar --mode=marketplace --id=M2

## 3. Starting the customer
java -jar target/marktsimulation-1.0.0-jar-with-dependencies.jar --mode=customer --id=C1


------------------------------


### Development

The target folder is the maven directory.
Never commit this - is in .gitignore, is automatically built

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