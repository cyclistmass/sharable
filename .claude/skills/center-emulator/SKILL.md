---
name: center-emulator
description: Pop the Android emulator window into the middle of the screen and in front of other windows (landscape-sized). Use whenever the emulator is off screen, hidden behind the terminal, the wrong size, or right after starting it or launching the app.
---

# Center the emulator

Run:

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools/center_emulator.ps1
```

It works in physical pixels (this PC: one 4K screen at 250% scaling), sizes the window to ~90% of
the screen width with the 2400x1080 landscape aspect, centers it (nudged left to leave room for
the side toolbar), and brings it to the front.

Why it needs to be this way (don't regress these):
- `SetForegroundWindow` alone is ignored by Windows when another app (the terminal) has focus, so
  the window ends up centered but hidden behind the full-screen terminal. The script minimizes
  and restores the window, and toggles always-on-top off again, which reliably brings it forward.
- The emulator re-lays out its own window when the display rotates (e.g. when the app starts in
  landscape), which can undo a placement. Run this AFTER launching the app; the script re-checks
  and re-centers up to 4 times.
- DPI-unaware tools see a fake 1536x864 screen; this script calls `SetProcessDPIAware` first.

Verify with a DPI-aware desktop capture (3840x2160, downscaled) when in doubt; the emulator
should be visible in the middle of the screen, not behind the terminal.

`tools/emulator.ps1` (start/landscape) already calls this at the end.
