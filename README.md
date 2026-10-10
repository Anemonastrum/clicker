# Clicker — `id.anemona.clicker`

An Android auto-clicker: schedule taps by wall-clock time (NTP-synced),
drive them through an accessibility service, and control everything from a
draggable floating overlay. App UI is themed with the
[MIUIX](https://github.com/compose-miuix-ui/miuix) Compose UI library.

## Features

- **Accessibility-driven taps** — `ClickAccessibilityService` performs taps
  via `dispatchGesture`; it consumes no accessibility events.
- **Floating overlay** (`OverlayService`, foreground service):
  - *Minimized*: live clock with milliseconds + last measured network RTT.
    The whole pill is draggable; chevron expands it.
  - *Expanded*: icon-only buttons — add touch point, remove last point, set
    execution start time (clock time, **not** a countdown), start, stop,
    minimize — plus a close button.
  - Overlay size adjustable in Settings (50%–200%).
- **Touch points**: place via the overlay (+ → drag the marker → check),
  or add at screen center from the Points tab. Per point: repeat count
  (0 = loop forever), interval between taps, delay after the point finishes.
  Existing points show as numbered markers while the overlay is expanded.
- **NTP scheduling**: set an execution start time; the engine syncs NTP
  (built-in minimal SNTP client, no extra dependency), converts the target
  to the device clock, and waits. Settings shows the measured clock offset,
  RTT, and a live network clock.
- **MIUIX theme**: `ClickerTheme` wraps `MiuixTheme` (system light/dark),
  giving the app the MIUI/HyperOS look.

## First run

1. Install the APK, open the app.
2. Home tab → enable the **accessibility service**
   (system Settings → Accessibility → Clicker).
3. Grant **"Display over other apps"** permission.
4. Show the overlay, tap **+** to drop touch points, set a start time,
   press **start**.

Notes:

- Taps need the accessibility service running; without it the engine
  refuses to start and says so.
- The overlay service runs in the foreground (persistent notification)
  so clicks survive while the app is in the background.
- NTP sync needs internet access.

## Build

Debug/release APKs are built by GitHub Actions
(`.github/workflows/build.yml`, JDK 17, Gradle 8.7) on every push to
`main`/`master`. Tagging `v*` publishes a release with the APKs.

Local build needs the Android SDK:

```sh
gradle :app:assemble
```

Release signing: copy `signing.properties.example` to `signing.properties`
and fill in your keystore; without it the release APK is signed with the
debug key.

## Dependency notes

- MIUIX (`top.yukonga.miuix.kmp:miuix-ui-android`) is **experimental** —
  APIs may change without notice. The version is pinned in
  `gradle/libs.versions.toml`; check the
  [getting-started guide](https://github.com/compose-miuix-ui/miuix/blob/HEAD/docs/guide/getting-started.md)
  when upgrading.
- Toolchain mirrors the HyperKernel project: AGP 8.5.2, Kotlin 2.0.20,
  Compose BOM 2024.09.00, Gradle 8.7. Stay on Gradle 8.x — AGP 8.5.2 does
  not support Gradle 9.

## Project layout

```
app/src/main/java/id/anemona/clicker/
├── ClickerApp.kt / MainActivity.kt
├── core/
│   ├── ClickAccessibilityService.kt  # gesture-only a11y service
│   ├── ClickEngine.kt                # tap sequencer + NTP scheduler
│   ├── NtpClient.kt                  # minimal SNTP client
│   ├── OverlayService.kt             # floating overlay (classic Views)
│   ├── TouchPoint.kt / ClickerSettings.kt
├── data/ClickerRepository.kt         # SharedPreferences + StateFlow store
└── ui/
    ├── theme/ClickerTheme.kt          # MIUIX theme wrapper
    ├── NavGraph.kt
    └── screens/ (Home, Points, Settings)
```
