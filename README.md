# Time-Management

Minimal starter implementation matching the requested architecture:

- `data/` → JSON files (`accounts.json`, `profiles.json`)
- `core` module → shared **data classes** and **program classes** for desktop + android
- `desktop` module → desktop **GUI classes** (Swing)
- `android` module → real Android app with `MainActivity` that uses the shared core logic

## Implemented requirements

- Desktop Google OAuth 2.0 sign-in flow with browser consent, access tokens, refresh tokens, and encrypted local session storage
- Desktop Microsoft OAuth 2.0 sign-in flow with browser consent, access tokens, refresh tokens, and encrypted local session storage
- Shared Google identity/session models so platform-specific sign-in flows can hand real OAuth identities into shared core logic
- Shared Microsoft identity/session models so platform-specific sign-in flows can hand real OAuth identities into shared core logic
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

The Android module is now a real Gradle Android app module. It currently demonstrates the shared profile-management flow, and can be extended later with platform-native Google sign-in.

## Microsoft OAuth desktop setup

Set a Microsoft OAuth client id before using Microsoft sign-in:

```bash
export TIME_MANAGEMENT_MICROSOFT_CLIENT_ID=your-microsoft-client-id
```

The desktop UI stores the encrypted Microsoft OAuth session separately at:

- `~/.time-management/microsoft-oauth-session.enc`

The Microsoft desktop flow requests these scopes:

- `openid`
- `email`
- `profile`
- `offline_access`
- `User.Read`
- `Files.ReadWrite.AppFolder`

The Android module can likewise be extended later with platform-native Microsoft sign-in while continuing to reuse the shared core logic.

## Run tests

```bash
mvn -pl core test
```

## Run desktop app

```bash
mvn -pl core,desktop -am install -DskipTests
mvn -f desktop/pom.xml -Dexec.mainClass=com.timemanagement.desktop.gui.DesktopApp exec:java
```

## Run the Android app in Android Studio

Open the repository root in Android Studio as a Gradle project. After **Sync Project with Gradle Files**, Android Studio imports:

- `:core` — shared Java library (data classes, profile/login managers)
- `:android` — real Android app (`com.timemanagement.android`)
- `:desktop` — desktop Java application (`com.timemanagement.desktop.gui.DesktopApp`)

Once sync succeeds, Android Studio generates the Android **app** run configuration from the imported `:android` module. Connect a device or start an emulator, then press **Run**. The app lets you:

1. Enter any account ID and press **Use Account**
2. Add profiles (name + type) associated with that account
3. See all profiles for the active account in a scrollable list

Data is persisted in the app's internal files directory at `/data/data/com.timemanagement.android/files/data/` (visible in Device File Explorer).

> **Tip:** This repository no longer checks in a shared Android `.run` file because the generated module name is IDE-specific. If the Android run target is missing, run **File → Sync Project with Gradle Files** and Android Studio will recreate it from the Gradle model. The first sync also needs network access to Google Maven so Android Gradle Plugin artifacts can be downloaded.

## Run the desktop app in Android Studio

Use the checked-in **Desktop App** Gradle run configuration. It runs `:desktop:run`, uses the Gradle Java 17 toolchain automatically, and sets the working directory to the repository root so the existing `data/` directory resolves correctly.

## Generate desktop UI screenshot

```bash
mvn -pl core,desktop -am install -DskipTests
mvn -f desktop/pom.xml -Dexec.mainClass=com.timemanagement.desktop.gui.DesktopApp -Dexec.args="--screenshot /tmp/time-management-desktop-ui.png" exec:java
```
