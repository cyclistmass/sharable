# Starts (if needed) the test emulator, puts it in landscape, and fits its window on screen.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\emulator.ps1 [-Avd Pixel8_API36] [-Launch]
param([string]$Avd = "Pixel8_API36", [switch]$Launch)
$ErrorActionPreference = "Stop"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdk\platform-tools\adb.exe"

Add-Type -AssemblyName System.Windows.Forms
Add-Type @'
using System; using System.Runtime.InteropServices;
public class EmuWin {
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int hh, bool repaint);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  public struct RECT { public int L, T, R, B; }
}
'@

function Get-EmuWindow {
    Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like "Android Emulator*" } |
        Select-Object -First 1
}

# Work in physical pixels: this PC runs a 4K screen at 250% scaling, and a DPI-unaware
# script would see (and capture) only a scaled-down 1536x864 view of it.
[EmuWin]::SetProcessDPIAware() | Out-Null

# 1. Start and wait for boot.
if (-not (Get-EmuWindow)) {
    Start-Process "$sdk\emulator\emulator.exe" -ArgumentList "-avd", $Avd, "-no-snapshot-save" -WindowStyle Normal
}
& $adb wait-for-device | Out-Null
for ($i = 0; $i -lt 120; $i++) {
    if ((& $adb shell getprop sys.boot_completed 2>$null) -match "1") { break }
    Start-Sleep -Seconds 2
}
for ($i = 0; $i -lt 30 -and -not (Get-EmuWindow); $i++) { Start-Sleep -Seconds 1 }
$p = Get-EmuWindow
if (-not $p) { throw "Emulator window not found" }

# 2. Landscape. The window's shape is NOT a reliable signal (an earlier resize can fake it), so:
#    a) rotate the virtual device until gravity points along x (device held sideways);
#    b) lock the display itself to landscape (auto-rotate off, user_rotation = 90 degrees).
for ($i = 0; $i -lt 4; $i++) {
    $g = (& $adb emu sensor get acceleration) -join " "
    if ($g -match "acceleration = ([-0-9.e]+):([-0-9.e]+):") {
        if ([Math]::Abs([double]$Matches[1]) -gt [Math]::Abs([double]$Matches[2])) { break }
    }
    & $adb emu rotate | Out-Null
    Start-Sleep -Milliseconds 1500
}
& $adb shell settings put system accelerometer_rotation 0 | Out-Null
& $adb shell settings put system user_rotation 1 | Out-Null
$r = New-Object EmuWin+RECT

# 3. Fit the window inside the primary screen's work area, top-left, keeping the 2400x1080 aspect.
$wa = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$w = [int]($wa.Width * 0.88)                   # big on a 4K screen; leaves room for the side toolbar
$titleBar = [int]($wa.Height * 0.035)
$h = [int]($w * 1080 / 2400) + $titleBar
if ($h -gt $wa.Height - 40) { $h = $wa.Height - 40; $w = [int](($h - $titleBar) * 2400 / 1080) }
[EmuWin]::MoveWindow($p.MainWindowHandle, $wa.Left + 20, $wa.Top + 20, $w, $h, $true) | Out-Null
[EmuWin]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 500
[EmuWin]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
"Emulator window: L=$($r.L) T=$($r.T) R=$($r.R) B=$($r.B) (screen $($wa.Width)x$($wa.Height))"

# 4. Optionally (re)launch the app, then confirm the display really is landscape.
if ($Launch) {
    & $adb shell am force-stop com.mauro.wgbg | Out-Null
    & $adb shell am start -n com.mauro.wgbg/.MainActivity | Out-Null
    Start-Sleep -Seconds 3
}
$rot = (& $adb shell dumpsys window displays) | Select-String -Pattern "mCurrentRotation=(\S+)" | Select-Object -First 1
"Display: $($rot.Matches[0].Groups[1].Value)  (want ROTATION_90 or ROTATION_270)"
