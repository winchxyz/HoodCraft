"""Export the drawn sealed-egg placeholders as PNGs.

    python scripts/export_sealed_eggs.py

Writes 16x16 sources (the real pixel grid, for editing) and 256x256 previews
(nearest-neighbour, for looking at) into design/sealed-eggs/.

The mask and shading rules here mirror components/SealedEgg.tsx exactly. If you
redraw the eggs, the 16x16 files are what to edit -- then either point the
candidate at a real image via `art:` in lib/candidates.ts, or paste the new
mask back into SealedEgg.tsx.
"""

from pathlib import Path
from PIL import Image

# Identical to MASK in components/SealedEgg.tsx.
MASK = [
    "................",
    ".......##.......",
    "......####......",
    ".....######.....",
    ".....######.....",
    "....########....",
    "....########....",
    "...##########...",
    "...##########...",
    "..############..",
    "..############..",
    "..############..",
    "...##########...",
    "....########....",
    ".....######.....",
    "................",
]

# id -> (face, dark). Mirrors `palette` in lib/candidates.ts.
PALETTES = {
    "slot-i":   ("#F0B23A", "#846426"),
    "slot-ii":  ("#5AA9E6", "#26567F"),
    "slot-iii": ("#C86AD8", "#5C2A66"),
    "slot-iv":  ("#E86A5A", "#7F2B21"),
    "locked":   ("#3A4A56", "#1F262E"),  # the greyed egg on the loop chain
}

SIZE = 16
PREVIEW = 256


def hex_to_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def mix_with_white(rgb, weight):
    """CSS color-mix(in srgb, <colour> <weight>%, #ffffff)."""
    return tuple(round(channel * weight + 255 * (1 - weight)) for channel in rgb)


def filled(row, col):
    return 0 <= row < SIZE and 0 <= col < SIZE and MASK[row][col] == "#"


def is_rim(row, col):
    return not filled(row + 1, col + 1) and (row >= 8 or col >= 9)


def is_specular(row, col):
    return 4 <= row <= 5 and 5 <= col <= 6


def render(face_hex, dark_hex):
    face = hex_to_rgb(face_hex)
    dark = hex_to_rgb(dark_hex)
    light = mix_with_white(face, 0.70)

    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()
    for row in range(SIZE):
        for col in range(SIZE):
            if not filled(row, col):
                continue
            if is_rim(row, col):
                colour = dark
            elif is_specular(row, col):
                colour = light
            else:
                colour = face
            pixels[col, row] = colour + (255,)
    return image


def main():
    out = Path(__file__).resolve().parent.parent / "design" / "sealed-eggs"
    out.mkdir(parents=True, exist_ok=True)

    sheet = Image.new("RGBA", (PREVIEW * len(PALETTES), PREVIEW), (0, 0, 0, 0))

    for index, (name, (face, dark)) in enumerate(PALETTES.items()):
        source = render(face, dark)
        source.save(out / f"{name}-16.png")

        preview = source.resize((PREVIEW, PREVIEW), Image.NEAREST)
        preview.save(out / f"{name}-256.png")
        sheet.paste(preview, (index * PREVIEW, 0))

        print(f"  {name:9s} {face} / {dark}")

    sheet.save(out / "_all-256.png")
    print(f"\nWrote {len(PALETTES) * 2 + 1} files to {out}")


if __name__ == "__main__":
    main()
