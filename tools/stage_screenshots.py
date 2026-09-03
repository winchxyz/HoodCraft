#!/usr/bin/env python3
"""Stage and capture release screenshots of HoodCraft.

Composing shots by hand means flying around in creative and hoping; this drives the whole thing
over RCON instead, so a shot is a few lines of setup and is reproducible when the art changes.

    ./gradlew runServer          # one shell, RCON enabled
    ./gradlew runScreenshots     # another - joins the server automatically as "Dev"
    python tools/stage_screenshots.py            # every scene
    python tools/stage_screenshots.py hero egg   # just these

Captures land in docs/screenshots/. The client window is brought to the front for each capture,
because the game renders through OpenGL and only an on-screen copy comes back with pixels in it.
"""

from __future__ import annotations

import subprocess
import sys
import time
from pathlib import Path

from rcon import Rcon

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "docs" / "screenshots"
CAPTURE = ROOT / "tools" / "capture_window.ps1"

PLAYER = "Dev"

# A flat, quiet stage well above the terrain, so shots do not depend on what the seed happened to
# generate. Each scene builds its own set on top of a shared floor slab.
STAGE_X, STAGE_Z = 40, 40
FLOOR_Y = 100                 # the block layer the set stands on
FEET_Y = FLOOR_Y + 1          # where the player's feet go, one above the floor
STAGE_R = 14                  # half-width of the slab, generous enough to hold the camera


def capture(name: str, hide_hud: bool = True) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out = OUT_DIR / f"{name}.png"
    args = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", str(CAPTURE), "-Out", str(out)]
    if hide_hud:
        args.append("-HideHud")
    result = subprocess.run(args, capture_output=True, text=True)
    print(f"    {result.stdout.strip() or result.stderr.strip()}")


def prepare(r: Rcon) -> None:
    """World-wide setup that every scene wants."""
    r.cmd(f"op {PLAYER}")
    r.cmd(f"gamemode creative {PLAYER}")
    r.cmd("gamerule doDaylightCycle false")
    r.cmd("gamerule doWeatherCycle false")
    r.cmd("gamerule doMobSpawning false")
    r.cmd("gamerule sendCommandFeedback false")
    r.cmd("gamerule doFireTick false")
    r.cmd("time set 6000")
    r.cmd("weather clear 1000000")
    r.cmd("difficulty peaceful")
    r.cmd(f"forceload add {STAGE_X - 40} {STAGE_Z - 40} {STAGE_X + 40} {STAGE_Z + 40}")


def clear_stage(r: Rcon) -> None:
    r.cmd("kill @e[type=hoodcraft:robin]")
    r.cmd("kill @e[type=item]")
    r.cmd(f"fill {STAGE_X - STAGE_R - 2} {FLOOR_Y - 1} {STAGE_Z - STAGE_R - 2} "
          f"{STAGE_X + STAGE_R + 2} {FLOOR_Y + 16} {STAGE_Z + STAGE_R + 2} minecraft:air")


def floor(r: Rcon, block: str) -> None:
    """Lay the slab the camera and the set both stand on."""
    r.cmd(f"fill {STAGE_X - STAGE_R} {FLOOR_Y} {STAGE_Z - STAGE_R} "
          f"{STAGE_X + STAGE_R} {FLOOR_Y} {STAGE_Z + STAGE_R} {block}")


def backdrop(r: Rcon, block: str, at_z: int, height: int = 6) -> None:
    r.cmd(f"fill {STAGE_X - STAGE_R} {FLOOR_Y + 1} {STAGE_Z + at_z} "
          f"{STAGE_X + STAGE_R} {FLOOR_Y + height} {STAGE_Z + at_z} {block}")


def stand(r: Rcon, dx: float, dz: float, yaw: float, pitch: float) -> None:
    """Put the player's feet on the slab. The camera sits 1.62 above this."""
    r.cmd(f"tp {PLAYER} {STAGE_X + dx} {FEET_Y} {STAGE_Z + dz} {yaw} {pitch}")


def hold(r: Rcon, item: str) -> None:
    r.cmd(f"item replace entity {PLAYER} weapon.mainhand with {item}")


def robin(r: Rcon, dx: float, dy: float, dz: float, yaw: float = 180.0,
          flying: bool = False) -> None:
    """Place a Robin. By default it is left to land on whatever is under it.

    Gravity matters for how it looks, not just where it ends up: the model reads `onGround()` to
    decide between the perched pose and the flight pose, so a bird pinned in the air with NoGravity
    renders frozen mid-wingbeat. Perched birds therefore fall the last fraction of a block onto
    their perch; pass flying=True only when the beating-wings pose is the one you want.
    """
    # NoAI freezes the entity outright, so it never falls and never reports being on the ground -
    # and the model picks its pose from onGround(), which would leave every bird stuck mid-wingbeat.
    # "Sitting" is the way out: TamableAnimal reads it straight from NBT into the sitting pose,
    # which is the perched look wanted here, and it does not depend on physics at all.
    tags = "NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Silent:1b"
    if not flying:
        tags += ",Sitting:1b"
    r.cmd(f"summon hoodcraft:robin {STAGE_X + dx} {FLOOR_Y + 1 + dy} {STAGE_Z + dz} "
          '{%s,Rotation:[%.1ff,0f]}' % (tags, yaw))


# --------------------------------------------------------------------- scenes
# Yaw 0 faces south (+Z), so a camera south of the set looks back at it with yaw 0.

def cash_cat(r: Rcon, dx: float, dz: float, yaw: float = 180.0, cheered: bool = False) -> None:
    """Place a Cash Cat. Cheered ones stand and behave like an ordinary cat.

    Unlike the Robin, no Sitting tag is needed: the model drops into the mascot slouch whenever the
    cat is miserable and not moving, which is exactly the state a frozen NoAI cat is in.
    """
    tags = "NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Silent:1b"
    if cheered:
        tags += ",CheerTicks:24000"
    r.cmd(f"summon hoodcraft:cash_cat {STAGE_X + dx} {FLOOR_Y + 1} {STAGE_Z + dz} "
          '{%s,Rotation:[%.1ff,0f]}' % (tags, yaw))


def scene_cash_cat(r: Rcon) -> None:
    """The Cash Cat sitting and weeping, with a cheered one alongside for contrast."""
    clear_stage(r)
    floor(r, "minecraft:polished_deepslate")
    backdrop(r, "minecraft:deepslate_tiles", at_z=7, height=8)
    hold(r, "minecraft:air")
    for dx in (-5, 5):
        r.cmd(f"setblock {STAGE_X + dx} {FLOOR_Y + 4} {STAGE_Z + 6} minecraft:soul_lantern")
    cash_cat(r, 0.1, 2.1, 145)
    cash_cat(r, -1.9, 3.4, 205, cheered=True)
    stand(r, 0.0, 0.0, 0, 18)


def scene_hero(r: Rcon) -> None:
    """Robins on a grassy ledge with open sky behind them."""
    clear_stage(r)
    floor(r, "minecraft:grass_block")
    # A stripped-log perch rather than grass: a green bird on a green field disappears.
    r.cmd(f"fill {STAGE_X - 6} {FLOOR_Y + 1} {STAGE_Z + 3} {STAGE_X + 6} {FLOOR_Y + 1} {STAGE_Z + 3} minecraft:oak_log[axis=x]")
    r.cmd(f"setblock {STAGE_X - 7} {FLOOR_Y + 1} {STAGE_Z + 3} minecraft:oak_log[axis=y]")
    r.cmd(f"setblock {STAGE_X + 7} {FLOOR_Y + 1} {STAGE_Z + 3} minecraft:oak_log[axis=y]")
    # Cut the ground away past the perch so open sky, not more grass, sits behind the birds.
    r.cmd(f"fill {STAGE_X - STAGE_R} {FLOOR_Y} {STAGE_Z + 4} {STAGE_X + STAGE_R} {FLOOR_Y} {STAGE_Z + STAGE_R} minecraft:air")
    hold(r, "minecraft:air")
    robin(r, -2.5, 1.0, 3.5, 140)
    robin(r, 0.5, 1.0, 3.5, 175)
    robin(r, 3.5, 1.0, 3.5, 210)
    robin(r, -0.6, 3.0, 5.5, 160, flying=True)
    stand(r, 0.5, -0.4, 0, -3)


def scene_portrait(r: Rcon) -> None:
    """One Robin in side profile at eye level, on dark stone so the green reads."""
    clear_stage(r)
    floor(r, "minecraft:polished_deepslate")
    backdrop(r, "minecraft:deepslate_tiles", at_z=5, height=8)
    hold(r, "minecraft:air")
    # A pedestal brings the bird up to roughly eye level (feet + 1.62), turned side-on.
    r.cmd(f"fill {STAGE_X} {FLOOR_Y + 1} {STAGE_Z + 2} {STAGE_X + 1} {FLOOR_Y + 1} {STAGE_Z + 2} minecraft:polished_deepslate")
    robin(r, 0.5, 1.0, 2.6, 118)
    stand(r, 0.5, 0.7, 0, 9)


def scene_brush(r: Rcon) -> None:
    """The Hood Brush in hand, over a patch of suspicious sand and gravel."""
    clear_stage(r)
    # A dark floor so the sand and gravel patches read as patches rather than as more floor - laid
    # two thick, because suspicious sand and gravel are gravity-affected and fall straight through
    # a single layer with air beneath it.
    r.cmd(f"fill {STAGE_X - STAGE_R} {FLOOR_Y - 1} {STAGE_Z - STAGE_R} "
          f"{STAGE_X + STAGE_R} {FLOOR_Y - 1} {STAGE_Z + STAGE_R} minecraft:deepslate_bricks")
    floor(r, "minecraft:deepslate_bricks")
    backdrop(r, "minecraft:deepslate_tiles", at_z=6, height=6)
    r.cmd(f"setblock {STAGE_X - 4} {FLOOR_Y + 3} {STAGE_Z + 5} minecraft:soul_lantern")
    r.cmd(f"setblock {STAGE_X + 5} {FLOOR_Y + 3} {STAGE_Z + 5} minecraft:soul_lantern")

    # Suspicious blocks on the left, their ordinary counterparts on the right, so the difference
    # is visible in one frame instead of having to be taken on trust.
    for dx in (-3, -2):
        for dz in (2, 3):
            r.cmd(f"setblock {STAGE_X + dx} {FLOOR_Y} {STAGE_Z + dz} minecraft:suspicious_gravel")
    for dx in (-1, 0):
        for dz in (2, 3):
            r.cmd(f"setblock {STAGE_X + dx} {FLOOR_Y} {STAGE_Z + dz} minecraft:suspicious_sand")
    for dx in (1, 2):
        r.cmd(f"setblock {STAGE_X + dx} {FLOOR_Y} {STAGE_Z + 2} minecraft:gravel")
        r.cmd(f"setblock {STAGE_X + dx} {FLOOR_Y} {STAGE_Z + 3} minecraft:sand")

    hold(r, "hoodcraft:hood_brush")
    stand(r, -0.5, 0.2, 0, 34)


def scene_egg(r: Rcon) -> None:
    """The three hatch stages, each on the substrate that sets its timer."""
    clear_stage(r)
    floor(r, "minecraft:moss_block")
    backdrop(r, "minecraft:deepslate_tiles", at_z=5, height=7)
    for dx, base in ((-2, "minecraft:white_wool"), (0, "minecraft:slime_block"), (2, "minecraft:honey_block")):
        r.cmd(f"setblock {STAGE_X + dx} {FLOOR_Y + 1} {STAGE_Z + 3} {base}")
    for dx, stage in ((-2, 0), (0, 1), (2, 2)):
        r.cmd(f"setblock {STAGE_X + dx} {FLOOR_Y + 2} {STAGE_Z + 3} hoodcraft:robin_egg[hatch={stage}]")
    hold(r, "minecraft:air")
    stand(r, 0.0, 0.8, 0, 6)


def scene_items(r: Rcon) -> None:
    """Feather, brush and egg as dropped items."""
    clear_stage(r)
    floor(r, "minecraft:polished_deepslate")
    backdrop(r, "minecraft:deepslate_tiles", at_z=4, height=6)
    hold(r, "hoodcraft:hood_brush")
    for dx, item in ((-0.8, "hoodcraft:black_feather"), (0.4, "hoodcraft:hood_brush"),
                     (1.6, "hoodcraft:robin_egg")):
        r.cmd(f"summon item {STAGE_X + dx} {FLOOR_Y + 2.2} {STAGE_Z + 2.2} "
              '{Item:{id:"%s",count:1},Age:-32768,PickupDelay:32767,NoGravity:1b,Motion:[0.0,0.0,0.0]}' % item)
    stand(r, 0.4, 0.9, 0, 3)


# name -> (builder, hide the HUD?). The brush scene keeps the HUD, because F1 hides the held item
# along with it and the whole point of that shot is the brush in hand.
SCENES = {
    "hero": (scene_hero, True),
    "portrait": (scene_portrait, True),
    "brush": (scene_brush, False),
    "egg": (scene_egg, True),
    "items": (scene_items, True),
    "cashcat": (scene_cash_cat, True),
}


def main() -> int:
    wanted = sys.argv[1:] or list(SCENES)
    unknown = [n for n in wanted if n not in SCENES]
    if unknown:
        sys.exit(f"unknown scene(s): {', '.join(unknown)}. Known: {', '.join(SCENES)}")

    r = Rcon()
    if not r.connect():
        sys.exit("could not reach RCON - is the dev server running?")
    print("connected")

    if "Test passed" not in r.cmd(f"execute if entity {PLAYER}"):
        sys.exit(f"'{PLAYER}' is not on the server - start ./gradlew runScreenshots and let it join")

    prepare(r)
    for name in wanted:
        print(f"  staging {name}")
        builder, hide_hud = SCENES[name]
        builder(r)
        time.sleep(2.5)     # let chunks light and the mobs settle before the shot
        capture(name, hide_hud=hide_hud)

    clear_stage(r)
    r.cmd("gamerule sendCommandFeedback true")
    print(f"\nwrote {len(wanted)} shot(s) to {OUT_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
