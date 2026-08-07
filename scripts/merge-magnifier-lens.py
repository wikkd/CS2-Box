#!/usr/bin/env python3
"""Idempotent merge of the magnifier-lens renderBg block into legacy platforms.

Template: v1_21_1's renderBg (already updated). Per-platform variance:
  - scroll variable name: widthNewAdd (v1_21_3..v1_21_10) vs renderWidthNow (v1_21_11)
"""
import re, sys, pathlib

ROOT = pathlib.Path("/Users/shuangyuexingxun/Desktop/CS2-Box")

OLD_BLOCK = re.compile(
    r"        // Draw right-to-left so the magnified card \(near the golden line\) is\n"
    r"        // composited on top of the card approaching from the right\.\n"
    r"        float spacing = this\.width \* 20F / 100F;\n"
    r"        float lineX = this\.width / 2F;\n"
    r"        float stripStartX = this\.width \* randomWidth / 100F;\n"
    r"        for \(int i = count - 1; i >= 0; i--\) \{\n"
    r"            ItemStack itemStack = itemInput\.get\(i\);\n"
    r"            if \(itemStack\.isEmpty\(\)\) continue;\n"
    r"\n"
    r"            float itemX = stripStartX \+ i \* spacing - Mth\.lerp\(progress, lastRenderWidth, (widthNewAdd|renderWidthNow)\);\n"
    r"            float distSpacing = Math\.abs\(itemX - lineX\) / spacing;\n"
    r"            float focus = \(distSpacing >= IconListTools\.FOCUS_FALLOFF_SPACING\)\n"
    r"                    \? 0\.0F\n"
    r"                    : \(float\) Math\.pow\(1\.0F - distSpacing / IconListTools\.FOCUS_FALLOFF_SPACING, 2\.0F\);\n"
    r"            if \(focus > 0\.02F\) \{\n"
    r"                float focusScale = 1\.0F \+ focus \* \(IconListTools\.FOCUS_PEAK_SCALE - 1\.0F\);\n"
    r"                IconListTools\.renderItemProgressFocus\(player, guiGraphics, itemStack,\n"
    r"                        itemX, this\.height \* 37F / 100F,\n"
    r"                        this\.width, this\.height, gradeInput\.get\(i\), focusScale\);\n"
    r"            \} else \{\n"
    r"                IconListTools\.renderItemProgress\(player, guiGraphics, itemStack,\n"
    r"                        itemX, this\.height \* 37F / 100F,\n"
    r"                        this\.width, this\.height, gradeInput\.get\(i\)\);\n"
    r"            \}\n"
    r"        \}\n"
    r"\n"
    r"        lastRenderWidth = (widthNewAdd|renderWidthNow);\n"
    r"\n"
    r"        int goldLineTop = this\.height \* 37 / 100;\n"
    r"        int goldLineBottom = goldLineTop \+ this\.height \* 25 / 100;\n"
    r"        guiGraphics\.fill\(this\.width / 2, goldLineTop,\n"
    r"                this\.width / 2 \+ 2, goldLineBottom,\n"
    r"                ColorTools\.argbColor\(128, 255, 215, 0\)\);",
    re.DOTALL)

def new_block(var: str) -> str:
    return f"""        // Magnifier lens: a fixed screen-space viewport whose left edge is the
        // golden line. Cards INSIDE the lens are magnified; cards outside are
        // rendered at their raw size - nothing scales by distance.
        float spacing = this.width * 20F / 100F;
        float lineX = this.width / 2F;
        float stripStartX = this.width * randomWidth / 100F;
        float lensTop = this.height * 37F / 100F;
        float cellWidth = this.width * 18F / 100F;
        float cellHeight = this.height * 25F / 100F;
        float lensWidth = cellWidth * IconListTools.FOCUS_PEAK_SCALE;
        float lensHeight = cellHeight * IconListTools.FOCUS_PEAK_SCALE;
        float lensLeft = lineX;
        float lensRight = lineX + lensWidth;
        float lensBottom = lensTop + lensHeight;

        // Picking the card currently inside the lens by maximum overlap (only
        // one card is truly the magnified one at any tick).
        int magnifiedIndex = -1;
        float maxOverlap = 0.0F;
        for (int i = 0; i < count; i++) {{
            if (itemInput.get(i).isEmpty()) continue;
            float itemX = stripStartX + i * spacing - Mth.lerp(progress, lastRenderWidth, {var});
            float overlap = Math.min(itemX + cellWidth, lensLeft + lensWidth)
                    - Math.max(itemX, lensLeft);
            if (overlap > maxOverlap) {{
                maxOverlap = overlap;
                magnifiedIndex = i;
            }}
        }}

        // Lens backing glass + golden frame: gives the magnifier a crisp
        // boundary and hides any dark gaps that would read as a black box.
        guiGraphics.fillGradient(0, (int) lensTop, this.width, (int) lensBottom,
                0xFF6A5FB0, 0xFF9A8BD0);
        guiGraphics.fill(0, (int) lensTop, this.width, (int) lensBottom,
                ColorTools.argbColor(40, 30, 28, 60));
        guiGraphics.fillGradient((int) lensLeft, (int) lensTop, (int) lensRight, (int) lensBottom,
                0x45A06AFF, 0x55407AE0);
        guiGraphics.fill((int) lensLeft, (int) lensTop, (int) lensRight, (int) lensTop + 3, ColorTools.argbColor(220, 255, 215, 0));
        guiGraphics.fill((int) lensLeft, (int) lensBottom - 3, (int) lensRight, (int) lensBottom, ColorTools.argbColor(220, 255, 215, 0));
        guiGraphics.fill((int) lensLeft, (int) lensTop, (int) lensLeft + 3, (int) lensBottom, ColorTools.argbColor(220, 255, 215, 0));
        guiGraphics.fill((int) lensRight - 3, (int) lensTop, (int) lensRight, (int) lensBottom, ColorTools.argbColor(220, 255, 215, 0));

        // Draw right-to-left so the magnified card (near the golden line) is
        // composited on top of the card approaching from the right.
        for (int i = count - 1; i >= 0; i--) {{
            ItemStack itemStack = itemInput.get(i);
            if (itemStack.isEmpty()) continue;

            float itemX = stripStartX + i * spacing - Mth.lerp(progress, lastRenderWidth, {var});
            if (i == magnifiedIndex && maxOverlap > 0.0F) {{
                // Hard boundary: the card is inside the lens -> scale toward
                // peak (ramp only over the entry overlap, ~6% of a cell, so
                // the magnification feels crisp without snapping). Outside
                // the lens the card is drawn at raw size, always.
                float ramp = cellWidth * 6F / 100F;
                float focus = Math.min(1.0F, maxOverlap / Math.max(ramp, 1.0F));
                float focusScale = 1.0F + focus * (IconListTools.FOCUS_PEAK_SCALE - 1.0F);
                IconListTools.renderItemProgressFocus(player, guiGraphics, itemStack,
                        itemX, lensTop, this.width, this.height, gradeInput.get(i), focusScale);
            }} else {{
                IconListTools.renderItemProgress(player, guiGraphics, itemStack,
                        itemX, lensTop, this.width, this.height, gradeInput.get(i));
            }}
        }}

        lastRenderWidth = {var};

        guiGraphics.fill((int) lineX, (int) lensTop, (int) lineX + 2, (int) (lensTop + cellHeight),
                ColorTools.argbColor(128, 255, 215, 0));"""

MODULES = ["v1_21_3", "v1_21_4", "v1_21_5", "v1_21_8", "v1_21_10", "v1_21_11"]

def main():
    for m in MODULES:
        p = ROOT / m / "src/main/java/com/reclizer/csgobox" / m / "gui/CsboxProgressScreen.java"
        src = p.read_text(encoding="utf-8")
        m1 = OLD_BLOCK.search(src)
        if not m1:
            print(f"{m}: OLD BLOCK NOT FOUND (already merged or differs)")
            continue
        var = m1.group(1)
        if var != m1.group(2):
            print(f"{m}: ambiguous variable match, abort"); sys.exit(1)
        src = src[:m1.start()] + new_block(var) + src[m1.end():]
        p.write_text(src, encoding="utf-8")
        print(f"{m}: merged (var={var})")

if __name__ == "__main__":
    main()
