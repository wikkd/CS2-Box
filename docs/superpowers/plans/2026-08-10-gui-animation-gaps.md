# GUI 动效补全 + Easing 提取 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补全 CS2-Box 三平台 GUI 的 6 处动效缝隙，并把散落的 easing 曲线收敛为 common 共享库。
**Architecture:** 全部动画 tick 驱动、复用现有 13 个 AnimRenderOps 原语、零新依赖。Easing 放 common（纯 Java，符合 CONSTRAINT-001）；平台改动以 v26_1_2 为基准，v26_2 / v1_21_1 定点适配（已归一化 diff 验证三平台同构）。
**Tech Stack:** NeoForge 21.x / 26.x、Java 21/25、JUnit 5（common）。

## Global Constraints

- common/ 不得 import `net.minecraft.*` / `net.neoforged.*`（编译即失败，`checkCommonArchitecture` 自动挂载）
- 每次 Gradle 调用仅能构建一个 MC 版本，用 `-Pactive_versions=<v>`；平台改动用 `--rerun-tasks` 编译确认（增量缓存可能造假象；clean 留到收尾）
- 禁止用 v26_1_2 整文件覆盖 v26_2（API 适配差异：`setScreen`→`setScreenAndShow`、`GuiGraphics`↔`GuiGraphicsExtractor`、tick↔ms、`BuiltInRegistries.ITEM.get()` Optional 等）
- 不新增 AnimRenderOps 原语（drift 脚本守护）
- 时间源：GUI 屏用 game tick；终端墙钟 ms 体系不动
- 曲线统一用 `Easing.*`；**禁止**新写手写 ease 公式
- **commit 纪律：工作区存在上一 milestone 的未提交修改（CHANGELOG.md、README.md、common/box/BoxDefaults.java、BoxJsonSchemaValidator.java、ColorTools.java、lang json、docs/、scripts/ 等），git add 只允许 add 本任务涉及的文件，禁止 `git add .` / `git add -A`**
- 工作目录：/Users/shuangyuexingxun/Desktop/CS2-Box（main 分支，项目惯例直接开发）

---

## Task 1: Easing 库（common）+ TerminalAnims 委托

**Files:**
- Create: `common/src/main/java/com/reclizer/csgobox/utils/Easing.java`
- Create: `common/src/test/java/com/reclizer/csgobox/utils/EasingTest.java`
- Modify: `common/src/main/java/com/reclizer/csgobox/terminal/TerminalAnims.java`

**Interfaces:**
- Produces: `Easing.clamp01(float)`, `Easing.easeOutCubic(float)`, `Easing.easeOutQuad(float)`, `Easing.easeOutBack(float)`, `Easing.smoothstep(float,float,float)`, `Easing.cubicBezierCurve(float)` — 全部 `static float`，纯函数
- Later tasks call: `Easing.easeOutCubic(t)` 是后续全部任务的唯一曲线入口

- [ ] **Step 1: 写失败测试**

```java
package com.reclizer.csgobox.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasingTest {

    @Test
    void clamp01Clamps() {
        assertEquals(0F, Easing.clamp01(-1F));
        assertEquals(1F, Easing.clamp01(2F));
        assertEquals(0.5F, Easing.clamp01(0.5F));
    }

    @Test
    void easeOutCubicEndpoints() {
        assertEquals(0F, Easing.easeOutCubic(0F));
        assertEquals(1F, Easing.easeOutCubic(1F));
    }

    @Test
    void easeOutCubicMid() {
        assertEquals(0.875F, Easing.easeOutCubic(0.5F), 1e-4F);
    }

    @Test
    void easeOutBackOvershoots() {
        assertTrue(Easing.easeOutBack(0.5F) > 0.5F);
        assertEquals(1F, Easing.easeOutBack(1F), 1e-4F);
    }

    @Test
    void cubicBezierEndpoints() {
        assertEquals(0F, Easing.cubicBezierCurve(0F), 1e-4F);
        assertEquals(1F, Easing.cubicBezierCurve(1F), 1e-4F);
    }

    @Test
    void cubicBezierIsMonotonic() {
        float prev = -1F;
        for (float t = 0F; t <= 1F; t += 0.05F) {
            float v = Easing.cubicBezierCurve(t);
            assertTrue(v >= prev);
            prev = v;
        }
    }

    @Test
    void smoothstepMid() {
        assertEquals(0.5F, Easing.smoothstep(0F, 1F, 0.5F), 1e-4F);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :common:test -Pactive_versions=26.1.2`
Expected: 编译失败 — `Easing` 类不存在

- [ ] **Step 3: 写实现**

```java
package com.reclizer.csgobox.utils;

/**
 * Shared easing curves for all GUI screens (tick-driven) and the terminal
 * (wall-clock driven). Pure functions, no MC imports — safe for common/.
 * Curves migrated from terminal/TerminalAnims so every platform and the
 * terminal use one implementation.
 */
public final class Easing {

    private Easing() {
    }

    public static float clamp01(float v) {
        return v < 0F ? 0F : (v > 1F ? 1F : v);
    }

    public static float easeOutCubic(float t) {
        float u = 1F - clamp01(t);
        return 1F - u * u * u;
    }

    public static float easeOutQuad(float t) {
        float p = clamp01(t);
        return 1F - (1F - p) * (1F - p);
    }

    /** easeOutBack — flip-in overshoot (c1/c3 standard constants). */
    public static float easeOutBack(float t) {
        float p = clamp01(t);
        final float c1 = 1.70158F;
        final float c3 = c1 + 1F;
        float q = p - 1F;
        float v = 1F + c3 * q * q * q + c1 * q * q;
        return v > 1F ? 1F : v;
    }

    /** GLSL smoothstep on [a,b]. */
    public static float smoothstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3F - 2F * t);
    }

    /**
     * Cubic-bezier(.25,.6,.3,1) evaluation (numerical x(t)=t solve, binary
     * search, 20 iterations, deviation < 1e-4). Migrated verbatim from
     * TerminalAnims.cubicBezierCurve — used by the terminal wear-bar arrow
     * and long-press fill.
     */
    public static float cubicBezierCurve(float t) {
        return cubicBezierX(clamp01(t), 0.25F, 0.60F, 0.30F, 1.00F);
    }

    private static float cubicBezierX(float x, float x1, float y1, float x2, float y2) {
        // Copy verbatim from TerminalAnims.cubicBezierX (binary search on
        // x(t)=target, 20 iterations). Exact same control-point values.
        // Implementation: standard cubic-bezier inversion.
        if (x <= 0F) return 0F;
        if (x >= 1F) return 1F;
        float lo = 0F, hi = 1F;
        for (int i = 0; i < 20; i++) {
            float mid = (lo + hi) * 0.5F;
            float m1 = 1F - mid;
            float bx = 3F * m1 * m1 * mid * x1 + 3F * m1 * mid * mid * x2 + mid * mid * mid;
            if (bx < x) lo = mid; else hi = mid;
        }
        float t = (lo + hi) * 0.5F;
        float m = 1F - t;
        return 3F * m * m * t * y1 + 3F * m * t * t * y2 + t * t * t;
    }
}
```

> 注：cubicBezierX 以现有 `TerminalAnims.cubicBezierX`（common/terminal/TerminalAnims.java:75-95）为准逐行核对，若原型实现不同以原型为准迁移。

- [ ] **Step 4: 改 TerminalAnims 为委托（保持公共 API 不变）**

替换 `TerminalAnims.java` 中以下方法的实现（约 L52-95）：

```java
    public static float clamp01(float v) {
        return Easing.clamp01(v);
    }

    public static float easeOutCubic(float t) {
        return Easing.easeOutCubic(t);
    }

    /** easeOutBack — used by the flip-in slide overshoot. */
    public static float easeOutBack(float t) {
        return Easing.easeOutBack(t);
    }

    /**
     * Cubic-bezier(.25,.6,.3,1) evaluation. Delegates to {@link Easing}.
     * CURVE_X1..Y2 constants are retained for source compatibility; the
     * canonical control points now live in Easing.cubicBezierCurve().
     */
    public static float cubicBezierCurve(float t) {
        return Easing.cubicBezierCurve(t);
    }
```

删除被替换的 `cubicBezierX` 私有方法；`CURVE_X1..Y2` 常量**保留**（防外部引用）。文件头加 import：`import com.reclizer.csgobox.utils.Easing;`

- [ ] **Step 5: 跑测试确认通过**

Run: `./gradlew :common:test -Pactive_versions=26.1.2`
Expected: 全绿（含既有 BoxJsonSchemaValidatorTest + 新增 7 用例；checkCommonArchitecture 自动通过——Easing 无 MC import）

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/com/reclizer/csgobox/utils/Easing.java common/src/test/java/com/reclizer/csgobox/utils/EasingTest.java common/src/main/java/com/reclizer/csgobox/terminal/TerminalAnims.java
git commit -m "feat(common): extract Easing curves from TerminalAnims into shared utils"
```

---

## Task 2: 瀑布流条目滑入推挤（CsboxBulkResultScreen ×3）

**Files:**
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxBulkResultScreen.java`（renderEntries L132-177）
- Modify: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxBulkResultScreen.java`（同构，同改法）
- Modify: `v26_2/src/main/java/com/reclizer/csgobox/v26_2/gui/CsboxBulkResultScreen.java`（ms 版，数值不同）

**Interfaces:**
- Consumes: `Easing.easeOutCubic`
- 不改数据结构、不改 tick 逻辑、不改 alpha 曲线——只改 renderEntries 的 y 计算

- [ ] **Step 1: 实现（v26_1_2 + v1_21_1 相同代码）**

在 `renderEntries` 的 `int index = 0;` 之前插入：

```java
        // Whole-list push: when the newest entry arrives it slides up from
        // one row below while every older entry is pushed up one row in
        // lock-step, so nothing teleports. Push eases out over 5 ticks.
        Entry firstEntry = visible.peekFirst();
        float freshAge = firstEntry == null
                ? 1F
                : (float) (now - firstEntry.appearTick + partialTicks) / LIFE_TICKS;
        float push = rowH * (1F - Easing.easeOutCubic(Math.min(1F, freshAge / 0.05F)));
```

将 `int y = baseY - index * rowH;` 替换为：

```java
            int y = baseY - index * rowH + (int) push;
```

文件头加 `import com.reclizer.csgobox.utils.Easing;`

- [ ] **Step 2: 适配 v26_2（ms 版）**

同 Step 1，但 freshAge 计算无 partialTicks（v26_2 已是 `(float)(now - e.appearTick) / LIFE_TICKS`）：

```java
        Entry firstEntry = visible.peekFirst();
        float freshAge = firstEntry == null
                ? 1F
                : (float) (now - firstEntry.appearTick) / LIFE_TICKS;
        float push = rowH * (1F - Easing.easeOutCubic(Math.min(1F, freshAge / 0.04F)));
```

`0.04F` = 200ms（TICKS_PER_ENTRY）/ 5000ms（LIFE_TICKS）。

- [ ] **Step 3: 编译验证三平台**

```bash
./gradlew :v26_1_2:compileJava -Pactive_versions=26.1.2 --rerun-tasks
./gradlew :v1_21_1:compileJava -Pactive_versions=1.21.1 --rerun-tasks
./gradlew :v26_2:compileJava -Pactive_versions=26.2 --rerun-tasks
```
Expected: BUILD SUCCESSFUL ×3

- [ ] **Step 4: Commit**

```bash
git add v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxBulkResultScreen.java v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxBulkResultScreen.java v26_2/src/main/java/com/reclizer/csgobox/v26_2/gui/CsboxBulkResultScreen.java
git commit -m "feat(gui): slide-in push for bulk waterfall entries"
```

---

## Task 3: 检视屏入场（CsLookItemScreen ×3）

**Files:**
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsLookItemScreen.java`（renderBg L201-229、renderLabels L379-407、tick L488-497）
- Modify: `v1_21_1` / `v26_2` 同文件（同构适配）

**Interfaces:**
- Consumes: `Easing.easeOutCubic`
- `screenTicks` 已存在（tick() 自增），直接用：6 tick = 300ms 入场

- [ ] **Step 1: 实现（三平台同构）**

`renderBg` 开头（`if (openItem.isEmpty()) return;` 之后）插入遮罩 + 入场进度：

```java
        // Enter transition: the strip screen's dark backdrop fades out while
        // the item scales up from 0.9 to 1.0 over 300ms (6 ticks).
        float enterE = Easing.easeOutCubic(Math.min(1F, this.screenTicks / 6F));
        int fadeAlpha = (int) (0xFF * (1F - enterE)) & 0xFF;
        if (fadeAlpha > 0) {
            AnimRenderOps.fill(guiGraphics, 0, 0, this.width, this.height,
                    (fadeAlpha << 24) | 0x000000);
        }
```

将物品渲染的 scale 改为动画 scale：

```java
        float scale = (previewTextureSize() / 16F) * (0.9F + 0.1F * enterE);
```

`renderLabels` 中标题与品级改为随入场淡入——两处颜色改为显式 alpha：

```java
        int titleAlpha = (int) (0xFF * enterE) & 0xFF;
        RenderFontTool.drawStringClamped(guiGraphics, this.font, openItem.getItem().getName(openItem),
                titleX, this.height * 5F / 100F, 0, 0, titleScale,
                titleMaxWidth, (titleAlpha << 24) | 0xFFFFFF);
        ...
        renderText(guiGraphics, ..., (titleAlpha << 24) | 0xFFFFFFFF);
```

> 注意：enterE 在 renderBg 中是局部变量，renderLabels 也需要——**把入场进度计算提取为私有方法 `private float enterE() { return Easing.easeOutCubic(Math.min(1F, this.screenTicks / 6F)); }`**，两处调用该方法，不存字段。

> 注：`renderText` 的 4 参重载内部调用 5 参版（默认白），需改为显式传色。三平台 RenderFontTool.drawStringClamped 签名一致（颜色参数为最后一个 int）。

文件头加 `import com.reclizer.csgobox.utils.Easing;`

- [ ] **Step 2: 编译验证三平台**
- [ ] **Step 3: Commit**

```bash
git add v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsLookItemScreen.java v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsLookItemScreen.java v26_2/src/main/java/com/reclizer/csgobox/v26_2/gui/CsLookItemScreen.java
git commit -m "feat(gui): look-item screen enter transition (fade + scale-in)"
```

---

## Task 4: 主屏首包入场（CsboxScreen ×3）

**Files:**
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxScreen.java`（字段区 ~L160、tick L537-541、containerTick L543-562、renderGridAnimated L278-289）
- Modify: `v1_21_1` / `v26_2` 同文件（同构适配）

**Interfaces:**
- Consumes: `Easing.easeOutCubic`

- [ ] **Step 1: 实现（三平台同构）**

字段区（`private int animFromPage = -1;` 附近）加：

```java
    private static final int ENTER_TICKS = 6;
    /** Grid fade-in on first server sync; 6 = settled (no enter anim). */
    private int enterTicks = ENTER_TICKS;
```

`tick()` 内 `containerTick()` 调用**之后**加推进：

```java
        if (this.enterTicks < ENTER_TICKS) {
            this.enterTicks++;
        }
```

`containerTick` 数据到达分支（`this.animFromPage = -1;` 之后）加：

```java
            this.enterTicks = 0;
```

`renderGridAnimated` 的 `animFromPage < 0` 分支替换为：

```java
        if (animFromPage < 0) {
            float e = Easing.easeOutCubic(Math.min(1F, this.enterTicks / (float) ENTER_TICKS));
            renderPageGrid(guiGraphics, this.page, Math.round(8F * (1F - e)),
                    (int) (255F * e));
            return;
        }
```

> 翻页动画触发时 `animFromPage >= 0`，走原有分支；翻页结束 `animFromPage = -1` 且 enterTicks 已满——不干扰。v1_21_1 与 v26_2 的 containerTick/字段结构同构（归一化 diff 已证），按此定点合入。

文件头加 `import com.reclizer.csgobox.utils.Easing;`

- [ ] **Step 2: 编译验证三平台**
- [ ] **Step 3: Commit**

```bash
git add v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxScreen.java v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxScreen.java v26_2/src/main/java/com/reclizer/csgobox/v26_2/gui/CsboxScreen.java
git commit -m "feat(gui): fade-in grid on first box sync"
```

---

## Task 5: 滚条起步 ease-in（CsboxProgressScreen ×3）

**Files:**
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxProgressScreen.java`（easedScroll L94-98、tick L382-387）
- Modify: `v1_21_1` / `v26_2` 同文件（同构适配）

**Interfaces:**
- 无（内部数学）；产出：起步 0 初速，前 5 tick 三次接驳段 C1 连续
- 数学：ramp 固定 5 tick；`r = 5/(totalTicks-1)`；接驳多项式 `p(u)=Au³+Bu²`（u∈[0,1]），`A = dr - 2·sr`、`B = 3·sr - dr`，其中 `sr = 1-(1-r)³`、`dr = 3(1-r)²·r`（对 u 的斜率，保证 C1 连续）

- [ ] **Step 1: 实现（三平台同构）**

替换 `easedScroll` 为：

```java
    private float easedScroll(float progressTick, float totalTicks, float totalDistance) {
        float rampFrac = 5F / Math.max(1F, totalTicks - 1F);
        float t = progressTick / Math.max(1F, totalTicks - 1F);
        if (t < rampFrac) {
            // Ramp: cubic segment with zero start velocity, C1-continuous
            // with easeOutCubic at t = rampFrac (solves the strip starting
            // at full speed; the old curve has its max slope at t=0).
            float u = t / rampFrac;
            float sr = 1F - (1F - rampFrac) * (1F - rampFrac) * (1F - rampFrac);
            float dr = 3F * (1F - rampFrac) * (1F - rampFrac) * rampFrac;
            float a = dr - 2F * sr;
            float b = 3F * sr - dr;
            return (a * u * u * u + b * u * u) * totalDistance;
        }
        float v = 1F - t;
        return totalDistance * (1F - v * v * v);
    }
```

`tick()` 中两处调用改为：

```java
        float progress = (float) startTime;
        renderWidthAdd = easedScroll(progress, totalTicks, targetScroll);

        float prevProgress = (float) Math.max(0, startTime - 1);
        velocityLerp = (easedScroll(progress, totalTicks, targetScroll)
                - easedScroll(prevProgress, totalTicks, targetScroll)) / 35F;
```

> 数学等价性：旧代码 `progress = startTime/(totalTicks-1)` 后内部 `t = progress`，等价于新代码 `t = progressTick/(totalTicks-1)`——t≥rampFrac 段输出逐帧一致；tick 音效是位移驱动的（soundWidthAdd 累加），随新曲线自动同步。

- [ ] **Step 2: 编译验证三平台**
- [ ] **Step 3: Commit**

```bash
git add v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxProgressScreen.java v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxProgressScreen.java v26_2/src/main/java/com/reclizer/csgobox/v26_2/gui/CsboxProgressScreen.java
git commit -m "feat(gui): zero-velocity start ramp for the opening strip"
```

---

## Task 6: info 面板入场/出场（CsLookItemScreen ×3）

**Files:**
- Modify: `v26_1_2` / `v1_21_1` / `v26_2` 的 `gui/CsLookItemScreen.java`（字段、tick、renderInfoPanel、renderToolbar active 态）

**Interfaces:**
- 无（不调用 Easing；与 toolbarGlow 同模式：指数趋近）

> **决议：本任务不调用 Easing，不需要 import**（计划早期文本的矛盾处以此为准）。

- [ ] **Step 1: 实现（三平台同构）**

字段（`private boolean showInfoPanel = false;` 附近）加：

```java
    /** Info panel open/close animation: 0 = closed, 1 = open. */
    private float infoPanelAnim = 0F;
```

`tick()` 中（toolbarGlow 趋近之后）加：

```java
        float panelTarget = this.showInfoPanel ? 1F : 0F;
        this.infoPanelAnim += (panelTarget - this.infoPanelAnim) * 0.35F;
        if (Math.abs(this.infoPanelAnim - panelTarget) < 0.005F) {
            this.infoPanelAnim = panelTarget;
        }
```

`renderInfoPanel` 开头替换为：

```java
    private void renderInfoPanel(GuiGraphicsExtractor guiGraphics) {
        if (this.infoPanelAnim < 0.02F || openItem.isEmpty()) return;
        float anim = this.infoPanelAnim;
        int textAlpha = (int) (0xFF * anim) & 0xFF;
        int cardAlpha = (int) (0xE0 * anim) & 0xFF;
```

卡片绘制（renderRoundedRect 调用）：颜色 `0xE0101014` → `(cardAlpha << 24) | 0x101014`，y 坐标加 `Math.round(8F * (1F - anim))`（从按钮方向上浮 8px）。

`drawInfoRow` 调用处与文字绘制：`0xFF9A9A9A` → `(textAlpha << 24) | 0x9A9A9A`、`0xFFFFFFFF` → `(textAlpha << 24) | 0xFFFFFF`。

`renderToolbar` 中 info 按钮 active 白色下划线 `0xFFFFFF` → `((int) (0xFF * anim) << 24) | 0xFFFFFF`。

- [ ] **Step 2: 编译验证三平台**
- [ ] **Step 3: Commit**

```bash
git add v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsLookItemScreen.java v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsLookItemScreen.java v26_2/src/main/java/com/reclizer/csgobox/v26_2/gui/CsLookItemScreen.java
git commit -m "feat(gui): animate info panel open/close"
```

---

## Task 7: show-all 网格 stagger 入场（CsboxBulkResultScreen ×3）

**Files:**
- Modify: `v26_1_2` / `v1_21_1` / `v26_2` 的 `gui/CsboxBulkResultScreen.java`（字段区、tick、mouseClicked、renderAllItemsGrid）

**Interfaces:**
- Consumes: `Easing.easeOutCubic`

- [ ] **Step 1: 实现（三平台同构）**

字段（`private boolean showAllItems = false;` 附近）加：

```java
    /** Stagger counter since show-all opened; -1 = not animating. */
    private int showAllTick = -1;
    private static final int SHOW_ALL_ENTER = 6;
```

`tick()` 末尾（`lastTickTime = now;` 之前）加：

```java
        if (this.showAllTick >= 0 && this.showAllTick < SHOW_ALL_ENTER) {
            this.showAllTick++;
        }
```

`mouseClicked` show-all 分支（`showAllItems = true;`）加 `this.showAllTick = 0;`

`renderAllItemsGrid` 内，行循环中计算入场并修改槽位绘制：

```java
        float enterE = this.showAllTick < 0
                ? 1F
                : Easing.easeOutCubic(Math.max(0F, Math.min(1F,
                        (this.showAllTick - row * 2F) / (float) SHOW_ALL_ENTER)));
        int cellAlpha = (int) (0xCC * enterE) & 0xFF;
        int rowOffset = Math.round(8F * (1F - enterE));
        int bgColor = (cellAlpha << 24) | (ColorTools.colorItems(grade) & 0x00FFFFFF);
        AnimRenderOps.fillGradient(guiGraphics, x, y + rowOffset, x + itemSize + 4, y + itemSize + 4 + rowOffset, bgColor, bgColor);
        AnimRenderOps.fill(guiGraphics, x, y + rowOffset, x + 3, y + itemSize + 4 + rowOffset, ColorTools.colorItems(grade));
```

（`row` 在循环内已算好——计算需在 `row` 定义之后、绘制之前；每行错峰 2 tick）

底部 collect 按钮加整体淡入：

```java
        float btnE = this.showAllTick < 0 ? 1F : Easing.easeOutCubic(Math.min(1F, this.showAllTick / 4F));
        int btnAlpha = (int) (0xFF * btnE) & 0xFF;
        int fill = hover ? 0xFF00CC00 : 0xFF008800;
        int border = hover ? 0xFF00FF00 : 0xFF00AA00;
        fill = (btnAlpha << 24) | (fill & 0x00FFFFFF);
        border = (btnAlpha << 24) | (border & 0x00FFFFFF);
```

（collect 按钮原 hover 逻辑保留；文字色同样合成 btnAlpha）

> 物品图标 `renderRewardCell` 无 alpha 参数——槽位背景 + 位移已提供 stagger 感，图标随行出现，可接受（不扩展原语）。

文件头加 `import com.reclizer.csgobox.utils.Easing;`

- [ ] **Step 2: 编译验证三平台**
- [ ] **Step 3: Commit**

```bash
git add v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxBulkResultScreen.java v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxBulkResultScreen.java v26_2/src/main/java/com/reclizer/csgobox/v26_2/gui/CsboxBulkResultScreen.java
git commit -m "feat(gui): staggered fade-in for bulk show-all grid"
```

---

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
