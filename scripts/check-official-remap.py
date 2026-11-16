#!/usr/bin/env python3
"""Executable-ref gate for 1.20.1 Forge dev remapped jars (SRG -> official).

Turned from the round-21 forensic script (csgo-r21-gate.py). Classifies every
SRG-named NameAndType constant-pool entry per class as:

  EXECUTABLE  - reached through a Memberref (Fieldref/Methodref/Interface-
                methodref): the JVM resolves it at link time, so a leftover
                SRG name here WILL throw NoSuchFieldError/NoSuchMethodError
                on the official-mapped ForgeGradle 7 dev runtime -> FAIL.
  METADATA    - only referenced by attributes the JVM never resolves
                (EnclosingMethod host-method names of anonymous classes,
                LocalVariableTable, ...): tolerated and counted.

Also scans *.refmap.json / *.json entries for leftover SRG ids (mixin refmap
rewrite leftovers).

Usage:
  python check-official-remap.py                # audit all *-official.jar in run/mods
  python check-official-remap.py --gate JAR...  # gate specific jars (remap-1201.sh)
  python check-official-remap.py --deps         # mods.toml mandatory-dependency
                                                # check vs run/mods contents
Exit code: number of failures (gate mode: first failing jar -> 1).
"""
import argparse
import glob
import json
import os
import re
import struct
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS = os.path.join(REPO, "forge_1_20_1", "run", "mods")
SRG = re.compile(rb"^(?:[fm]_\d+_|field_\d+|method_\d+|p_\d+_\d+_)$")
SRG_TEXT = re.compile(r"(?<![A-Za-z0-9])(?:[fm]_\d+_|field_\d+|method_\d+)(?![A-Za-z0-9])")


def classify(data):
    """Return (executable_srg_names, metadata_srg_names) for one class file.

    Constant-pool layout per JVM spec: NameAndType = (name_index,
    descriptor_index). Memberrefs point at the NAT *slot*; round 19's false
    negative came from reading the descriptor index as the name.
    """
    n = struct.unpack(">H", data[8:10])[0]
    i = 10
    idx = 1
    utf8 = {}
    nat = {}            # nat slot -> name utf8 index
    mem_to_nat = set()  # NAT slots reached by a Memberref
    while idx < n:
        tag = data[i]
        if tag == 1:
            ln = struct.unpack(">H", data[i + 1:i + 3])[0]
            utf8[idx] = data[i + 3:i + 3 + ln]
            i += 3 + ln
        elif tag in (7, 8, 16, 19, 20):
            i += 3
        elif tag == 15:
            i += 4  # MethodHandle -> memberref; the walk covers it
        elif tag in (3, 4):
            i += 5
        elif tag in (5, 6):
            i += 9
            idx += 1
        elif tag in (9, 10, 11):
            a, b = struct.unpack(">HH", data[i + 1:i + 5])
            mem_to_nat.add(b)
            i += 5
        elif tag == 12:
            a, b = struct.unpack(">HH", data[i + 1:i + 5])
            nat[idx] = a
            i += 5
        elif tag in (17, 18):
            i += 5
        else:
            raise ValueError("bad tag %d at cp %d" % (tag, idx))
        idx += 1
    exe, meta = set(), set()
    for slot, name_idx in nat.items():
        nm = utf8.get(name_idx)
        if nm and SRG.match(nm):
            (exe if slot in mem_to_nat else meta).add(nm)
    return exe, meta


def refmap_leftovers(zf):
    """Distinct SRG ids inside any *.json entry of the jar (refmap rewrite
    leftovers). Returns list of (entry, count)."""
    out = []
    for cn in zf.namelist():
        if not cn.endswith(".json"):
            continue
        try:
            txt = zf.read(cn).decode("utf-8")
        except Exception:
            continue
        hits = sorted(set(SRG_TEXT.findall(txt)))
        if hits:
            out.append((cn, len(hits)))
    return out


def gate_jar(path):
    """Gate one jar. Returns (ok, lines) - lines already indented."""
    lines = []
    ok = True
    zf = zipfile.ZipFile(path)
    exe_where, meta_total, parsed = {}, 0, 0
    for cn in zf.namelist():
        if not cn.endswith(".class"):
            continue
        try:
            exe, meta = classify(zf.read(cn))
        except Exception as e:
            lines.append("  PARSE FAIL %s: %s" % (cn, e))
            ok = False
            continue
        parsed += 1
        meta_total += len(meta)
        for nm in exe:
            exe_where.setdefault(nm, []).append(cn)
    for nm, where in exe_where.items():
        ok = False
        lines.append("  EXECUTABLE SRG %s in %s" % (nm.decode(), where[0]))
    left = refmap_leftovers(zf)
    for cn, cnt in left:
        ok = False
        lines.append("  REFMAP SRG %s: %d distinct ids" % (cn, cnt))
    status = "PASS" if ok else "FAIL"
    lines.insert(0, "[%s] %s (classes=%d, metadata-only srg NAT=%d)"
                 % (status, os.path.basename(path), parsed, meta_total))
    return ok, lines


def audit_mods():
    jars = sorted(glob.glob(os.path.join(MODS, "*-official.jar")))
    print("AUDIT %d official jars in %s" % (len(jars), os.path.relpath(MODS, REPO)))
    fail = 0
    for j in jars:
        ok, lines = gate_jar(j)
        for ln in lines:
            print(ln)
        if not ok:
            fail += 1
    print("audit:", "ALL PASS" if fail == 0 else "%d FAILURES" % fail)
    return fail


def mandatory_dep_ids(zf):
    """modIds declared mandatory=true in META-INF/mods.toml (best-effort)."""
    try:
        txt = zf.read("META-INF/mods.toml").decode("utf-8", "replace")
    except KeyError:
        return []
    # pair each dependency block's modId with its mandatory flag
    blocks, cur, mand = [], None, False
    for raw in txt.splitlines():
        line = raw.strip()
        if line.startswith("[[dependencies") or line.startswith("[dependencies"):
            if cur is not None:
                blocks.append((cur, mand))
            cur, mand = None, False
            continue
        if cur is None or (line.startswith("[") and not line.startswith("[[dependencies")):
            if line.startswith("[") and not line.startswith("[[dependencies"):
                if cur is not None:
                    blocks.append((cur, mand))
                cur, mand = None, False
            continue
        if "=" in line:
            k, _, v = line.partition("=")
            k, v = k.strip(), v.strip().strip('"')
            if k == "modId":
                cur = v
            elif k == "mandatory":
                mand = v.lower() == "true"
    if cur is not None:
        blocks.append((cur, mand))
    return [m for m, req in blocks if req and m]


def check_deps():
    """Cross-check mandatory modIds of every deployed jar against the
    run/mods file list (round-21's 12:22 missing-create crash class)."""
    jars = sorted(glob.glob(os.path.join(MODS, "*.jar")))
    names = set()
    for j in jars:
        b = os.path.basename(j)
        # strip -official suffix and version-ish tails for modId guessing
        names.add(b.lower())
    warn = 0
    print("DEP CHECK over %d jars in run/mods" % len(jars))
    for j in jars:
        try:
            zf = zipfile.ZipFile(j)
        except Exception as e:
            print("  UNREADABLE %s: %s" % (os.path.basename(j), e))
            warn += 1
            continue
        for mid in mandatory_dep_ids(zf):
            # crude containment: modId appears in any deployed file name
            if not any(mid.lower() in n for n in names):
                print("  WARN %s: mandatory dependency '%s' not in run/mods"
                      % (os.path.basename(j), mid))
                warn += 1
    print("dep check:", "clean" if warn == 0 else "%d warnings" % warn)
    return 0  # warnings never fail the gate


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--gate", nargs="*", metavar="JAR",
                    help="gate the given jars (used by remap-1201.sh)")
    ap.add_argument("--deps", action="store_true",
                    help="check mandatory mods.toml dependencies vs run/mods")
    args = ap.parse_args()
    if args.gate is not None:
        ok = True
        for j in args.gate:
            good, lines = gate_jar(j)
            for ln in lines:
                print(ln)
            ok = ok and good
        sys.exit(0 if ok else 1)
    fail = audit_mods()
    if args.deps:
        check_deps()
    sys.exit(0 if fail == 0 else 1)


if __name__ == "__main__":
    main()
