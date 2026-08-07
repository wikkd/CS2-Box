#!/usr/bin/env python3
"""Idempotently port the CsboxScreen pagination fix (v26_2 -> legacy 1.21.x).

Applies the 5 surgical edits that were hand-ported to v1_21_1:
  1. page/ITEMS_PER_PAGE fields + renderableCount()/pageCount() helpers
  2. renderBg item loop -> per-page window, break on grade > 4
  3. renderLabels item loop -> per-page window, page indicator "N/M"
  4. mouseScrolled override
  5. containerTick resets page on data sync

Idempotent: re-running on an already-migrated file is a no-op.
Usage: python3 scripts/merge-pagination.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULES = ["v1_21_3", "v1_21_4", "v1_21_5", "v1_21_8", "v1_21_10", "v1_21_11", "v26_1_2"]

FIELDS = """    private static final int ITEMS_PER_PAGE = 20;

    private int page;

    private int renderableCount() {
        int count = 0;
        for (int i = 0; i < itemsList.size(); i++) {
            if (gradeList.get(i) > 4) break;
            count++;
        }
        return count;
    }

    private int pageCount() {
        int n = renderableCount();
        return Math.max(1, (n + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
    }

    private int boxKeyCount;
"""

RENDER_BG_LOOP = """            int startIdx = this.page * ITEMS_PER_PAGE;
            for (int i = startIdx; i < Math.min(itemsList.size(), startIdx + ITEMS_PER_PAGE); i++) {
                int py = 55;
                int px = i - startIdx;
                if (px > 9) {
                    py = 73;
                    px -= 10;
                }
                ItemStack itemStack1 = itemsList.get(i);
                int grade = gradeList.get(i);
                x = px;
                y = py;
                if (grade > 4) break;
"""

RENDER_BG_TAIL = """            if (!gradeList.isEmpty() && gradeList.get(gradeList.size() - 1) > 4
                    && this.page == pageCount() - 1) {
"""

RENDER_LABELS_LOOP = """        int startIdx = this.page * ITEMS_PER_PAGE;
        for (int i = startIdx; i < Math.min(itemsList.size(), startIdx + ITEMS_PER_PAGE); i++) {
            int py = 67;
            int px = i - startIdx;
            if (px > 9) {
                py = 85;
                px -= 10;
            }
"""

PAGE_INDICATOR = """        if (pageCount() > 1) {
            renderText(guiGraphics, Component.literal((this.page + 1) + "/" + pageCount()).getVisualOrderText(),
                    this.width * 88 / 100F, this.height * 54 / 100F, 0.6F);
        }
"""

MOUSE_SCROLLED = """    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && pageCount() > 1) {
            int target = this.page + (verticalAmount > 0 ? -1 : 1);
            if (target >= 0 && target < pageCount()) {
                this.page = target;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
"""

MOUSE_SCROLLED_26 = """    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && pageCount() > 1) {
            int target = this.page + (scrollY > 0 ? -1 : 1);
            if (target >= 0 && target < pageCount()) {
                this.page = target;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
"""


def apply(path: Path) -> list:
    src = path.read_text(encoding="utf-8")
    changed = []

    # 1. fields + helpers (insert before "private int boxKeyCount;")
    if "private static final int ITEMS_PER_PAGE = 20;" not in src:
        src = src.replace("    private int boxKeyCount;", FIELDS, 1)
        changed.append("fields")

    # 2. renderBg loop head
    old_bg = """            for (int i = 0; i < itemsList.size(); i++) {
                int py = 55;
                int px = i;
                if (i > 9) {
                    py = 73;
                    px = i - 10;
                }
                ItemStack itemStack1 = itemsList.get(i);
                int grade = gradeList.get(i);
                x = px;
                y = py;
                if (grade == 5) break;
"""
    if old_bg in src:
        src = src.replace(old_bg, RENDER_BG_LOOP, 1)
        changed.append("renderBg loop")

    # 2b. renderBg empty-frame condition
    old_tail = """            if (!gradeList.isEmpty() && gradeList.get(gradeList.size() - 1) == 5) {
"""
    if old_tail in src:
        src = src.replace(old_tail, RENDER_BG_TAIL, 1)
        changed.append("renderBg tail")

    # 3. renderLabels loop head (distinct from renderBg by py == 67)
    old_lb = """        for (int i = 0; i < itemsList.size(); i++) {
            int py = 67;
            int px = i;
            if (i > 9) {
                py = 85;
                px = i - 10;
            }
"""
    if old_lb in src and "int startIdx = this.page * ITEMS_PER_PAGE;\n        for (int i = startIdx;" not in src:
        src = src.replace(old_lb, RENDER_LABELS_LOOP, 1)
        changed.append("renderLabels loop")

    # 3b. page indicator after label_gold block
    gold_block = """        if (showNames) {
            renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.label_gold").getVisualOrderText(),
                    this.width * 4 / 100F + x * this.width * 9 / 100F,
                    this.height * y / 100F, 0.6F);
        }
"""
    if "PAGE_INDICATOR_MARK" not in src and PAGE_INDICATOR not in src:
        src = src.replace(gold_block, gold_block + PAGE_INDICATOR, 1)
        changed.append("page indicator")

    # 4. mouseScrolled after keyPressed (1.21.9- uses (int,int,int), 1.21.10+ uses KeyEvent, 26.x uses KeyEvent + scrollX/scrollY)
    if "public boolean mouseScrolled" not in src:
        key_pressed_end = """        return super.keyPressed(key, b, c);
    }
"""
        key_pressed_event_end = """        return super.keyPressed(event);
    }
"""
        inserted = False
        for pattern in (key_pressed_end, key_pressed_event_end):
            if pattern in src:
                body = MOUSE_SCROLLED_26 if "26_1_2" in str(path) else MOUSE_SCROLLED
                src = src.replace(pattern, pattern + "\n" + body, 1)
                changed.append("mouseScrolled")
                inserted = True
                break
        if not inserted:
            print(f"[WARN] {path.name}: keyPressed tail pattern not found")

    # 5. containerTick resets page
    if "this.boxKeyCount = countKeys();" in src and "this.page = 0;" not in src:
        src = src.replace(
            "            this.boxKeyCount = countKeys();",
            "            this.boxKeyCount = countKeys();\n            this.page = 0;",
            1,
        )
        changed.append("containerTick reset")

    if changed:
        path.write_text(src, encoding="utf-8")
    return changed


def main() -> int:
    rc = 0
    for mod in MODULES:
        p = ROOT / mod / "src/main/java/com/reclizer/csgobox" / mod / "gui/CsboxScreen.java"
        if not p.exists():
            print(f"[SKIP] {mod}: file missing")
            continue
        changed = apply(p)
        if changed:
            print(f"[OK] {mod}: {', '.join(changed)}")
        else:
            print(f"[--] {mod}: already migrated (idempotent no-op)")
        # sanity: migrated file must contain the key markers
        src = p.read_text(encoding="utf-8")
        missing = [
            m
            for m in ("ITEMS_PER_PAGE", "pageCount()", "mouseScrolled", "page = 0;")
            if m not in src
        ]
        if missing:
            print(f"[WARN] {mod}: missing markers {missing}")
            rc = 1
    return rc


if __name__ == "__main__":
    sys.exit(main())
