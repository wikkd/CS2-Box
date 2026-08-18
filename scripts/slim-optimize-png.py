#!/usr/bin/env python3
"""Lossless PNG re-encode for all CS2-Box textures (slim pass).

Pillow optimize=True (better deflate + metadata strip) is applied to every
PNG under common/src/main/resources/assets/csgobox. A file is only written
when the re-encode is strictly smaller, so the script is idempotent:
re-running it leaves already-optimized files untouched.

Usage:
    python3 scripts/slim-optimize-png.py [--dry-run]

Output: per-file delta report and total savings (uncompressed bytes).
"""
import io
import os
import sys

from PIL import Image

ROOT = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "common", "src", "main", "resources", "assets", "csgobox"))


def main():
    dry_run = "--dry-run" in sys.argv
    total_before = total_after = 0
    changed = 0
    rows = []
    for dirpath, _dirs, files in os.walk(ROOT):
        for name in sorted(files):
            if not name.endswith(".png"):
                continue
            path = os.path.join(dirpath, name)
            before = os.path.getsize(path)
            with Image.open(path) as im:
                buf = io.BytesIO()
                im.save(buf, "PNG", optimize=True)
            after = len(buf.getvalue())
            total_before += before
            total_after += after
            rel = os.path.relpath(path, ROOT)
            if after < before:
                changed += 1
                rows.append((before - after, rel, before, after))
                if not dry_run:
                    with open(path, "wb") as fh:
                        fh.write(buf.getvalue())
    rows.sort(reverse=True)
    for delta, rel, before, after in rows:
        print(f"{delta/1024:7.1f} KB  {rel:48s} {before/1024:6.1f} -> {after/1024:6.1f} KB")
    print(f"\nTOTAL: {total_before/1024:.1f} -> {total_after/1024:.1f} KB  "
          f"saved {(total_before-total_after)/1024:.1f} KB across {changed} files"
          + ("  (dry-run)" if dry_run else ""))


if __name__ == "__main__":
    main()
