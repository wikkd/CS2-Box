#!/usr/bin/env python3
"""Generate v1_21_11 platform files from v26_1_2 equivalents.

Minecraft 1.21.11 ships the same decoupled GUI API as 26.1.2 (Matrix3x2fStack,
RenderPipeline blits, no RenderSystem statics, MouseButtonEvent in client.input)
but keeps the legacy Screen.render(GuiGraphics) entry point, names the item
helper renderItem(...) instead of item(...), and the text helper drawString(...)
instead of text(...). The v1_21_11 module was scaffolded from v1_21_1 and never
adapted; this script ports the already-adapted v26_1_2 files with the minimal
rename set. Files whose 26.1.2 and 1.21.11 APIs diverge further (Font drawing,
lighting) are patched by per-file handlers.

Usage: python3 scripts/port-12111.py <relative-path>...
  relative-path is relative to v26_1_2/src/main/java, e.g.
  gui/CsboxScreen.java  (com.reclizer.csgobox.<ver>/ prefix is handled)
"""
import os
import re
import sys

SRC = "v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2"
DST = "v1_21_11/src/main/java/com/reclizer/csgobox/v1_21_11"


def transform(text: str) -> str:
    t = text
    t = t.replace("csgobox.v26_1_2", "csgobox.v1_21_11")
    t = t.replace("GuiGraphicsExtractor", "GuiGraphics")
    # legacy Screen entry point: extractRenderState -> render
    t = t.replace("super.extractRenderState(", "super.render(")
    t = re.sub(
        r"public void extractRenderState\(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks\)",
        "public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks)",
        t,
    )
    # item helper rename
    t = t.replace("guiGraphics.item(", "guiGraphics.renderItem(")
    # text helper rename (Font drawInBatch is void in both; use GuiGraphics.drawString)
    t = t.replace("guiGraphics.text(font,", "guiGraphics.drawString(font,")
    t = t.replace("guiGraphics.text(pFont,", "guiGraphics.drawString(pFont,")
    t = re.sub(r"guiGraphics\.text\(font, ([^,]+), ([^,]+), ([^,]+), ([^,]+), ([^,]+)\)",
               r"guiGraphics.drawString(font, \1, \2, \3, \4, \5)", t)
    return t


def main():
    for rel in sys.argv[1:]:
        src = os.path.join(SRC, rel)
        if not os.path.isfile(src):
            print(f"missing source: {src}")
            sys.exit(1)
        dst = os.path.join(DST, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(src, encoding="utf-8") as f:
            content = f.read()
        with open(dst, "w", encoding="utf-8") as f:
            f.write(transform(content))
        print(f"generated -> {dst}")


if __name__ == "__main__":
    main()
