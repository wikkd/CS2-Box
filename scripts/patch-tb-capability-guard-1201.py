#!/usr/bin/env python3
"""Splice the TB capability config guard into travelersbackpack-*-official.jar.

Travelers Backpack 9.1.58 reads its SERVER config from the item capability
provider (TravelersBackpackItem$2 -> enableCurios). On a client, SERVER specs
don't load until a world exists, so Epic Fight's config-load item scan crashes
the FG7 dev client with "Cannot get config value before config is loaded"
(Forge's dev-only strict check; production never throws — crash reports
2026-11-18_10.26 and 2026-11-20_16.09). The fix recompiles the tiny anonymous
provider class (scripts/patch-src/tb-1201-config-guard/TravelersBackpackItem2.java)
with an IllegalStateException guard and replaces exactly that one entry.

Usage:
    python patch-tb-capability-guard-1201.py <tb-official.jar> [<more.jar> ...]

Idempotent: a jar whose $2 class already carries the guard is skipped.
Classpath for the compile: the mavenizer recompiled forge jar (official
names) + the target TB jar itself. javac is located under ~/.jdks
(ms-21.0.9 preferred to match remap-1201.sh, ms-17.0.14 fallback), always
with --release 17.

NOTE: this patch runs inside remap-1201.sh on fresh builds only. After
editing the patch source, force a re-remap by deleting the TB product and
its .stamp (travelersbackpack-*-official.jar*) so the pipeline re-applies it.
"""
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

TARGET_ENTRY = "com/tiviacz/travelersbackpack/items/TravelersBackpackItem$2.class"
MARKER = b"curiosEnabledGuarded"
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "patch-src", "tb-1201-config-guard", "TravelersBackpackItem2.java")
# repo root = parent of scripts/ — never resolve from getcwd(): remap-1201.sh
# invokes this with the caller's working directory.
ROOT = os.path.dirname(HERE)
FORGE_JAR = os.path.join(".gradle", "mavenizer", "repo", "net", "minecraftforge",
                         "forge", "1.20.1-47.4.22", "forge-1.20.1-47.4.22.jar")
# TravelersBackpackCurio.getCurioCapability's erased signature drags
# top.theillusivec4.curios...ICurio into the compile — curios ships the API.
CURIOS_JAR = os.path.join("local-repo", "com", "kaleidoscope", "1.20.1",
                          "curios-forge-5.14.1+1.20.1.jar")


def find_javac():
    home = os.path.expanduser("~")
    for v in ("ms-21.0.9", "ms-17.0.14"):
        for exe in ("javac.exe", "javac"):
            p = os.path.join(home, ".jdks", v, "bin", exe)
            if os.path.isfile(p):
                return p
    return "javac"  # last resort: PATH


def compile_patch_class(javac, cp):
    out = tempfile.mkdtemp(prefix="tbpatch-")
    argv = [javac, "--release", "17", "-encoding", "UTF-8", "-nowarn",
            "-cp", cp, "-d", out, SRC]
    proc = subprocess.run(argv, capture_output=True, text=True)
    if proc.returncode != 0:
        sys.stderr.write(proc.stdout or "")
        sys.stderr.write(proc.stderr or "")
        raise SystemExit("ERROR: javac failed (rc=%d)" % proc.returncode)
    cls = os.path.join(out, *TARGET_ENTRY.split("/"))
    if not os.path.isfile(cls):
        raise SystemExit("ERROR: compiled class missing: %s" % cls)
    return cls, out


def patch_jar(jar, cls):
    with zipfile.ZipFile(jar, "r") as z:
        names = z.namelist()
        if TARGET_ENTRY not in names:
            raise SystemExit("ERROR: %s not found in %s" % (TARGET_ENTRY, jar))
        old = z.read(TARGET_ENTRY)
        if MARKER in old:
            print("already patched, skip: %s" % os.path.basename(jar))
            return False
        entries = [(item, z.read(item.filename)) for item in z.infolist()]
    data = open(cls, "rb").read()
    tmp = jar + ".tmp.jar"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for item, payload in entries:
            zout.writestr(item, data if item.filename == TARGET_ENTRY else payload)
    shutil.move(tmp, jar)
    # read-back verification
    with zipfile.ZipFile(jar, "r") as z:
        if MARKER not in z.read(TARGET_ENTRY):
            raise SystemExit("ERROR: patch verification failed: %s" % jar)
    print("patched %s: %s %d -> %d bytes"
          % (os.path.basename(jar), TARGET_ENTRY.split("/")[-1], len(old), len(data)))
    return True


def main():
    jars = [a for a in sys.argv[1:] if a != "-"]
    if not jars or not os.path.isfile(SRC):
        print(__doc__)
        return 1
    forge = os.path.join(ROOT, FORGE_JAR)
    if not os.path.isfile(forge):
        raise SystemExit("ERROR: forge compile jar missing: %s" % forge)
    curios = os.path.join(ROOT, CURIOS_JAR)
    if not os.path.isfile(curios):
        raise SystemExit("ERROR: curios compile jar missing: %s" % curios)
    for jar in jars:
        if not os.path.isfile(jar):
            raise SystemExit("ERROR: jar missing: %s" % jar)
    cp = forge + os.pathsep + curios + os.pathsep + jars[0]
    cls, out = compile_patch_class(find_javac(), cp)
    try:
        for jar in jars:
            patch_jar(jar, cls)
    finally:
        shutil.rmtree(out, ignore_errors=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
