# Liquid Glass — iOS 26 style for Android

A single-activity Android home screen with Apple's iOS 26 "Liquid Glass" look: real frosted-glass
panels, a squircle app grid over your own wallpaper, and a working Control Centre.

**Prebuilt APK:** download from the live page — https://site-cfec98903e8c4284b1bd2e88156d3e42.freebuff.page  
**Source build:** `gradle :app:assembleRelease` (Android 8.0+, minSdk 26)

## Install

```
adb install -r LiquidGlass-1.0.apk
```

Or copy the APK to the phone and open it (allow "install unknown apps" for your file manager).

Open it once, then make it the home app:
**Settings → Apps → Default apps → Home app → Liquid Glass**, or press Home and pick it.

## What actually works

| Area | Behaviour |
| --- | --- |
| App grid | Real installed apps from PackageManager, squircle-masked icons, tap to launch |
| Dock | Best-match phone / messages / browser / camera apps, padded with your apps |
| Search | Full app search + "Search the web" row |
| Long-press an app | App info / Uninstall |
| Clock widget | Real time, date and battery, refreshed once a minute |
| Control Centre | Torch (real), brightness, media volume, Focus/DND, battery |
| System panels | Wi-Fi, Bluetooth, Settings, Wallpaper picker, Camera |

Notes on Android limits, handled honestly rather than faked:

- **Brightness** is applied to this app's window. Changing the system brightness needs
  `WRITE_SETTINGS`, which this build deliberately does not request.
- **Wi-Fi / Bluetooth** cannot be toggled by third-party apps on Android 10+, so those tiles open
  the official system panels instead of showing a fake on/off state.
- **Do Not Disturb** toggles for real once you grant it: the tile sends you to the one-time
  "Do Not Disturb access" screen the first time.

## Why it stays light

- One activity. No services, no receivers, no alarms, no widgets, no polling loops.
- No internet permission, no analytics, no ads, no libraries beyond AndroidX/Compose.
- The wallpaper is copied and blurred **once** at startup into a tiny downsample; each glass panel
  samples that bitmap at its own position. That is the whole "backdrop blur", done on the GPU with
  one draw call per panel — no RenderScript, no per-frame blur cost.
- Icons are decoded once on a background thread and cached in memory.

## Build from source

Requires JDK 17 and Android SDK 34:

```
gradle :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (signed with the local debug key so it
sideloads; replace the signing config with your own keystore for distribution).
