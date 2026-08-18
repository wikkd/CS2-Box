#!/usr/bin/env bash
# Re-encode the three CS2-Box UI sounds to mono 22050 Hz Vorbis ~q4 (~64 kbps).
# Originals are copied next to the script's caller in /tmp/csbox-slim-sounds-orig
# (git history also preserves the pre-change files).
#
# Requires: ffmpeg (brew install ffmpeg) + vorbis-tools (brew install vorbis-tools)
# Usage:    scripts/slim-encode-sounds.sh
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
SOUNDS="$DIR/../common/src/main/resources/assets/csgobox/sounds"
BACKUP="${CSBOX_SLIM_BACKUP:-/tmp/csbox-slim-sounds-orig}"

mkdir -p "$BACKUP"
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT
for f in "$SOUNDS"/*.ogg; do
    name="$(basename "$f")"
    cp "$f" "$BACKUP/$name"
    before=$(stat -f%z "$f")
    tmp="$TMPDIR/$name"
    wav="$TMPDIR/$name.wav"
    ffmpeg -v error -y -i "$f" -ac 1 -ar 22050 -f wav "$wav"
    oggenc -Q -q 4 -o "$tmp" "$wav"
    after=$(stat -f%z "$tmp")
    mv "$tmp" "$f"
    echo "$name: $before -> $after bytes ($(( (before-after)*100/before ))% smaller)"
done
