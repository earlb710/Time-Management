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

The Android module is still a placeholder module in this repository, but it now accepts real `GoogleIdentity` / `GoogleOAuthSession` objects from an Android-specific sign-in flow so a real Android app can plug in platform-native credential storage separately.

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

The Android module also accepts real `MicrosoftIdentity` / `MicrosoftOAuthSession` objects so a real Android app can plug in platform-native credential storage separately for Microsoft accounts too.

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

Open the repository root in Android Studio. Android Studio detects `settings.gradle.kts` and imports the Gradle project with two modules:

- `:core` — shared Java library (data classes, profile/login managers)
- `:android` — real Android app (`com.timemanagement.android`)

Android Studio shows the **app** run configuration automatically. Connect a device or start an emulator, then press **Run** to build and deploy the APK. The app lets you:

1. Enter any account ID and press **Use Account**
2. Add profiles (name + type) associated with that account
3. See all profiles for the active account in a scrollable list

Data is persisted in the app's internal files directory at `/data/data/com.timemanagement.android/files/data/` (visible in Device File Explorer).

> **Tip:** If Android Studio does not pick up modules after opening, choose **File → Sync Project with Gradle Files**.

## Run the desktop app in Android Studio

The `.run/Desktop App.run.xml` Maven configuration launches the Swing desktop app. Android Studio also imports the Maven `core` and `desktop` modules, so the shared configuration is available in the run dropdown. Use **Maven** tool window → reload if the `desktop` module is not visible.

## Generate desktop UI screenshot

```bash
mvn -pl core,desktop -am install -DskipTests
mvn -f desktop/pom.xml -Dexec.mainClass=com.timemanagement.desktop.gui.DesktopApp -Dexec.args="--screenshot /tmp/time-management-desktop-ui.png" exec:java
```
