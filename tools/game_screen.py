#!/usr/bin/env python3
"""Tell whether the game is showing a menu, and get it back into the world if it is.

Written after several takes were recorded with the pause menu across every frame. Focus is stolen
whenever this script spawns a subprocess, and Minecraft opens the pause menu when the window loses
focus - `pauseOnLostFocus:false` does not prevent it. Escape closes the menu, but Escape on a game
that is *already* in the world opens one, so it can only be pressed against an actual reading of
what is on screen.

The reading looks at the "Back to Game" button. Its interior is a flat, desaturated grey, which
nothing in a Minecraft landscape looks like: grass is strongly green, stone is darker and noisy,
sky is blue and bright. Earlier attempts sampled a band that happened to miss the button entirely
and reported "clean" for frames that were nothing but menu.
"""

from __future__ import annotations

import statistics
import subprocess
import tempfile
from pathlib import Path

from PIL import Image

FFMPEG = Path(
    "C:/Users/oxman/AppData/Local/Microsoft/WinGet/Packages"
    "/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-8.1.1-full_build/bin/ffmpeg.exe"
)
WINDOW_TITLE = "Minecraft NeoForge* 1.21.1 - Multiplayer (3rd-party Server)"
FOCUS = Path(__file__).resolve().parent / "focus_game.ps1"

# "Back to Game", as a fraction of the window. Comfortably inside the button on any window size.
BUTTON = (0.36, 0.275, 0.64, 0.315)


def grab(path: Path) -> bool:
    result = subprocess.run(
        [str(FFMPEG), "-hide_banner", "-loglevel", "error",
         "-f", "gdigrab", "-framerate", "5", "-i", f"title={WINDOW_TITLE}",
         "-frames:v", "1", "-y", str(path)],
        capture_output=True, text=True)
    return result.returncode == 0 and path.exists()


def menu_is_open(debug: bool = False) -> bool:
    with tempfile.TemporaryDirectory() as tmp:
        shot = Path(tmp) / "screen.png"
        if not grab(shot):
            return False
        im = Image.open(shot).convert("RGB")
        w, h = im.size
        box = im.crop((int(BUTTON[0] * w), int(BUTTON[1] * h),
                       int(BUTTON[2] * w), int(BUTTON[3] * h)))
        px = list(box.getdata())
        r = statistics.fmean(p[0] for p in px)
        g = statistics.fmean(p[1] for p in px)
        b = statistics.fmean(p[2] for p in px)
        grey = abs(r - g) < 14 and abs(g - b) < 14 and abs(r - b) < 14
        lit = 110 < (r + g + b) / 3 < 200
        if debug:
            print(f"    button sample RGB=({r:.0f},{g:.0f},{b:.0f}) grey={grey} lit={lit}")
        return grey and lit


def press_escape() -> None:
    subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", str(FOCUS), "-CloseMenu"], capture_output=True, text=True)


def ensure_in_world(attempts: int = 4, debug: bool = False) -> bool:
    """Close any menu that is showing. Returns True once the world is visible."""
    for _ in range(attempts):
        if not menu_is_open(debug=debug):
            return True
        press_escape()
    return not menu_is_open(debug=debug)


if __name__ == "__main__":
    print("menu open:", menu_is_open(debug=True))
    print("clearing ->", ensure_in_world(debug=True))
