#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Platform drift gate for CS2-Box mirrored files (baseline mode).

Checks that files mirrored across a platform family have NOT drifted *further*
apart than the recorded baseline. Families (per AGENTS.md mirror discipline):

  - v26_1_2  vs v26_2         (NeoForge 26.x, API-adapted)
  - forge_26_1_2 vs forge_26_2 (Forge 26.x, API-adapted)

Cross-loader pairs are out of scope (NeoForge vs Forge APIs legitimately
differ). GUI/render files legitimately carry hundreds of adapted lines — that
is exactly why a single diff threshold does not work; instead we compare each
file's current diff against its recorded baseline and only fail when a file
drifted *beyond* its recorded value (or a previously identical file gained
ANY diff).

Usage:
    python3 scripts/check-platform-drift.py --save-baseline FILE   # record now
    python3 scripts/check-platform-drift.py --baseline FILE        # gate (CI)
    python3 scripts/check-platform-drift.py --list                 # info dump

Exit code: 0 = clean, 1 = new drift detected, 2 = usage error.
The baseline is a small JSON map: {"v26_1_2|v26_2": {"rel/path.java": n, ...}}
"""

import argparse
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

FAMILIES = [
    ("v26_1_2", "v26_2"),
    ("forge_26_1_2", "forge_26_2"),
]

# Lines that legitimately differ between family members: the package statement
# and the import of the module's own entry point (CsgoBox). Everything else
# must match byte-for-byte for "identical" files.
PACKAGE_RE = re.compile(r"^\s*package\s+")
SELF_IMPORT_RE = re.compile(
    r"^\s*import\s+com\.reclizer\.csgobox\.(v26_1_2|v26_2|forge_26_1_2|forge_26_2)\.CsgoBox\s*;")


def norm(source: pathlib.Path) -> list[str]:
    try:
        lines = source.read_text("utf-8").splitlines()
    except (UnicodeDecodeError, OSError):
        return ["<unreadable>"]
    out = []
    for line in lines:
        if PACKAGE_RE.match(line) or SELF_IMPORT_RE.match(line):
            continue
        out.append(line.rstrip())
    return out


def module_src(module: str) -> pathlib.Path:
    return ROOT / module / "src" / "main" / "java" / "com" / "reclizer" / "csgobox" / module


def collect_files(module: str) -> dict[str, pathlib.Path]:
    base = module_src(module)
    if not base.is_dir():
        return {}
    # as_posix(): the baseline is shared across Windows dev machines and
    # Linux CI — a backslash key would never match a forward-slash key and
    # every mirrored file would look NEW to the gate.
    return {p.relative_to(base).as_posix(): p for p in base.rglob("*.java")}


def diff_count(path_a: pathlib.Path, path_b: pathlib.Path) -> int:
    na, nb = norm(path_a), norm(path_b)
    return sum(1 for x, y in zip(na, nb) if x != y) + abs(len(na) - len(nb))


def family_key(a: str, b: str) -> str:
    return f"{a}|{b}"


def compute_all() -> dict[str, dict[str, int]]:
    result: dict[str, dict[str, int]] = {}
    for family_a, family_b in FAMILIES:
        key = family_key(family_a, family_b)
        result[key] = {}
        files_a = collect_files(family_a)
        if not files_a:
            continue
        for rel, path_a in sorted(files_a.items()):
            path_b = module_src(family_b) / rel
            if path_b.exists():
                result[key][rel] = diff_count(path_a, path_b)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--save-baseline", metavar="FILE",
                        help="write the current diff table to FILE and exit 0")
    parser.add_argument("--baseline", metavar="FILE",
                        help="gate: fail when any file's diff exceeds its recorded baseline")
    parser.add_argument("--list", action="store_true",
                        help="print the current diff table (info only)")
    args = parser.parse_args()

    current = compute_all()

    if args.save_baseline:
        pathlib.Path(args.save_baseline).write_text(
            json.dumps(current, indent=1, sort_keys=True), "utf-8")
        print(f"[drift] baseline saved to {args.save_baseline}")
        return 0

    if args.list:
        for key in sorted(current):
            for rel, d in sorted(current[key].items(), key=lambda kv: (-kv[1], kv[0])):
                if d > 0:
                    print(f"[drift] {key}: {rel}  diff={d}")
        return 0

    if args.baseline:
        try:
            baseline = json.loads(pathlib.Path(args.baseline).read_text("utf-8"))
        except (OSError, json.JSONDecodeError) as e:
            print(f"[drift] cannot read baseline {args.baseline}: {e}", file=sys.stderr)
            return 2
        bad = []
        for key in sorted(current):
            prev = baseline.get(key, {})
            for rel, d in sorted(current[key].items()):
                if d > prev.get(rel, 0):
                    badge = "NEW" if rel not in prev else "DRIFTED"
                    bad.append((key, rel, prev.get(rel, 0), d, badge))
        if bad:
            print(f"[drift] FAIL ({len(bad)} new/regressed drift — mirror the change "
                  f"to the family sibling or update the baseline deliberately):")
            for key, rel, before, after, badge in bad[:40]:
                print(f"  [{badge:7s}] {key}: {rel}  {before} -> {after}")
            return 1
        total = sum(len(v) for v in current.values())
        print(f"[drift] OK: {total} mirrored pairs within baseline")
        return 0

    parser.print_help()
    return 2


if __name__ == "__main__":
    sys.exit(main())