#!/usr/bin/env python3
"""End-to-end worldgen check for the suspicious gravel added to ancient cities.

HoodCraft overrides a vanilla processor list to place suspicious gravel in ancient cities. Two
things have to be true and neither shows up in a compile: the gravel must actually appear in a
generated city, and vanilla's own degradation rules - which the override reproduces by hand -
must still be working.

    ./gradlew runServer          # in one shell, with RCON enabled in run/server.properties
    python tools/verify_worldgen.py

Locating and generating a city takes a couple of minutes. The script stops the server afterwards.
"""

from __future__ import annotations

import re
import sys
import time

from rcon import Checks, Rcon

# 32 x 32 x 32 is exactly the 32768-block volume limit on /fill.
BOX = 32
Y_LOW, Y_HIGH = -59, -28


def count_blocks(r: Rcon, cx: int, cz: int, block: str, span: int = 96) -> int:
    """Count `block` across a `span`-wide footprint centred on the city.

    `fill ... replace <block>` reports how many blocks it changed, which makes a usable counter,
    but /fill caps out at 32768 blocks - so the footprint is swept in 32x32 tiles. It has to cover
    more than the centre: the city centre and the outlying structures are built from different
    template pools with different processor lists, and sampling only the middle misses whichever
    of the two is broken.

    Destructive by design - it replaces matches with air. Run it on a throwaway world.
    """
    total = 0
    half = span // 2
    for x0 in range(cx - half, cx + half, BOX):
        for z0 in range(cz - half, cz + half, BOX):
            out = r.cmd(f"fill {x0} {Y_LOW} {z0} {x0 + BOX - 1} {Y_HIGH} {z0 + BOX - 1} "
                        f"minecraft:air replace {block}")
            if "Successfully filled" in out:
                match = re.search(r"(\d+)", out)
                total += int(match.group(1)) if match else 0
    return total


def main() -> int:
    r = Rcon()
    if not r.connect():
        sys.exit("could not reach RCON - is the dev server running with rcon enabled?")
    print("connected\n")
    check = Checks()

    r.cmd("gamerule doMobSpawning false")

    print("Locating an ancient city (this generates chunks and takes a while)")
    out = r.cmd("locate structure minecraft:ancient_city")
    print(f"     {out.strip()[:120]}")
    coords = re.search(r"\[(-?\d+), (-?\d+|~), (-?\d+)\]", out)
    if not coords:
        sys.exit(f"could not parse /locate output: {out!r}")
    cx, cz = int(coords.group(1)), int(coords.group(3))
    print(f"     city centre at x={cx} z={cz}")

    # /locate finds where a structure will be without necessarily generating it, so the chunks
    # have to be forced into existence before anything can be counted in them.
    r.cmd(f"forceload add {cx - 48} {cz - 48} {cx + 48} {cz + 48}")
    for _ in range(24):
        time.sleep(5)
        if r.passed(f"execute if block {cx} -51 {cz} #minecraft:deepslate_ore_replaceables") or \
           r.passed(f"execute unless block {cx} -51 {cz} minecraft:air"):
            break
    time.sleep(10)

    gravel = count_blocks(r, cx, cz, "minecraft:suspicious_gravel")
    check("suspicious gravel generated in the ancient city", gravel > 0,
          f"{gravel} blocks across a 96x{Y_HIGH - Y_LOW + 1}x96 sweep")

    cracked = count_blocks(r, cx, cz, "minecraft:cracked_deepslate_bricks")
    check("vanilla's degradation survived the processor override", cracked > 0,
          f"{cracked} cracked deepslate bricks")

    r.cmd(f"forceload remove {cx - 48} {cz - 48} {cx + 48} {cz + 48}")
    status = check.summary()
    r.cmd("stop")
    return status


if __name__ == "__main__":
    raise SystemExit(main())
