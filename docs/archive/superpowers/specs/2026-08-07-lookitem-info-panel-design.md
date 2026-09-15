# CsLookItemScreen 信息面板重排设计 — 复刻 CS:GO 磨损信息界面

日期：2026-08-07
状态：已获用户批准（设计层面）

## 背景

用户提供一张 CS:GO 游戏内截图（264×330，黑底、无边框的 5 行文字区），要求模组内的「磨损度查看」页面完美复刻排版与文字内容。经本地视觉模型（qwen3-vl:4b / 8b-instruct）两次独立读取，截图内容一致：

1. `皮肤风格: 自定义手绘风`
2. `皮肤编号: 309`
3. `图案模板: 940`
4. `磨损率: 0.054425440`（9 位小数）
5. `外观: 崭新出厂`

关键发现：这 5 行文案与模组 `zh_cn.json` 现有翻译**完全一致**（`皮肤风格: %s` 等），磨损值 9 位小数也与现状 `%.9f` 一致。差异完全在**排版**。

## 现状 vs 目标

### 现状（9 平台一致，v1_21_1 为基准）

`CsLookItemScreen.renderInfoPanel()`（v1_21_1 line 193-233）：

- 半透明深色面板 `0xE0101014` + 4 条白色描边（top/bottom/left/right `0x40FFFFFF`）
- 每行 `drawInfoRow`：标签灰色 `0xFF9A9A9A` 渲染在左侧（左对齐），值白色 `0xFFFFFFFF` 渲染在 `panelRight - 8` 处（**右对齐**），字号 scale 0.7
- `lineCount = statTrak ? 6 : 5`；StatTrak 行（12% 概率）用橙色 `0xFFFF6A00` 显示击杀数
- 标签通过 `Component.translatable(labelKey)` 渲染——但 lang 字符串自带 `%s`（如 `皮肤风格: %s`），无参渲染时 `%s` 显示为空，实际界面呈现「皮肤风格: 」+ 右侧空值，与截图「皮肤风格: 自定义手绘风」单行不符

### 目标（截图样式）

- 无边框：保留暗色底 `0xE0101014`，删除 4 条白色描边
- 每行渲染为整条 `Component.translatable(key, value)`，**左对齐**于同一 X（`textX = panelX + 8`），统一浅灰白色 `0xFFCCCCCC`
- 固定 5 行，行距 `rowH = 13`、字号 scale 0.7 沿用
- **删除 StatTrak 行**（字段、生成逻辑、渲染一并移除）

## 设计

### 1. `renderInfoPanel()` 重写

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
    // 删除 4 条 0x40FFFFFF 描边

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
    // 删除 StatTrak 分支
}

private void drawInfoRow(GuiGraphics guiGraphics, int x, int y, float scale,
                         String labelKey, Component value) {
    renderText(guiGraphics, Component.translatable(labelKey, value).getVisualOrderText(),
            x, y, scale, 0xFFCCCCCC);
}
```

- 原双参/三参 `drawInfoRow`（右对齐值版本）删除，只保留整行版本
- 标签与值合并为单条 `Component.translatable(key, value)`——lang 已含 `: ` 与 `%s`（如 `磨损率: %s`），输出即「磨损率: 0.054425440」

### 2. 删除 StatTrak

- 字段 `private final boolean statTrak;`、`private final int statTrakKills;`
- 构造器 `this.statTrak = rnd.nextFloat() < 0.12F;`、`this.statTrakKills = rnd.nextInt(1, 500);`
- `renderInfoPanel` 中 `lineCount` 的 statTrak 三元表达式与第 6 行渲染分支
- `drawInfoRow` 的 `int valueColor` 重载（橙色）不再需要

### 3. 数值格式不变

- `formatWear()` `%.9f` 保留（9 位小数，与截图一致）
- `wearTierKey()` 分档（FN/MW/FT/WW/BS）保留
- 随机值范围保留（skinId `nextInt(100, 1301)`、patternSeed `nextInt(1000)`，截图 309/940 均在范围内）

### 4. 平台合入策略（AGENTS.md 镜像纪律）

文件是**既有文件且有适配差异**，禁止整文件覆盖，采用定点合入：

1. 基准：`v1_21_1` 先改（legacy 基准）
2. legacy 其余平台（v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10）：同构手工合入（无 API 差异，区域结构一致）
3. new 基准：`v26_1_2` 再改
4. v1_21_11 / v26_2：合入时注意 `GuiGraphicsExtractor`（26.x blit 管线差异）——`renderInfoPanel` 区域仅用 `fill` 与 `RenderFontTool.drawString`，若签名一致可同构合入
5. 每平台 `./gradlew :<module>:compileJava -Pactive_versions=<v> --rerun-tasks` 验证（改动涉及平台用 clean 编译确认，防增量缓存假象）

> 注：forge_26_1_2 为实验模块（本地 WIP 未提交，不参与镜像纪律），本改动**不覆盖**该模块；如该模块本地存在同类代码由用户在本地自行同步。

## 测试

- 无自动化 UI 测试框架（平台层仅 v26_1_2 有 PlatformSmokeTest，不涉及 GUI 渲染）
- 验证方式：每平台 clean `compileJava` 通过（架构约束 `checkCommonArchitecture` 自动挂载，不涉 common 无风险）
- 运行时人工验证（参考 docs/RELEASE.md 质量门）：打开 ⓘ 面板 → 确认 5 行左对齐、无描边、统一浅灰白、无 StatTrak 行；面板位置/行距/字号与改动前一致

## 不做的事（YAGNI）

- 不调整检视页整体背景/其它 UI（范围仅 ⓘ 信息面板）
- 不引入公共 UI 渲染抽象（common 无法 import MC 类型，CONSTRAINT-001）
- 不改随机值范围与磨损分级逻辑
