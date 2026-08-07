#!/usr/bin/env python3
"""Generate assets/csgobox/textures/screens/spot_glow.png - a clean radial
white glow (transparent rim, no ring) used as the CS2-style lamp spotlight
behind the reel strip. Pure-stdlib PNG writer, no PIL required."""
import struct, zlib, math, os

SIZE = 256
PEAK_ALPHA = 56  # ~22% white at the centre, fading to 0 at the rim

def smoothstep(t: float) -> float:
    return t * t * (3.0 - 2.0 * t)

rows = []
for y in range(SIZE):
    row = bytearray([0])  # PNG filter type 0
    for x in range(SIZE):
        dx = (x + 0.5 - SIZE / 2) / (SIZE / 2)
        dy = (y + 0.5 - SIZE / 2) / (SIZE / 2)
        r = min(math.sqrt(dx * dx + dy * dy), 1.0)
        a = int(round(PEAK_ALPHA * (1.0 - smoothstep(r))))
        row += bytes((255, 255, 255, a))
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
                   "spot_glow.png")
with open(out, "wb") as f:
    f.write(png)

# sanity print: alpha at centre / half radius / rim
print("wrote", os.path.abspath(out), len(png), "bytes")
print("alpha centre:", rows[SIZE // 2][1 + (SIZE // 2) * 4 + 3],
      "half:", rows[SIZE // 2][1 + (SIZE * 3 // 4) * 4 + 3],
      "rim:", rows[SIZE // 2][1 + (SIZE - 1) * 4 + 3])
