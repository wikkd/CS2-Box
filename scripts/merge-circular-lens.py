#!/usr/bin/env python3
"""Idempotent merge of the CS2Deck-style circular magnifier lens into all
non-baseline platforms. Template: v1_21_1's renderBg (already updated).

The lens block is inserted right before the `lastRenderWidth = ...;` line of
renderBg, after the strip pass. Per-platform variance:
  - legacy (v1_21_3..v1_21_10): GuiGraphics + ResourceLocation.parse +
    RenderSystem.enableBlend/disableBlend around the mask blit
  - new (v1_21_11, v26_1_2, v26_2): GuiGraphicsExtractor + RenderPipelines
    + Identifier.parse, no explicit blend (blit handles it)
"""
import re, sys, pathlib

ROOT = pathlib.Path("/Users/shuangyuexingxun/Desktop/CS2-Box")

LEGACY35_BLIT = """        // Circular backing mask: transparent center (magnified strip shows
        // through), solid #06194f outside the disc, soft rim shade = the
        // "glass edge" depth cue of the lens.
        guiGraphics.blit(RenderType.GUI_TEXTURED,
                ResourceLocation.parse("csgobox:textures/screens/lens_vignette.png"),
                lensMinX, lensMinY, 0F, 0F, lensW, lensW, lensW, lensW, 0xFFFFFFFF);"""

LEGACY810_BLIT = """        // Circular backing mask: transparent center (magnified strip shows
        // through), solid #06194f outside the disc, soft rim shade = the
        // "glass edge" depth cue of the lens.
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                ResourceLocation.parse("csgobox:textures/screens/lens_vignette.png"),
                lensMinX, lensMinY, 0F, 0F, lensW, lensW, lensW, lensW, 0xFFFFFFFF);"""

NEW_BLIT = """        // Circular backing mask: transparent center (magnified strip shows
        // through), solid #06194f outside the disc, soft rim shade = the
        // "glass edge" depth cue of the lens.
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                Identifier.parse("csgobox:textures/screens/lens_vignette.png"),
                lensMinX, lensMinY,
                0F, 0F,
                lensW, lensW,
                lensW, lensW
        );"""

# Opaque lens backing upgrade: already-merged platforms had the magnified
# strip drawn straight over the transparent vignette centre, so the raw 1x
# strip bled through the disc (ghost residue). This inserts an opaque glass
# plate fill into each slice before the magnified card draw. Idempotent.
BACKING_UPGRADE_OLD = """                int x0 = (int) (lensCX - halfW);
                int x1 = (int) (lensCX + halfW) + 1;
                guiGraphics.enableScissor(x0, y, x1, y + h);"""

BACKING_UPGRADE_NEW = """                int x0 = (int) (lensCX - halfW);
                int x1 = (int) (lensCX + halfW) + 1;
                // Opaque lens backing: the vignette's glass centre is fully
                // transparent, so without this the raw 1x strip drawn in the
                // strip pass would bleed through the disc and double up with
                // the magnified view. Fill the whole slice first - the raw
                // residue is sealed under a neutral glass-colour plate.
                guiGraphics.fill(x0, y, x1, y + h, 0xFF545454);
                guiGraphics.enableScissor(x0, y, x1, y + h);"""

# Wrong legacy blit emitted by an earlier run of this script (plain 8-arg
# blit + RenderSystem blend) - replaced in place on the next run.
WRONG_LEGACY_BLIT = re.compile(
    r"        // Circular backing mask: transparent center \(magnified strip shows\n"
    r"        // through\), solid #06194f outside the disc, soft rim shade = the\n"
    r"        // \"glass edge\" depth cue of the lens\.\n"
    r"        RenderSystem\.enableBlend\(\);\n"
    r"        guiGraphics\.blit\(ResourceLocation\.parse\(\"csgobox:textures/screens/lens_vignette\.png\"\),\n"
    r"                lensMinX, lensMinY, 0, 0, lensW, lensW, lensW, lensW\);\n"
    r"        RenderSystem\.disableBlend\(\);\n", re.DOTALL)

LENS_BODY = """        // CS2Deck-style magnifier lens: a fixed circular viewport that the
        // passing strip zooms through (whole strip rendered again scaled about
        // the lens centre, so the same card stays aligned). The magnified
        // strip is clipped to the disc itself - a true magnifier bounded by
        // the circular filter edge, not a single-card "focus" animation.
        float lensCX = this.width / 2F;
        float lensCY = this.height / 2F;
        float lensScale = IconListTools.FOCUS_PEAK_SCALE;
        float lensR = this.width * 20F / 100F;
        int lensMinX = (int) (lensCX - lensR);
        int lensMinY = (int) (lensCY - lensR);
        int lensW = (int) (lensR * 2F);
        float magnifiedTop = lensCY + (lensTop - lensCY) * lensScale;
        float magnifiedBottom = magnifiedTop + cellHeight * lensScale;
        float cardScale = cellWidth * lensScale;
        // Only cards whose magnified rect reaches the lens bbox can ever show
        // in a slice - resolve that index window once instead of scanning the
        // whole strip for every slice.
        int iMin = count;
        int iMax = -1;
        for (int i = 0; i < count; i++) {
            float itemX = stripStartX + i * spacing - scrollNow;
            float pX = lensCX + (itemX - lensCX) * lensScale;
            if (pX + cardScale <= lensMinX) continue;
            if (pX >= lensMinX + lensW) break;
            if (iMin == count) iMin = i;
            iMax = i;
        }
        // Real-time magnification bounded by the circular lens edge. Scissor
        // only supports rectangles, so the magnified strip is drawn in
        // horizontal slices. Each slice's half-width is taken at the slice
        // edge FARTHEST from the lens centre, making the scissor rect
        // inscribed in the disc: magnified content never spills outside the
        // circle. Slice height adapts to the local slope of the arc - coarse
        // in the flat middle, 1-2px near the steep poles - so the inscribed
        // error stays well under a pixel.
        //
        // Adjacent slices whose clip rect rounds to the same (x0, x1) produce
        // bit-identical output, so they are merged into a single band first -
        // the card redraw (a 3D item render + buffer flush per card) then
        // runs once per band instead of once per slice. The merged rect stays
        // inscribed in the disc (same rounding, same far edge), so visual
        // output is unchanged.
        int yMin = Math.max(lensMinY, (int) Math.ceil(magnifiedTop));
        int yMax = Math.min(lensMinY + lensW, (int) Math.floor(magnifiedBottom));
        int maxBands = yMax - yMin + 1;
        int[] bands = new int[maxBands * 4];
        int bandCount = 0;
        int y = yMin;
        int curY = yMin, curH = 0, curX0 = 0, curX1 = 0;
        boolean curOpen = false;
        while (y <= yMax) {
            float farDist = Math.max(Math.abs(y - lensCY), Math.abs(y + 1 - lensCY));
            float slope = farDist / (float) Math.sqrt(Math.max(lensR * lensR - farDist * farDist, 1.0F));
            int h = Math.max(1, Math.min(16, (int) Math.ceil(0.5F / Math.max(slope, 0.03F))));
            h = Math.min(h, yMax - y + 1);
            float farDy = Math.max(Math.abs(y - lensCY), Math.abs(y + h - lensCY));
            float halfW2 = lensR * lensR - farDy * farDy;
            if (halfW2 > 0F) {
                float halfW = (float) Math.sqrt(halfW2);
                int x0 = (int) (lensCX - halfW);
                int x1 = (int) (lensCX + halfW) + 1;
                if (!curOpen || x0 != curX0 || x1 != curX1) {
                    if (curOpen) {
                        int o = bandCount++ * 4;
                        bands[o] = curY; bands[o + 1] = curH; bands[o + 2] = curX0; bands[o + 3] = curX1;
                    }
                    curY = y; curH = h; curX0 = x0; curX1 = x1; curOpen = true;
                } else {
                    curH += h;
                }
            } else if (curOpen) {
                int o = bandCount++ * 4;
                bands[o] = curY; bands[o + 1] = curH; bands[o + 2] = curX0; bands[o + 3] = curX1;
                curOpen = false;
            }
            y += h;
        }
        if (curOpen) {
            int o = bandCount++ * 4;
            bands[o] = curY; bands[o + 1] = curH; bands[o + 2] = curX0; bands[o + 3] = curX1;
        }
        for (int b = 0; b < bandCount; b++) {
            int o = b * 4;
            int by = bands[o], bh = bands[o + 1], x0 = bands[o + 2], x1 = bands[o + 3];
            // Opaque lens backing: the vignette's glass centre is fully
            // transparent, so without this the raw 1x strip drawn in the
            // strip pass would bleed through the disc and double up with
            // the magnified view. Fill the whole band first - the raw
            // residue is sealed under a neutral glass-colour plate.
            guiGraphics.fill(x0, by, x1, by + bh, 0xFF545454);
            guiGraphics.enableScissor(x0, by, x1, by + bh);
            for (int i = iMax; i >= iMin; i--) {
                ItemStack itemStack = itemInput.get(i);
                if (itemStack.isEmpty()) continue;
                float itemX = stripStartX + i * spacing - scrollNow;
                float pX = lensCX + (itemX - lensCX) * lensScale;
                if (pX > x1 + cardScale || pX + cardScale < x0) continue;
                IconListTools.renderItemProgressFocus(player, guiGraphics, itemStack,
                        pX, magnifiedTop, this.width, this.height, gradeInput.get(i), lensScale);
            }
            guiGraphics.disableScissor();
        }

"""

# Slice-loop -> merged-band upgrade: platforms merged before the slice-merging
# optimisation still walk the slice loop one scissor rect per row; the upgrade
# replaces the whole loop with the banded variant (same inscribed rects, same
# rounding, so identical output, but one card redraw per merged band).
SPLICE_UPGRADE_OLD = re.compile(
    r"        int yMin = Math\.max\(lensMinY, \(int\) Math\.ceil\(magnifiedTop\)\);\n"
    r"        int yMax = Math\.min\(lensMinY \+ lensW, \(int\) Math\.floor\(magnifiedBottom\)\);\n"
    r"        int y = yMin;\n"
    r"        while \(y <= yMax\) \{\n.*?"
    r"            y \+= h;\n"
    r"        \}\n", re.DOTALL)

# The banded replacement block (matches the v1_21_1 baseline). Ends at the
# band-draw loop close brace so the following vignette blit is untouched.
# The opaque lens backing is baked in, so this supersedes BACKING_UPGRADE.
SPLICE_UPGRADE_NEW = """        int yMin = Math.max(lensMinY, (int) Math.ceil(magnifiedTop));
        int yMax = Math.min(lensMinY + lensW, (int) Math.floor(magnifiedBottom));
        int maxBands = yMax - yMin + 1;
        int[] bands = new int[maxBands * 4];
        int bandCount = 0;
        int y = yMin;
        int curY = yMin, curH = 0, curX0 = 0, curX1 = 0;
        boolean curOpen = false;
        while (y <= yMax) {
            float farDist = Math.max(Math.abs(y - lensCY), Math.abs(y + 1 - lensCY));
            float slope = farDist / (float) Math.sqrt(Math.max(lensR * lensR - farDist * farDist, 1.0F));
            int h = Math.max(1, Math.min(16, (int) Math.ceil(0.5F / Math.max(slope, 0.03F))));
            h = Math.min(h, yMax - y + 1);
            float farDy = Math.max(Math.abs(y - lensCY), Math.abs(y + h - lensCY));
            float halfW2 = lensR * lensR - farDy * farDy;
            if (halfW2 > 0F) {
                float halfW = (float) Math.sqrt(halfW2);
                int x0 = (int) (lensCX - halfW);
                int x1 = (int) (lensCX + halfW) + 1;
                if (!curOpen || x0 != curX0 || x1 != curX1) {
                    if (curOpen) {
                        int o = bandCount++ * 4;
                        bands[o] = curY; bands[o + 1] = curH; bands[o + 2] = curX0; bands[o + 3] = curX1;
                    }
                    curY = y; curH = h; curX0 = x0; curX1 = x1; curOpen = true;
                } else {
                    curH += h;
                }
            } else if (curOpen) {
                int o = bandCount++ * 4;
                bands[o] = curY; bands[o + 1] = curH; bands[o + 2] = curX0; bands[o + 3] = curX1;
                curOpen = false;
            }
            y += h;
        }
        if (curOpen) {
            int o = bandCount++ * 4;
            bands[o] = curY; bands[o + 1] = curH; bands[o + 2] = curX0; bands[o + 3] = curX1;
        }
        for (int b = 0; b < bandCount; b++) {
            int o = b * 4;
            int by = bands[o], bh = bands[o + 1], x0 = bands[o + 2], x1 = bands[o + 3];
            // Opaque lens backing: the vignette's glass centre is fully
            // transparent, so without this the raw 1x strip drawn in the
            // strip pass would bleed through the disc and double up with
            // the magnified view. Fill the whole band first - the raw
            // residue is sealed under a neutral glass-colour plate.
            guiGraphics.fill(x0, by, x1, by + bh, 0xFF545454);
            guiGraphics.enableScissor(x0, by, x1, by + bh);
            for (int i = iMax; i >= iMin; i--) {
                ItemStack itemStack = itemInput.get(i);
                if (itemStack.isEmpty()) continue;
                float itemX = stripStartX + i * spacing - scrollNow;
                float pX = lensCX + (itemX - lensCX) * lensScale;
                if (pX > x1 + cardScale || pX + cardScale < x0) continue;
                IconListTools.renderItemProgressFocus(player, guiGraphics, itemStack,
                        pX, magnifiedTop, this.width, this.height, gradeInput.get(i), lensScale);
            }
            guiGraphics.disableScissor();
        }
"""

# Anchor: the strip-pass closing brace followed by lastRenderWidth assignment.
ANCHOR = re.compile(
    r"        }\n\n        lastRenderWidth = (widthNewAdd|renderWidthAdd);\n",
    re.DOTALL)

MODULES = [
    ("v1_21_3", "legacy35"),
    ("v1_21_4", "legacy35"),
    ("v1_21_5", "legacy35"),
    ("v1_21_8", "legacy810"),
    ("v1_21_10", "legacy810"),
    ("v1_21_11", "new"),
    ("v26_1_2", "new"),
    ("v26_2", "new"),
]

BLIT_BY_FAMILY = {
    "legacy35": LEGACY35_BLIT,
    "legacy810": LEGACY810_BLIT,
    "new": NEW_BLIT,
}

def main():
    dry = "--dry-run" in sys.argv
    for m, family in MODULES:
        p = ROOT / m / "src/main/java/com/reclizer/csgobox" / m / "gui/CsboxProgressScreen.java"
        src = p.read_text(encoding="utf-8")
        # Repair pass: replace a wrong legacy blit (from an earlier run) with
        # the per-platform variant before checking "already merged".
        src, n_repair = WRONG_LEGACY_BLIT.subn(BLIT_BY_FAMILY[family], src)
        if "CS2Deck-style magnifier lens" in src:
            # Slice-loop -> merged-band upgrade (idempotent): applies before the
            # backing repair so a pre-existing slice loop is first converted,
            # then its new band body is left alone by BACKING_UPGRADE (which
            # targets the old per-slice shape and therefore no longer matches).
            if SPLICE_UPGRADE_OLD.search(src):
                src, n_splice = SPLICE_UPGRADE_OLD.subn(SPLICE_UPGRADE_NEW, src)
                if dry:
                    print(f"{m}: would upgrade slice-loop to merged bands (repair={n_repair})")
                    continue
                p.write_text(src, encoding="utf-8")
                print(f"{m}: upgraded slice-loop to merged bands (repair={n_repair})")
                continue
            # Already merged: still apply the opaque-backing upgrade (idempotent).
            if BACKING_UPGRADE_OLD in src:
                src = src.replace(BACKING_UPGRADE_OLD, BACKING_UPGRADE_NEW)
                if dry:
                    print(f"{m}: would upgrade opaque lens backing (repair={n_repair})")
                    continue
                p.write_text(src, encoding="utf-8")
                print(f"{m}: upgraded opaque lens backing (repair={n_repair})")
            else:
                print(f"{m}: already merged, skip (repair={n_repair})")
            continue
        m1 = ANCHOR.search(src)
        if not m1:
            print(f"{m}: ANCHOR NOT FOUND (strip pass shape differs), abort")
            sys.exit(1)
        var = m1.group(1)
        blit = BLIT_BY_FAMILY[family]
        block = LENS_BODY + blit + "\n\n"
        new_src = src[:m1.start()] + "        }\n\n        lastRenderWidth = " + var + ";\n\n" + block + src[m1.end():]
        if dry:
            print(f"{m}: would merge (var={var}, family={family}, repair={n_repair})")
            continue
        p.write_text(new_src, encoding="utf-8")
        print(f"{m}: merged (var={var}, family={family}, repair={n_repair})")

if __name__ == "__main__":
    main()
