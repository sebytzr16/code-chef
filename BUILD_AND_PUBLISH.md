# Building & Publishing Stock Widget

This guide takes you from source code → an APK on your phone → (optionally) a live
listing on the Google Play Store.

---

## 0. Prerequisites

- **Android Studio** (Ladybug 2024.2 or newer) installed.
- The project opened and a successful **Gradle Sync** (Android Studio downloads Gradle
  8.9, the Android Gradle Plugin, the SDK for API 35, and all libraries on first sync).
- A phone with **USB debugging** enabled (Settings → About phone → tap "Build number" 7×
  to unlock Developer options → enable **USB debugging**).

---

## 1. Quick install on your phone (debug build)

This is the fastest way to try it on your device. No signing setup required.

**Option A — run it directly (recommended):**
1. Plug your phone in via USB; accept the "Allow USB debugging?" prompt.
2. In Android Studio, pick your device in the top toolbar dropdown.
3. Press the green **▶ Run**. It builds, installs, and launches the app.

**Option B — build a debug APK and share/install it:**
1. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
2. When it finishes, click **locate** in the notification (or find it at
   `app/build/outputs/apk/debug/app-debug.apk`).
3. Copy that file to your phone (USB, email, Drive…) and tap it to install. You'll need to
   allow "Install from unknown sources" for your file manager.

From the terminal you can also run:
```bash
./gradlew assembleDebug         # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug          # builds AND installs onto a connected device
```

> A **debug** APK is fine for personal use, but it's signed with a temporary debug key —
> you can't publish it, and it shows a "not optimized" profile. For anything you share or
> publish, build a **release** APK (next section).

---

## 2. Create a signed RELEASE APK (for keeping/sharing/sideloading)

A release build is optimized and signed with **your own key**. You create the key once and
reuse it forever (it identifies you as the author — keep it safe!).

### 2.1 Create a signing key (one time)

Android Studio: **Build → Generate Signed App Bundle / APK → APK → Next → Create new…**

Fill in:
- **Key store path:** choose a safe location, e.g. `~/keystores/stock-widget.jks`
- **Password** (keystore) and a **key alias** + **key password** — write these down somewhere
  safe (a password manager). **If you lose them you can never update the app on Play.**
- Validity: 25+ years. Fill in your name/org (can be minimal).

Or from the terminal:
```bash
keytool -genkey -v -keystore stock-widget.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias stock-widget
```

### 2.2 Wire the key into the build (so it's not hard-coded)

Create a file **`keystore.properties`** in the project root (it's already git-ignored —
**never commit it**):
```properties
storeFile=/absolute/path/to/stock-widget.jks
storePassword=your-keystore-password
keyAlias=stock-widget
keyPassword=your-key-password
```

Then add a `signingConfig` to `app/build.gradle.kts`. Near the top of the file:
```kotlin
import java.util.Properties
import java.io.FileInputStream

val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(FileInputStream(f))
}
```
Inside `android { }`:
```kotlin
signingConfigs {
    create("release") {
        if (rootProject.file("keystore.properties").exists()) {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        // (already present) isMinifyEnabled / proguardFiles …
    }
}
```

### 2.3 Build it

Android Studio: **Build → Generate Signed … → APK**, pick **release**, choose your key, Finish.

Or terminal:
```bash
./gradlew assembleRelease       # → app/build/outputs/apk/release/app-release.apk
```

Copy `app-release.apk` to your phone and install. This is the version to keep.

---

## 3. Publishing to the Google Play Store

Play distributes **App Bundles (.aab)**, not APKs. Google re-signs and generates
per-device APKs for you ("Play App Signing").

### 3.1 One-time setup
1. Create a **Google Play Console** account: <https://play.google.com/console> —
   **$25 one-time** registration fee.
2. Complete identity verification (Google now requires this; for personal/individual
   accounts it can take a little while, so start early).

### 3.2 Build the release bundle
```bash
./gradlew bundleRelease          # → app/build/outputs/bundle/release/app-release.aab
```
(Or **Build → Generate Signed App Bundle / APK → Android App Bundle**, signed with your
release key from §2.)

### 3.3 Create the app in Play Console
1. **Create app** → name "Stock Widget", language, **App** (not game), **Free**.
2. Accept the developer agreements.

### 3.4 Fill in the required listing & policy info
Play won't let you publish until these are done (under **Dashboard → Set up your app**):
- **Store listing:** short description, full description, an **app icon (512×512)**, a
  **feature graphic (1024×500)**, and **screenshots** (at least 2 phone screenshots — grab
  them from the running app, including the widget on the home screen).
- **Privacy policy URL** — required. Since the app fetches quotes from Yahoo and stores
  your tickers locally, you need a simple hosted privacy policy page (a GitHub Pages page
  or a free generator works).
- **Data safety** form — declare what data you collect. This app stores your watchlist
  **only on-device** and makes network calls to fetch prices; it collects no personal data
  and has no analytics/ads — declare accordingly.
- **Content rating** questionnaire (it'll come out "Everyone").
- **Target audience**, **Ads** (none), **App access** (no login required), **Government
  apps** (no).

### 3.5 Upload & roll out
1. Go to **Release → Testing → Internal testing** (best first step — installs only for
   testers you list by email, available in minutes).
2. **Create new release** → upload `app-release.aab` → add release notes → **Save → Review
   → Roll out**.
3. Once you're happy, promote to **Closed testing** → **Production**. The **first**
   production submission goes through a Google review that can take a few days.

### 3.6 Versioning for future updates
Each upload must increase the version. In `app/build.gradle.kts` bump:
```kotlin
versionCode = 2          // must increase every upload (integer)
versionName = "1.1"      // human-readable, your choice
```

---

## ⚠️ A few important honesty notes for publishing

1. **The data source is an unofficial Yahoo endpoint.** It needs no key and works well for
   personal use, but it isn't a licensed/commercial API. Before publishing publicly,
   consider whether that's acceptable for your use, or switch to an official provider with
   a proper API plan. For a private/internal app or personal sideload, it's fine.
2. **Financial data + disclaimers.** If you publish a stock app, add a short disclaimer
   ("prices may be delayed; not financial advice"). Play sometimes flags finance apps.
3. **App icon.** The current launcher icon is a simple generated vector. You'll likely want
   a nicer 512×512 icon for the store listing.
4. **Keep your keystore safe and backed up.** Losing it means you can never update the app
   on Play again (you'd have to publish a brand-new listing).

---

## TL;DR

| Goal | Command / action |
|---|---|
| Try it on your phone now | Plug in, press ▶ Run in Android Studio |
| Debug APK | `./gradlew assembleDebug` → `app/build/outputs/apk/debug/` |
| Keepable signed APK | Create key (§2.1), wire it (§2.2), `./gradlew assembleRelease` |
| Play Store upload | `./gradlew bundleRelease` → upload the `.aab` in Play Console |
