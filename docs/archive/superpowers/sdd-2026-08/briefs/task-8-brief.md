## Task 8: v1_21_1 主屏按钮补 hover（对齐 v26 系）

**Files:**
- Modify: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxScreen.java`（drawButton L283-286 + 两个调用点）

**Interfaces:**
- 无新接口。这是三平台中唯一按钮连 hover 都没有的平台

> **决策记录：不做 press 反馈**。MC GUI 在 `mouseClicked` 内同步执行动作（开箱/确认/返回全部立即切屏），不存在 press 停留窗口——按下视觉必须在动作执行前渲染，需要把动作延迟到 mouseReleased，属于 hack 且收益为负。v1_21_1 的 hover 缺失是真实缝隙（玩家靠 hover 瞄准按钮），本任务只补这个。

- [ ] **Step 1: 实现**

`drawButton` 改为带 hover 双态：

```java
    private void drawButton(GuiGraphics guiGraphics, int x, int y, int w, int h,
                            int fillColor, int borderColor,
                            int fillHover, int borderHover, boolean hover) {
        int fill = hover ? fillHover : fillColor;
        int border = hover ? borderHover : borderColor;
        AnimRenderOps.fill(guiGraphics, x, y, x + w, y + h, border);
        AnimRenderOps.fill(guiGraphics, x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }
```

找到两个调用点（open 按钮与 back 按钮），分别改为传 hover 双态色：

```java
        boolean openHover = isInside(mouseX, mouseY, x, y, w, h);
        drawButton(guiGraphics, x, y, w, h, 0xFF00AA00, 0xFF00FF00, 0xFF33DD55, 0xFF66FF88, openHover);
        ...
        boolean backHover = isInside(mouseX, mouseY, x, y, w, h);
        drawButton(guiGraphics, x, y, w, h, 0xFFAA0000, 0xFFFF0000, 0xFFCC4444, 0xFFFF6666, backHover);
```

> 若 v1_21_1 绘制方法无 mouseX/mouseY 参数，检查调用链：把 hover 计算上移到有鼠标坐标的调用处传入。以文件实际结构为准，保证 hover 命中矩形与按钮一致。hover 双态色对齐 v26 系 ButtonPalette 的 OPEN/DANGER 视觉（亮 25-30%）。

- [ ] **Step 2: 编译验证**

```bash
./gradlew :v1_21_1:compileJava -Pactive_versions=1.21.1 --rerun-tasks
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxScreen.java
git commit -m "fix(v1_21_1): add hover states to main screen buttons"
```

---

## 收尾验证（全部任务完成后）

- [ ] 跑 `scripts/check-animops-drift.sh` — Expected: 三平台 OK（未新增原语）
- [ ] 跑 `./gradlew :common:test -Pactive_versions=26.1.2` — Expected: 全绿
- [ ] 三平台 clean 编译
- [ ] 运行时人工回归：开箱→检视过渡、批量瀑布流推挤、show-all、info 面板、翻页（改动屏的全流程走查，清单见 docs/RELEASE.md）

## 范围外（明确不做）

- P3：终端屏动效（墙钟体系独立、TerminalAnims 已完整）、3D 拖拽惯性
- 按钮 press 反馈（Task 8 决策记录：MC 无 press 停留窗口）
- 世界内反馈（属增强非修复）
- 任何新 AnimRenderOps 原语 / 新前置依赖
