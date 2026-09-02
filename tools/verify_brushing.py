#!/usr/bin/env python3
"""Actually brush blocks with the Hood Brush and check what falls out.

Brushing is a use-item-over-time action driven by holding right-click. There is no vanilla command
equivalent and synthetic mouse input does not reach the game reliably, so the mod registers a
dev-only `/hcbrush <pos>` (see HCTestCommands) that runs a full dig through the real item code.
The loot table rolling correctly on its own - which `verify_gameplay.py` proves - says nothing
about whether the item ever reaches the ground, and that gap is how this shipped broken once.

A player has to be connected, because the brush routine wants one.

    ./gradlew runServer          # one shell, RCON enabled
    ./gradlew runScreenshots     # another - joins the dev server automatically as "Dev"
    python tools/verify_brushing.py

The script stops the server when it finishes.
"""

from __future__ import annotations

import re
import sys
import time

from rcon import Checks, Rcon

PLAYER = "Dev"
X, Z = 60, 60
FLOOR_Y = 100                 # floor blocks occupy this layer, top surface at 101
TARGET = (X, FLOOR_Y + 2, Z + 2)   # brushed block, sitting at roughly eye level

# A stand-in for "this block generated naturally", which is all a vanilla loot table means here.
NATURAL_TABLE = "minecraft:archaeology/trail_ruins_common"

EXPECTED = {
    "minecraft:nautilus_shell",
    "minecraft:emerald",
    "minecraft:leather_boots",
    "minecraft:stone_hoe",
}


def dropped_items(r: Rcon) -> list[str]:
    """Every item entity lying near the target block."""
    out = r.cmd(f"data get entity @e[type=item,limit=1,sort=nearest,"
                f"x={X},y={FLOOR_Y},z={Z},distance=..8] Item")
    return re.findall(r'id:\s*"([^"]+)"', out)


def build(r: Rcon, block: str, natural: bool) -> None:
    tx, ty, tz = TARGET
    r.cmd("kill @e[type=item]")
    r.cmd(f"fill {X - 4} {FLOOR_Y} {Z - 4} {X + 4} {FLOOR_Y + 6} {Z + 6} minecraft:air")
    r.cmd(f"fill {X - 4} {FLOOR_Y} {Z - 4} {X + 4} {FLOOR_Y} {Z + 6} minecraft:stone")
    # Suspicious gravel is gravity-affected, so the target needs something solid underneath it.
    r.cmd(f"setblock {tx} {FLOOR_Y + 1} {tz} minecraft:stone")
    r.cmd(f"setblock {tx} {ty} {tz} {block}")
    if natural:
        r.cmd(f'data merge block {tx} {ty} {tz} {{LootTable:"{NATURAL_TABLE}"}}')
    r.cmd(f"item replace entity {PLAYER} weapon.mainhand with hoodcraft:hood_brush")
    r.cmd(f"tp {PLAYER} {X + 0.5} {FLOOR_Y + 1} {Z + 0.5} 0 3")
    time.sleep(0.5)


def brush_once(r: Rcon, label: str, block: str, natural: bool) -> list[str]:
    build(r, block, natural)
    tx, ty, tz = TARGET
    print(f"    {label}: {r.cmd(f'execute as {PLAYER} at {PLAYER} run hcbrush {tx} {ty} {tz}').strip()[:50]}")
    time.sleep(0.6)
    items = dropped_items(r)
    turned = "Test passed" in r.cmd(f"execute unless block {tx} {ty} {tz} {block}")
    print(f"      dug through: {turned}   dropped: {', '.join(items) or 'nothing'}")
    return items


def main() -> int:
    r = Rcon()
    if not r.connect():
        sys.exit("could not reach RCON - is the dev server running?")
    print("connected")
    if "Test passed" not in r.cmd(f"execute if entity {PLAYER}"):
        sys.exit(f"'{PLAYER}' is not on the server - start ./gradlew runScreenshots first")

    check = Checks()
    r.cmd(f"op {PLAYER}")
    r.cmd(f"gamemode creative {PLAYER}")
    r.cmd("gamerule doMobSpawning false")
    r.cmd("gamerule sendCommandFeedback false")
    r.cmd("time set 6000")
    r.cmd("weather clear 1000000")
    r.cmd(f"forceload add {X - 16} {Z - 16} {X + 16} {Z + 16}")

    print("\n1. Player-placed suspicious gravel")
    items = brush_once(r, "gravel", "minecraft:suspicious_gravel", natural=False)
    check("player-placed gravel yields loot", bool(items), ", ".join(items) or "nothing")
    check("and it is from the Hood Brush table", set(items) <= EXPECTED and bool(items),
          ", ".join(sorted(set(items) - EXPECTED)) or "as expected")

    print("\n2. Player-placed suspicious sand")
    items = brush_once(r, "sand", "minecraft:suspicious_sand", natural=False)
    check("player-placed sand yields loot", bool(items), ", ".join(items) or "nothing")

    print("\n3. Naturally generated gravel (carries a vanilla loot table)")
    items = brush_once(r, "natural gravel", "minecraft:suspicious_gravel", natural=True)
    check("natural gravel yields loot", bool(items), ", ".join(items) or "nothing")
    check("vanilla's table was replaced by ours", set(items) <= EXPECTED and bool(items),
          ", ".join(sorted(set(items) - EXPECTED)) or "no vanilla trail-ruins loot")

    r.cmd("kill @e[type=item]")
    r.cmd(f"forceload remove {X - 16} {Z - 16} {X + 16} {Z + 16}")
    r.cmd("gamerule sendCommandFeedback true")
    status = check.summary()
    r.cmd("stop")
    return status


if __name__ == "__main__":
    raise SystemExit(main())
