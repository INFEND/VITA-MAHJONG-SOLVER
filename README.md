# VITA MAHJONG SOLVER · INFEND

Native Android Java prototype for automatic Vita Mahjong Solitaire moves.

> **Status: 0.1.0 prototype.** The Java move selector has offline unit tests; APK build and actual Vita Mahjong recognition have **not** been verified on a device. Fully unattended reliable play is **not yet proven**.

## What is implemented

- AccessibilityService with `canTakeScreenshot` and `canPerformGestures` (Android 11+, no root).
- On-device tile-face candidate detection from bright connected components.
- Symbol signature comparison without OCR/network API; exposed-pair selection and taps.
- Small draggable accessibility overlay, AMOLED main UI with play/pause, speed, tolerance, auto-next setting.
- Optional next-level activation when an accessibility-exposed button has exactly `Next Level`, `Play Next`, or `Следующий уровень` as its label.
- OTA workflow cloned in architecture from QR LINK: pinned GitHub Releases path, single permanent signing bundle, SHA-256 and signer checks, signed APK published with `update.json` and cumulative release history.

## Critical limitations

- Not all pieces and overlays can be detected reliably with a simple image signature. Hidden tiles cannot be identified from a screenshot.
- UI/text-only buttons may be inaccessible to Accessibility; image-only next-level buttons need calibration.
- Unattended play is **not guaranteed**: the service pauses rather than making low-confidence moves.
- The service only handles the **classic exposed free-pair mode**. Special events, tile-tray modes, ads, shuffles, alternate layouts and skins require additional implementation.
- Android may require explicit system confirmation to install an update. This cannot be bypassed by the app.
- This independent project is not affiliated with Vita Studio.

## Target app

Package: `com.vitastudio.mahjong` (Google Play Vita Mahjong).

Package of the solver: `dev.infend.vitasolver`.

## Build

Use Java 17, Gradle 9.6.1, AGP 9.3.1, Android SDK 36. For a local debug build:

```bash
gradle :app:assembleDebug
```

Test the platform-independent solver logic:

```bash
bash scripts/test_core.sh
```

## GitHub OTA setup

1. Create a **public** repository called `INFEND/VITA-MAHJONG-SOLVER` with default branch `main`. GitHub connector currently cannot create repositories, so this step requires manual account action.
2. Push the files from this project archive into `main` (not the surrounding ZIP directory).
3. Go to **Settings → Secrets and variables → Actions → New repository secret**.
4. Name: `VITA_SIGNING_BUNDLE`; value: one complete Base64 line from the separately provided `VITA-SOLVER-GITHUB-SECRET.txt` file.
5. Retain the private key backup and credential instructions **offline**. Never put them in Git.
6. GitHub Actions `VITA SOLVER · Android OTA` builds with the same signing identity and publishes `VITA-SOLVER.apk` plus `update.json` to Releases.
7. For later releases bump both `versionCode` and `versionName` in `app/build.gradle`, and add `release_notes/v<version>.txt`.
8. The application checks the official OTA channel from `INFEND/VITA-MAHJONG-SOLVER`. Repo name/owner cannot be changed without changing the application updater.

Latest APK after the first **successful** CI release:
`https://github.com/INFEND/VITA-MAHJONG-SOLVER/releases/latest/download/VITA-SOLVER.apk`

Manifest after the first successful release:
`https://github.com/INFEND/VITA-MAHJONG-SOLVER/releases/latest/download/update.json`

## Security

Private signing material is NOT included in the public project archive. The app uses the pinned SHA-256 fingerprint of the permanent release certificate. The signing secret is used only by GitHub Actions, and installation requires Android's system installer approval. Screenshots are analyzed locally.
