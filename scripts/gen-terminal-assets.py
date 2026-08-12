#!/usr/bin/env python3
"""Generate the pre-baked terminal-machine textures (Stage 0 of the HTML->Java
visual migration, spec: .qoder/specs/终端机视觉迁移Java方案_task-63c.md).

Pure-stdlib PNG writer (struct + zlib) for every texture; the dealer avatar is
converted from design/assets/arms-dealer.webp with PIL (one-off conversion,
the mosaic is already baked into the source image).

Output: common/src/main/resources/assets/csgobox/textures/gui/terminal/
Re-runnable: deterministic output (same bytes for the same parameters).
"""
import math
import os
import struct
import sys
import zlib

OUT = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "common", "src", "main", "resources", "assets", "csgobox",
    "textures", "gui", "terminal"))


def write_png(path, w, h, pixel_fn):
    """pixel_fn(x, y) -> (r, g, b, a); rows are filter-0 RGBA.
    Idempotent: keeps an existing file unless --force was passed.
    Returns True when the file was written, False when skipped."""
    name = os.path.basename(path)
    if os.path.exists(path) and "--force" not in sys.argv:
        print("  exists, skip %s (--force to regenerate)" % name)
        return False
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r, g, b, a = pixel_fn(x, y)
            raw += bytes((r & 0xFF, g & 0xFF, b & 0xFF, a & 0xFF))
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)
    print("  wrote %s" % name)
    return True


def lerp(a, b, t):
    return a + (b - a) * t


def color_lerp(c1, c2, t):
    t = max(0.0, min(1.0, t))
    r1, g1, b1 = (c1 >> 16) & 0xFF, (c1 >> 8) & 0xFF, c1 & 0xFF
    r2, g2, b2 = (c2 >> 16) & 0xFF, (c2 >> 8) & 0xFF, c2 & 0xFF
    return (round(lerp(r1, r2, t)), round(lerp(g1, g2, t)), round(lerp(b1, b2, t)))


def clamp01(v):
    return max(0.0, min(1.0, v))


# ---------------------------------------------------------------------------
# SVG mini path parser (M / h / v / l / H / V / z only; compact negatives like
# "l2-9" == "l 2 -9" supported)
# ---------------------------------------------------------------------------

def parse_path(d):
    tokens = []
    i = 0
    n = len(d)
    while i < n:
        c = d[i]
        if c.isspace():
            i += 1
            continue
        if c in "MhvlHVz":
            tokens.append(c)
            i += 1
            continue
        if c in "+-." or c.isdigit():
            j = i
            # a sign is only valid at the very start of a number, so compact
            # forms like "l2-9" split into "2" then "-9"
            if d[i] in "+-":
                j = i + 1
            while j < n and (d[j] in "." or d[j].isdigit()):
                j += 1
            tokens.append(float(d[i:j]))
            i = j
            continue
        raise ValueError("unexpected char %r in path at %d" % (c, i))
    pts = []
    cx = cy = 0.0
    start = None
    k = 0
    while k < len(tokens):
        cmd = tokens[k]
        k += 1
        if cmd == "M":
            cx, cy = tokens[k], tokens[k + 1]
            k += 2
            start = (cx, cy)
            pts.append((cx, cy))
        elif cmd == "m":
            cx += tokens[k]
            cy += tokens[k + 1]
            k += 2
            if start is None:
                start = (cx, cy)
            pts.append((cx, cy))
        elif cmd == "h":
            cx += tokens[k]
            k += 1
            pts.append((cx, cy))
        elif cmd == "v":
            cy += tokens[k]
            k += 1
            pts.append((cx, cy))
        elif cmd == "l":
            cx += tokens[k]
            cy += tokens[k + 1]
            k += 2
            pts.append((cx, cy))
        elif cmd == "H":
            cx = tokens[k]
            k += 1
            pts.append((cx, cy))
        elif cmd == "V":
            cy = tokens[k]
            k += 1
            pts.append((cx, cy))
        elif cmd == "z":
            pts.append(start)
            break
        else:
            raise ValueError("unsupported command %r" % cmd)
    return pts


def point_in_poly(x, y, pts):
    inside = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi) + xi):
            inside = not inside
        j = i
    return inside


# ---------------------------------------------------------------------------
# Weapon renders: 2x scale (64x40 -> 128x80), diagonal two-stop gradient,
# 14deg pre-rotation, baked 2px drop shadow (HTML drop-shadow(0 2px 3px #000b)).
# ---------------------------------------------------------------------------

WEAPON_DEFS = [
    # name, svg path, c1, c2
    ("pistol", "M8 10h46v7H34l-4 13H18l4-13H8z", 0xCFE8F5, 0x5A8FC0),
    ("rifle", "M2 14h58v6h-9v4h-6l-2 9h-8l2-9H25l-2 5h-7l2-5H6l-2 4H0v-4h2z", 0xE878BC, 0x7D4A86),
    ("smg", "M4 12h50v4h8v6h-10v4h-6l-2 10h-9l2-10H22l-2 6h-8l2-6H4z", 0xD05050, 0x5A2626),
]


def make_weapon(name, path_d, c1, c2):
    W, H = 128, 80
    SRC_W, SRC_H = 64.0, 40.0
    angle = math.radians(14.0)
    cos_a, sin_a = math.cos(angle), math.sin(angle)
    poly = parse_path(path_d)

    def grad_t(x, y):
        # SVG linearGradient objectBoundingBox (0,0)->(1,1) diagonal.
        return ((x / SRC_W) + (y / SRC_H)) / 2.0

    def gradient(t):
        if t < 0.55:
            return color_lerp(c1, c2, t / 0.55)
        return color_lerp(c2, c1, (t - 0.55) / 0.45)

    def sample(px, py, shadow):
        # inverse map: target pixel (128x80) -> centred 64x40 space ->
        # inverse-rotate by -14deg -> source space (this is the inverse of
        # rot_pt; the rotation is baked into the output pixels)
        x0 = (px - 64.0) / 2.0
        y0 = (py - 40.0) / 2.0
        ox = x0 * cos_a + y0 * sin_a + 32.0
        oy = -x0 * sin_a + y0 * cos_a + 20.0
        if shadow:
            oy -= 2.0  # shadow polygon sits 2px lower in source space
        if not point_in_poly(ox, oy, poly):
            return (0, 0, 0, 0)
        if shadow:
            return (0, 0, 0, 90)
        r, g, b = gradient(grad_t(ox, oy))
        return (r, g, b, 255)

    def pixel(x, y):
        shadow = sample(x, y, True)
        body = sample(x, y, False)
        # shadow only where the body is absent
        if body[3] == 0 and shadow[3] != 0:
            return shadow
        return body

    write_png(os.path.join(OUT, "weapon_%s.png" % name), W, H, pixel)


# ---------------------------------------------------------------------------
# Simple shapes
# ---------------------------------------------------------------------------

def make_round_rect():
    # 16x16 white mask, 2px 45deg corner cuts (matches the original asset:
    # corner triangle x+y<2 transparent, every other pixel solid white)
    def pixel(x, y):
        cx = min(x, 15 - x)
        cy = min(y, 15 - y)
        if cx < 2 and cy < 2 and cx + cy < 2:
            return (255, 255, 255, 0)
        return (255, 255, 255, 255)
    write_png(os.path.join(OUT, "terminal_round_rect.png"), 16, 16, pixel)


def make_dot():
    # 6x6 typing dot: grey-blue body (154,164,173), alpha falls linearly
    # from the centre: a = clamp(int(255 * (2.8 - d))) with d measured from
    # (2.5, 2.5) — reproduces the original [..,173,255,255,173,..] profile.
    def pixel(x, y):
        d = math.sqrt((x - 2.5) ** 2 + (y - 2.5) ** 2)
        a = max(0, min(255, int(255 * (2.8 - d))))
        return (154, 164, 173, a)
    write_png(os.path.join(OUT, "terminal_dot.png"), 6, 6, pixel)


def make_dot_tile():
    # 24x24 minimal tile: one 2x2px dot at (16,17), alpha 22. The old 512x512
    # asset was the same pattern tiled 21x21 (455x the decoded memory); the
    # renderer tiles this 24x24 at 1:1. Transparent pixels keep white RGB.
    def pixel(x, y):
        if (x - 16) % 24 < 2 and (y - 16) % 24 < 2:
            return (255, 255, 255, 22)
        return (255, 255, 255, 0)
    write_png(os.path.join(OUT, "terminal_dot_tile.png"), 24, 24, pixel)


def make_badge():
    # 72x72 米色径向徽章（HTML .slot-badge）：#f0ece1 → #cfc8b8 70% → #b3ac9b + 2px 内暗环
    def pixel(x, y):
        d = math.sqrt((x - 35.5) ** 2 + (y - 35.5) ** 2)
        if d > 36:
            return (0, 0, 0, 0)
        t = d / 36.0
        r, g, b = color_lerp(0xF0ECE1, 0xB3AC9B, t ** 0.9)
        if 34 <= d <= 36:  # inset ring #0004
            r, g, b = (round(r * 0.75), round(g * 0.75), round(b * 0.75))
        return (r, g, b, 255)
    write_png(os.path.join(OUT, "terminal_badge.png"), 72, 72, pixel)


def make_scan_band():
    # 8x24 vertical light band: horizontal transparent->white->transparent.
    # Approximate cosine falloff; low-alpha so the exact curve is visually
    # indistinguishable from the original (max delta ~7/255).
    def pixel(x, y):
        t = math.cos(math.pi * (x - 3.5) / 7.5)
        a = int(255 * 0.44 * t * t)
        return (255, 255, 255, max(0, min(255, a)))
    write_png(os.path.join(OUT, "terminal_scan_band.png"), 8, 24, pixel)


def make_circle_glow():
    # 128x128 radial white glow: a = 72*cos^2(pi*d/128), d from (63.5,63.5),
    # transparent pixels keep white RGB. Matches the original profile (peak 72).
    def pixel(x, y):
        d = math.sqrt((x - 63.5) ** 2 + (y - 63.5) ** 2)
        a = int(72 * math.cos(math.pi * d / 128.0) ** 2)
        return (255, 255, 255, max(0, min(255, a)))
    write_png(os.path.join(OUT, "terminal_circle_glow.png"), 128, 128, pixel)


# ---------------------------------------------------------------------------
# Avatar (PIL, one-off webp conversion) + grayscale watermark
# ---------------------------------------------------------------------------

def make_avatar():
    src = os.path.normpath(os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "design", "assets", "arms-dealer.webp"))
    try:
        from PIL import Image
    except ImportError:
        print("  ! PIL missing - skipping avatar (terminal_avatar / terminal_avatar_wm)")
        return
    existing = os.path.join(OUT, "terminal_avatar.png")
    wm_path = os.path.join(OUT, "terminal_avatar_wm.png")
    if os.path.exists(wm_path) and "--force" not in sys.argv:
        print("  exists, skip terminal_avatar_wm.png (--force to regenerate)")
        return
    if os.path.exists(src):
        if not os.path.exists(existing) or "--force" in sys.argv:
            img = Image.open(src).convert("RGBA").resize((64, 64), Image.LANCZOS)
            img.save(existing)
            print("  wrote terminal_avatar.png (from webp)")
        else:
            print("  exists, skip terminal_avatar.png (--force to regenerate)")
            img = Image.open(existing).convert("RGBA")
    else:
        if not os.path.exists(existing):
            print("  ! source webp AND existing avatar missing - skipping avatar")
            return
        img = Image.open(existing).convert("RGBA")
        print("  ! arms-dealer.webp missing - keeping existing terminal_avatar.png")
    gray = img.convert("RGBA")
    # composite onto black so transparent source pixels become black instead
    # of greying out the watermark
    bg = Image.new("RGBA", gray.size, (0, 0, 0, 255))
    gray = Image.alpha_composite(bg, gray).convert("L").resize((128, 128), Image.LANCZOS)
    wm = Image.new("RGBA", (128, 128))
    px = wm.load()
    for y in range(128):
        for x in range(128):
            v = gray.getpixel((x, y))
            px[x, y] = (v, v, v, round(255 * 0.05))
    wm.save(os.path.join(OUT, "terminal_avatar_wm.png"))
    print("  wrote terminal_avatar_wm.png")


# ---------------------------------------------------------------------------

def make_info():
    # 32x32 (blitted at 4x4 gui = 16px render, 2:1 downsample): the region-6
    # info badge — light disc (#cfd6db) + dark "i" (#20242a), baked colours so
    # the blit tint is white. Replaces the old 3-part draw (TEX_CIRCLE blit +
    # two 1-gui fills) whose fills rendered as chunky 4px blocks at guiScale 4.
    # Disc: centre (15.5,15.5), hard edge with a 2px soft band.
    # "i": 2px-at-render dot + stem (4 texture units = 2 render px each), the
    # classic info glyph centred in the disc.
    DISC = (207, 214, 219)
    I = (32, 36, 42)

    def disc_alpha(x, y):
        d = math.sqrt((x - 15.5) ** 2 + (y - 15.5) ** 2)
        if d <= 14.5:
            return 255
        if d >= 16.5:
            return 0
        return int(255 * (16.5 - d))

    def in_dot(x, y):
        return 14 <= x <= 17 and 8 <= y <= 11

    def in_stem(x, y):
        return 14 <= x <= 17 and 14 <= y <= 23

    def pixel(x, y):
        a = disc_alpha(x, y)
        if a == 0:
            return (DISC[0], DISC[1], DISC[2], 0)
        if in_dot(x, y) or in_stem(x, y):
            return (I[0], I[1], I[2], 255)
        return (DISC[0], DISC[1], DISC[2], a)

    write_png(os.path.join(OUT, "terminal_info.png"), 32, 32, pixel)


def make_chevron():
    # 32x32 (blitted at 3x2 gui = 12x8px render): upward triangle #9aa4ad,
    # matching the HTML .chev (border-top triangle, always pointing up).
    # 1px AA band via signed distance to the three edges.
    APEX = (16.0, 5.0)
    BL = (3.0, 27.0)
    BR = (29.0, 27.0)
    COL = (154, 164, 173)

    def sd_tri(x, y):
        # signed distance: positive inside for the two slants, y <= base
        d_left = 22.0 * (x - APEX[0]) + 13.0 * (y - APEX[1])  # <=0 inside
        d_right = 22.0 * (x - APEX[0]) - 13.0 * (y - APEX[1])  # >=0 inside
        # normalise to px distance
        n = math.sqrt(22.0 ** 2 + 13.0 ** 2)
        dl = d_left / n
        dr = -d_right / n
        db = 27.0 - y
        return min(dl, dr, db)

    def pixel(x, y):
        d = sd_tri(x + 0.5, y + 0.5)
        if d >= 0.5:
            return (COL[0], COL[1], COL[2], 255)
        if d <= -0.5:
            return (COL[0], COL[1], COL[2], 0)
        return (COL[0], COL[1], COL[2], int(255 * (d + 0.5)))
    write_png(os.path.join(OUT, "terminal_chevron.png"), 32, 32, pixel)


def main():
    print(OUT)
    make_round_rect()
    make_dot()
    make_dot_tile()
    make_scan_band()
    make_circle_glow()
    make_info()
    make_chevron()
    make_badge()
    make_avatar()
    for name, path_d, c1, c2 in WEAPON_DEFS:
        make_weapon(name, path_d, c1, c2)
    print("done")


if __name__ == "__main__":
    main()

