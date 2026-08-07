#!/usr/bin/env python3
"""port-focus — 把中央槽位放大(聚焦)效果批量合入平台模块。

对每个平台 IconListTools.java:
  1) 类头部插入 FOCUS_PEAK_SCALE / FOCUS_FALLOFF_SPACING 常量;
  2) 复制 renderItemProgress -> renderItemProgressFocus(新增 focusScale 参数,
     frameWidth/frameHeight 表达式乘算出, 末尾注入紫蓝 tint), 插入原方法之后。
对每个平台 CsboxProgressScreen.java:
  替换 renderBg 里的正向循环为 right-to-left + distance-focused 循环,
  命中 focus>0.02 时用 renderItemProgressFocus 渲染。

幂等: 已含 focus 的文件跳过。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLATFORMS = ["v1_21_3", "v1_21_4", "v1_21_5",
             "v1_21_8", "v1_21_10", "v1_21_11",
             "v26_1_2"]

CONSTANTS = """/**
     * Peak magnification of the card sitting at the golden line during the
     * opening animation (1.0 = no magnification). The card whose left edge is
     * closest to the line scales up toward this factor; neighbors ramp back
     * down to 1.0.
     */
    public static final float FOCUS_PEAK_SCALE = 1.25F;

    /** Focus reaches 1.0 (no magnification) at this many card spacings from the line. */
    public static final float FOCUS_FALLOFF_SPACING = 1.0F;
"""

TINT_TAIL = """
        // Focus tint: periwinkle/blue gradient lit up inside the focused card
        // (mirrors the CS:GO inspect highlight), strengthening with focus.
        float focus = (focusScale - 1.0F) / (FOCUS_PEAK_SCALE - 1.0F);
        int tintA = (int) (40F * (0.4F + 0.6F * focus));
        int tintTop = ColorTools.argbColor(tintA, 176, 140, 255);
        int tintBottom = ColorTools.argbColor(tintA - 12, 48, 80, 255);
        guiGraphics.fillGradient(bx0 + 4, by0 + 4, toX - 4, toY - 4, tintTop, tintBottom);
    }
"""


def patch_icon_tools(platform: str) -> bool:
    p = ROOT / platform / "src/main/java/com/reclizer/csgobox" / platform / "utils/IconListTools.java"
    text = p.read_text(encoding="utf-8")
    if "renderItemProgressFocus" in text:
        return False

    # 1) constants before constructor
    m = re.search(r"\n(\s*)private IconListTools\(\)", text)
    if not m:
        raise RuntimeError(f"{platform}: IconListTools 构造函数未找到")
    if "FOCUS_PEAK_SCALE" not in text.split("#pragma")[0]:
        text = text[: m.start()] + "\n" + CONSTANTS + "\n" + text[m.start():]

    # 2) locate renderItemProgress method
    head = "    public static void renderItemProgress("
    start = text.index(head)
    end = text.index("\n    }\n", start) + len("\n    }\n")
    body = text[start:end]

    sig = re.match(r"\s*public static void renderItemProgress\(([^)]*)\) \{", body)
    if not sig:
        raise RuntimeError(f"{platform}: 签名解析失败")
    focus_sig = "public static void renderItemProgressFocus(%s, float focusScale) {" % sig.group(1)
    inner = body[sig.end():]               # 从 "{ 之后" (含第一个换行) 到 "    }\n"
    inner = inner[: inner.rindex("\n    }\n")]  # 去掉最后 closing 大括号

    # frameWidth/frameHeight × focusScale
    inner = re.sub(r"float frameWidth = (.*?);", "float frameWidth = \\1 * focusScale;", inner, count=1)
    inner = re.sub(r"float frameHeight = (.*?);", "float frameHeight = \\1 * focusScale;", inner, count=1)
    # inject bx0/by0 after signature if missing
    if "int bx0" not in inner:
        inner = inner + "\n        int bx0 = (int) pX;\n        int by0 = (int) pY;"
    new_method = focus_sig + inner + TINT_TAIL

    text = text[:end] + "\n" + new_method + text[end:]
    p.write_text(text, encoding="utf-8")
    print(f"[icon] {platform}: focus 方法已合入")
    return True


def patch_progress_screen(platform: str) -> bool:
    p = Path(platform) / "src/main/java/com/reclizer/csgobox" / platform / "gui/CsboxProgressScreen.java"
    text = p.read_text(encoding="utf-8")
    if "renderItemProgressFocus" in text:
        return False

    # 定位 renderBg 的循环块: for (int i = 0; i < count; i++) { ... }
    old = re.search(r"(?s)        for \(int i = 0; i < count; i\+\+\) \{.*?        \}\n", text)
    if not old:
        raise RuntimeError(f"{platform}: CsboxProgressScreen 循环未找到")
    loop_block = old.group(0)
    if "renderItemProgressFocus" in loop_block:
        return False

    # itemX 与 scroll 变量名可能不同, 但统一是 Mth.lerp(progress, lastRenderWidth, ??); 我们抓表达式
    # 提取 itemInput/gradeInput.itemX 表达式行
    itemX_m = re.search(r"float itemX = (.*?);", loop_block, re.S)
    if not itemX_m:
        raise RuntimeError(f"{platform}: itemX 表达式未找到")
    # 我们把整个 for 块替换为紧密新块 (依赖 spacing/lineX/scrollNow)
    scroll = re.search(r"Mth\.lerp\(progress, lastRenderWidth, ([A-Za-z0-9_]+)\)", loop_block)
    scrollVar = scroll.group(1) if scroll else "widthNewAdd"
    # 用 python 动态处理 scrollVar 在 11/26.1.2 上为 renderWidthNow
    new_block = f"""        // Draw right-to-left so the magnified card (near the golden line) is
        // composited on top of the card approaching from the right.
        float spacing = this.width * 20F / 100F;
        float lineX = this.width / 2F;
        float stripStartX = this.width * randomWidth / 100F;
        for (int i = count - 1; i >= 0; i--) {{
            ItemStack itemStack = itemInput.get(i);
            if (itemStack.isEmpty()) continue;

            float itemX = stripStartX + i * spacing - Mth.lerp(progress, lastRenderWidth, {scrollVar});
            float distSpacing = Math.abs(itemX - lineX) / spacing;
            float focus = (distSpacing >= IconListTools.FOCUS_FALLOFF_SPACING)
                    ? 0.0F
                    : (float) Math.pow(1.0F - distSpacing / IconListTools.FOCUS_FALLOFF_SPACING, 2.0F);
            if (focus > 0.02F) {{
                float focusScale = 1.0F + focus * (IconListTools.FOCUS_PEAK_SCALE - 1.0F);
                IconListTools.renderItemProgressFocus(player, guiGraphics, itemStack,
                        itemX, this.height * 37F / 100F,
                        this.width, this.height, gradeInput.get(i), focusScale);
            }} else {{
                IconListTools.renderItemProgress(player, guiGraphics, itemStack,
                        itemX, this.height * 37F / 100F,
                        this.width, this.height, gradeInput.get(i));
            }}
        }}
"""
    text = text[: old.start()] + new_block + text[old.end():]
    p.write_text(text, encoding="utf-8")
    print(f"[screen] {platform}: renderBg 循环已替换")
    return True


def main() -> int:
    for pl in PLATFORMS:
        try:
            patch_icon_tools(pl)
        except Exception as e:
            print(f"!! {pl} icon 失败: {e}")
        try:
            patch_progress_screen(pl)
        except Exception as e:
            print(f"!! {pl} screen 失败: {e}")
    return 0


if __name__ == "__main__":
    sys.exit(main())