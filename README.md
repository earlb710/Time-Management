# Time-Management

Minimal starter implementation matching the requested architecture:

- `data/` → JSON files (`accounts.json`, `profiles.json`)
- `core` module → shared **data classes** and **program classes** for desktop + android
- `desktop` module → desktop **GUI classes** (Swing)
- `android` module → android-specific **GUI placeholder classes** that reuse shared core logic

## Implemented requirements

- Google-account-based sign-in flow (gmail/googlemail validation + deterministic account id)
- Multiple profiles per signed-in login
- Shared non-GUI logic across desktop and android modules

## Run tests

```bash
mvn -pl core test
```

## Run desktop app

```bash
mvn -pl core,desktop -am install -DskipTests
mvn -f desktop/pom.xml -Dexec.mainClass=com.timemanagement.desktop.gui.DesktopApp exec:java
```

## Generate desktop UI screenshot

```bash
mvn -pl core,desktop -am install -DskipTests
mvn -f desktop/pom.xml -Dexec.mainClass=com.timemanagement.desktop.gui.DesktopApp -Dexec.args="--screenshot /tmp/time-management-desktop-ui.png" exec:java
```
