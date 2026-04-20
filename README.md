# Time-Management

Minimal starter implementation matching the requested architecture:

- `data/` → JSON files (`accounts.json`, `profiles.json`)
- `core` module → shared **data classes** and **program classes** for desktop + android
- `desktop` module → desktop **GUI classes** (Swing)
- `android` module → real Android app with `MainActivity` that uses the shared core logic

## Implemented requirements

- Desktop and Android both default to local storage for profiles/accounts
- Desktop storage menu with **Local Storage**, **Connect Google Drive**, and **Connect Microsoft Drive** pages
- Android storage menu with **Local Storage**, **Connect Google Drive**, and **Connect Microsoft Drive** pages
- Desktop Google OAuth 2.0 browser sign-in flow with access tokens, refresh tokens, and encrypted local session storage
- Desktop Microsoft OAuth 2.0 browser sign-in flow with access tokens, refresh tokens, and encrypted local session storage
- Shared Google identity/session models so platform-specific sign-in flows can hand real OAuth identities into shared core logic
- Shared Microsoft identity/session models so platform-specific sign-in flows can hand real OAuth identities into shared core logic
- Multiple profiles per signed-in login
- Shared non-GUI logic across desktop and android modules

## Default local storage behavior

Both apps now start in a local-storage mode automatically:

- Desktop stores profile/account JSON in `data/`
- Android stores profile/account JSON in `/data/data/com.timemanagement.android/files/data/`

Cloud connections are optional and live behind each app's storage menu.

## Google OAuth desktop setup

The desktop **Connect Google Drive** page now walks through the setup process and lets you paste a Google client id directly into the UI. You can still prefill it with:

```bash
export TIME_MANAGEMENT_GOOGLE_CLIENT_ID=your-desktop-client-id.apps.googleusercontent.com
```

The desktop UI asks for a credential passphrase on the Google Drive page. That passphrase encrypts the saved OAuth session at:

- `~/.time-management/google-oauth-session.enc`

The desktop flow requests these scopes:

- `openid`
- `email`
- `profile`
- `https://www.googleapis.com/auth/drive.file`

The Android **Connect Google Drive** menu page now documents the OAuth setup values the mobile app needs while leaving local storage as the default mode.

## Microsoft OAuth desktop setup

The desktop **Connect Microsoft Drive** page likewise accepts a client id directly in the UI, or you can prefill it with:

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

The Android **Connect Microsoft Drive** menu page likewise captures the client-id setup values while the app continues using local storage by default.

## Run tests

```bash
mvn -pl core test
```

## Run desktop app

```bash
./gradlew :desktop:runDesktopApp
```

## Build desktop app

```bash
./gradlew :desktop:buildDesktopApp
```

## Run the Android app in Android Studio

Open the repository root in Android Studio as a Gradle project. After **Sync Project with Gradle Files**, Android Studio imports:

- `:core` — shared Java library (data classes, profile/login managers)
- `:android` — real Android app (`com.timemanagement.android`)
- `:desktop` — desktop Java application (`com.timemanagement.desktop.gui.DesktopApp`)

Once sync succeeds, Android Studio exposes the checked-in Gradle run configurations:

- **Android App Build** → `:android:buildAndroidApp`
- **Android App** → `:android:runAndroidApp`
- **Desktop App Build** → `:desktop:buildDesktopApp`
- **Desktop App** → `:desktop:runDesktopApp`

For the Android run task, connect a device or start an emulator first. The task installs the debug build and launches `MainActivity` through `adb`. The app lets you:

1. Start in **Local storage** automatically
2. Add profiles (name + type) associated with the default local-storage account
3. Use the app menu to open the Google Drive or Microsoft Drive setup pages
4. See all profiles for the active storage account in a scrollable list

Data is persisted in the app's internal files directory at `/data/data/com.timemanagement.android/files/data/` (visible in Device File Explorer).

> **Tip:** The Android and desktop IDE targets are now Gradle task configurations, so they do not depend on IDE-generated module IDs. The first Gradle sync still needs network access to Google Maven so Android Gradle Plugin artifacts can be downloaded.

## Run the desktop app in Android Studio

Use the checked-in **Desktop App** and **Desktop App Build** Gradle configurations. They run `:desktop:runDesktopApp` and `:desktop:buildDesktopApp`, use the Gradle Java 17 toolchain automatically, and keep the working directory at the repository root so the existing `data/` directory resolves correctly. Once the app opens, use the **Storage** menu to switch between the local storage page and the two cloud-connection setup pages.

## Build Android app

```bash
./gradlew :android:buildAndroidApp
```

## Run Android app

```bash
./gradlew :android:runAndroidApp
```

`runAndroidApp` requires a connected device or emulator plus `adb` available from `ANDROID_SDK_ROOT`, `ANDROID_HOME`, or `local.properties`.

## Generate desktop UI screenshot

```bash
./gradlew :desktop:runDesktopApp --args="--screenshot /tmp/time-management-desktop-ui.png"
```
