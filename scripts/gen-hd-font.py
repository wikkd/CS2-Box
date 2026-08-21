#!/usr/bin/env python3
"""Generate the CS2-Box HD bitmap fonts (3 tiers) from the UI wordlist.

Produces (committed to the repo, CI does NOT run this script):
  assets/csgobox/textures/font/hd_small.png | hd_mid.png | hd_large.png
  assets/csgobox/font/hd_small.json | hd_mid.json | hd_large.json

Font sources (OFL, free for commercial use; downloaded on first run and
cached in ~/.cache/csgbox-fonts, never committed):
  - Rajdhani (latin):    google/fonts ofl/rajdhani, via jsDelivr
  - Noto Sans SC (CJK):  @fontsource/noto-sans-sc chinese-simplified woff

Every glyph is a pre-rendered bitmap; the MC font JSON declares a
"reference" to minecraft:default after the bitmap so any character outside
the wordlist falls back to the vanilla glyphs (no tofu boxes).
"""

import argparse
import json
import math
import pathlib
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
LANG_DIR = ROOT / "common/src/main/resources/assets/csgobox/lang"
OUT_TEXTURES = ROOT / "common/src/main/resources/assets/csgobox/textures/font"
OUT_FONTS = ROOT / "common/src/main/resources/assets/csgobox/font"

FONT_URLS = {
    "Rajdhani-Regular.ttf": "https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/rajdhani/Rajdhani-Regular.ttf",
    "NotoSansSC.ttf": "https://cdn.jsdelivr.net/npm/@fontsource/noto-sans-sc@5.3.0/files/noto-sans-sc-chinese-simplified-400-normal.woff",
}

# (tier id, target glyph pixel height, cells per row)
TIERS = [
    ("hd_small", 4, 32),   # scales in [0.43, 0.6]
    ("hd_mid", 6, 32),     # scales in [0.72, 0.95]
    ("hd_large", 10, 32),  # scales in (1.0, 1.6]
]

ASCENT = 7  # must match the vanilla default font so glyph tops align


def load_wordlist():
    chars = set()
    for lang_file in sorted(LANG_DIR.glob("*.json")):
        data = json.loads(lang_file.read_text(encoding="utf-8"))
        for value in data.values():
            chars.update(value)
    # Dynamic UI strings (countdown digits, progress counters, "xN" stack
    # counts, " #idx" labels) are not in the lang files — cover the full
    # printable ASCII range so they never mix vanilla glyphs into an HD
    # string (e.g. a vanilla "6/7/8" next to HD digits in the countdown).
    chars.update(chr(o) for o in range(0x20, 0x7F))
    # Keep only CJK + printable ASCII + space; everything else falls back
    # to minecraft:default automatically.
    keep = set()
    for c in chars:
        o = ord(c)
        if c == " " or 0x21 <= o <= 0x7E or 0x4E00 <= o <= 0x9FFF:
            keep.add(c)
    # Deterministic ordering: ASCII first, then CJK by codepoint.
    ascii_chars = sorted((c for c in keep if ord(c) < 0x80), key=ord)
    cjk_chars = sorted((c for c in keep if ord(c) >= 0x4E00), key=ord)
    return ascii_chars + cjk_chars


def get_fonts(fonts_dir):
    fonts_dir = pathlib.Path(fonts_dir)
    fonts_dir.mkdir(parents=True, exist_ok=True)
    cache = pathlib.Path.home() / ".cache/csgbox-fonts"
    cache.mkdir(parents=True, exist_ok=True)
    result = {}
    for name, url in FONT_URLS.items():
        for base in (fonts_dir, cache):
            candidate = base / name
            if candidate.exists():
                result[name] = candidate
                break
        if name not in result:
            print(f"downloading {name} ...", file=sys.stderr)
            target = cache / name
            urllib.request.urlretrieve(url, target)
            result[name] = target
    return result


def calibrate_size(font_path, target_h, sample):
    """Binary-search the font size whose bbox height for `sample` matches
    target_h."""
    from PIL import ImageFont

    lo, hi = 3, 40
    best = None
    while lo <= hi:
        mid = (lo + hi) // 2
        font = ImageFont.truetype(str(font_path), mid)
        bb = font.getbbox(sample)
        h = bb[3] - bb[1]
        best = (mid, bb)
        if h < target_h:
            lo = mid + 1
        elif h > target_h:
            hi = mid - 1
        else:
            break
    return best


def render_glyph(font_path, size, ch):
    """Return (mask, width, total_height) at the glyph origin; the mask top
    is the cell top so glyph tops align with the vanilla default font."""
    from PIL import ImageFont

    font = ImageFont.truetype(str(font_path), size)
    mask = font.getmask(ch)
    size = mask.size
    return mask, size[0], size[1]



def save_palette_png(img, png):
    """Save as a 256-colour palette PNG (tRNS alpha quantised to 32 levels).

    Glyphs are pure white, so RGB carries no information: a palette keeps the
    file ~3x smaller than RGBA while stb_image (used by MC's NativeImage)
    loads palette PNGs without issue. Alpha 0 stays 0 so BitmapProvider's
    "rightmost non-zero alpha column" advance is unchanged.
    """
    from PIL import Image

    step = 8
    palette = []
    transparency = []
    for i in range(256):
        palette += [255, 255, 255]
        transparency.append(min(255, i * step))
    levels = [0] + [max(step, min(255 - step, round(a / step) * step)) for a in range(1, 256)]
    a = img.getchannel("A").point(lambda v: levels[v])
    out = Image.new("P", img.size)
    out.putpalette(palette)
    out.paste(a.point(lambda v: v // step), (0, 0))
    out.save(png, optimize=True, compress_level=9, transparency=bytes(transparency))


def build_tier(tier_id, target_h, cols, chars, fonts):
    from PIL import Image

    cjk_font = fonts["NotoSansSC.ttf"]
    latin_font = fonts["Rajdhani-Regular.ttf"]
    size_cjk, _ = calibrate_size(cjk_font, target_h, "下")
    size_latin, _ = calibrate_size(latin_font, target_h, "A")

    # cell width = widest glyph across both fonts at the calibrated size.
    cell_w = target_h
    for ch in "下剑箱ABCW0":
        f = cjk_font if ord(ch) >= 0x4E00 else latin_font
        sz = size_cjk if ord(ch) >= 0x4E00 else size_latin
        mask, w, _ = render_glyph(f, sz, ch)
        cell_w = max(cell_w, w)

    rows = math.ceil(len(chars) / cols)
    cell_h = ASCENT + 2  # baseline + descender headroom
    for ch in chars:
        f = cjk_font if ord(ch) >= 0x4E00 else latin_font
        sz = size_cjk if ord(ch) >= 0x4E00 else size_latin
        _, _, total_h = render_glyph(f, sz, ch)
        cell_h = max(cell_h, total_h + 1)

    from PIL import ImageDraw, ImageFont

    img = Image.new("RGBA", (cols * cell_w, rows * cell_h), (255, 255, 255, 0))
    draw = ImageDraw.Draw(img)
    padded = chars + [" "] * (rows * cols - len(chars))
    char_rows = []
    for r in range(rows):
        row_chars = padded[r * cols:(r + 1) * cols]
        char_rows.append("".join(row_chars))
        for c_i, ch in enumerate(row_chars):
            if ch == " ":
                continue
            f = cjk_font if ord(ch) >= 0x4E00 else latin_font
            sz = size_cjk if ord(ch) >= 0x4E00 else size_latin
            font = ImageFont.truetype(str(f), sz)
            bb = font.getbbox(ch)
            gw = bb[2] - bb[0]
            # Top-align the visible glyph with the cell top (the mask origin
            # includes ascender blank space) and centre it horizontally.
            draw.text((c_i * cell_w + (cell_w - gw) // 2 - bb[0],
                       r * cell_h - bb[1]), ch, font=font, fill=(255, 255, 255, 255))

    png = OUT_TEXTURES / f"{tier_id}.png"
    png.parent.mkdir(parents=True, exist_ok=True)
    # Gamma-boost the alpha channel: keeps small glyphs from rendering as
    # faint ghosts while preserving anti-aliased edges on the larger tiers.
    alpha = img.getchannel("A").point(lambda a: int(255 * (a / 255.0) ** 0.6))
    img.putalpha(alpha)
    save_palette_png(img, png)

    font_json = {
        "providers": [
            {
                "type": "bitmap",
                "file": f"csgobox:font/{tier_id}.png",
                "ascent": ASCENT,
                "height": cell_h,
                "chars": char_rows,
            },
            {"type": "reference", "id": "minecraft:default"},
        ]
    }
    jf = OUT_FONTS / f"{tier_id}.json"
    jf.parent.mkdir(parents=True, exist_ok=True)
    jf.write_text(json.dumps(font_json, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"{tier_id}: target={target_h}px cjk_size={size_cjk} latin_size={size_latin} "
        f"cell={cell_w}x{cell_h} chars={len(chars)} -> {png.name} ({png.stat().st_size//1024}KB), {jf.name}"
    )


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--fonts-dir", default=str(ROOT / "scripts/.fonts"),
                    help="directory with Rajdhani-Regular.ttf + NotoSansSC.ttf (default: scripts/.fonts)")
    args = ap.parse_args()

    from PIL import Image  # noqa: F401  (fail fast if missing)

    fonts = get_fonts(args.fonts_dir)
    chars = load_wordlist()
    print(f"wordlist: {len(chars)} glyphs "
          f"({sum(1 for c in chars if ord(c) < 0x80)} ascii, "
          f"{sum(1 for c in chars if ord(c) >= 0x4E00)} cjk)", file=sys.stderr)
    for tier_id, target_h, cols in TIERS:
        build_tier(tier_id, target_h, cols, chars, fonts)


if __name__ == "__main__":
    main()
