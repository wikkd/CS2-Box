# Task 2 Report: 卡片状态标签 + 系统文案玩家名 + 接受按钮价格

**Status:** DONE

## 实现内容

### Step 1 — lang（common，三平台共享）
- `zh_cn.json` / `en_us.json` 各 6 键，全部按简报 verbatim：
  - `csgobox.terminal.accept` → `接受报价 %s` / `Accept offer %s`（`sys.failed` 无 %s 保留原样）
  - `csgobox.terminal.sys.accepted` → `%s已接受报价。` / `%s accepted the offer.`
  - `csgobox.terminal.sys.rejected` → `%s已拒绝报价。` / `%s rejected the offer.`
  - 新增 `csgobox.terminal.card.rejected` = ` - 已拒绝` / ` - Rejected`
  - 新增 `csgobox.terminal.card.accepted` = ` - 已接受` / ` - Accepted`
  - 新增 `csgobox.terminal.name` = `毁灭之手终端机` / `Hand of Ruin Terminal`
- 两个文件 JSON 语法校验通过（python3 json.load）。

### Step 2/3 — TerminalChatRegion ×3 平台（同编辑，基准 v26_1_2 → 定点合入 v26_2 / v1_21_1）
1. `drawOfferCard` head 构建处：`oe.status()` == `OFFER_REJECTED`/`OFFER_ACCEPTED` 时追加 `card.rejected`/`card.accepted` 后缀（`OfferEntry.status()` 与常量均来自 Task 1 基线）。
2. 头部行渲染：`row(...)` 调用替换为 `RenderFontTool.drawStringClamped(gg, font, head, ix, iy, 0, 0, 1.5F, CARD_W - 124 - 8, ...)`（防溢出，颜色 `finalRound ? dimColor : RARITY_TEXT`）。name/wear/price 三行保留 `row(...)`。
3. 新增 `private static String sysText(NegotiationModel.SystemEntry se)`（`Minecraft.getInstance().player`，null 时用 `"?"`，`Component.translatable(se.textKey(), playerName)`），替换 `entryHeight` 与 `drawSystem` 两处的 `Component.translatable(se.textKey()).getString()`。`Player` 用 FQN，无新增 import，三平台零差异。

## 编译证据（全部 clean 编译）
- `./gradlew :v26_1_2:clean :v26_1_2:compileJava -Pactive_versions=26.1.2` → **BUILD SUCCESSFUL**（52s）
- `./gradlew :v26_2:clean :v26_2:compileJava -Pactive_versions=26.2` → **BUILD SUCCESSFUL**（43s）
- `./gradlew :v1_21_1:clean :v1_21_1:compileJava -Pactive_versions=1.21.1` → **BUILD SUCCESSFUL**（23s）

## 改动文件（提交 cfa0a86，5 文件）
- `common/src/main/resources/assets/csgobox/lang/zh_cn.json`
- `common/src/main/resources/assets/csgobox/lang/en_us.json`
- `v26_1_2/.../gui/terminal/TerminalChatRegion.java`
- `v26_2/.../gui/terminal/TerminalChatRegion.java`
- `v1_21_1/.../gui/terminal/TerminalChatRegion.java`

提交文件级限定，wiki/ 与 IDE 文件未被吞并（`git status` 验证）。

## 自审发现
- 三平台 diff 逐字节一致（除文件头与既有的 `GuiGraphicsExtractor`/`GuiGraphics` API 差异），镜像纪律满足；未用整文件覆盖 v26_2。
- `csgobox.terminal.accept` 现有调用方 `TerminalActionBar:113` 已传 `"¥" + model.offerPrice()` 作为 %s —— 键值与调用方天然吻合，无需改调用方。
- `drawStringClamped` 三平台签名已确认（`(gg, font, text, x, y, ox, oy, scale, maxPixelWidth, color)`，String 重载），调用参数顺序正确。
- LSP 曾报一次 `sysText undefined`，为编辑顺序造成的瞬时状态（用法先于助手定义落盘），第四次编辑后消失，clean 编译佐证。

## Concerns
- 头部行换用 `drawStringClamped` 后**不再参与 row() 的 flip-in 动画**（flipAlpha/flipSlideY 丢失）——这是简报 Step 2 的逐字指令，判定为有意为之（按简报执行），但在 HTML parity 视角下 head 行会即刻显形而其余 3 行仍延迟滑入，若后续发现视觉突兀可在 Task 反馈。另外 clamp 宽度 `CARD_W - 124 - 8` 硬编码与既有 26.x 布局一致。

---

## 自审修复（commit 385afed）：恢复 head 行 flip-in 动画

### 问题
Task 2 中 head 行用裸 `drawStringClamped` 替换了 `row(...)`，丢失 flip-in（alpha + slide）动画；其余 3 行仍走 `row(...)`，与 HTML 原型 4 行全翻转不一致。

### 修复（三平台 drawOfferCard head 行，基准 v26_1_2 → 定点合入 v26_2 / v1_21_1）
裸 clamp 调用替换为 alpha/slide + clamp 组合（与 `row(...)` 同计时源 `TerminalAnims.flipAlpha/flipSlideY`，stagger 0）：
- `headAlpha > 0F` 时渲染（与 `row()` 一致的提前裁剪）
- `alphaColor = (headColor & 0x00FFFFFF) | (round(255 * min(1F, headAlpha)) << 24)`——headColor 原为全 alpha（`finalRound ? dimColor : RARITY_TEXT`），alpha 调制安全
- clamp 宽度 `CARD_W - 124 - 8`、scale 1.5F、防溢出行为均保留
- `TerminalAnims` 三平台已有 import（`com.reclizer.csgobox.terminal.TerminalAnims`），未新增

### 编译证据（全部 clean）
- `:v26_1_2:clean :v26_1_2:compileJava -Pactive_versions=26.1.2` → **BUILD SUCCESSFUL**（41s）
- `:v26_2:clean :v26_2:compileJava -Pactive_versions=26.2` → **BUILD SUCCESSFUL**（55s）
- `:v1_21_1:clean :v1_21_1:compileJava -Pactive_versions=1.21.1` → **BUILD SUCCESSFUL**（30s）

### 改动文件（提交 385afed，3 文件，文件级限定）
- `v26_1_2/.../gui/terminal/TerminalChatRegion.java`
- `v26_2/.../gui/terminal/TerminalChatRegion.java`
- `v1_21_1/.../gui/terminal/TerminalChatRegion.java`

三平台 diff 逐字节一致（除文件头与既有 API 差异），镜像纪律满足；工作区 wiki/ 等未提交文件未被吞并。

### 自审
- 视觉行为：head 行现在与 name/wear/price 同源翻转（stagger 0 先行），动画中途 clamp 依旧生效（drawStringClamped 在 flip 结束时截断）。
- 潜在残余差异：head 行走 drawStringClamped 而非 row() 的 drawString——仅在超宽文本时行为不同（clamp 截断 vs 溢出），符合本任务目标。
