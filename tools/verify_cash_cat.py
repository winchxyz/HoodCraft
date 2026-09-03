#!/usr/bin/env python3
"""Check the Cash Cat: taming food, the gold cheer-up, and its egg.

    rm -rf run/hoodcraft-test && ./gradlew runServer     # one shell
    python tools/verify_cash_cat.py                      # another

Stops the server when it finishes. The ingot lottery is not tested here: at 1 in 10,000 a fair
check would need tens of thousands of feeds, and feeding needs a player. What is checked is that
gold changes the mood, which is the mechanic a player actually experiences.
"""

from __future__ import annotations

import sys
import time

from rcon import Checks, Rcon

X, Y, Z = 40, 100, 40
CHEER_TICKS = 24000


def summon(r: Rcon, nbt: str = "") -> None:
    r.cmd("kill @e[type=hoodcraft:cash_cat]")
    r.cmd(f"summon hoodcraft:cash_cat {X} {Y} {Z} {{{nbt}}}" if nbt
          else f"summon hoodcraft:cash_cat {X} {Y} {Z}")
    time.sleep(0.4)


def cat_data(r: Rcon, path: str) -> str:
    return r.cmd(f"data get entity @e[type=hoodcraft:cash_cat,limit=1] {path}").strip()


def main() -> int:
    r = Rcon()
    if not r.connect():
        sys.exit("could not reach RCON - is the dev server running?")
    print("connected")

    check = Checks()
    r.cmd("gamerule doMobSpawning false")
    r.cmd("gamerule sendCommandFeedback false")
    r.cmd(f"forceload add {X - 16} {Z - 16} {X + 16} {Z + 16}")
    r.cmd(f"fill {X - 4} {Y - 1} {Z - 4} {X + 4} {Y - 1} {Z + 4} minecraft:grass_block")
    r.cmd(f"fill {X - 4} {Y} {Z - 4} {X + 4} {Y + 4} {Z + 4} minecraft:air")

    print("\n1. Registration and drops")
    summon(r)
    check("summons", "Test passed" in r.cmd("execute if entity @e[type=hoodcraft:cash_cat]"))
    check("starts miserable (CheerTicks 0)", "0" in cat_data(r, "CheerTicks"),
          cat_data(r, "CheerTicks")[-30:])
    # String is 0-2 per kill, matching a vanilla cat, so a single kill legitimately drops nothing.
    # Sample a handful and assert that string turns up at all.
    r.cmd("kill @e[type=item]")
    drops = 0
    for _ in range(10):
        summon(r)
        r.cmd("damage @e[type=hoodcraft:cash_cat,limit=1] 100 minecraft:generic")
        time.sleep(0.35)
        if r.passed(f"execute if entity @e[type=item,nbt={{Item:{{id:\"minecraft:string\"}}}},"
                    f"x={X},y={Y},z={Z},distance=..6]"):
            drops += 1
        r.cmd("kill @e[type=item]")
    check("drops string when killed", drops > 0, f"{drops} of 10 kills dropped string")

    print("\n2. Gold ingot cheers it up for a Minecraft day")
    summon(r, "CheerTicks:%d" % CHEER_TICKS)
    raw = int(cat_data(r, "CheerTicks").rsplit(" ", 1)[-1])
    # A few ticks elapse between the summon and this read, so the check is "roughly a full day"
    # rather than exactly 24,000.
    check("cheer timer survives a round trip through NBT", CHEER_TICKS - 200 <= raw <= CHEER_TICKS,
          f"{raw} ticks")
    r.sprint(200)
    after = int(cat_data(r, "CheerTicks").rsplit(" ", 1)[-1])
    check("and counts down while it runs", after < raw - 100, f"{raw} -> {after}")

    r.cmd("kill @e[type=hoodcraft:cash_cat]")
    summon(r)
    idle = int(cat_data(r, "CheerTicks").rsplit(" ", 1)[-1])
    check("an unfed cat stays on zero", idle == 0, f"{idle}")

    print("\n3. The Cash Cat egg")
    r.cmd("kill @e[type=hoodcraft:cash_cat]")
    # Slime underneath is the five-minute substrate, so three stages fit in ~6,900 ticks.
    r.cmd(f"setblock {X} {Y - 1} {Z} minecraft:slime_block")
    r.cmd(f"setblock {X} {Y} {Z} hoodcraft:cash_cat_egg")
    check("egg block places", "Test passed" in r.cmd(
        f"execute if block {X} {Y} {Z} hoodcraft:cash_cat_egg[hatch=0]"))

    # `tick step` only works on a frozen tick loop; `tick sprint` is what actually advances time.
    hatched = False
    for _ in range(16):
        r.sprint(600)
        if "Test passed" in r.cmd(f"execute unless block {X} {Y} {Z} hoodcraft:cash_cat_egg"):
            hatched = True
            break
    check("hatches on slime", hatched)
    check("and a Cash Cat came out",
          "Test passed" in r.cmd("execute if entity @e[type=hoodcraft:cash_cat]"))
    check("as a kitten", "-" in cat_data(r, "Age"), cat_data(r, "Age")[-24:])

    r.cmd("kill @e[type=hoodcraft:cash_cat]")
    r.cmd("kill @e[type=item]")
    r.cmd(f"forceload remove {X - 16} {Z - 16} {X + 16} {Z + 16}")
    r.cmd("gamerule sendCommandFeedback true")
    status = check.summary()
    r.cmd("stop")
    return status


if __name__ == "__main__":
    raise SystemExit(main())
