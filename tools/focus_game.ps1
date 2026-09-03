# Bring the Minecraft window genuinely to the front, and optionally close an open pause menu.
#
# SetForegroundWindow on its own is refused when the caller is not already the foreground process,
# which is why every earlier attempt silently left another window on top. Attaching to the current
# foreground window's input queue first lifts that restriction, which is the documented way round it.
#
#   powershell -File tools/focus_game.ps1 -CloseMenu
param(
    [string]$TitleMatch = "Minecraft",
    [switch]$CloseMenu,
    [switch]$ToggleHud,
    [int]$SettleMs = 700
)

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Focus {
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, IntPtr pid);
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint a, uint b, bool attach);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int n);
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, IntPtr extra);

    public static bool Grab(IntPtr target) {
        IntPtr fg = GetForegroundWindow();
        if (fg == target) return true;
        uint fgThread = GetWindowThreadProcessId(fg, IntPtr.Zero);
        uint me = GetCurrentThreadId();
        AttachThreadInput(me, fgThread, true);
        ShowWindow(target, 9);
        BringWindowToTop(target);
        bool ok = SetForegroundWindow(target);
        AttachThreadInput(me, fgThread, false);
        return ok;
    }
}
"@

$candidates = @(Get-Process -Name java -ErrorAction SilentlyContinue |
    Where-Object { $_.MainWindowTitle -like "*$TitleMatch*" })
if ($candidates.Count -ne 1) {
    Write-Error "Expected exactly one window matching '$TitleMatch', found $($candidates.Count)."
    exit 1
}
$h = $candidates[0].MainWindowHandle

# The grab occasionally loses a race with whatever else is being drawn, so try a few times.
$ok = $false
for ($i = 0; $i -lt 5 -and -not $ok; $i++) {
    [void][Focus]::Grab($h)
    Start-Sleep -Milliseconds $SettleMs
    $ok = ([Focus]::GetForegroundWindow() -eq $h)
}
if (-not $ok) {
    Write-Error "Could not bring the game to the front; something else is holding focus."
    exit 2
}

if ($CloseMenu) {
    # Escape closes an open pause menu. Harmless if none is showing: in multiplayer it simply
    # opens and the second press closes it again, leaving the game where it started.
    [Focus]::keybd_event(0x1B, 0, 0, [IntPtr]::Zero)
    [Focus]::keybd_event(0x1B, 0, 2, [IntPtr]::Zero)
    Start-Sleep -Milliseconds 400
}

if ($ToggleHud) {
    # F1. The HUD state is sticky, so this is sent once per session rather than per shot.
    [Focus]::keybd_event(0x70, 0, 0, [IntPtr]::Zero)
    [Focus]::keybd_event(0x70, 0, 2, [IntPtr]::Zero)
    Start-Sleep -Milliseconds 350
}

Write-Output "focused: $($candidates[0].MainWindowTitle)"
