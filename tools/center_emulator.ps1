# Centers the running emulator window on the primary screen, landscape-sized, and keeps it there.
# The emulator re-lays out its own window when the display rotates (e.g. when the app starts in
# landscape), which can push it off screen after it was placed, so this re-checks and re-centers.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\center_emulator.ps1 [-Port 5554] [-Width 0.9]
param([int]$Port = 0, [double]$Width = 0.9, [int]$Checks = 4)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Windows.Forms
Add-Type @'
using System; using System.Runtime.InteropServices;
public class EmuCenter {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int hh, bool repaint);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int w, int hh, uint flags);
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
  public struct RECT { public int L, T, R, B; }
}
'@
[EmuCenter]::SetProcessDPIAware() | Out-Null   # physical pixels (4K at 250% on this PC)

$p = Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue |
     Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like "Android Emulator*" -and ($Port -eq 0 -or $_.MainWindowTitle -like "*:$Port*") } | Select-Object -First 1
if (-not $p) { throw "No emulator window. Start it with tools\emulator.ps1 first." }
$h = $p.MainWindowHandle
$wa = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea

# Landscape 2400x1080 device + title bar; leave room on the right for the side toolbar.
$w = [int]($wa.Width * $Width)
$hh = [int]($w * 1080 / 2400 + $wa.Height * 0.035)
if ($hh -gt $wa.Height * 0.9) { $hh = [int]($wa.Height * 0.9); $w = [int](($hh - $wa.Height * 0.035) * 2400 / 1080) }
$x = $wa.Left + [int](($wa.Width - $w) / 2) - [int]($wa.Width * 0.02)   # nudge left: toolbar sits to the right
$y = $wa.Top + [int](($wa.Height - $hh) / 2)

$r = New-Object EmuCenter+RECT
for ($i = 0; $i -lt $Checks; $i++) {
    # Minimize then restore: the one reliable way to bring a background app's window to the front
    # on Windows (SetForegroundWindow alone is ignored when another app has focus).
    [EmuCenter]::ShowWindow($h, 6) | Out-Null          # SW_MINIMIZE
    Start-Sleep -Milliseconds 300
    [EmuCenter]::ShowWindow($h, 9) | Out-Null          # SW_RESTORE
    [EmuCenter]::MoveWindow($h, $x, $y, $w, $hh, $true) | Out-Null
    # Windows ignores SetForegroundWindow from background processes, which left the emulator hidden
    # behind a full-screen terminal. Flip it always-on-top and back, and tap Alt so focus may move.
    [EmuCenter]::SetWindowPos($h, [IntPtr](-1), 0, 0, 0, 0, 0x0003) | Out-Null   # HWND_TOPMOST, no move/size
    [EmuCenter]::SetWindowPos($h, [IntPtr](-2), 0, 0, 0, 0, 0x0003) | Out-Null   # HWND_NOTOPMOST: stays in front
    [EmuCenter]::keybd_event(0x12, 0, 0, [UIntPtr]::Zero); [EmuCenter]::keybd_event(0x12, 0, 2, [UIntPtr]::Zero)
    [EmuCenter]::SetForegroundWindow($h) | Out-Null
    Start-Sleep -Milliseconds 1500
    [EmuCenter]::GetWindowRect($h, [ref]$r) | Out-Null
    $onScreen = $r.L -ge $wa.Left -and $r.T -ge $wa.Top -and $r.R -le $wa.Right -and $r.B -le $wa.Bottom
    $centered = [Math]::Abs((($r.L + $r.R) / 2) - ($x + $w / 2)) -lt 40 -and [Math]::Abs((($r.T + $r.B) / 2) - ($y + $hh / 2)) -lt 40
    if ($onScreen -and $centered) { break }
}
"Emulator window: L=$($r.L) T=$($r.T) R=$($r.R) B=$($r.B) on screen $($wa.Width)x$($wa.Height) (centered=$centered)"
