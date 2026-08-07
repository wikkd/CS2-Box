#!/usr/bin/env python3
"""Generate assets/csgobox/textures/screens/lens_vignette.png - the circular
window mask of the CS2Deck-style magnifier lens.

The mask is drawn over the lens bounding box AFTER the magnified strip:
  - inside the circle  -> fully transparent (magnified card shows through)
  - circle rim         -> soft dark shade (the "glass edge" depth cue)
  - outside the circle -> fully transparent: the four corners of the blit
    square stay see-through, so only the disc shape is visible, never a gray
    box. The dimmed raw strip behind shows through outside the lens.
Pure-stdlib PNG writer, no PIL required."""
import struct, zlib, math, os

SIZE = 512
CENTER = SIZE / 2
RADIUS = 248.0      # circle radius in texture px (bbox edge = 256 = dark rim)
FEATHER = 14.0      # alpha ramp from RADIUS back down to 0 (transparent) outside
INNER_SHADE_START = 213.0
INNER_SHADE_PEAK = 80
BG = (84, 84, 84)   # neutral gray backing (only visible inside the rim shade)


def alpha_at(d: float) -> int:
    if d <= INNER_SHADE_START:
        return 0
    if d < RADIUS:
        t = (d - INNER_SHADE_START) / (RADIUS - INNER_SHADE_START)
        return int(round(INNER_SHADE_PEAK * t * t))
    if d < RADIUS + FEATHER:
        t = (d - RADIUS) / FEATHER
        return int(round(INNER_SHADE_PEAK * (1 - t) * (1 - t)))
    return 0


rows = []
for y in range(SIZE):
    row = bytearray([0])
    for x in range(SIZE):
        dx = x + 0.5 - CENTER
        dy = y + 0.5 - CENTER
        d = math.sqrt(dx * dx + dy * dy)
        a = alpha_at(d)
        row += bytes((BG[0], BG[1], BG[2], a))
    rows.append(bytes(row))
raw = b"".join(rows)


def chunk(tag: bytes, data: bytes) -> bytes:
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


png = b"\x89PNG\r\n\x1a\n"
png += chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(raw, 9))
png += chunk(b"IEND", b"")

out = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main",
                   "resources", "assets", "csgobox", "textures", "screens",
                   "lens_vignette.png")
with open(out, "wb") as f:
    f.write(png)

print("wrote", os.path.abspath(out), len(png), "bytes")
print("alpha center:", rows[SIZE // 2][1 + (SIZE // 2) * 4 + 3],
      "| rim:", rows[SIZE // 2][1 + (SIZE // 2 + 245) * 4 + 3],
      "| bbox edge:", rows[SIZE // 2][1 + (SIZE - 1) * 4 + 3],
      "| corner:", rows[2][1 + 2 * 4 + 3])
