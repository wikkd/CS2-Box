#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Mechanical port forge_1_20_1 (Forge 47.x) -> forge_1_18_2 (Forge 40.x).

Baseline is forge_1_20_1 (the loader-matching line: SimpleChannel networking,
NBT item storage, LazyOptional capabilities, ForgeConfigSpec). The big 1.18.2
delta — no GuiGraphics — is handled by the mechanical PoseStack transform below
plus a hand-written AnimRenderOps facade; the rest of the API deltas surface as
compile errors and are fixed by hand (see docs/PORTING-forge-1182.md).

Run from the repo root:  python scripts/port-forge-1182.py [--dry-run]
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "forge_1_20_1" / "src" / "main" / "java" / "com" / "reclizer" / "csgobox" / "forge_1_20_1"
DST = ROOT / "forge_1_18_2" / "src" / "main" / "java" / "com" / "reclizer" / "csgobox" / "forge_1_18_2"

# No 1.18.2 build exists: Create (deployer API shape differs in 0.5.1 — revisit
# later), EMI / WTHIT / TOP (upstream never shipped for MC 1.18.2). Hand-adapted
# below the script: entry point, creative tab, AnimRenderOps facade, REI plugin.
EXCLUDE = [
    "create/DeployerBoxOpen.java",
    "create/DeployerBoxOpenRecipe.java",
    "create/DeployerBoxOpenRecipeSearch.java",
    "emi/BoxEmiPlugin.java",
    "emi/BoxEmiRecipe.java",
    "wthit/BoxItemEntityProvider.java",
    "wthit/CsgoBoxWthitPlugin.java",
    "wthit/RecyclerBlockProvider.java",
    "top/BoxItemEntityProvider.java",
    "top/CsgoBoxTopPlugin.java",
    "top/RecyclerBlockProvider.java",
    "utils/AnimRenderOps.java",
    "CsgoBox.java",
    "item/ModItems.java",
]

REPLACEMENTS = [
    # Package rename.
    (r"com\.reclizer\.csgobox\.forge_1_20_1", "com.reclizer.csgobox.forge_1_18_2", "package rename"),
    # --- registries: 1.18.2 has no BuiltInRegistries ---
    (r"import net\.minecraft\.core\.registries\.BuiltInRegistries;",
     "import net.minecraftforge.registries.ForgeRegistries;", "BuiltInRegistries import"),
    (r"BuiltInRegistries\.ITEM\b", "ForgeRegistries.ITEMS", "item registry"),
    (r"BuiltInRegistries\.ENTITY_TYPE\b", "ForgeRegistries.ENTITY_TYPES", "entity registry"),
    # --- GuiGraphics does not exist on 1.18.2 ---
    (r"import net\.minecraft\.client\.gui\.GuiGraphics;",
     "import com.mojang.blaze3d.vertex.PoseStack;", "GuiGraphics import"),
    (r"\bGuiGraphics\b", "PoseStack", "GuiGraphics type"),
    (r"\bguiGraphics\b", "pose", "guiGraphics param"),
    (r"\bgg\b", "pose", "gg param"),
    # Direct vanilla call sites on the (now) pose object — Screen/GuiComponent
    # members on 1.18.2 take the PoseStack as the first argument:
    (r"\bpose\.fill\(", "fill(pose, ", "pose.fill"),
    (r"\bpose\.fillGradient\(", "fillGradient(pose, ", "pose.fillGradient"),
    (r"\bpose\.drawString\(", "drawString(pose, ", "pose.drawString"),
    (r"\bpose\.drawCenteredString\(", "drawCenteredString(pose, ", "pose.drawCenteredString"),
    (r"\bpose\.renderTooltip\(", "renderTooltip(pose, ", "pose.renderTooltip"),
    (r"\bpose\.renderComponentTooltip\(", "renderComponentTooltip(pose, ", "pose.renderComponentTooltip"),
    (r"\bpose\.pose\(\)", "pose", "pose.pose()"),
    (r"[ \t]*pose\.flush\(\);[ \t]*\n", "", "pose.flush()"),
]


def apply_replacements(text):
    used = []
    for pattern, repl, desc in REPLACEMENTS:
        if re.search(pattern, text):
            text = re.sub(pattern, repl, text)
            used.append(desc)
    return text, used


def main():
    dry = "--dry-run" in sys.argv
    copied, replaced, skipped = 0, 0, 0
    for src_file in sorted(SRC.rglob("*.java")):
        rel = src_file.relative_to(SRC).as_posix()
        if rel in EXCLUDE:
            skipped += 1
            print(f"SKIP  {rel}  (hand-adapted / not ported)")
            continue
        out_file = DST / rel
        text = src_file.read_text(encoding="utf-8")
        new_text, used = apply_replacements(text)
        if dry:
            print(f"{'REPL ' if used else 'COPY '} {rel}")
        else:
            out_file.parent.mkdir(parents=True, exist_ok=True)
            out_file.write_text(new_text, encoding="utf-8", newline="\n")
        if used:
            replaced += 1
        else:
            copied += 1
    mode = "(dry-run)" if dry else ""
    print(f"\nDone {mode}: {replaced} file(s) with replacements, {copied} verbatim, {skipped} excluded.")


if __name__ == "__main__":
    main()
