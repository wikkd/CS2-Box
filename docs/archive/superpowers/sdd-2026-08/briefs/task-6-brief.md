### Task 6: 底行三件套（区域 11 名称 / 倒计时冒号变暗 / 圆形徽章）

**Files:**
- Modify: `common/src/main/java/com/reclizer/csgobox/terminal/TerminalPalette.java`（+COUNT_COLON）
- Modify: `common/src/test/java/com/reclizer/csgobox/terminal/TerminalAssetsTest.java`
- Modify: `scripts/gen-terminal-assets.py`
- Modify: `{3 平台}/.../gui/terminal/TerminalBottomRow.java`

- [ ] **Step 1: 脚本** `gen-terminal-assets.py` 增加 `make_badge()` 并在 `main()` 调用：
```python
def make_badge():
    # 72x72 米色径向徽章（HTML .slot-badge）：#f0ece1 → #cfc8b8 70% → #b3ac9b + 2px 内暗环
    def pixel(x, y):
        d = math.sqrt((x - 35.5) ** 2 + (y - 35.5) ** 2)
        if d > 36:
            return (0, 0, 0, 0)
        t = d / 36.0
        r, g, b = color_lerp(0xF0ECE1, 0xB3AC9B, t ** 0.9)
        if 34 <= d <= 36:  # inset ring #0004
            r, g, b = (round(r * 0.75), round(g * 0.75), round(b * 0.75))
        return (r, g, b, 255)
    write_png(os.path.join(OUT, "terminal_badge.png"), 72, 72, pixel)
```
  运行 `python3 scripts/gen-terminal-assets.py`。`TerminalAssetsTest.ASSETS` 增一行 `{"terminal_badge.png", "72", "72"}`。
- [ ] **Step 2: TerminalPalette** 加 `public static final int COUNT_COLON = 0x8CCFD6DB;`
- [ ] **Step 3: v26_1_2 `TerminalBottomRow`**（按内容定位）：
  - 区域 11 名称（dots 绘制前）：
```java
String xpName = Component.translatable("csgobox.terminal.name").getString();
RenderFontTool.drawString(gg, font, fcs(xpName), xpX, midY - 4, 0, 0, 1.2F, TerminalPalette.TEXT);
int dotX = xpX + Math.round(font.width(xpName) * 1.2F) + 16;
```
  - 倒计时分段绘制（替换单串绘制，`slide`/`color`/`expired` 沿用）：
```java
String[] toks = {text.substring(0, 2), text.substring(3, 5), text.substring(6, 8), text.substring(9, 11)};
float tx = cx0 + 2;
for (int i = 0; i < 4; i++) {
    RenderFontTool.drawString(gg, font, fcs(toks[i]), tx, midY - 8 + slide, 0, 0, 1.8F, color);
    tx += font.width(toks[i]) * 1.8F;
    if (i < 3) {
        RenderFontTool.drawString(gg, font, fcs(":"), tx + 1, midY - 8 + slide, 0, 0, 1.8F,
                TerminalPalette.COUNT_COLON);
        tx += font.width(":") * 1.8F + 2;
    }
}
```
  - 徽章替换（slot 绘制处：删 glow blit 与 4 条边框 fill，改徽章纹理）：
```java
AnimRenderOps.blitTextured(gg, TEX_BADGE, slotX - slotW / 2, slotCy - slotW / 2, slotW, slotW, 72, 72);
```
  常量：`public static final Identifier TEX_BADGE = Identifier.parse("csgobox:gui/terminal/terminal_badge");`（v1_21_1 用 `ResourceLocation.fromNamespaceAndPath`）
- [ ] **Step 4/5/6: v26_2、v1_21_1 合入 + 三平台 clean 编译 + `./gradlew :common:test`（资产测试）**
- [ ] **Step 7: 提交**：`feat(terminal): region-11 name, dim countdown colons, circular slot badge`

