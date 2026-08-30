# 物品检视右键菜单（箱子/终端机预览页 → 3D 检视屏） Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在箱子预览页（CsboxScreen）与终端机预览页（TerminalScreen）支持 **右键物品 → 弹出含单一「检视」按钮的上下文菜单 → 点击后跳转到物品 3D 展示界面 → ESC 返回原预览页**。

**Architecture:** 新增每平台一个 `InspectMenu`（单按钮右键菜单组件），检视屏**复用现有开箱奖励 3D 展示屏 `CsLookItemScreen`**（新增 `previousScreen` 返回栈重载；设计迭代后放弃了独立的 `CsInspectItemScreen`——复用避免重复实现磨损面板/皮肤/拖拽/工具栏等全部展示能力）。菜单组件只用现有 `AnimRenderOps` 原语渲染（零新增原语，drift 守护不破）。右键命中与菜单交互在各屏内联实现。以 v26_1_2 为基准，其余 5 平台定点适配。

**Tech Stack:** NeoForge 21.x / 26.x / Forge 26.x / Forge 1.20.1、Java 21/25/17、JUnit 5（common）。

## Global Constraints

- common/ 不得 import `net.minecraft.*` / `net.neoforged.*`（`checkCommonArchitecture` 守护）
- 每次 Gradle 调用仅构建一个 MC 版本：`-Pactive_versions=1.21.1|26.1.2|26.2|forge-26.1.2|forge-26.2|forge-1.20.1`
- 禁止用 v26_1_2 整文件覆盖其它平台（API 差异：`setScreen`↔`setScreenAndShow`、`GuiGraphicsExtractor`↔`GuiGraphics`、`MouseButtonEvent`↔`int mouseButton`、`KeyEvent`↔`int keyCode` 等）
- 不新增 AnimRenderOps 原语（`scripts/check-animops-drift.sh` 守护，13 op 三平台一致）
- 新屏/新菜单组件**每平台一份**（`gui/` 或 `gui/terminal/`），复用 `AnimRenderOps`/`RenderFontTool`/`ButtonPalette`/`ColorTools` 原语
- 返回栈语义：**复用同一屏幕实例**（`setScreen(previousScreen)`），不重建——`CsboxScreen.init()` 为空、`TerminalScreen` 状态在实例字段中，重建会重发 `PacketTerminalOpen`（破坏会话续谈）。故 `CsInspectItemScreen` 持 `previousScreen` 引用，关屏时 `setScreen(previousScreen)`
- 工作目录：/Users/shuangyuexingxun/Desktop/CS2-Box（main 分支，惯例直接开发）
- commit 纪律：git add 只加本任务文件，禁止 `git add .` / `git add -A`

---

## Task 1: 设计定稿（本文件）

已定稿：
- **右键目标**：
  - `CsboxScreen`：2×10 物品网格单元（`renderPageGrid` 的 `listArea.x() + px*pctW(9)` / `pctH(py)` 命中框，含 grade>4 的「更多」占位不可右键）。
  - `TerminalScreen`：**当前报价物品**（region 8，`TerminalOfferRegion` 中心 `itemCx/itemCy` 圆域，半径 = `max(40, inspectW+80)`）。
- **菜单**：`InspectMenu` 组件——在命中点上方渲染一个含「检视」按钮的黑色圆角胶囊（`TerminalChatRegion.drawRounded`，0.7 缩放白字）；左键点击触发「检视」，右键/其它点击关闭菜单。单一按钮。
- **检视屏**：复用 `CsLookItemScreen(item, grade, previousScreen)`——原开箱奖励 3D 展示屏新增返回栈重载：`previousScreen != null` 时入场静默（不播放开箱完成音）、关屏 `setScreen(previousScreen)` 返回原预览页实例（不重建）。
- **lang**：新增 `gui.csgobox.inspect.open`（检视）中英键（复用现有 `toolbar.inspect` 文案风格）。

---

## Task 2: v26_1_2 基准实现

**Files:**
- Create: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsInspectItemScreen.java`
- Create: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/InspectMenu.java`
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxScreen.java`
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/TerminalScreen.java`
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/terminal/TerminalOfferRegion.java`（暴露 itemCx/itemCy 命中）
- Modify: `common/src/main/resources/assets/csgobox/lang/zh_cn.json` / `en_us.json`

**Interfaces:**
- `CsInspectItemScreen(item, grade, previousScreen)`：`keyPressed(256)→setScreen(previous)`；返回按钮同；`removed()` 不改 hideGui（本屏不隐藏 HUD）
- `InspectMenu`：`openAt(mx,my) / close() / isOpen()`、`render(gg,mx,my)`、`mouseClicked(btn,mx,my)→ HIT_INSPECT | HIT_NONE`；命中区在点击点上方 `menuW x menuH`
- `CsboxScreen.mouseClicked`：`button()==1` 时命中网格单元 → `menu.openAt`；菜单开时左键命中「检视」→ `setScreen(new CsInspectItemScreen(item, grade, this))`
- `TerminalScreen.mouseClicked`：`button()==1` 时命中报价物品圆域 → `menu.openAt`；同上跳转
- `TerminalOfferRegion`：新增 `public boolean hitItem(int mx,int my)` 暴露圆域命中（与 `mouseDown` 拖拽同半径）

- [x] **Step 1**: lang 键
- [x] **Step 2**: `CsInspectItemScreen`（3D 展示 + 返回栈）
- [x] **Step 3**: `InspectMenu` 组件
- [x] **Step 4**: `CsboxScreen` 右键接入
- [x] **Step 5**: `TerminalScreen` + `TerminalOfferRegion` 右键接入
- [x] **Step 6**: `./gradlew :v26_1_2:compileJava -Pactive_versions=26.1.2 --rerun-tasks` 通过；`:common:test` 通过

---

## Task 3: 镜像其余 5 平台 ✅

以 v26_1_2 为基准，逐平台定点适配（禁止整文件覆盖）：
- `v26_2`：`setScreen`→`setScreenAndShow`（若 26.2 用后者）、同 decoupled 渲染
- `forge_26_1_2` / `forge_26_2`：Forge 事件 API（`MouseButtonEvent`→Forge 等价）、`GuiGraphicsExtractor`→Forge 等价
- `v1_21_1` / `forge_1_20_1`：legacy `GuiGraphics`、`MouseButtonEvent`→`double mouseX/Y,int button`、`KeyEvent`→`int keyCode`、`ResourceLocation`（非 `Identifier`）
- 每个平台 `compileJava --rerun-tasks` 通过

**Files:** 每平台新增 `gui/InspectMenu.java`；改 `gui/CsboxScreen.java`、`gui/TerminalScreen.java`、`gui/terminal/TerminalOfferRegion.java`、`gui/CsLookItemScreen.java`（+previousScreen 返回栈）；lang 已共享（common）。设计迭代后 `CsInspectItemScreen` 已从全部 6 平台删除。

---

## Task 4: 收尾验证

- `./gradlew :common:test` 全绿
- 6 平台 `clean compileJava`（`-Pactive_versions=<v>` 逐平台）
- `scripts/check-animops-drift.sh` EXIT=0（未新增原语）
- 提交（逻辑分块：feat(inspect-menu) 6 平台 + lang）

---

## 验收标准

1. 箱子预览页右键任意网格物品 → 出现单按钮「检视」菜单；点击 → 进入该物品 3D 展示屏（可拖拽旋转、自转、显示档位色条与名称）；ESC / 返回按钮 → 回到箱子预览页（页面状态保留）。
2. 终端机预览页右键当前报价物品 → 同上；ESC 返回后终端会话不重发 open（复用实例）。
3. 6 平台编译通过；无新增 AnimRenderOps 原语；common 测试通过。
