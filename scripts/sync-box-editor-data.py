#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sync box-editor embedded data from the authoritative docs.

Reads docs/box-schema/*.schema.json, docs/examples/*.json and the TACZ
compat pack, then regenerates box-editor/js/data.js (embedded, so the
editor works from file:// with no fetch) and copies the raw schemas into
box-editor/data/schemas/shared/ for future per-version overrides.

Run from the repository root:
    python scripts/sync-box-editor-data.py
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT_JS = ROOT / "box-editor" / "js" / "data.js"
SCHEMA_COPY_DIR = ROOT / "box-editor" / "data" / "schemas" / "shared"

# Version metadata. Extend per version when the runtime schema starts
# diverging: add a "schemaFile" pointing at box-editor/data/schemas/<v>/...
VERSIONS = [
    {
        "key": "1.21.1",
        "label": {"en": "1.21.1 (NeoForge)", "zh": "1.21.1 (NeoForge)"},
        "short": {"en": "1.21.1", "zh": "1.21.1"},
        "taczVariants": True,
        "components": True,
        "note": {
            "en": "Data Components; TACZ GunId/AmmoId variants supported.",
            "zh": "Data Components；支持 TACZ GunId/AmmoId 变体键。",
        },
    },
    {
        "key": "26.1.2",
        "label": {"en": "26.1.2", "zh": "26.1.2"},
        "short": {"en": "26.1.2", "zh": "26.1.2"},
        "taczVariants": False,
        "components": True,
        "note": {
            "en": "Data Components; no id#variant price keys.",
            "zh": "Data Components；价格表不使用 id#变体 子键。",
        },
    },
    {
        "key": "26.2",
        "label": {"en": "26.2", "zh": "26.2"},
        "short": {"en": "26.2", "zh": "26.2"},
        "taczVariants": False,
        "components": True,
        "note": {
            "en": "Data Components; no id#variant price keys.",
            "zh": "Data Components；价格表不使用 id#变体 子键。",
        },
    },
    {
        "key": "forge-26.1.2",
        "label": {"en": "Forge 26.1.2", "zh": "Forge 26.1.2"},
        "short": {"en": "Forge 26.1.2", "zh": "Forge 26.1.2"},
        "taczVariants": False,
        "components": True,
        "note": {
            "en": "Data Components; no id#variant price keys.",
            "zh": "Data Components；价格表不使用 id#变体 子键。",
        },
    },
    {
        "key": "forge-26.2",
        "label": {"en": "Forge 26.2", "zh": "Forge 26.2"},
        "short": {"en": "Forge 26.2", "zh": "Forge 26.2"},
        "taczVariants": False,
        "components": True,
        "note": {
            "en": "Data Components; no id#variant price keys.",
            "zh": "Data Components；价格表不使用 id#变体 子键。",
        },
    },
    {
        "key": "forge-1.20.1",
        "label": {"en": "Forge 1.20.1", "zh": "Forge 1.20.1"},
        "short": {"en": "Forge 1.20.1", "zh": "Forge 1.20.1"},
        "taczVariants": True,
        "components": False,
        "note": {
            "en": "Legacy NBT tag (no Data Components); TACZ variants supported.",
            "zh": "旧版 NBT tag（无 Data Components）；支持 TACZ 变体键。",
        },
    },
]

# The five grade bands (grade1..grade5).
GRADE_NAMES = [
    {"id": "consumer", "zh": "消费级（灰）", "en": "Consumer (Gray)"},
    {"id": "industrial", "zh": "工业级（浅蓝）", "en": "Industrial (Light Blue)"},
    {"id": "mil_spec", "zh": "军规级（蓝）", "en": "Mil-Spec (Blue)"},
    {"id": "restricted", "zh": "受限级（紫）", "en": "Restricted (Purple)"},
    {"id": "classified", "zh": "保密级（粉/红）", "en": "Classified (Pink/Red)"},
]

GRADE_DEFAULT_PRICES = [6, 10, 16, 22, 30]
GRADE_RECYCLE_YIELDS = [3, 5, 7, 8, 8]


def load_json(path: pathlib.Path):
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def build_examples():
    examples = []
    doc_examples = [
        ("weapon_dealer", "weapon_dealer.json", "weapon_dealer.json", "_prices.json",
         {"en": "Weapon vendor terminal", "zh": "武器供应商终端"}),
        ("enchant_vendor", "enchant_vendor.json", "enchant_vendor.json", "_prices.json",
         {"en": "Enchanted book vendor terminal", "zh": "附魔书终端"}),
        ("supply_outpost", "supply_outpost.json", "supply_outpost.json", "_prices.json",
         {"en": "Survival supply terminal", "zh": "生存补给终端"}),
        ("tacz_gun_crate", "gun_crate.json", "gun_crate.json", "_prices.json",
         {"en": "TACZ gun crate (compat pack)", "zh": "TACZ 军火枪械箱（联动包）"}),
    ]
    for key, box_file, _, prices_file, desc in doc_examples:
        if key == "tacz_gun_crate":
            box_path = ROOT / "compat-packs" / "tacz-pack" / box_file
            prices_path = ROOT / "compat-packs" / "tacz-pack" / prices_file
        else:
            box_path = ROOT / "docs" / "examples" / box_file
            prices_path = ROOT / "docs" / "examples" / prices_file
        examples.append({
            "key": key,
            "file": box_file,
            "desc": desc,
            "box": load_json(box_path),
            "prices": load_json(prices_path),
        })
    return examples


def main():
    if sys.version_info < (3, 9):
        print("python 3.9+ required", file=sys.stderr)
        return 1
    box_schema = load_json(ROOT / "docs" / "box-schema" / "box.schema.json")
    prices_schema = load_json(ROOT / "docs" / "box-schema" / "prices.schema.json")
    examples = build_examples()

    payload = {
        "schemas": {"box": box_schema, "prices": prices_schema},
        "versions": VERSIONS,
        "gradeNames": GRADE_NAMES,
        "gradeDefaultPrices": GRADE_DEFAULT_PRICES,
        "gradeRecycleYields": GRADE_RECYCLE_YIELDS,
        "examples": examples,
    }
    js = (
        "// AUTO-GENERATED by scripts/sync-box-editor-data.py - do not edit by hand.\n"
        "// Run `python scripts/sync-box-editor-data.py` after changing\n"
        "// docs/box-schema/*.schema.json or the bundled examples.\n"
        "window.CSBDATA = "
        + json.dumps(payload, ensure_ascii=False, indent=2)
        + ";\n"
    )

    SCHEMA_COPY_DIR.mkdir(parents=True, exist_ok=True)
    (SCHEMA_COPY_DIR / "box.schema.json").write_text(
        json.dumps(box_schema, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (SCHEMA_COPY_DIR / "prices.schema.json").write_text(
        json.dumps(prices_schema, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    OUT_JS.parent.mkdir(parents=True, exist_ok=True)
    OUT_JS.write_text(js, encoding="utf-8")
    print(f"wrote {OUT_JS.relative_to(ROOT)} ({len(js)} bytes)")
    print(f"wrote {SCHEMA_COPY_DIR.relative_to(ROOT)}/box.schema.json, prices.schema.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())