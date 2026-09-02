#!/usr/bin/env python3
"""Functional checks for HoodCraft, driven against a running dev server over RCON.

A compile proves the API calls are right; only a running game proves the loot tables roll, the
egg hatches on schedule and the mob actually spawns. Start the server first, then run this:

    ./gradlew runServer          # in one shell, with RCON enabled in run/server.properties
    python tools/verify_gameplay.py

The script stops the server when it finishes.
"""

from __future__ import annotations

import re
import sys
from collections import Counter

from rcon import Checks, Rcon

SITE_STR = "0 200 0"

# The pet egg is deliberately absent while it is WIP: there is only one mob so far, and it spawns
# in the world already, so an egg for it would be a circular reward. The other four keep their
# original weights (4/4/3/3 = 14) rather than being rescaled, so re-adding a weight-1 egg entry
# restores exactly 1/15 = 6.67% with no other change.
EXPECTED_BRUSH_LOOT = {
    "minecraft:nautilus_shell",
    "minecraft:emerald",
    "minecraft:leather_boots",
    "minecraft:stone_hoe",
}


def collect_brush_loot(r: Rcon, barrels: int = 60, rolls_per_barrel: int = 40) -> Counter:
    """Roll the brush's loot table into barrels and tally what comes out.

    Rolls are batched into fresh barrels because leather boots and stone hoes do not stack: a
    barrel's 27 slots fill up long before a statistically useful sample is collected.
    """
    tally: Counter = Counter()
    for _ in range(barrels):
        r.cmd(f"setblock {SITE_STR} minecraft:air")
        r.cmd(f"setblock {SITE_STR} minecraft:barrel")
        for _ in range(rolls_per_barrel):
            r.cmd(f"loot insert {SITE_STR} loot hoodcraft:archaeology/hood_brushing")
        snbt = r.cmd(f"data get block {SITE_STR} Items")
        for count, item in re.findall(r'count:\s*(\d+),\s*Slot:\s*\d+b,\s*id:\s*"([^"]+)"', snbt):
            tally[item] += int(count)
    r.cmd(f"setblock {SITE_STR} minecraft:air")
    return tally


def egg_state(r: Rcon, at: str) -> str:
    """"hatch=0/1/2" for an egg still in place, or "gone" once it has hatched."""
    for level in (0, 1, 2):
        if r.passed(f"execute if block {at} hoodcraft:robin_egg[hatch={level}]"):
            return f"hatch={level}"
    return "gone"


def place_egg(r: Rcon, x: int, substrate: str) -> str:
    """Place a fresh egg on `substrate` at its own x, and return the position string.

    Each egg gets its own coordinates on purpose. Minecraft's scheduled-tick set is keyed on
    position and block alone, so scheduling a tick where one is already pending is silently
    dropped - meaning a replacement egg at the same block would inherit the previous egg's timer
    and be measured against the wrong substrate. (Vanilla's sniffer egg behaves the same way.)
    """
    site, below = f"{x} 200 0", f"{x} 199 0"
    r.cmd(f"setblock {below} {substrate}")
    r.cmd(f"setblock {site} minecraft:air")
    r.cmd(f"setblock {site} hoodcraft:robin_egg")
    return site


def hatch_timeline(r: Rcon, site: str, step: int = 600, max_ticks: int = 9000) -> dict[str, int]:
    """Record the tick at which the egg at `site` is first seen in each hatch state."""
    start = r.gametime()
    seen: dict[str, int] = {}
    elapsed = 0
    while elapsed <= max_ticks:
        state = egg_state(r, site)
        seen.setdefault(state, elapsed)
        if state == "gone":
            break
        r.sprint(step)
        elapsed = r.gametime() - start
    return seen


def robin_age(r: Rcon) -> int | None:
    """A hatchling's Age, which is negative while it is still a chick.

    AgeableMob stores its age as an `Age` int; `IsBaby` is a Monster thing (zombies, piglins) and
    matches nothing here.
    """
    out = r.cmd("data get entity @e[type=hoodcraft:robin,limit=1,sort=nearest] Age")
    match = re.search(r"(-?\d+)\s*$", out.strip())
    return int(match.group(1)) if match else None


def main() -> int:
    r = Rcon()
    if not r.connect():
        sys.exit("could not reach RCON - is the dev server running with rcon enabled?")
    print("connected\n")
    check = Checks()

    r.cmd("gamerule doMobSpawning false")
    r.cmd("gamerule randomTickSpeed 0")
    r.cmd("forceload add 0 0")
    r.cmd(f"setblock {SITE_STR} minecraft:air")

    # ------------------------------------------------------- 1. registration
    print("1. Registration and entity loot")
    check("summon hoodcraft:robin", "Summoned" in r.cmd(f"summon hoodcraft:robin {SITE_STR}"))
    check("robin exists in world", r.passed("execute if entity @e[type=hoodcraft:robin]"))

    r.cmd("kill @e[type=hoodcraft:robin]")
    r.sprint(5)
    check("killing a Robin drops a Black Feather", r.passed(
        f'execute positioned {SITE_STR} if entity '
        f'@e[type=item,distance=..32,nbt={{Item:{{id:"hoodcraft:black_feather"}}}}]'))
    r.cmd("kill @e[type=item]")

    # --------------------------------------------------- 2. brush loot table
    print("\n2. Hood Brush loot table  (no egg: it is WIP)")
    tally = collect_brush_loot(r)
    total = sum(tally.values())
    print(f"     {total} rolls collected")
    for item, n in tally.most_common():
        print(f"       {item:<34} {n:>5}  {100.0 * n / max(total, 1):>5.2f}%")

    check("no pet eggs while the egg is WIP", tally.get("hoodcraft:robin_egg", 0) == 0,
          f"{tally.get('hoodcraft:robin_egg', 0)} eggs in {total} rolls")
    check("only the four intended items", set(tally) == EXPECTED_BRUSH_LOOT,
          f"unexpected: {sorted(set(tally) - EXPECTED_BRUSH_LOOT) or 'none'}")

    # ------------------------------------------------------- 3. egg hatching
    # Sampled as a timeline rather than asserted at fixed instants. A stage takes
    # 2000 + random(0..299) ticks on slime, so a check pinned to one exact tick count sits right
    # on the boundary of that random offset and flakes; watching the whole run does not.
    print("\n3. Robin Egg hatching  (slime: 3 stages of 2000-2299 ticks, so 6000-6897 total)")
    slime_site = place_egg(r, 0, "minecraft:slime_block")
    timeline = hatch_timeline(r, slime_site)
    for state, tick in timeline.items():
        print(f"       {state:<8} first seen at +{tick} ticks")

    check("starts uncracked", timeline.get("hatch=0") == 0)
    check("passes through hatch=1", "hatch=1" in timeline)
    check("passes through hatch=2", "hatch=2" in timeline)
    hatched_at = timeline.get("gone")
    check("hatches, and inside the designed window", hatched_at is not None
          and 5500 <= hatched_at <= 7600, f"at +{hatched_at} ticks")
    check("a Robin hatched from it",
          r.passed(f"execute positioned {slime_site} if entity @e[type=hoodcraft:robin,distance=..16]"))
    age = robin_age(r)
    check("and it is a chick", age is not None and age < 0, f"Age={age}")
    r.cmd("kill @e[type=hoodcraft:robin]")

    # -------------------------------------------- 4. the substrate matters
    # Without this control, "it hatched" would pass even if every egg ignored what it sits on.
    print("\n4. Substrate boost actually matters")
    stone_site = place_egg(r, 4, "minecraft:stone")
    wool_site = place_egg(r, 8, "minecraft:white_wool")
    r.sprint(6600)
    check("on stone, still uncracked at 6600 ticks (30 min: a stage is 12000)",
          r.passed(f"execute if block {stone_site} hoodcraft:robin_egg[hatch=0]"),
          egg_state(r, stone_site))
    check("on wool, cracked once by 6600 ticks (15 min: a stage is 6000)",
          r.passed(f"execute if block {wool_site} hoodcraft:robin_egg[hatch=1]"),
          egg_state(r, wool_site))

    # ------------------------------------------------------- 5. the recipe
    # Driven through a Crafter rather than asserted from the JSON: a recipe that parses can still
    # fail to match. The pattern is one column - feather, copper ingot, stick - so it goes in the
    # left-hand column of the 3x3 grid, slots 0, 3 and 6.
    print("\n5. Hood Brush recipe")
    r.cmd("kill @e[type=item]")
    r.cmd("setblock 12 200 0 minecraft:air")
    r.cmd("setblock 12 199 0 minecraft:stone")
    r.cmd("setblock 12 200 0 minecraft:crafter")
    for slot, item in ((0, "hoodcraft:black_feather"),
                       (3, "minecraft:copper_ingot"),
                       (6, "minecraft:stick")):
        r.cmd(f"item replace block 12 200 0 container.{slot} with {item}")
    r.cmd("setblock 13 200 0 minecraft:redstone_block")   # rising edge fires the crafter
    r.sprint(4)
    # Checked world-wide rather than within a radius: the crafter ejects the result into open air
    # at y=200 and it falls fast, so even a few ticks put it outside any sensible radius. All other
    # item entities were cleared a moment ago, so anything found here is the thing just crafted.
    check("crafts a Hood Brush from feather + copper + stick",
          r.passed('execute if entity @e[type=item,nbt={Item:{id:"hoodcraft:hood_brush"}}]'),
          r.cmd("data get block 12 200 0 Items").strip()[-60:])

    # ---------------------------------------------------------- teardown
    r.cmd("kill @e[type=hoodcraft:robin]")
    r.cmd("kill @e[type=item]")
    for x in (0, 4, 8, 12, 13):
        r.cmd(f"setblock {x} 200 0 minecraft:air")
    r.cmd("forceload remove 0 0")

    status = check.summary()
    r.cmd("stop")
    return status


if __name__ == "__main__":
    raise SystemExit(main())
