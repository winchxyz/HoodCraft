#!/usr/bin/env python3
"""Record a showcase video of the mod, unattended.

    rm -rf run/hoodcraft-test && ./gradlew runServer     # one shell
    ./gradlew runScreenshots                             # another - joins as "Dev"
    python tools/record_showcase.py

Each scene is staged over RCON, then filmed while the camera is walked along a path a few frames at
a time. Clips are concatenated into docs/showcase.mp4.

Two things make this work where earlier attempts did not.

Capture is scoped to the game's own window (`gdigrab -i title=...`), not to a rectangle of the
desktop. A desktop grab photographs whatever happens to be on top, which both ruins the take and
captures whatever else is on screen; a window grab does neither.

And focus is taken with focus_game.ps1, which attaches to the foreground window's input queue
first. A plain SetForegroundWindow from a background process is refused, which is why the game kept
being left behind another window with its pause menu open.
"""

from __future__ import annotations

import math
import subprocess
import sys
import time
from pathlib import Path

from rcon import Rcon
import game_screen
import stage_screenshots as st

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "docs"
CLIPS = ROOT / "build" / "showcase-clips"
FOCUS = ROOT / "tools" / "focus_game.ps1"

FFMPEG = Path(
    "C:/Users/oxman/AppData/Local/Microsoft/WinGet/Packages"
    "/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-8.1.1-full_build/bin/ffmpeg.exe"
)
WINDOW_TITLE = "Minecraft NeoForge* 1.21.1 - Multiplayer (3rd-party Server)"
FPS = 30
CAMERA_HZ = 20          # how often the camera is nudged; the server ticks at 20 too
EYE = 1.62              # /tp places the feet; the camera sits this far above them


# --------------------------------------------------------------------- helpers

def focus_game(close_menu: bool = False, toggle_hud: bool = False, required: bool = True) -> None:
    args = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(FOCUS)]
    if close_menu:
        args.append("-CloseMenu")
    if toggle_hud:
        args.append("-ToggleHud")
    result = subprocess.run(args, capture_output=True, text=True)
    if result.returncode == 0:
        return
    # Capture is scoped to the game's own window, so a failed re-focus costs frame rate rather
    # than the shot itself. Only the first grab of the run - which closes the menu and hides the
    # HUD - actually has to succeed.
    if required:
        sys.exit("could not focus the game window: " + result.stderr.strip())
    print("    (warning: could not re-focus; recording anyway)")



def start_capture(path: Path, seconds: float) -> subprocess.Popen:
    path.parent.mkdir(parents=True, exist_ok=True)
    return subprocess.Popen(
        [str(FFMPEG), "-hide_banner", "-loglevel", "error",
         "-f", "gdigrab", "-framerate", str(FPS), "-i", f"title={WINDOW_TITLE}",
         "-t", f"{seconds:.2f}",
         # h264 needs even dimensions and the window is rarely an even height.
         "-vf", "crop=trunc(iw/2)*2:trunc(ih/2)*2",
         "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p",
         "-y", str(path)],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def fly(r: Rcon, path, seconds: float, action=None) -> None:
    """Walk the camera along `path(t)` for `seconds`, t running 0 to 1.

    `action(r, t)` runs alongside it, for anything that has to happen mid-shot.
    """
    steps = max(1, int(seconds * CAMERA_HZ))
    started = time.time()
    for i in range(steps + 1):
        t = i / steps
        x, y, z, yaw, pitch = path(t)
        # Paths are written in terms of where the camera should be, so the feet go below that.
        r.cmd(f"tp {st.PLAYER} {x:.3f} {y - EYE:.3f} {z:.3f} {yaw:.2f} {pitch:.2f}")
        if action:
            action(r, t)
        target = started + (i + 1) / CAMERA_HZ
        slack = target - time.time()
        if slack > 0:
            time.sleep(slack)


def orbit(cx: float, cz: float, radius: float, height: float,
          start_deg: float, end_deg: float, pitch: float):
    """A camera path circling a point, always looking at it."""
    def path(t: float):
        deg = start_deg + (end_deg - start_deg) * t
        rad = math.radians(deg)
        x = cx + math.sin(rad) * radius
        z = cz - math.cos(rad) * radius
        # Minecraft yaw: 0 faces +Z, and increases clockwise looking down.
        yaw = -deg % 360
        return x, height, z, yaw, pitch
    return path


def push_in(x: float, z0: float, z1: float, height: float, yaw: float, pitch: float):
    def path(t: float):
        return x, height, z0 + (z1 - z0) * t, yaw, pitch
    return path


# ---------------------------------------------------------------------- scenes

def scene_rays(r: Rcon) -> float:
    """Rays perched on a log, camera orbiting."""
    st.clear_stage(r)
    st.floor(r, "minecraft:grass_block")
    st.hold(r, "minecraft:air")
    r.cmd(f"fill {st.STAGE_X - 2} {st.FLOOR_Y + 1} {st.STAGE_Z} "
          f"{st.STAGE_X + 2} {st.FLOOR_Y + 1} {st.STAGE_Z} minecraft:oak_log[axis=x]")
    for dx in (-1.5, -0.2, 1.1):
        st.ray(r, dx, 1.0, 0.0, yaw=170)
    st.ray(r, 2.4, 2.6, -1.2, yaw=200, flying=True)
    return 8.0


def scene_cat_crying(r: Rcon) -> float:
    """The Cash Cat sitting and weeping, camera pushing in."""
    st.clear_stage(r)
    st.floor(r, "minecraft:grass_block")
    st.hold(r, "minecraft:air")
    st.cash_cat(r, 0.0, 0.0, yaw=180)
    return 7.0


def scene_gold(r: Rcon) -> float:
    """A gold ingot lands and the cat stands up - the mood change, on camera."""
    st.clear_stage(r)
    st.floor(r, "minecraft:grass_block")
    st.hold(r, "minecraft:gold_ingot")
    # Left to think for itself, so the cheer-up actually changes what it does.
    r.cmd(f"summon hoodcraft:cash_cat {st.STAGE_X} {st.FLOOR_Y + 1} {st.STAGE_Z} "
          '{PersistenceRequired:1b,Silent:1b,Rotation:[180.0f,0f]}')
    return 9.0


def scene_brush(r: Rcon) -> float:
    """A suspicious block dug out with the Hood Brush, loot popping free."""
    st.clear_stage(r)
    st.floor(r, "minecraft:deepslate_bricks")
    r.cmd(f"fill {st.STAGE_X - st.STAGE_R} {st.FLOOR_Y - 1} {st.STAGE_Z - st.STAGE_R} "
          f"{st.STAGE_X + st.STAGE_R} {st.FLOOR_Y - 1} {st.STAGE_Z + st.STAGE_R} "
          "minecraft:deepslate_bricks")
    st.hold(r, "hoodcraft:hood_brush")
    for dx, block in ((-1, "minecraft:suspicious_gravel"), (0, "minecraft:suspicious_sand"),
                      (1, "minecraft:suspicious_gravel")):
        r.cmd(f"setblock {st.STAGE_X + dx} {st.FLOOR_Y} {st.STAGE_Z + 2} {block}")
    return 8.0


def scene_egg(r: Rcon) -> float:
    """An egg on slime cracking through its stages and hatching a kitten."""
    st.clear_stage(r)
    st.floor(r, "minecraft:grass_block")
    st.hold(r, "minecraft:air")
    r.cmd(f"setblock {st.STAGE_X} {st.FLOOR_Y + 1} {st.STAGE_Z} minecraft:slime_block")
    r.cmd(f"setblock {st.STAGE_X} {st.FLOOR_Y + 2} {st.STAGE_Z} hoodcraft:cash_cat_egg[hatch=0]")
    return 9.0


# Heights below are where the camera ends up, measured from the floor the set stands on. The
# subjects are small - a Ray is half a block - so these are much tighter than they look.
SCENES = [
    ("rays", scene_rays,
     lambda: orbit(st.STAGE_X, st.STAGE_Z, 2.7, st.FLOOR_Y + 2.5, -34, 34, 6), None),
    ("cat-crying", scene_cat_crying,
     lambda: push_in(st.STAGE_X + 0.1, st.STAGE_Z - 2.6, st.STAGE_Z - 1.35,
                     st.FLOOR_Y + 1.6, 0, 7), None),
    ("gold", scene_gold,
     lambda: orbit(st.STAGE_X, st.STAGE_Z, 2.5, st.FLOOR_Y + 2.0, -22, 22, 10),
     "gold"),
    # The suspicious blocks sit flat in the floor, so this one has to get right down on top of
    # them or they read as three specks in a paving slab.
    ("brush", scene_brush,
     lambda: push_in(st.STAGE_X, st.STAGE_Z - 0.9, st.STAGE_Z + 0.35,
                     st.FLOOR_Y + 1.9, 0, 42), "brush"),
    ("egg", scene_egg,
     lambda: orbit(st.STAGE_X, st.STAGE_Z, 2.2, st.FLOOR_Y + 2.7, -26, 26, 14), "egg"),
]


def make_action(kind, seconds):
    """Anything that has to happen partway through a shot."""
    fired = set()

    def at(t, mark):
        if t >= mark and mark not in fired:
            fired.add(mark)
            return True
        return False

    def action(r, t):
        if kind == "gold":
            # A third of the way in, the ingot lands and the cat perks up.
            if at(t, 0.34):
                r.cmd(f"particle minecraft:happy_villager {st.STAGE_X} {st.FLOOR_Y + 1.6} "
                      f"{st.STAGE_Z} 0.3 0.3 0.3 0.1 25")
                r.cmd("data merge entity @e[type=hoodcraft:cash_cat,limit=1] {CheerTicks:24000}")
        elif kind == "brush":
            for i, mark in enumerate((0.30, 0.55, 0.78)):
                if at(t, mark):
                    r.cmd(f"execute as {st.PLAYER} at {st.PLAYER} run hcbrush "
                          f"{st.STAGE_X + i - 1} {st.FLOOR_Y} {st.STAGE_Z + 2}")
        elif kind == "egg":
            for stage, mark in ((1, 0.28), (2, 0.52)):
                if at(t, mark):
                    r.cmd(f"setblock {st.STAGE_X} {st.FLOOR_Y + 2} {st.STAGE_Z} "
                          f"hoodcraft:cash_cat_egg[hatch={stage}]")
            if at(t, 0.76):
                # The last stage tick is what hatches it, so let the block do its own thing.
                r.cmd(f"setblock {st.STAGE_X} {st.FLOOR_Y + 2} {st.STAGE_Z} minecraft:air")
                r.cmd(f"summon hoodcraft:cash_cat {st.STAGE_X + 0.5} {st.FLOOR_Y + 2} "
                      f"{st.STAGE_Z + 0.5} {{Age:-24000,Silent:1b}}")
                r.cmd(f"particle minecraft:cloud {st.STAGE_X} {st.FLOOR_Y + 2.3} {st.STAGE_Z} "
                      "0.3 0.3 0.3 0.05 30")
    return action


# ------------------------------------------------------------------------ main

def main() -> int:
    if not FFMPEG.exists():
        sys.exit(f"ffmpeg not found at {FFMPEG}")

    r = Rcon()
    if not r.connect():
        sys.exit("could not reach RCON - is the dev server running?")
    if not r.passed(f"execute if entity {st.PLAYER}"):
        sys.exit(f"'{st.PLAYER}' is not on the server - start ./gradlew runScreenshots first")
    print("connected")

    st.prepare(r)
    CLIPS.mkdir(parents=True, exist_ok=True)
    for old in CLIPS.glob("*.mp4"):
        old.unlink()

    # F1 toggles, and the HUD state survives between runs, so recording twice in a row against
    # the same client would put the hotbar back. Start the client fresh for a take - it always
    # begins with the HUD showing, which makes exactly one press correct.
    focus_game(toggle_hud=True)
    # Spawning any subprocess steals focus, and Minecraft opens the pause menu when it loses focus
    # - pauseOnLostFocus:false does not stop it. Escape cannot be sent blind, because on a game
    # already in the world it *opens* the menu instead. So the screen is read first.
    if not game_screen.ensure_in_world():
        sys.exit("could not get the game back into the world; a menu is stuck open")
    # Chat lingers for about ten seconds, and the gamemode notice from prepare() would otherwise
    # sit across the first shot.
    print("  waiting for chat to fade...")
    time.sleep(11)

    made = []
    for name, build, path_fn, action_kind in SCENES:
        print(f"  staging {name}...", flush=True)
        seconds = build(r)
        time.sleep(1.2)                     # let entities settle and chunks catch up
        focus_game(required=False)
        game_screen.ensure_in_world()
        clip = CLIPS / f"{name}.mp4"
        proc = start_capture(clip, seconds)
        time.sleep(0.6)                     # ffmpeg needs a moment before the first frame
        fly(r, path_fn(), seconds - 0.6, make_action(action_kind, seconds))
        proc.wait(timeout=60)
        if proc.returncode != 0 or not clip.exists():
            err = proc.stderr.read().decode(errors="replace") if proc.stderr else ""
            print(f"    FAILED: {err.strip()[:200]}")
            continue
        print(f"    recorded {name}.mp4  ({clip.stat().st_size / 1048576:.1f} MB)")
        made.append(clip)

    if not made:
        sys.exit("no clips were recorded")

    listing = CLIPS / "clips.txt"
    listing.write_text("".join(f"file '{c.as_posix()}'\n" for c in made), encoding="utf-8")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    final = OUT_DIR / "showcase.mp4"
    subprocess.run([str(FFMPEG), "-hide_banner", "-loglevel", "error",
                    "-f", "concat", "-safe", "0", "-i", str(listing),
                    "-c", "copy", "-y", str(final)], check=True)
    print(f"\nwrote {final}  ({final.stat().st_size / 1048576:.1f} MB, {len(made)} scenes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
