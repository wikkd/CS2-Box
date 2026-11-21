"""Merge incremental TSRG entries into the ForgeGradle official->SRG tsrg.

Usage:
    python scripts/merge-tsrg-1201.py [output.tsrg.gz]

Reads the default official-1.20.1-obf.tsrg.gz from the MCP mavenizer cache
plus scripts/tsrg-extra-1201.txt (tsrg2 rows in official->SRG orientation)
and writes the concatenated mapping to output (default: build/tsrg-merged-1201.tsrg.gz).
Point remap-1201.sh at the result:

    TSRG_OVERRIDE=<output> bash scripts/remap-1201.sh

Deterministic output (fixed gzip mtime) so the remap stamp fingerprint stays
stable across reruns.
"""
import gzip
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_TSRG = (ROOT / ".gradle-cache-probe-placeholder" if False else
                Path.home() / ".gradle/caches/minecraftforge/forgegradle/mavenizer/caches/"
                "mcp/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/"
                "data/mapings/official/1.20.1/official-1.20.1-obf.tsrg.gz")
EXTRA = ROOT / "scripts" / "tsrg-extra-1201.txt"
DEFAULT_OUT = ROOT / "build" / "tsrg-merged-1201.tsrg.gz"


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUT
    if not DEFAULT_TSRG.exists():
        sys.exit(f"base tsrg not found: {DEFAULT_TSRG}")
    base = gzip.open(DEFAULT_TSRG, "rt", encoding="utf-8").read()
    extra = EXTRA.read_text(encoding="utf-8")
    # keep only non-comment lines from the extra file (parser skips blanks but
    # comments would otherwise parse as garbage class lines)
    extra_rows = "".join(
        ln + "\n" for ln in extra.splitlines() if ln.strip() and not ln.lstrip().startswith("#")
    )
    merged = base.rstrip("\n") + "\n" + extra_rows
    out.parent.mkdir(parents=True, exist_ok=True)
    with gzip.GzipFile(out, "wb", mtime=0) as fh:
        fh.write(merged.encode("utf-8"))
    print(f"merged tsrg written: {out} ({out.stat().st_size} bytes, "
          f"base {len(base.splitlines())} lines + extra {len(extra_rows.splitlines())} lines)")


if __name__ == "__main__":
    main()
