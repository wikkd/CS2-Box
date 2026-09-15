# CsLookItemScreen 信息面板重排（复刻 CS:GO 磨损信息）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 9 个平台的 `CsLookItemScreen` ⓘ 信息面板改为 CS:GO 截图样式：无描边暗色面板、5 行「标签: 值」整行左对齐统一浅灰白、删除 StatTrak 行。

**Architecture:** 每平台独立文件，区域结构同构（legacy 用 `GuiGraphics`，v1_21_11/26.x 用 `GuiGraphicsExtractor`）。改动 = 重写 `renderInfoPanel()` + 简化 `drawInfoRow()` + 删 StatTrak 字段/生成。逐平台定点合入（禁止整文件覆盖），每平台 clean 编译验证。

**Tech Stack:** Java 21（legacy）/ Java 25 + preview（26.x），NeoForge 7.1.38，Gradle `-Pactive_versions`。

## Global Constraints

- 所有修改在 `CsLookItemScreen.java` 单文件内；禁止用 v1_21_1/v26_1_2 整文件覆盖其它模块（AGENTS.md 镜像纪律）
- 仅改信息面板区域；不动 `renderBg`/`renderToolbar`/`renderLabels`/物品渲染/随机值范围/磨损分级
- 新颜色常量 `0xFFCCCCCC`（整行文字）、面板底色沿用 `0xE0101014`；描边 `0x40FFFFFF` 四行删除
- 删除 `statTrak`、`statTrakKills` 字段与构造器生成、`lineCount` 三元、StatTrak 渲染分支、`drawInfoRow` 三参橙色重载
- 数值格式 `%.9f`、`wearTierKey()`、随机范围不变
- 不覆盖 `forge_26_1_2`（实验模块，本地 WIP）
- 平台映射：legacy = v1_21_1/3/4/5/8/10，new = v26_1_2/v1_21_11/v26_2
- 每次 Gradle 调用只构建一个 MC 版本；涉及平台改动用 `clean` 编译防增量缓存假象

---

### Task 1: v1_21_1 基准修改 + 编译验证

**Files:**
- Modify: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsLookItemScreen.java:30-37,58-81,193-247`

**Interfaces:**
- Produces: 修改后的 `renderInfoPanel(GuiGraphics)` / `drawInfoRow(GuiGraphics,int,int,float,String,Component)`（无三参重载）、`formatWear()`、`wearTierKey()`、`SKIN_STYLES` 均不变；删 `statTrak`/`statTrakKills` 字段。后续任务以此文件为 legacy 合入范本。

- [ ] **Step 1: 删除 StatTrak 字段**

在 v1_21_1 文件中，删除字段声明（当前 line 36-37）：

```java
    private final boolean statTrak;
    private final int statTrakKills;
```

- [ ] **Step 2: 删除构造器中的 StatTrak 生成**

删除构造器中的两行（当前 line 73-74）：

```java
        this.statTrak = rnd.nextFloat() < 0.12F;
        this.statTrakKills = rnd.nextInt(1, 500);
```

- [ ] **Step 3: 重写 renderInfoPanel + drawInfoRow**

用以下内容整体替换当前 `renderInfoPanel` 方法体与两个 `drawInfoRow` 方法（v1_21_1 line 193-247）：

```java
    private void renderInfoPanel(GuiGraphics guiGraphics) {
        if (!this.showInfoPanel || openItem.isEmpty()) return;
        int panelX = this.width * 8 / 100;
        int panelY = this.height * 20 / 100;
        int panelW = Math.max(200, this.width * 16 / 100);
        int rowH = 13;
        int lineCount = 5;
        int panelH = 12 + lineCount * rowH;
        int panelRight = panelX + panelW;
        int panelBottom = panelY + panelH;
        guiGraphics.fill(panelX, panelY, panelRight, panelBottom, 0xE0101014);

        int textX = panelX + 8;
        int y = panelY + 8;
        float scale = 0.7F;
        int rowIndex = 0;
        drawInfoRow(guiGraphics, textX, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.skin_style",
                Component.translatable("gui.csgobox.csgo_box.style." + SKIN_STYLES[this.skinStyleIndex]));
        drawInfoRow(guiGraphics, textX, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.skin_id",
                Component.literal(String.valueOf(this.skinId)));
        drawInfoRow(guiGraphics, textX, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.pattern",
                Component.literal(String.valueOf(this.patternSeed)));
        drawInfoRow(guiGraphics, textX, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.wear_rating",
                Component.literal(formatWear()));
        drawInfoRow(guiGraphics, textX, y + rowIndex * rowH, scale,
                "gui.csgobox.csgo_box.info.exterior",
                Component.translatable(wearTierKey()));
    }

    private void drawInfoRow(GuiGraphics guiGraphics, int x, int y, float scale,
                             String labelKey, Component value) {
        renderText(guiGraphics, Component.translatable(labelKey, value).getVisualOrderText(),
                x, y, scale, 0xFFCCCCCC);
    }
```

注意：原 `renderInfoPanel` 中的 4 条描边 `guiGraphics.fill(..., 0x40FFFFFF)` 全部删除，只保留背景 `0xE0101014`；两个旧 `drawInfoRow`（含三参橙色重载）整体替换为上述单方法。

- [ ] **Step 4: 编译验证（clean）**

Run: `./gradlew :v1_21_1:compileJava -Pactive_versions=21.1 --rerun-tasks`
Expected: `BUILD SUCCESSFUL`（`checkCommonArchitecture` 一并通过，不涉 common）

- [ ] **Step 5: 人工检查残留**

Run: `grep -n "statTrak\|valueColor\|0x40FFFFFF" v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsLookItemScreen.java`
Expected: 无输出（`grep` 返回非零退出码）

---

### Task 2: legacy 其余 5 平台同构合入

**Files:**
- Modify: `v1_21_3/src/main/java/com/reclizer/csgobox/v1_21_3/gui/CsLookItemScreen.java`
- Modify: `v1_21_4/src/main/java/com/reclizer/csgobox/v1_21_4/gui/CsLookItemScreen.java`
- Modify: `v1_21_5/src/main/java/com/reclizer/csgobox/v1_21_5/gui/CsLookItemScreen.java`
- Modify: `v1_21_8/src/main/java/com/reclizer/csgobox/v1_21_8/gui/CsLookItemScreen.java`
- Modify: `v1_21_10/src/main/java/com/reclizer/csgobox/v1_21_10/gui/CsLookItemScreen.java`

**Interfaces:**
- Consumes: Task 1 的范本（legacy 区域结构一致，行号偏移 ±3，方法与字段同名同型）。
- Produces: 5 个 legacy 平台与 v1_21_1 信息面板同构。

- [ ] **Step 1: 逐平台执行 Task 1 的 Step 1-3（同构替换）**

每个平台依次：删 `statTrak`/`statTrakKills` 字段 → 删构造器两行生成 → 替换 `renderInfoPanel`+`drawInfoRow` 为 Task 1 Step 3 的代码块（`GuiGraphics` 不变）。行号以各文件实际为准，先 `grep -n "renderInfoPanel\|statTrak" <file>` 定位。

- [ ] **Step 2: 逐平台残留检查**

Run: `grep -n "statTrak\|valueColor\|0x40FFFFFF" <module>/src/main/java/com/reclizer/csgobox/<module>/gui/CsLookItemScreen.java`
Expected: 每平台均无输出

- [ ] **Step 3: 逐平台 clean 编译**

Run（5 次，每次一个版本）:
`./gradlew :v1_21_3:compileJava -Pactive_versions=21.3 --rerun-tasks`
`./gradlew :v1_21_4:compileJava -Pactive_versions=21.4 --rerun-tasks`
`./gradlew :v1_21_5:compileJava -Pactive_versions=21.5 --rerun-tasks`
`./gradlew :v1_21_8:compileJava -Pactive_versions=21.8 --rerun-tasks`
`./gradlew :v1_21_10:compileJava -Pactive_versions=21.10 --rerun-tasks`
Expected: 全部 `BUILD SUCCESSFUL`

---

### Task 3: v26_1_2 new 基准修改 + 编译验证

**Files:**
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsLookItemScreen.java:35-38,59-82,221-275`

**Interfaces:**
- Consumes: 无（与 Task 1 独立，但以 Task 1 逻辑为范本）。
- Produces: new 平台合入范本（`GuiGraphicsExtractor` 版），后续 v1_21_11/v26_2 以此为准。

- [ ] **Step 1: 删除 StatTrak 字段与生成**

删除字段（当前 line 37-38）与构造器两行生成。构造器当前内容为：

```java
        this.statTrak = rnd.nextFloat() < 0.12F;
        this.statTrakKills = rnd.nextInt(1, 500);
```

- [ ] **Step 2: 重写 renderInfoPanel + drawInfoRow（GuiGraphicsExtractor 版）**

替换当前 `renderInfoPanel(GuiGraphicsExtractor)` 与两个 `drawInfoRow`（line 221-275），内容与 Task 1 Step 3 相同，仅方法签名类型改为 `GuiGraphicsExtractor`：

```java
    private void renderInfoPanel(GuiGraphicsExtractor guiGraphics) {
        if (!this.showInfoPanel || openItem.isEmpty()) return;
        int panelX = this.width * 8 / 100;
        int panelY = this.height * 20 / 100;
        int panelW = Math.max(200, this.width * 16 / 100);
        int rowH = 13;
        int lineCount = 5;
        int panelH = 12 + lineCount * rowH;
        int panelRight = panelX + panelW;
        int panelBottom = panelY + panelH;
        guiGraphics.fill(panelX, panelY, panelRight, panelBottom, 0xE0101014);

        int textX = panelX + 8;
        int y = panelY + 8;
        float scale = 0.7F;
        int rowIndex = 0;
        drawInfoRow(guiGraphics, textX, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.skin_style",
                Component.translatable("gui.csgobox.csgo_box.style." + SKIN_STYLES[this.skinStyleIndex]));
        drawInfoRow(guiGraphics, textX, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.skin_id",
                Component.literal(String.valueOf(this.skinId)));
        drawInfoRow(guiGraphics, textX, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.pattern",
                Component.literal(String.valueOf(this.patternSeed)));
        drawInfoRow(guiGraphics, textX, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.wear_rating",
                Component.literal(formatWear()));
        drawInfoRow(guiGraphics, textX, y + rowIndex * rowH, scale,
                "gui.csgobox.csgo_box.info.exterior",
                Component.translatable(wearTierKey()));
    }

    private void drawInfoRow(GuiGraphicsExtractor guiGraphics, int x, int y, float scale,
                             String labelKey, Component value) {
        renderText(guiGraphics, Component.translatable(labelKey, value).getVisualOrderText(),
                x, y, scale, 0xFFCCCCCC);
    }
```

- [ ] **Step 3: 残留检查 + clean 编译**

Run: `grep -n "statTrak\|valueColor\|0x40FFFFFF" v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsLookItemScreen.java`
Expected: 无输出
Run: `./gradlew :v26_1_2:compileJava -Pactive_versions=26.1.2 --rerun-tasks`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 运行 PlatformSmokeTest（可选但推荐）**

Run: `./gradlew :v26_1_2:test -Pactive_versions=26.1.2 --rerun-tasks`
Expected: `BUILD SUCCESSFUL`，`PlatformSmokeTest` 通过

---

### Task 4: v1_21_11 + v26_2 合入

**Files:**
- Modify: `v1_21_11/src/main/java/com/reclizer/csgobox/v1_21_11/gui/CsLookItemScreen.java:225-262`
- Modify: `v26_2/src/main/java/com/reclizer/csgobox/v26_2/gui/CsLookItemScreen.java:222-274`

**Interfaces:**
- Consumes: Task 3 的范本（两平台信息面板区域与 v26_1_2 结构一致：`GuiGraphicsExtractor` 类型、`drawInfoRow(guiGraphics, textX, panelRight - 8, ...)` 旧签名）。
- Produces: 全部 9 平台信息面板同构完成。

- [ ] **Step 1: 应用 Task 3 的替换到 v1_21_11 与 v26_2**

两平台分别执行 Task 3 的 Step 1-2（字段/构造器/方法体替换，签名均为 `GuiGraphicsExtractor`）。替换前用 `grep -n "renderInfoPanel\|statTrak" <file>` 定位实际行号。

- [ ] **Step 2: 残留检查**

Run: `grep -n "statTrak\|valueColor\|0x40FFFFFF" v1_21_11/src/main/java/com/reclizer/csgobox/v1_21_11/gui/CsLookItemScreen.java v26_2/src/main/java/com/reclizer/csgobox/v26_2/gui/CsLookItemScreen.java`
Expected: 无输出

- [ ] **Step 3: clean 编译**

Run: `./gradlew :v1_21_11:compileJava -Pactive_versions=21.11 --rerun-tasks`
Run: `./gradlew :v26_2:compileJava -Pactive_versions=26.2 --rerun-tasks`
Expected: 全部 `BUILD SUCCESSFUL`

---

### Task 5: 全量一致性核对 + 收尾

**Files:**
- Modify: 无（只读核对）
- 可选修改：`docs/superpowers/plans/` 无需改动

- [ ] **Step 1: 9 平台信息面板 diff 一致性核对**

Run: `for m in v1_21_1 v1_21_3 v1_21_4 v1_21_5 v1_21_8 v1_21_10; do grep -c "0xFFCCCCCC\|lineCount = 5" $m/src/main/java/com/reclizer/csgobox/$m/gui/CsLookItemScreen.java; done`
Expected: 每平台输出 2（`0xFFCCCCCC` 与 `lineCount = 5` 各一处）
Run: `for m in v1_21_11 v26_1_2 v26_2; do grep -c "0xFFCCCCCC\|lineCount = 5" $m/src/main/java/com/reclizer/csgobox/$m/gui/CsLookItemScreen.java; done`
Expected: 每平台输出 2

- [ ] **Step 2: 核对 lang 无需改动**

Run: `grep -n "stattrak" common/src/main/resources/assets/csgobox/lang/zh_cn.json common/src/main/resources/assets/csgobox/lang/en_us.json`
Expected: 若有 `gui.csgobox.csgo_box.info.stattrak` 键——保留（lang 键删除非本计划范围，删除会导致其它引用报错风险，留待后续清理）；本计划不改 lang。

- [ ] **Step 3: 汇总改动清单**

列出 9 个被修改的 `CsLookItemScreen.java` 及改动摘要（每文件：删 2 字段、删 2 行构造器、重写 1 方法、替换 1 方法、删 4 行描边、删 StatTrak 分支）。不提交 git（用户未要求）。

## 自审记录

- **Spec 覆盖**：设计文档 4 节均有任务对应——§1 renderInfoPanel/drawInfoRow（Task 1/3）、§2 StatTrak 删除（Task 1/3 Step 1-2）、§3 数值格式不变（所有任务未触碰）、§4 平台合入（Task 2/4）、测试（各 Task 编译步骤）。
- **占位符扫描**：所有步骤含完整代码块与可执行命令；无 TBD/待定项。
- **类型一致性**：Task 1 与 Task 2 用 `GuiGraphics`，Task 3/4 用 `GuiGraphicsExtractor`；`drawInfoRow` 新签名全计划一致（guiGraphics, x, y, scale, labelKey, value）；无旧签名残留。
