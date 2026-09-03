#!/usr/bin/env python3
"""Copy hand-drawn textures into the mod's resource tree.

`textures/` is the drawing folder: files there are named however is convenient while working, and
are the source of truth. `src/main/resources/assets/hoodcraft/textures/` is the built resource tree,
where every file must carry the exact name the game looks up. This script is the bridge, and it only
ever reads from the drawing folder - redrawing a texture and re-running it is always safe.

Add a pet by adding its rows to MAPPING. Nothing else here needs to change.

    python tools/sync_assets.py           # copy
    python tools/sync_assets.py --check   # report what would change, write nothing
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required:  python -m pip install Pillow")

ROOT = Path(__file__).resolve().parent.parent
DRAWING = ROOT / "textures"
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "hoodcraft" / "textures"

# source filename -> (destination path under assets/hoodcraft/textures, expected size)
# A source whose size differs from the expected size is centred into the expected canvas rather
# than resampled, so pixel art is never blurred by an accidental resize.
MAPPING: list[tuple[str, str, tuple[int, int]]] = [
    # Source paths are relative to textures/, which is organised one folder per pet.

    # --- Robin -------------------------------------------------------------
    ("jay/robin_bird.png", "entity/robin.png", (64, 64)),
    ("jay/Invicon_Robin_Spawn_Egg.png", "item/robin_spawn_egg.png", (16, 16)),
    ("jay/Robin_Feather.png", "item/black_feather.png", (16, 16)),
    ("jay/Robin_brush.png", "item/hood_brush.png", (16, 16)),

    # --- Cash Cat ----------------------------------------------------------
    # 64x32, the vanilla cat sheet. The model splits the ears onto their own parts but keeps their
    # offsets, so ear pixels stay exactly where the vanilla layout puts them.
    ("cashcat/cashcat.png", "entity/cash_cat.png", (64, 32)),
    ("cashcat/cashcat_tame.png", "entity/cash_cat_tamed.png", (64, 32)),
    ("cashcat/cashcat_egg_item.png", "item/cash_cat_egg.png", (16, 16)),
]


def fit_to(image: Image.Image, size: tuple[int, int]) -> tuple[Image.Image, str]:
    """Return the image on a canvas of exactly `size`, plus a note about what was done.

    Off-size sprites are never resampled - that would blur the pixel art. Instead the drawn content
    is cropped out of its transparent margin and centred on a canvas of the right size, which fixes
    the usual case of a sprite saved a row or two too tall without touching a single pixel of art.
    """
    if image.size == size:
        return image, ""

    content = image.crop(image.getbbox()) if image.getbbox() else image
    if content.width > size[0] or content.height > size[1]:
        raise ValueError(
            f"drawn area is {content.size}, which does not fit in {size}; redraw it smaller")

    canvas = Image.new("RGBA", size, (0, 0, 0, 0))
    canvas.alpha_composite(content, ((size[0] - content.width) // 2,
                                     (size[1] - content.height) // 2))
    return canvas, f"{image.size} -> {size}, art recentred"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="report what would change without writing")
    args = parser.parse_args()

    missing, changed, identical = [], [], 0

    for source_name, dest_rel, size in MAPPING:
        source = DRAWING / source_name
        dest = ASSETS / dest_rel

        if not source.exists():
            missing.append(source_name)
            continue

        image, note = fit_to(Image.open(source).convert("RGBA"), size)
        new_bytes = image.tobytes()
        old_bytes = None
        if dest.exists():
            old = Image.open(dest).convert("RGBA")
            old_bytes = old.tobytes() if old.size == size else None

        if old_bytes == new_bytes:
            identical += 1
            continue

        changed.append((source_name, dest_rel, note))
        if not args.check:
            dest.parent.mkdir(parents=True, exist_ok=True)
            image.save(dest)

    verb = "would update" if args.check else "updated"
    for source_name, dest_rel, note in changed:
        suffix = f"  ({note})" if note else ""
        print(f"  {verb}: {source_name} -> {dest_rel}{suffix}")
    if identical:
        print(f"  {identical} already up to date")
    for name in missing:
        print(f"  MISSING in textures/: {name}", file=sys.stderr)

    if missing:
        print(f"\n{len(missing)} source texture(s) missing.", file=sys.stderr)
        return 1
    print(f"\n{len(changed)} file(s) {verb}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
