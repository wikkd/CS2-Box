#!/usr/bin/env python3
"""Trim float precision in terminal.json (the 1021-cube voxel item model).

The Bedrock-geometry conversion left long float artifacts (e.g.
8.536999999999999). Rounding every float literal to 4 decimals is safe at
block scale: the smallest element axis is >= 0.05 units, so no face can
collapse. Validation after the rewrite: JSON parses, element count and
min-element-thickness are unchanged.

Usage:
    python3 scripts/slim-trim-model-precision.py [--dry-run]
"""
import json
import math
import os
import re
import sys

PATH = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "common", "src", "main", "resources",
    "assets", "csgobox", "models", "item", "terminal.json"))

FLOAT_RE = re.compile(r"-?\d+\.\d+")


def min_thickness(elements):
    return min(min(e["to"][a] - e["from"][a] for a in range(3)) for e in elements)


def main():
    dry_run = "--dry-run" in sys.argv
    text = open(PATH, encoding="utf-8").read()
    data = json.loads(text)
    elements = data["elements"]
    before_count = len(elements)
    before_thin = min_thickness(elements)
    before_floats = len(FLOAT_RE.findall(text))

    def round_float(m):
        v = float(m.group(0))
        r = round(v, 4)
        # avoid "-0.0"
        if r == 0:
            r = 0.0
        s = repr(r)
        return s if "." in s else s + ".0"

    new_text = FLOAT_RE.sub(round_float, text)
    if new_text == text:
        print("no change: no float precision to trim")
        return
    new_data = json.loads(new_text)
    new_count = len(new_data["elements"])
    new_thin = min_thickness(new_data["elements"])
    assert new_count == before_count, "element count changed!"
    assert new_thin >= before_thin - 1e-9, "min thickness shrank!"
    delta = len(text) - len(new_text)
    print(f"floats: {before_floats} -> {len(FLOAT_RE.findall(new_text))} "
          f"({before_floats - len(FLOAT_RE.findall(new_text))} trimmed)")
    print(f"bytes: {len(text)} -> {len(new_text)} ({delta/1024:.1f} KB saved)")
    print(f"elements: {before_count} unchanged; min thickness {before_thin:.4f} unchanged")
    if not dry_run:
        with open(PATH, "w", encoding="utf-8") as fh:
            fh.write(new_text)
        print("written")


if __name__ == "__main__":
    main()
