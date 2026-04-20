# Time-Management

Minimal starter implementation matching the requested architecture:

- `data/` → JSON files (`accounts.json`, `profiles.json`)
- `core` module → shared **data classes** and **program classes** for desktop + android
- `desktop` module → desktop **GUI classes** (Swing)
- `android` module → android-specific **GUI placeholder classes** that reuse shared core logic

## Implemented requirements

- Desktop Google OAuth 2.0 sign-in flow with browser consent, access tokens, refresh tokens, and encrypted local session storage
- Shared Google identity/session models so platform-specific sign-in flows can hand real OAuth identities into shared core logic
- Multiple profiles per signed-in login
- Shared non-GUI logic across desktop and android modules

## Google OAuth desktop setup

Set a desktop OAuth client id before using Google sign-in:

```bash
export TIME_MANAGEMENT_GOOGLE_CLIENT_ID=your-desktop-client-id.apps.googleusercontent.com
```

The desktop UI now asks for a credential passphrase. That passphrase encrypts the saved OAuth session at:

- `~/.time-management/google-oauth-session.enc`

The desktop flow requests these scopes:

- `openid`
- `email`
- `profile`
- `https://www.googleapis.com/auth/drive.file`

The Android module is still a placeholder module in this repository, but it now accepts real `GoogleIdentity` / `GoogleOAuthSession` objects from an Android-specific sign-in flow so a real Android app can plug in platform-native credential storage separately.

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
