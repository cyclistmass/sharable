"""Imports the pirate event die style options (Codex art via mauro-7a) into res/drawable-nodpi.

Source: C:/Users/mauro/wgbg-art/dice-themed/pirate-event-options/P{n}_event_{1..6}.png and
P{n}_event_tumble_{k}.png (512x512 RGBA). Same treatment as the approved dice: faces cropped to the
die body and scaled to 192 px, tumbles scaled to 256 px, saturation -25% (Pip's pass), WebP.
Writes dice_pe{n}_{1..6}.webp and dice_tumble_pe{n}_{k}.webp, then rewrites the TUMBLE_PE{n} lists in
DiceArt.kt to match the frames found.

Usage: py tools/import_event_dice.py [source dir]
"""
import re
import sys
from pathlib import Path
from PIL import Image, ImageEnhance

SRC = Path(sys.argv[1] if len(sys.argv) > 1 else r"C:/Users/mauro/wgbg-art/dice-themed/pirate-event-options")
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/res/drawable-nodpi"
DICE_ART = ROOT / "app/src/main/java/com/mauro/wgbg/DiceArt.kt"
SATURATION = 0.75


def tone(img: Image.Image) -> Image.Image:
    img = img.convert("RGBA")
    alpha = img.getchannel("A")
    rgb = ImageEnhance.Color(img.convert("RGB")).enhance(SATURATION)
    rgb.putalpha(alpha)
    return rgb


def body_box(img: Image.Image):
    """Square box around the solid die body (alpha > 160), ignoring soft shadow and glow."""
    l, t, r, b = img.getchannel("A").point(lambda a: 255 if a > 160 else 0).getbbox()
    side = max(r - l, b - t)
    cx, cy = (l + r) // 2, (t + b) // 2
    return cx - side // 2, cy - side // 2, cx - side // 2 + side, cy - side // 2 + side


for n in (1, 2, 3):
    for f in range(1, 7):
        img = tone(Image.open(SRC / f"P{n}_event_{f}.png"))
        img = img.crop(body_box(img)).resize((192, 192), Image.LANCZOS)
        img.save(OUT / f"dice_pe{n}_{f}.webp", quality=90, method=6)
    k = 0
    while (SRC / f"P{n}_event_tumble_{k + 1}.png").exists():
        k += 1
        img = tone(Image.open(SRC / f"P{n}_event_tumble_{k}.png")).resize((256, 256), Image.LANCZOS)
        img.save(OUT / f"dice_tumble_pe{n}_{k}.webp", quality=90, method=6)
    print(f"P{n}: 6 faces, {k} tumbles")
    frames = ", ".join(f"R.drawable.dice_tumble_pe{n}_{i}" for i in range(1, k + 1))
    src = DICE_ART.read_bytes().decode()
    src, hits = re.subn(rf"val TUMBLE_PE{n} = listOf\([^)]*\)", f"val TUMBLE_PE{n} = listOf({frames})", src)
    assert hits == 1, f"TUMBLE_PE{n} not found in DiceArt.kt"
    DICE_ART.write_bytes(src.encode())
