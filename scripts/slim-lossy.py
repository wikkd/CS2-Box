#!/usr/bin/env python3
"""Lossy texture slim pass (accepted quality tradeoffs, see docs/SIZE-SLIM.md).

Applied to shared textures only (four platforms share the same resources).
Uses pngquant (RGBA palette quantization with perceptual quality targeting);
Pillow-only quantization was measured and rejected for these assets (smooth
gradients and fine stripes lose more bytes to dithering noise than they gain).

Accepted qualities (validated visually, see shots/_slim_*_before_after.png):
  1. lens_vignette.png : --quality 60-95 (radial overlay, visually lossless)
  2. csgo_box.png      : --quality 85-100 (fine stripes must survive)
  3. terminal UI sprites : --quality 85-100 (small UI textures, no visible
     banding; validated with vision model on shots/_slim_ui_sprites_grid.png).
     Files: spot_glow / terminal_avatar / terminal_circle_glow /
     terminal_dot_tile / terminal_badge / gold_item.

Idempotent: already-quantized files (palette mode) are skipped; --force
regenerates and rewrites; --dry-run prints the plan without writing.

Requires: pngquant on PATH (brew install pngquant)
"""
import os
import shutil
import subprocess
import sys

from PIL import Image

ASSETS = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "common", "src", "main", "resources", "assets", "csgobox"))

TARGETS = {
    "textures/screens/lens_vignette.png": "60-95",
    "textures/item/csgo_box.png": "85-100",
    "textures/screens/spot_glow.png": "85-100",
    "textures/screens/gold_item.png": "85-100",
    "textures/gui/terminal/terminal_avatar.png": "85-100",
    "textures/gui/terminal/terminal_circle_glow.png": "85-100",
    "textures/gui/terminal/terminal_dot_tile.png": "85-100",
    "textures/gui/terminal/terminal_badge.png": "85-100",
}


def main():
    dry_run = "--dry-run" in sys.argv
    force = "--force" in sys.argv
    if not dry_run and shutil.which("pngquant") is None:
        print("pngquant not found — run: brew install pngquant")
        sys.exit(1)
    for rel, quality in TARGETS.items():
        path = os.path.join(ASSETS, rel)
        with Image.open(path) as im:
            already = im.mode == "P"
        if already and not force:
            print(f"skip   {rel} (already quantized, --force to redo)")
            continue
        before = os.path.getsize(path)
        tmp = path + ".pngq"
        subprocess.run(["pngquant", "--quality", quality, "--speed", "1",
                        "--force", "--output", tmp, path],
                       check=True, capture_output=True)
        after = os.path.getsize(tmp)
        print(f"q{quality:7s} {rel:44s} {before/1024:6.1f} -> {after/1024:6.1f} KB "
              f"({(before-after)/before*100:4.1f}%)")
        if after < before and not dry_run:
            os.replace(tmp, path)
        else:
            os.unlink(tmp)
            print("  !! no size win, keeping original")


if __name__ == "__main__":
    main()
