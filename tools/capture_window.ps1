# Capture the Minecraft dev client window to a PNG.
#
# The game renders through OpenGL, so PrintWindow generally comes back black; the reliable route is
# to bring the window to the front and copy the matching region off the screen. That does mean the
# window has to be visible while this runs.
#
#   powershell -File tools/capture_window.ps1 -Out shot.png
param(
    [Parameter(Mandatory = $true)][string]$Out,
    [string]$TitleMatch = "Minecraft",
    [int]$SettleMs = 900,
    [switch]$HideHud
)

Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hWnd, ref POINT lpPoint);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern uint SendInput(uint n, INPUT[] i, int cb);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
    [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT {
        public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo;
    }
    [StructLayout(LayoutKind.Explicit, Size = 40)] public struct INPUT {
        [FieldOffset(0)] public uint type;
        [FieldOffset(8)] public KEYBDINPUT ki;
    }
    // Minecraft reads the keyboard through GLFW, which sits on the normal Windows message loop -
    // so SendInput reaches it, while PostMessage-style injection does not.
    public static void TapKey(ushort vk) {
        INPUT[] down = new INPUT[1];
        down[0].type = 1; down[0].ki.wVk = vk;
        SendInput(1, down, Marshal.SizeOf(typeof(INPUT)));
        System.Threading.Thread.Sleep(60);
        INPUT[] up = new INPUT[1];
        up[0].type = 1; up[0].ki.wVk = vk; up[0].ki.dwFlags = 2;
        SendInput(1, up, Marshal.SizeOf(typeof(INPUT)));
    }
}
"@

# Without this the window rect comes back in logical units while CopyFromScreen works in physical
# pixels, so on a scaled display the capture is offset and cropped - which looks like the title bar
# leaking into the shot.
[void][Win]::SetProcessDPIAware()

$proc = Get-Process | Where-Object { $_.MainWindowTitle -like "*$TitleMatch*" } | Select-Object -First 1
if ($null -eq $proc) {
    Write-Error "No window whose title matches '$TitleMatch'. Is the client running?"
    exit 1
}
$h = $proc.MainWindowHandle
Write-Output "window: '$($proc.MainWindowTitle)' (pid $($proc.Id))"

[void][Win]::ShowWindow($h, 9)     # SW_RESTORE, in case it is minimised
[void][Win]::SetForegroundWindow($h)
Start-Sleep -Milliseconds $SettleMs

$rect = New-Object Win+RECT
if (-not [Win]::GetClientRect($h, [ref]$rect)) { Write-Error "GetClientRect failed"; exit 1 }
$origin = New-Object Win+POINT
if (-not [Win]::ClientToScreen($h, [ref]$origin)) { Write-Error "ClientToScreen failed"; exit 1 }

$w = $rect.Right - $rect.Left
$hgt = $rect.Bottom - $rect.Top
if ($w -le 0 -or $hgt -le 0) { Write-Error "Window has no client area ($w x $hgt)"; exit 1 }

$VK_F1 = 0x70
if ($HideHud) { [Win]::TapKey($VK_F1); Start-Sleep -Milliseconds 350 }

$bmp = New-Object System.Drawing.Bitmap $w, $hgt
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($origin.X, $origin.Y, 0, 0, (New-Object System.Drawing.Size $w, $hgt))
$g.Dispose()

if ($HideHud) { [Win]::TapKey($VK_F1) }

$dir = Split-Path -Parent $Out
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output "saved $Out ($w x $hgt)"
