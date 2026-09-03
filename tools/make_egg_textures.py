#!/usr/bin/env python3
"""Generate the three hatch-stage textures for a pet egg.

Every HoodCraft pet needs an egg block in its own colour, each with an uncracked, a slightly
cracked and a very cracked face. Drawing nine near-identical sprites by hand per pet is wasted
effort, so they are generated from one base colour instead. Output is deterministic - the speckles
come from a fixed seed - so re-running never produces a spurious diff.

Add a pet by adding a row to EGGS.

    python tools/make_egg_textures.py
"""

from __future__ import annotations

import random
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required:  python -m pip install Pillow")

ROOT = Path(__file__).resolve().parent.parent
BLOCKS = ROOT / "src" / "main" / "resources" / "assets" / "hoodcraft" / "textures" / "block"

SIZE = 16

DRAWING = ROOT / "textures"

# name prefix -> (base colour, optional hand-drawn 16x16 source relative to textures/).
#
# Only the uncracked stage is ever drawn by hand. The two cracked stages are always generated from
# it, so the damage accumulates consistently and a redrawn egg does not mean redrawing three files.
EGGS: list[tuple[str, tuple[int, int, int], str | None]] = [
    ("robin_egg", (0x00, 0xC8, 0x05), None),
    ("cash_cat_egg", (0x8A, 0x6A, 0x4F), "cashcat/cashcat_egg_block.png"),
]

# Crack pixels, as (x, y) runs. The "slightly cracked" stage uses the first list only; the
# "very cracked" stage uses both, so the damage visibly accumulates rather than being redrawn.
FIRST_CRACKS = [
    (7, 3), (7, 4), (8, 5), (8, 6), (7, 7), (6, 8), (7, 9),
    (3, 6), (4, 7), (4, 8),
]
SECOND_CRACKS = [
    (7, 10), (8, 11), (8, 12), (9, 13),
    (11, 4), (11, 5), (10, 6), (11, 7), (12, 8),
    (3, 10), (4, 11), (3, 12),
]


def shade(colour: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    return tuple(max(0, min(255, round(channel * factor))) for channel in colour)


def base_egg(colour: tuple[int, int, int]) -> Image.Image:
    """A speckled egg: lit from above, darker towards the bottom, with darker flecks."""
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()
    rng = random.Random(0xC805)

    for y in range(SIZE):
        # 1.15 at the top down to 0.72 at the bottom.
        factor = 1.15 - (y / (SIZE - 1)) * 0.43
        row = shade(colour, factor)
        for x in range(SIZE):
            pixels[x, y] = (*row, 255)

    # Darker speckles, and a few lighter ones to keep it from looking flat. Kept sparse so the
    # crack lines drawn on top stay readable against them.
    for _ in range(18):
        x, y = rng.randrange(SIZE), rng.randrange(SIZE)
        factor = 1.15 - (y / (SIZE - 1)) * 0.43
        pixels[x, y] = (*shade(colour, factor * rng.choice((0.68, 0.74, 1.22))), 255)

    return image


def draw_cracks(image: Image.Image, colour: tuple[int, int, int], runs: list[tuple[int, int]]) -> None:
    """Darken the crack run itself, and put a highlight beside it so the break reads as depth."""
    pixels = image.load()
    for x, y in runs:
        if 0 <= x + 1 < SIZE and 0 <= y < SIZE and (x + 1, y) not in runs:
            factor = 1.15 - (y / (SIZE - 1)) * 0.43
            pixels[x + 1, y] = (*shade(colour, factor * 1.30), 255)
    for x, y in runs:
        if 0 <= x < SIZE and 0 <= y < SIZE:
            factor = 1.15 - (y / (SIZE - 1)) * 0.43
            pixels[x, y] = (*shade(colour, factor * 0.12), 255)


def main() -> int:
    BLOCKS.mkdir(parents=True, exist_ok=True)
    written = []

    for name, colour, source in EGGS:
        if source is not None:
            drawn = DRAWING / source
            if not drawn.exists():
                print(f"  MISSING in textures/: {source}", file=sys.stderr)
                return 1
            uncracked = Image.open(drawn).convert("RGBA")
            if uncracked.size != (SIZE, SIZE):
                print(f"  {source} is {uncracked.size}, expected {SIZE}x{SIZE}", file=sys.stderr)
                return 1
        else:
            uncracked = base_egg(colour)

        slightly = uncracked.copy()
        draw_cracks(slightly, colour, FIRST_CRACKS)

        very = slightly.copy()
        draw_cracks(very, colour, SECOND_CRACKS)

        for suffix, image in (("", uncracked),
                              ("_slightly_cracked", slightly),
                              ("_very_cracked", very)):
            path = BLOCKS / f"{name}{suffix}.png"
            image.save(path)
            written.append(path.name)

    for name in written:
        print(f"  wrote: {name}")
    print(f"\n{len(written)} texture(s) generated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
