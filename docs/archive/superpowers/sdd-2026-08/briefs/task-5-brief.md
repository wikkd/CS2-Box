### Task 5: 聊天区 ♞ 水印

**Files:**
- Modify: `{3 平台}/.../gui/terminal/TerminalChatRegion.java`

- [ ] **Step 1: v26_1_2** `render()` 中 `drawDotGrid` 之后、条目绘制之前插入：
```java
// 棋子水印（HTML .watermark &#9822; = U+265E，Unifont 含字形；alpha 0.045×255≈12）
String wm = "♞";
Font f = Minecraft.getInstance().font;
float wmScale = (x1 - x0) / 12.7F;
RenderFontTool.drawString(gg, f, fcs(wm),
        (x0 + x1) / 2F - f.width(wm) * wmScale / 2F,
        (bodyTop + y1) / 2F - f.height * wmScale / 2F,
        0, 0, wmScale, 0x0CFFFFFF);
```
- [ ] **Step 2/3/4: v26_2、v1_21_1 合入 + 三平台 clean 编译**
- [ ] **Step 5: 提交**：`feat(terminal): chess-knight chat watermark`
- [ ] **Step 6: 运行时验证**：♞ 可见性（若 Unifont 缺字形→豆腐块，备选烘焙纹理，运行验证任务记录）

