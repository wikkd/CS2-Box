#!/usr/bin/env python3
"""Class-list audit for ProGuard minified jars (hard gate, see SIZE-SLIM.md).

Diffs the class entries of the original jar against the minified jar and
fails (exit 1) when classes were removed that are not on the allowlist.
Added classes and resource changes are reported but never fail the gate.

The allowlist is only for classes verified to be compile-time-inlined
constants (no runtime references); everything else that disappears is an
unexpected removal that must land as a new -keep rule in proguard-rules.pro.

Usage:
    python3 scripts/slim-audit-classes.py ORIG.jar MINIFIED.jar \
        [--allow com/reclizer/.../NetworkLimits.class ...]
"""
import sys
import zipfile


def class_set(path):
    with zipfile.ZipFile(path) as z:
        return {i.filename for i in z.infolist() if i.filename.endswith(".class")}


def main():
    args = sys.argv[1:]
    if len(args) < 2 or "--help" in args:
        print(__doc__)
        sys.exit(2)
    allow = set()
    positional = []
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--allow":
            i += 1
            while i < len(args) and not args[i].startswith("-"):
                allow.add(args[i])
                i += 1
            continue
        positional.append(a)
        i += 1
    orig, mini = positional[:2]
    removed = sorted(class_set(orig) - class_set(mini))
    added = sorted(class_set(mini) - class_set(orig))
    unexpected = [c for c in removed if c not in allow]
    for c in removed:
        tag = "expected" if c in allow else "UNEXPECTED"
        print(f"  [{tag:9s}] {c}")
    for c in added:
        print(f"  [added   ] {c}")
    print(f"\nremoved={len(removed)} added={len(added)} "
          f"unexpected={len(unexpected)}")
    if unexpected:
        print("GATE FAILED: add -keep rules for the unexpected classes "
              "(see proguard-rules.pro)")
        sys.exit(1)
    print("GATE PASSED")


if __name__ == "__main__":
    main()
