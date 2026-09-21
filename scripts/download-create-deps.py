#!/usr/bin/env python3
"""Download the Create (机械动力) compileOnly jars and extract their
jar-in-jar bundles into local-repo, so :v1_21_1 / :forge_1_20_1 can resolve
their optional Create deployer-integration dependencies in CI.

The jars are NOT committed (cf. .gitignore *.jar). Mirrors the contract of
scripts/download-tacz.sh: idempotent, integrity-checked (zipfile.is_zipfile),
safe to run before every build.

Artifacts produced (coordinates already pinned in the build.gradle files):
- local-repo/com/simibubi/create/create/6.0.10/create-6.0.10.jar   (v1_21_1)
- local-repo/com/simibubi/create/create/6.0.8/create-6.0.8.jar    (forge_1_20_1)
- every entry of META-INF/jarjar/metadata.json inside each Create jar,
  placed at <group>/<artifact>/<version>/<artifact>-<version>.jar
  (ponder / flywheel / Registrate / mixinextras)

Env overrides (mainly for testing): LOCAL_REPO, CREATE_SIDE (v1_21_1 |
forge_1_20_1; default = both).
"""
import json
import os
import pathlib
import urllib.error
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
LOCAL_REPO = pathlib.Path(os.environ.get("LOCAL_REPO", ROOT / "local-repo"))
SIDE = os.environ.get("CREATE_SIDE", "")
UA = {"User-Agent": "CS2-Box CI (github.com/wikkd/CS2-Box)"}

# Modrinth project slug "create"; target release + MC/loader axis.
TARGETS = [
    {"prefix": "6.0.10", "game": "1.21.1", "loader": "neoforge",
     "dest": "com/simibubi/create/create/6.0.10/create-6.0.10.jar"},
    {"prefix": "6.0.8", "game": "1.20.1", "loader": "forge",
     "dest": "com/simibubi/create/create/6.0.8/create-6.0.8.jar"},
]


def modrinth_file_url(target):
    """Exact release file URL from the Modrinth API. Modrinth version_number
    formats are inconsistent across Create releases (6.0.10+mc1.21.1 vs
    mc1.21.1-6.0.9), so the reliable key is the exact jar filename
    create-<mc>-<release>.jar."""
    exact = f"create-{target['game']}-{target['prefix']}.jar"
    api = (f"https://api.modrinth.com/v2/project/create/version"
           f"?game_versions=%5B%22{target['game']}%22%5D"
           f"&loaders=%5B%22{target['loader']}%22%5D")
    with urllib.request.urlopen(
            urllib.request.Request(api, headers=UA), timeout=30) as resp:
        versions = json.load(resp)
    for v in versions:
        for f in v.get("files", []):
            if f["filename"] == exact and f.get("primary", True):
                return f["url"], v.get("version_number", exact)
    raise SystemExit(f"[create-deps] Modrinth has no file {exact!r}")


def download(url, dest):
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_name(dest.name + ".part")
    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(
                    urllib.request.Request(url, headers=UA), timeout=180) as resp, \
                    open(tmp, "wb") as out:
                while True:
                    chunk = resp.read(1 << 20)
                    if not chunk:
                        break
                    out.write(chunk)
            # Modrinth CDN can serve byte-count-correct but truncated jars on
            # flaky networks — same integrity gate as download-tacz.sh.
            if zipfile.is_zipfile(tmp):
                os.replace(tmp, dest)
                return
        except (OSError, urllib.error.URLError) as exc:
            print(f"  attempt {attempt}/3 failed: {attempt and _msg(exc)}")
        finally:
            if tmp.exists() and not dest.exists():
                tmp.unlink(missing_ok=True)
    raise SystemExit(f"[create-deps] download failed: {url}")


def _msg(exc):
    return str(exc)[:120]


def extract_jar_in_jar(create_jar, local_repo):
    """Copy each META-INF/jarjar bundle to its maven coordinates inside
    local-repo (artifactVersion wins over the declared range)."""
    with zipfile.ZipFile(create_jar) as z:
        try:
            meta = json.loads(z.read("META-INF/jarjar/metadata.json"))
        except KeyError:
            print(f"  (no jarjar metadata in {create_jar.name})")
            return 0
        written = 0
        for entry in meta.get("jars", []):
            ident = entry.get("identifier", {})
            group = ident.get("group", "")
            artifact = ident.get("artifact", "")
            version = entry.get("version", {})
            version_str = (version.get("artifactVersion")
                           if isinstance(version, dict) else str(version))
            path = entry.get("path") or entry.get("file")
            if not (group and artifact and version_str and path):
                continue
            dest = local_repo / group.replace(".", "/") / artifact / \
                version_str / f"{artifact}-{version_str}.jar"
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(z.read(path))
            print(f"  {group}:{artifact}:{version_str} -> {dest.relative_to(local_repo)}")
            written += 1
        return written


def main():
    side_filter = SIDE.strip()
    count = 0
    for target in TARGETS:
        if side_filter and side_filter not in target["dest"]:
            continue
        dest = LOCAL_REPO / target["dest"]
        if dest.exists() and zipfile.is_zipfile(dest):
            print(f"[create-deps] present: {dest.relative_to(LOCAL_REPO)}")
        else:
            url, vn = modrinth_file_url(target)
            print(f"[create-deps] downloading Create {vn} ...")
            download(url, dest)
        count += extract_jar_in_jar(dest, LOCAL_REPO)
    print(f"[create-deps] OK ({count} jar-in-jar bundles written to {LOCAL_REPO})")


if __name__ == "__main__":
    main()
