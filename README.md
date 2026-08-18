# VyaparDesk — Retail Business Manager

> Fast billing, inventory, attendance & payroll — runs as a **PWA on the web** and as a **native Android app (WebView wrapper)** from the *same codebase*.

![VyaparDesk](app_icon.png)

VyaparDesk is a local-first retail manager for small shops in India. No server required to demo — all data lives in `localStorage` (and WebView storage on Android). Install it as a PWA or build the APK and run it like any native app.

---

## ✨ Features

- **Owner / Staff PIN login** — 4-digit ID + PIN. Owner can create staff, reset PINs, manage permissions.
- **Invoices** — cash / online, full / partial / unpaid, search, share/print PDF, owner-only delete (restores stock).
- **Inventory** — add/edit products, cost/price/stock, low-stock alerts, reorder level.
- **Staff & Access** — per-staff permission matrix (invoices, create, inventory, attendance, statements).
- **Attendance** — clock-in/out, approval flow, history.
- **Payroll** — baseline calculation from attendance.
- **Statements** — billing statement table + **CSV export**.
- **Theming** — Light / Dark / Ocean / Forest, responsive for desktop & Android.
- **Offline** — Service Worker cache for web; WebView cache + DOM storage for Android.

---

## Demo Logins

| Role  | ID    | PIN  |
|-------|-------|------|
| Owner | `1000`| `1234` |
| Staff | `1001`| `1111` |

> Forgot PIN? Use secret answer flow on login. Owner can reset any staff PIN to `1111`.

---

## Repository Structure

```
Apk/
├── index.html               # Main web app (single-page, vanilla JS)
├── manifest.webmanifest     # PWA manifest
├── sw.js                    # Service worker (vyapardesk-v8)
├── icon.svg                 # Brand icon
├── app_icon.png             # 512px PNG for README / Play Store
├── VyaparDesk-Final.zip     # Original upload (kept for reference)
│
├── app/                     # Android Studio project
│   ├── build.gradle
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/vyapardesk/app/MainActivity.kt  # WebView wrapper
│   │   ├── assets/www/      # Copy of web assets (index.html, etc) + android-bridge.js
│   │   └── res/             # Icons, colors, themes, FileProvider paths
│   └── proguard-rules.pro
├── gradle/wrapper/          # Gradle 8.7 wrapper
├── gradlew / gradlew.bat
├── build.gradle & settings.gradle
├── .github/workflows/build-apk.yml   # GitHub Actions: builds debug + release APK
└── README.md
```

The **web** and **Android** app share the *exact* same `index.html` business logic. Android just wraps it in a `WebView` with:

- `file:///android_asset/www/index.html` loading
- JavaScript + DOM storage enabled
- `android-bridge.js` intercepting `blob:` downloads (PDF/CSV) and routing them through `Android.saveBase64File()` → `FileProvider` share sheet
- `DownloadManager` for normal https downloads
- Back-button = WebView history
- `tel:` / `mailto:` intents forwarded to system

---

## 🌐 Run as Web App (PWA)

No build needed. Just serve over HTTP so the PWA install & service worker work.

```bash
# Python
python3 -m http.server 8080
# then open http://localhost:8080

# Node
npx serve .
```

- Installable via browser “Add to Home Screen”
- Offline works after first load (sw.js caches index.html, manifest, icon)

---

## 📱 Run as Android App

### Option A — Android Studio (recommended)

1. Open `Apk/` folder in **Android Studio Hedgehog+** (JDK 17).
2. Let Gradle sync (uses `gradle-8.7-bin.zip` + AGP 8.2.2 + Kotlin 1.9.22).
3. Connect device / start emulator (API 24+ — Android 7.0).
4. **Run** (`Shift+F10`) — installs `com.vyapardesk.app` (debug).
5. To build APK manually:

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release-unsigned.apk
```

Sign the release APK for Play Store:

```bash
keytool -genkey -v -keystore vyapardesk.jks -keyalg RSA -keysize 2048 -validity 10000 -alias vyapardesk
./gradlew assembleRelease -PstoreFile=vyapardesk.jks -PstorePassword=*** -PkeyAlias=vyapardesk -PkeyPassword=***
# or use Android Studio > Build > Generate Signed Bundle/APK
```

### Option B — GitHub Actions (no local SDK)

Push to `main` or `arena/*` or trigger manually:

1. `git push origin arena/019fe9fa-apk`
2. GitHub → **Actions → Build APK** → download artifacts:
   - `VyaparDesk-debug-apk` (installable immediately, allow “unknown sources”)
   - `VyaparDesk-release-apk` (unsigned — sign before Play Store)

Workflow file: `.github/workflows/build-apk.yml` uses `setup-java@v4` (Temurin 17) + `setup-android@v3`.

### Android App Details

- **Package:** `com.vyapardesk.app`
- **Min SDK:** 24 (Android 7.0) — WebView with modern storage
- **Target/Compile:** 34 (Android 14)
- **Permissions:** `INTERNET`, `ACCESS_NETWORK_STATE` only (downloads use `DownloadManager` + `FileProvider`, no storage permission needed)
- **Icons:** Adaptive icon (API 26+) + legacy mipmap PNGs (48–192px) generated from `icon.svg`
- **File sharing:** `android-bridge.js` captures `blob:` PDF/CSV anchor clicks → `Android.saveBase64File(base64, name, mime)` → saves to `cache/` + `externalFiles/Downloads` → `ACTION_SEND` share sheet

---

## 🛠️ Development Notes

### Web → Android Sync

If you edit `index.html` at repo root, copy it to Android assets:

```bash
cp index.html manifest.webmanifest sw.js icon.svg app/src/main/assets/www/
# then bump versionCode in app/build.gradle
```

Or add a Gradle task to auto-sync (optional).

### Debugging WebView

- `WebView.setWebContentsDebuggingEnabled(true)` is on — inspect via Chrome: `chrome://inspect` → your device → VyaparDesk.
- Logs appear in `logcat`: `adb logcat | grep VyaparDesk`

### Production Hardening (before real shop use)

This baseline is **local-first**. Before multi-device production:

- Replace `localStorage` PINs with hashed auth + backend (Firebase/Supabase/Node+Postgres)
- Add cloud sync, backups, and GST/tax rules
- Implement proper role JWT and audit logs
- Sign APK/AAB with real keystore and enable `minifyEnabled true` + R8

---

## 📄 License

MIT — use for your shop, fork for clients. Keep the `icon.svg` brand or replace with your own.

---

## Quick Start Checklist

- [ ] `python3 -m http.server 8080` → test web login `1000/1234`
- [ ] Open in Android Studio → Run on emulator
- [ ] Push to GitHub → Actions builds APK automatically
- [ ] Install `app-debug.apk` on phone (allow unknown sources)

Questions? Open an issue or see `sw.js` / `MainActivity.kt` for integration points.

