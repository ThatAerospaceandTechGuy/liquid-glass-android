# Liquid Glass Launcher

A home-screen launcher (`CATEGORY_HOME`) plus a 4x2 clock/battery home-screen widget,
written only against the `android.*` platform SDK. No AndroidX, no Compose, no
Material, no image loader, no coroutine runtime, no third-party code of any kind.

**Measured:** `app-release-unsigned.apk` = **32,224 bytes (31 KB)**; signed = 41,937
bytes. `classes.dex` = 47 KB, `resources.arsc` = 3.3 KB. Budget was < 500 KB.

---

## 1. Build

Requirements: JDK 17 and an Android SDK with `platforms;android-35` +
`build-tools;35.0.0`. (Verified with AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9.)

```bash
git clone https://github.com/ThatAerospaceandTechGuy/liquid-glass-android.git
cd liquid-glass-android

# point the build at your SDK (local.properties is gitignored)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# The Gradle wrapper JAR is a binary, so it is not committed. Generate it once:
gradle wrapper --gradle-version 8.9

# release build: R8 full mode + resource shrinking are already on
./gradlew :app:assembleRelease
ls -l app/build/outputs/apk/release/app-release-unsigned.apk
```

Opening the folder in Android Studio also generates the wrapper on first sync.

### Sign it (release APKs from AGP are unsigned)

```bash
keytool -genkeypair -keystore ~/glass.jks -alias glass -keyalg RSA \
        -keysize 2048 -validity 10000

$ANDROID_HOME/build-tools/35.0.0/apksigner sign \
        --ks ~/glass.jks \
        --out liquid-glass.apk \
        app/build/outputs/apk/release/app-release-unsigned.apk

$ANDROID_HOME/build-tools/35.0.0/apksigner verify liquid-glass.apk
adb install -r liquid-glass.apk
```

### Make it the home app

```bash
adb shell cmd package set-home-activity dev.glass.launcher/.MainActivity
# or: Settings → Apps → Default apps → Home app → Liquid Glass
```

### Add the widget

Long-press any empty spot on any launcher → Widgets → Liquid Glass → drag the 4x2
clock onto the screen. It does not require this app to be the active home app.

---

## 2. Verify the memory footprint

```bash
adb shell dumpsys meminfo dev.glass.launcher
```

Read these lines, not `TOTAL PSS`:

```
App Summary
    Java Heap:     ~1000        <- your custom heap, the number that matters
    Native Heap:   ...
    Views:         a few dozen  <- at rest, not thousands
    Activities:    1
    AppContexts:   2
```

Then exercise the expensive paths:

```bash
# open + close the app drawer (builds and tears down the search sheet)
adb shell input swipe 540 1600 540 600 120
adb shell dumpsys meminfo dev.glass.launcher | sed -n '/App Summary/,/Objects/p'

# force the trim path that onTrimMemory(UI_HIDDEN) implements
adb shell am send-trim-memory dev.glass.launcher RUNNING_LOW
adb shell dumpsys meminfo dev.glass.launcher | grep -E "Java Heap|Views"

# dalvik/ART detail for the same process
adb shell dumpsys meminfo dev.glass.launcher -d
```

What to expect and why:

* `Java Heap` should sit near 1 MB at rest and stay under ~2–3 MB with the drawer
  open. That is the custom-heap budget: icons, views, shaders and the app list.
* `TOTAL PSS` will read ~25–45 MB. That is normal and is **not** app allocation:
  it includes shared Zygote/ART mappings, framework code, the dex mapping and the
  window's graphics buffers that every app process gets.
* `Views` is the fastest sanity check that the drawer and settings sheet are
  built on demand and released again.

Frame timing, if you want it:

```bash
adb shell dumpsys gfxinfo dev.glass.launcher reset
# swipe around, then
adb shell dumpsys gfxinfo dev.glass.launcher
```

---

## 3. How each constraint is met

| Requirement | Implementation |
|---|---|
| Zero third-party deps | `app/build.gradle.kts` has an empty `dependencies {}`. Only `android.*`, `java.*`, `kotlin.*`. `android.useAndroidX=false`. |
| APK < 500 KB | 31 KB unsigned. R8 full mode, `isShrinkResources`, resource + class obfuscation, `resourceConfigurations=["en"]`, `buildConfig=false`, Kotlin metadata excluded from packaging. |
| No full-res icons | `IconStore` decodes the icon **resource** with `BitmapFactory.Options.inSampleSize` (bounds pass, then sampled decode), scales to exactly `48 * density` px and recycles the intermediate. |
| 512 KB `LruCache` | One `LruCache<String, Bitmap>` with `maxSize = 512*1024` and `sizeOf = byteCount`; `entryRemoved` recycles evictions. No view holds a `Bitmap`, so eviction is always safe. |
| Release on trim/stop | `onStop` → `icons.trim(TRIM_MEMORY_UI_HIDDEN)`; `onTrimMemory` halves at `RUNNING_LOW` and empties at `UI_HIDDEN`; `onLowMemory` empties. |
| RenderEffect / AGSL | `GlassShadow` is a rounded rect blurred by `RenderEffect.createBlurEffect`. `GlassEffects.attachPanel` installs a `RuntimeShader` via `createRuntimeShaderEffect(shader, "content")` that refracts the panel's own pixels at the rim. API 33+, no-op below 31. |
| < API 31 fallback | `LiquidGlassDrawable` draws shadow, scrim, frost, specular rim and glint procedurally. No bitmaps. |
| Wallpaper window | `windowShowWallpaper` + transparent background + `FLAG_SHOW_WALLPAPER`; on API 31+ `Window.setBackgroundBlurRadius` also frosts the wallpaper. |
| 4-column grid | Platform `GridView`, 4 `STRETCH_COLUMN_WIDTH` columns, 20 apps, one flat custom `View` per cell. |
| Dock | `LinearLayout` pill with a stadium `LiquidGlassDrawable`, four pinned apps. |
| Swipe-up drawer | `GestureDetector.onFling` fed from `dispatchTouchEvent`; search is a plain `EditText` + `TextWatcher`. |
| Widget | `AppWidgetProvider` + `RemoteViews` + XML layer-list glass; minute tick via repeating `AlarmManager`. No services. |
| Settings overlay | Long-press empty home space or the dock → animated bottom sheet with three `SeekBar`s in `SharedPreferences`; changes repaint live. |

---

## 4. Platform truths worth knowing

1. **A `View`'s `RenderEffect` cannot blur what is behind it.** The SDK has no
   backdrop-blur API; a `RenderEffect` only samples the view's own display list.
   Copying the wallpaper into a bitmap would violate the no-bitmap rule, so the
   frosted backdrop comes from `Window.setBackgroundBlurRadius` (API 31+) and the
   per-panel look from the procedural drawable plus AGSL rim refraction.
2. **`RemoteViews` cannot run `RenderEffect`.** Widgets render in the host
   launcher's process, so the widget glass is a two-layer XML drawable.
3. **512 KB is about six icons.** At xxhdpi a 48 dp icon is 144×144×4 ≈ 81 KB, so
   the hard cap holds ~6 icons and the rest re-decode in under a millisecond.
   Raise `ICON_CACHE_BYTES` in `IconStore.kt` to 2 MB if you prefer zero reloads.
4. **Views are not destroyed on `onStop`.** Recreating the hierarchy on every app
   launch causes flicker; what is released is every pixel (the entire icon cache).
5. **`Window.setBackgroundBlurRadius` is best-effort.** Some OEM launcher windows
   refuse cross-window blur; the procedural glass remains in place.

---

## 5. Layout

```
build.gradle.kts                 AGP + Kotlin plugin pins only
settings.gradle.kts              repos, single :app module
gradle.properties                R8 full mode, no AndroidX, no BuildConfig
app/build.gradle.kts             release shrinking, packaging trims, empty deps
app/proguard-rules.pro           component keeps + log stripping
app/src/main/AndroidManifest.xml HOME activity, widget receiver, 1 permission

app/src/main/java/dev/glass/launcher/
  MainActivity.kt                window flags, grid, dock, drawer, settings
  LiquidGlassDrawable.kt         procedural glass surface
  GlassEffects.kt                AGSL refraction + GPU-blurred shadow
  IconStore.kt                   inSampleSize decode + 512 KB LruCache
  AppAdapter.kt                  AppEntry, package scan, GlassIconView, BaseAdapter
  GlassWidgetProvider.kt         RemoteViews widget + minute ticker
  GlassPrefs.kt                  three ints in SharedPreferences

app/src/main/res/                widget layout, sheet, glass drawable, icon vectors
apk/index.html                   download page
```
