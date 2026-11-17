# Task 8 Report: v1_21_1 主屏按钮补 hover（对齐 v26 系）

## Status: DONE

## 实现内容

文件：`v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxScreen.java`

### 1. `drawButton` 扩展为 hover 双态

原签名 `drawButton(guiGraphics, x, y, w, h, fillColor, borderColor)` 扩展为：

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

与 brief 中的目标代码逐字一致；渲染原语仍是 `AnimRenderOps.fill` ×2（未新增任何原语）。

### 2. 调用点改动 + 鼠标坐标线程化细节

关键结构事实（与 brief 行号不同，按当前文件实况适配）：

- 两个调用点在 `renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy)` 内（原 L245-248）。
- `renderBg` 的 `gx/gy` 参数就是鼠标坐标——由 `render(GuiGraphics, int mouseX, int mouseY, float)` 以 `this.renderBg(guiGraphics, partialTicks, mouseX, mouseY)` 传入（L207）。
- 因此**无需上提调用链**：鼠标坐标在调用点处已经可用，直接在 `renderBg` 内计算 hover，命中矩形与按钮绘制矩形完全一致。

改造后的调用点（open 按钮）：

```java
int openX = openButtonX();
int openY = this.height * 94 / 100;
int openW = actionButtonWidth();
int openH = this.height * 5 / 100;
boolean openHover = isInside(gx, gy, openX, openY, openW, openH);
drawButton(guiGraphics, openX, openY, openW, openH, 0xFF00AA00, 0xFF00FF00,
        0xFF33DD55, 0xFF66FF88, openHover);
```

back 按钮同理（base `0xFFAA0000`/`0xFFFF0000`，hover `0xFFCC4444`/`0xFFFF6666`）。

### 3. 新增 `isInside` 辅助方法

`CsboxScreen` 原本没有命中检测 helper（`mouseClicked` 里是内联判断）。按本平台惯例补齐（与 `CsboxBulkOverviewScreen`/`CsboxConfirmScreen`/`CsboxBulkResultScreen` 的 `private static isInside` 逐字一致）：

```java
private static boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
    return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
}
```

语义与 v26 `ButtonPalette.isInside`（`mouseX <= x + w`，闭区间）一致，与 `mouseClicked` 的内联判断同样一致——hover 命中与点击命中不会出现边界偏差。

### 4. 颜色

- base 色保持文件现状原样：open `0xFF00AA00`/`0xFF00FF00`、back `0xFFAA0000`/`0xFFFF0000`（与 brief 一致，无需改动）。
- hover 色取 brief 给定值：open `0xFF33DD55`/`0xFF66FF88`、back `0xFFCC4444`/`0xFFFF6666`。与 v26 `ButtonPalette` 的 OPEN/DANGER hover 关系核对（fill ~+20-35%、border ~+25-30%，明度整体提高约 25-30%）一致。
- 按决策记录：**未加 press 反馈**（MC 在 `mouseClicked` 内同步切屏，无 press 停留窗口）。

## 编译验证

```bash
./gradlew :v1_21_1:compileJava -Pactive_versions=1.21.1 --rerun-tasks
```

**BUILD SUCCESSFUL**（17s，25 actionable tasks）。

## 变更文件

仅 1 个：`v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxScreen.java`（+26/-7）

Commit: `8f82807` `fix(v1_21_1): add hover states to main screen buttons`（`git show --stat HEAD` 确认仅 1 文件；未使用 `git add .`/`-A`，仅 stage 目标路径）。

## 自审清单

- [x] drawButton 双态签名与 brief 目标代码一致
- [x] 鼠标坐标在 `renderBg` 的 `gx/gy`（由 `render` 的 `mouseX/mouseY` 传入）处可用，调用点直接计算 hover，无需上提
- [x] hover 命中矩形 = 按钮绘制矩形（同一组 x/y/w/h 变量），且与 `mouseClicked` 点击命中一致（闭区间边界）
- [x] base 色与文件现状完全一致，未改动
- [x] 未新增 AnimRenderOps 原语（漂移检查应保持三平台 OK，本轮未跑）
- [x] 未加 press 反馈（决策记录）
- [x] 只动了 v1_21_1（26.x 已有 ButtonPalette hover，属正确的跨平台差异，不做镜像）

## 关注点 / 遗留

1. **运行时人工回归**：hover 变亮幅度为经验值（对齐 v26 的 25-30% 关系），需在游戏内目检确认在暗色背景上的观感（收尾验证清单中的全流程走查覆盖）。
2. 本任务未跑 `scripts/check-animops-drift.sh` 与 `:common:test`（未触碰 AnimRenderOps 与 common，属收尾验证项）。
3. 与平行工作流（page-turn 等）共存：编译通过即说明无冲突；commit 只含本任务改动（该文件其余 WIP 已在平行流先行提交）。
