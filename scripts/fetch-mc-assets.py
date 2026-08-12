#!/usr/bin/env python3
"""Pre-fetch Minecraft vanilla assets for the Forge 26.1.2 Slime Launcher.

Background: forge_26_1_2's runClient uses ForgeGradle's Slime Launcher, which
downloads the *vanilla* asset index (id "30", ~450 MB) into the official
launcher assets dir (`~/Library/Application Support/minecraft/assets` on macOS,
`~/.minecraft/assets` elsewhere). The download is sequential, skips existing
files by size, and Mojang's CDN frequently times out on CN networks (observed
`HttpTimeoutException` on `resources.download.minecraft.net`), leaving a
15-minute `runClient` failure.

This helper fills the missing objects with parallel `curl` + retries so the
next `runClient` skips the "Downloading missing asset" phase entirely.

Usage:
    python3 scripts/fetch-mc-assets.py [asset-index-id] [--assets-root DIR]

Default asset-index-id is "30" (MC 26.1.2, used by forge_26_1_2).
Exit code 0 = all objects present; 1 = some downloads failed.
"""
import json
import os
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE_URL = "https://resources.download.minecraft.net"
WORKERS = 8


def default_assets_root() -> str:
    home = os.path.expanduser("~")
    if sys.platform == "darwin":
        return os.path.join(home, "Library", "Application Support", "minecraft", "assets")
    if sys.platform.startswith("win"):
        return os.path.join(os.environ.get("APPDATA", home), ".minecraft", "assets")
    return os.path.join(home, ".minecraft", "assets")


def fetch(hash_, size, objects_dir):
    dst = os.path.join(objects_dir, hash_[:2], hash_)
    if os.path.exists(dst) and os.path.getsize(dst) == size:
        return hash_, True
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    tmp = dst + ".tmp"
    url = f"{BASE_URL}/{hash_[:2]}/{hash_}"
    for _ in range(6):
        r = subprocess.run(
            ["curl", "-fsS", "--connect-timeout", "15", "--max-time", "120", "-o", tmp, url],
            stderr=subprocess.DEVNULL,
        )
        if r.returncode == 0 and os.path.exists(tmp) and os.path.getsize(tmp) == size:
            os.replace(tmp, dst)
            return hash_, True
        if os.path.exists(tmp):
            try:
                os.remove(tmp)
            except OSError:
                pass
    return hash_, False


def main():
    args = sys.argv[1:]
    index_id = "30"
    assets_root = default_assets_root()
    if args and not args[0].startswith("--"):
        index_id = args.pop(0)
    if args and args[0] == "--assets-root" and len(args) > 1:
        assets_root = args.pop(1)

    index_file = os.path.join(assets_root, "indexes", f"{index_id}.json")
    if not os.path.exists(index_file):
        print(f"asset index not found: {index_file}", file=sys.stderr)
        print("run :forge_26_1_2:runClient once to let Slime Launcher fetch it, "
              "or pass --assets-root", file=sys.stderr)
        return 2

    objects_dir = os.path.join(assets_root, "objects")
    objs = json.load(open(index_file))["objects"]
    todo = []
    for v in objs.values():
        p = os.path.join(objects_dir, v["hash"][:2], v["hash"])
        if not (os.path.exists(p) and os.path.getsize(p) == v["size"]):
            todo.append((v["hash"], v["size"]))

    print(f"index {index_id}: total={len(objs)} missing={len(todo)}", flush=True)
    if not todo:
        print("all assets present; runClient will skip downloading")
        return 0

    ok, failed = 0, []
    done = 0
    with ThreadPoolExecutor(max_workers=WORKERS) as ex:
        futs = {ex.submit(fetch, h, s, objects_dir): h for h, s in todo}
        for fut in as_completed(futs):
            h, good = fut.result()
            done += 1
            if good:
                ok += 1
            else:
                failed.append(h)
            if done % 100 == 0 or done == len(todo):
                print(f"progress {done}/{len(todo)} ok={ok} fail={len(failed)}", flush=True)

    print(f"DONE ok={ok} fail={len(failed)}")
    if failed:
        print("FAILED:", " ".join(failed[:20]), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
