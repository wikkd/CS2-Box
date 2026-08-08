#!/usr/bin/env python3
"""Idempotent merge: fix tiny reward icons in CsboxBulkResultScreen.

Root cause: IconListTools.renderItemFrame() derives the icon size from the
PASSED width/height (frame = w*8% x h*11%, icon = 60% of frame width). It was
designed for the main open screen which passes FULL-SCREEN dims. The bulk
result screen passes CELL dims (colW=360 -> 17px icons; 68px grid cells ->
~3px icons).

Fix: add IconListTools.renderRewardCell() (frame fills the cell, icon scales
to the cell interior) and switch the two call sites in CsboxBulkResultScreen
to it. Mirrors the per-platform API flavours:
  - v1_21_0/v1_21_1: renderGuiItem(entity, entity.level(), guiGraphics, ...)
  - v1_21_3+:       renderGuiItem(entity, guiGraphics, ...)
  - v26_1_2/v26_2:  GuiGraphicsExtractor + blitGoldItemAspect()

Usage:
  scripts/merge-reward-cell.py --check   # dry-run, report only
  scripts/merge-reward-cell.py           # apply (idempotent)
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

LEGACY_OLD = """        int itemX = pX + frameWidth * 20 / 100;
        int itemY = pY + frameHeight * 10 / 100;
        if (grade == 5) {"""

LEGACY_00 = """        int itemX = pX + frameWidth * 20 / 100;
        int itemY = pY + frameHeight * 10 / 100;
        if (grade == 5) {
            guiGraphics.fillGradient(pX, pY, toX, toY, 0xFF533c00, 0xFFb69008);
            guiGraphics.fill(pX, pY, pX + 2, toY, color);
            guiGraphics.blit(GOLD_ITEM_TEXTURE, pX + 2, pY + 2, 0, 0,
                    frameWidth - 4, frameHeight - 4, frameWidth - 4, frameHeight - 4);
        } else {
            renderRarity(guiGraphics, pX, pY, toX, toY, color);
            renderGuiItem(entity, entity.level(), guiGraphics, itemStack, itemX, itemY, scale);
        }
    }"""

LEGACY_NEW = """        int itemX = pX + frameWidth * 20 / 100;
        int itemY = pY + frameHeight * 10 / 100;
        if (grade == 5) {
            guiGraphics.fillGradient(pX, pY, toX, toY, 0xFF533c00, 0xFFb69008);
            guiGraphics.fill(pX, pY, pX + 2, toY, color);
            guiGraphics.blit(GOLD_ITEM_TEXTURE, pX + 2, pY + 2, 0, 0,
                    frameWidth - 4, frameHeight - 4, frameWidth - 4, frameHeight - 4);
        } else {
            renderRarity(guiGraphics, pX, pY, toX, toY, color);
            renderGuiItem(entity, guiGraphics, itemStack, itemX, itemY, scale);
        }
    }"""

GOLD_LEGACY = """        if (grade == 5) {
            guiGraphics.fillGradient(pX, pY, pX + width, pY + height, 0xFF533c00, 0xFFb69008);
            guiGraphics.fill(pX, pY, pX + 2, pY + height, color);
            guiGraphics.blit(GOLD_ITEM_TEXTURE, pX + 2, pY + 2, 0, 0,
                    width - 4, height - 4, width - 4, height - 4);
        } else {
            guiGraphics.fillGradient(pX, pY, pX + width, pY + height, 0xFF696969, 0xFFD3D3D3);
            guiGraphics.fill(pX, pY, pX + 2, pY + height, color);
            renderGuiItem({entity_arg}, itemX, itemY, iconW / 16F);
        }
    }"""

GOLD_V26 = """        if (grade == 5) {
            guiGraphics.fillGradient(pX, pY, pX + width, pY + height, 0xFF533c00, 0xFFb69008);
            guiGraphics.fill(pX, pY, pX + 2, pY + height, color);
            blitGoldItemAspect(guiGraphics, pX + 2, pY + 2, width - 4, height - 4);
        } else {
            guiGraphics.fillGradient(pX, pY, pX + width, pY + height, 0xFF696969, 0xFFD3D3D3);
            guiGraphics.fill(pX, pY, pX + 2, pY + height, color);
            renderGuiItem(entity, guiGraphics, itemStack, itemX, itemY, iconW / 16F);
        }
    }"""

CELL_LEGACY_00 = """    public static void renderRewardCell(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, int pX, int pY, int width, int height, int grade) {
        int color = ColorTools.colorItems(grade);
        int pad = Math.max(3, Math.min(8, width / 10));
        int iconW = Math.max(8, width - pad * 2);
        int iconH = Math.max(8, height - pad * 2);
        int itemX = pX + (width - iconW) / 2;
        int itemY = pY + (height - iconH) / 2;
""" + GOLD_LEGACY.replace("{entity_arg}", "entity, entity.level(), guiGraphics, itemStack") + """

    public static void renderGuiItem(LivingEntity entity, Level world, GuiGraphics guiGraphics, ItemStack itemStack, float pX, float pY, float scale) {"""

CELL_LEGACY_34 = """    public static void renderRewardCell(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, int pX, int pY, int width, int height, int grade) {
        int color = ColorTools.colorItems(grade);
        int pad = Math.max(3, Math.min(8, width / 10));
        int iconW = Math.max(8, width - pad * 2);
        int iconH = Math.max(8, height - pad * 2);
        int itemX = pX + (width - iconW) / 2;
        int itemY = pY + (height - iconH) / 2;
""" + GOLD_V26 + """

    public static void renderGuiItem(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, float pX, float pY, float scale) {"""

CELL_V26 = """    public static void renderRewardCell(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack, int pX, int pY, int width, int height, int grade) {
        int color = ColorTools.colorItems(grade);
        int pad = Math.max(3, Math.min(8, width / 10));
        int iconW = Math.max(8, width - pad * 2);
        int iconH = Math.max(8, height - pad * 2);
        int itemX = pX + (width - iconW) / 2;
        int itemY = pY + (height - iconH) / 2;
""" + GOLD_V26 + """

    public static void renderGuiItem(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack, float pX, float pY, float scale) {"""

# waterfall entry call site -> cell-sized, method renamed (handles both the
# original colW args and the half-applied itemSize state from a prior run)
WATERFALL_RE = re.compile(
    r"IconListTools\.renderItemFrame\(this\.player, guiGraphics, e\.stack,\s*"
    r"itemX \+ 2, itemY, (?:colW|itemSize), itemSize, e\.grade\);")
WATERFALL_NEW = ("IconListTools.renderRewardCell("
                 "this.player, guiGraphics, e.stack, "
                 "itemX + 2, itemY, itemSize, itemSize, e.grade);")

# grid call site -> same args, new method
GRID_RE = re.compile(
    r"IconListTools\.renderItemFrame\(this\.player, guiGraphics, stack,\s*"
    r"x \+ 2, y \+ 2, itemSize \+ 4, itemSize \+ 4, grade\);")
GRID_NEW = ("IconListTools.renderRewardCell("
            "this.player, guiGraphics, stack, "
            "x + 2, y + 2, itemSize + 4, itemSize + 4, grade);")

# normalize a half-applied grid call left over from an earlier script version
# (only matches the multi-line form; the single-line form is already final)
GRID_NORM_RE = re.compile(
    r"IconListTools\.renderRewardCell\(this\.player, guiGraphics, stack,\n\s*"
    r"x \+ 2, y \+ 2, itemSize \+ 4, itemSize \+ 4, grade\);")
GRID_NORM_NEW = ("IconListTools.renderRewardCell("
                 "this.player, guiGraphics, stack, "
                 "x + 2, y + 2, itemSize + 4, itemSize + 4, grade);")

# repair: an inserted renderRewardCell whose gold branch still uses the
# legacy 9-arg blit (1.21.3+/26.x files must funnel through blitGoldItemAspect)
REPAIR_BLIT_RE = re.compile(
    r"            guiGraphics\.blit\(GOLD_ITEM_TEXTURE, pX \+ 2, pY \+ 2, 0, 0,\n"
    r"                    width - 4, height - 4, width - 4, height - 4\);")
REPAIR_BLIT_NEW = ("            blitGoldItemAspect(guiGraphics, "
                   "pX + 2, pY + 2, width - 4, height - 4);")

MODULES = [
    "v1_21_0", "v1_21_1", "v1_21_3", "v1_21_4", "v1_21_5",
    "v1_21_8", "v1_21_10", "v1_21_11", "v26_1_2", "v26_2",
]


def cell_flavour(module: str) -> str:
    if module.startswith("v26"):
        return CELL_V26
    if module in ("v1_21_0", "v1_21_1"):
        return CELL_LEGACY_00
    return CELL_LEGACY_34


def old_anchor(module: str) -> str:
    if module.startswith("v26"):
        return "    public static void renderGuiItem(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack, float pX, float pY, float scale) {"
    if module in ("v1_21_0", "v1_21_1"):
        return "    public static void renderGuiItem(LivingEntity entity, Level world, GuiGraphics guiGraphics, ItemStack itemStack, float pX, float pY, float scale) {"
    return "    public static void renderGuiItem(LivingEntity entity, GuiGraphics guiGraphics, ItemStack itemStack, float pX, float pY, float scale) {"


def apply_module(module: str, dry: bool) -> int:
    base = ROOT / module / "src" / "main" / "java" / "com" / "reclizer" / "csgobox" / module
    icons = base / "utils" / "IconListTools.java"
    screen = base / "gui" / "CsboxBulkResultScreen.java"
    changes = 0

    # 1. IconListTools: insert renderRewardCell before renderGuiItem anchor
    text = icons.read_text(encoding="utf-8")
    if "renderRewardCell" not in text:
        anchor = old_anchor(module)
        assert anchor in text, f"{module}: IconListTools anchor missing"
        text = text.replace(anchor, cell_flavour(module), 1)
        if not dry:
            icons.write_text(text, encoding="utf-8")
        changes += 1
    else:
        # repair a previously inserted cell that still uses the wrong blit
        if not module.startswith("v26") and module not in ("v1_21_0", "v1_21_1"):
            text, nr = REPAIR_BLIT_RE.subn(REPAIR_BLIT_NEW, text, count=1)
            if nr:
                if not dry:
                    icons.write_text(text, encoding="utf-8")
                changes += 1

    # 2. CsboxBulkResultScreen: waterfall call site
    text = screen.read_text(encoding="utf-8")
    orig = text
    text, n = WATERFALL_RE.subn(WATERFALL_NEW, text)
    if n:
        changes += 1
    # 3. grid call site
    text, n2 = GRID_RE.subn(GRID_NEW, text, count=1)
    if n2:
        changes += 1
    # 4. normalize half-applied grid call from older script runs
    text, n3 = GRID_NORM_RE.subn(GRID_NORM_NEW, text, count=1)
    if n3:
        changes += 1
    if text != orig and not dry:
        screen.write_text(text, encoding="utf-8")

    return changes


def main() -> int:
    dry = "--check" in sys.argv
    total = 0
    for module in MODULES:
        c = apply_module(module, dry)
        total += c
        print(f"{module}: {c} change(s)" + ("  [dry-run]" if dry else ""))
    print(f"total: {total} change(s)" + ("  [dry-run]" if dry else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
