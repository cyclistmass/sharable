---
name: emulator
description: Start, orient and position the Android test emulator for this project (landscape, fully on screen), then install/launch the app and take screenshots. Use whenever the emulator needs to be started, is off screen, is in portrait, or before any on-device check or screenshot.
---

# Emulator setup (do it this way, every time)

This PC has a **4K screen (3840x2160) at 250% scaling**. Anything that is not DPI-aware sees a
fake 1536x864 screen, so hand-rolled window moves and desktop captures go wrong. The emulator
window must be **landscape and fully on screen** before showing the user anything.

## 1. Always use the script

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools/emulator.ps1 -Launch
```

It starts `Pixel8_API36` if needed, waits for boot, rotates the virtual device until gravity
points sideways (checked with `adb emu sensor get acceleration`; the window's shape is NOT a
reliable signal), locks the display to landscape (`accelerometer_rotation 0`, `user_rotation 1`),
fits the window at the top-left of the screen in physical pixels, brings it to the front, and
(with `-Launch`) restarts the app. Check the last line says `ROTATION_90` or `ROTATION_270`.

Never start the emulator with a bare `emulator -avd ...` or move its window by hand.

## 2. Install and drive the app

```
export JAVA_HOME="$(ls -d "/c/Program Files/Eclipse Adoptium"/jdk-17*)"
./gradlew :app:installDebug
```

Then rerun the script with `-Launch` (or `adb shell am start -n com.mauro.wgbg/.MainActivity`).
Debug launch extras for screenshots: `--ef zoom 1.6 --ef tilt 0.8`.
Find buttons with `uiautomator dump` (use `MSYS_NO_PATHCONV=1` in Git Bash for `/sdcard` paths).

## 3. Screenshots

- Device screen: `adb exec-out screencap -p > "$TEMP/x.png"` (2400x1080 when landscape).
- Whole desktop (to check what the user sees): capture in a DPI-aware process at 3840x2160 and
  downscale, e.g. call `SetProcessDPIAware()` before `CopyFromScreen`. A DPI-unaware capture only
  shows the top-left corner.
