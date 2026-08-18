#!/usr/bin/env python3
"""Propagate comment-only edits from a base file to its platform mirrors.

Usage:
    python3 scripts/slim-prop-comments.py OLD_BASE NEW_BASE MIRROR...
        # OLD_BASE = the mirror file BEFORE the edit (e.g. v26_1_2's original)
        # NEW_BASE = the edited file
        # MIRROR  = the same-relative-path file in another module; its comment
        #           lines are replaced in place. Non-comment lines are kept
        #           verbatim, so platform-specific code differences survive.

Exits 1 if any changed comment hunk cannot be matched in a mirror.
"""
import sys
import difflib


def is_comment(line):
    s = line.strip()
    return s.startswith("//") or s.startswith("*") or s.startswith("/*") \
        or s.startswith("/**") or s.startswith("*/")


def comment_edits(old_lines, new_lines):
    """Yield (removed_block, added_block) for comment-only hunks."""
    sm = difflib.SequenceMatcher(None, old_lines, new_lines, autojunk=False)
    edits = []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            continue
        removed = old_lines[i1:i2]
        added = new_lines[j1:j2]
        if removed and not all(is_comment(l) for l in removed):
            continue  # code changed — not a comment-only hunk
        edits.append((removed, added))
    return edits


def apply_edits(mirror_lines, edits):
    """Replace comment blocks in the mirror; return (new_lines, unmatched)."""
    out = list(mirror_lines)
    unmatched = []
    for removed, added in edits:
        if not removed:
            # pure insertion: find a unique anchor line after the insertion
            # (the first non-comment line of `added`'s context) — not supported
            unmatched.append(("insert", added))
            continue
        # find the removed block in the mirror
        idx = None
        for i in range(len(out) - len(removed) + 1):
            if out[i:i + len(removed)] == removed:
                idx = i
                break
        if idx is None:
            unmatched.append(("replace", removed, added))
            continue
        out[idx:idx + len(removed)] = added
    return out, unmatched


def main():
    args = sys.argv[1:]
    if len(args) < 3 or "--help" in args:
        print(__doc__)
        return 2
    old_base, new_base = args[0], args[1]
    old_lines = open(old_base).read().splitlines(keepends=True)
    new_lines = open(new_base).read().splitlines(keepends=True)
    edits = comment_edits(old_lines, new_lines)
    if not edits:
        print("no comment-only changes found")
        return 1
    ok = True
    for mirror in args[2:]:
        mlines = open(mirror).read().splitlines(keepends=True)
        new_mlines, unmatched = apply_edits(mlines, edits)
        if unmatched:
            ok = False
            for u in unmatched:
                print(f"UNMATCHED in {mirror}: {u}")
            continue
        open(mirror, "w").write("".join(new_mlines))
        print(f"synced {mirror}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
