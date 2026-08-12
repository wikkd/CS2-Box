# 终端机演示原型对齐（terminal-chat.html → 模组）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `design/terminal-chat.html` 与模组终端机的 4 项核心缺失 + 行为/视觉偏差全部对齐。

**Architecture:** 改动分两层 —— `common/`（纯 Java：模型/调色板/测试，一次改动三平台生效）与 `v26_1_2`（基准模块，先改+编译验证）→ 手工定点合入 `v26_2`（decoupled）与 `v1_21_1`（legacy）。

**Tech Stack:** Java 21/25, NeoForge 26.1.2/26.2/21.x, JUnit 5（common 测试）, Python 3 资源生成脚本。

**基线注意：** 执行基线 = `911b744`（并行 WIP 排版打磨已并入：ROW_H 19 / BUBBLE_SCALE 1.625 / drawSpacedText / clamp 气泡文本）。**实现者必须按内容定位代码区域，本计划中的行号仅供参考。**

## 全局约束

- **CONSTRAINT-001**：`common/` 不得 import `net.minecraft.*`/`net.neoforged.*`。`:common:checkCommonArchitecture` 自动拦截。
- **镜像纪律**：基准 `v26_1_2` 先改先编译；`v26_2`/`v1_21_1` 定点合入（禁止整文件覆盖 v26_2）。改动涉及平台时**必须 `clean` 编译**。
- **平台 API 差异速查**：
  | 差异点 | v26_1_2 / v26_2 | v1_21_1 (legacy) |
  |---|---|---|
  | 资源 ID | `Identifier.parse(...)` | `ResourceLocation.fromNamespaceAndPath(...)` |
  | 渲染上下文 | `GuiGraphicsExtractor` | `GuiGraphics` |
  | Screen 输入 | `mouseScrolled(double×4)`（同签名） | `mouseScrolled(double×4)`（同签名） |
  | 开屏 | v26_1_2/v1_21_1 `mc.setScreen(...)`；v26_2 `setScreenAndShow(...)` |
- 新增 lang key 须 zh_cn + en_us 双写。
- 每个任务独立可验证、独立提交。

## 文件映射

| 任务 | common（一次改） | v26_1_2 基准 | v26_2 合入 | v1_21_1 合入 |
|---|---|---|---|---|
| 1 模型 | `NegotiationModel.java` + 新 `NegotiationModelTest.java` | — | — | — |
| 2 卡片标签/系统文案 | `zh_cn.json`/`en_us.json` | `TerminalChatRegion.java` | 同 | 同 |
| 3 胶囊填充 | `TerminalPalette.java` | `TerminalActionBar.java` | 同 | 同 |
| 4 聊天滚动 | — | `TerminalChatRegion.java` + `TerminalScreen.java` | 同 | 同 |
| 5 水印 | — | `TerminalChatRegion.java` | 同 | 同 |
| 6 底行三件套 | `TerminalAssetsTest.java` + `scripts/gen-terminal-assets.py` + `TerminalPalette.java` | `TerminalBottomRow.java` | 同 | 同 |
| 7 中央物品接箱池 | — | `TerminalScreen.java` + `ClickEvent.java` + `TerminalOfferRegion.java` | 同 | 同 |

---

### Task 1: OfferEntry 状态 + 时序对齐 + 模型测试（TDD）

**Files:**
- Modify: `common/src/main/java/com/reclizer/csgobox/terminal/NegotiationModel.java`
- Create: `common/src/test/java/com/reclizer/csgobox/terminal/NegotiationModelTest.java`

**决策（用户已确认）：** 拒绝→下一轮 450ms（对齐 HTML）；接受→立即成交；卡片带状态标签。

- [ ] **Step 1: 先写失败测试**（TDD 红）— 测试文件内容：
```java
package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class NegotiationModelTest {

    private static NegotiationModel fresh() {
        NegotiationModel m = new NegotiationModel();
        m.start(100_000L);
        return m;
    }

    @Test
    @DisplayName("start -> TYPING round 1, tick -> PENDING + OfferEntry")
    void startAndPending() {
        NegotiationModel m = fresh();
        assertEquals(NegotiationModel.Status.TYPING, m.status());
        assertEquals(1, m.round());
        assertInstanceOf(NegotiationModel.LineEntry.class, m.history().get(0));
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        assertEquals(NegotiationModel.Status.PENDING, m.status());
        assertNotNull(m.pending());
        assertInstanceOf(NegotiationModel.OfferEntry.class, m.history().get(1));
        NegotiationModel.OfferEntry oe = (NegotiationModel.OfferEntry) m.history().get(1);
        assertEquals(NegotiationModel.OFFER_PENDING, oe.status());
        assertEquals(0.1139F, oe.offer().wearVal(), 1e-6F);
    }

    @Test
    @DisplayName("accept: PENDING -> ACCEPT_BUSY -> CLOSED next tick, card tagged ACCEPTED")
    void acceptFlow() {
        NegotiationModel m = fresh();
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        m.acceptNow(200_000L);
        assertEquals(NegotiationModel.Status.ACCEPT_BUSY, m.status());
        assertEquals(NegotiationModel.OFFER_ACCEPTED, lastOfferEntry(m).status());
        m.tick(200_001L);
        assertEquals(NegotiationModel.Status.CLOSED, m.status());
    }

    @Test
    @DisplayName("accept during TYPING is ignored")
    void acceptWhileTypingIgnored() {
        NegotiationModel m = fresh();
        m.acceptNow(150_000L);
        assertEquals(NegotiationModel.Status.TYPING, m.status());
    }

    @Test
    @DisplayName("reject: card tagged REJECTED, next round after 450ms")
    void rejectFlow() {
        NegotiationModel m = fresh();
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        m.rejectNow(200_000L);
        assertEquals(NegotiationModel.Status.REJECT_BUSY, m.status());
        assertEquals(NegotiationModel.OFFER_REJECTED, lastOfferEntry(m).status());
        m.tick(200_000L + NegotiationModel.REJECT_BUSY_MS);
        assertEquals(2, m.round());
        assertEquals(NegotiationModel.Status.TYPING, m.status());
    }

    @Test
    @DisplayName("round 5 reject -> FAILED + failed system entry")
    void finalRejectFails() {
        NegotiationModel m = fresh();
        for (int r = 1; r <= 5; r++) {
            m.tick(100_000L + r * 500_000L + NegotiationModel.TYPING_MS);
            m.rejectNow(100_000L + r * 500_000L + 1L);
            m.tick(100_000L + r * 500_000L + 1L + NegotiationModel.REJECT_BUSY_MS);
        }
        assertEquals(NegotiationModel.Status.FAILED, m.status());
        assertInstanceOf(NegotiationModel.SystemEntry.class,
                m.history().get(m.history().size() - 1));
    }

    @Test
    @DisplayName("timing constants aligned with HTML")
    void timings() {
        assertEquals(450L, NegotiationModel.REJECT_BUSY_MS);
        assertEquals(0L, NegotiationModel.ACCEPT_BUSY_MS);
    }

    @Test
    @DisplayName("countdown ticks down per second")
    void countdown() {
        NegotiationModel m = fresh();
        m.tick(103_000L);
        assertEquals(NegotiationModel.COUNT_INITIAL_MS - 3_000L, m.countdownMs());
    }

    private static NegotiationModel.OfferEntry lastOfferEntry(NegotiationModel m) {
        for (int i = m.history().size() - 1; i >= 0; i--) {
            if (m.history().get(i) instanceof NegotiationModel.OfferEntry oe) {
                return oe;
            }
        }
        throw new AssertionError("no offer entry");
    }
}
```
- [ ] **Step 2: 运行验证红**：`./gradlew :common:test -Pactive_versions=26.1.2`（编译失败即可）
- [ ] **Step 3: 实现模型**（`NegotiationModel.java`）：
  - 新增：`public static final int OFFER_PENDING = 0, OFFER_REJECTED = 1, OFFER_ACCEPTED = 2;`
  - `OfferEntry` 加第三字段：`public record OfferEntry(Offer offer, long atMs, int status) {}`
  - `becomePending`：`history.add(new OfferEntry(offer, nowMs, OFFER_PENDING));`
  - 新增私有方法 + 两处调用：
```java
private void markOfferStatus(int status) {
    for (int i = history.size() - 1; i >= 0; i--) {
        if (history.get(i) instanceof OfferEntry oe) {
            history.set(i, new OfferEntry(oe.offer(), oe.atMs(), status));
            return;
        }
    }
}
// acceptNow() 内、状态改完后：markOfferStatus(OFFER_ACCEPTED);
// rejectNow() 内、状态改完后：markOfferStatus(OFFER_REJECTED);
```
  - 时序：`REJECT_BUSY_MS = 450L;`、`ACCEPT_BUSY_MS = 0L;`；删除无引用的 `NEXT_ROUND_MS`（已确认全仓无使用）。
  - 更新类 javadoc 状态机（acceptNow → CLOSED 下一 tick；rejectNow → REJECT_BUSY --(450ms)--> round<5 ? TYPING : FAILED）。
- [ ] **Step 4: 运行验证绿**：`./gradlew :common:test -Pactive_versions=26.1.2`
- [ ] **Step 5: 提交**：`feat(terminal): offer card status + HTML-parity timings (reject 450ms, accept instant)`

### Task 2: 卡片状态标签 + 系统文案带玩家名 + 接受按钮价格

**Files:**
- Modify: `common/src/main/resources/assets/csgobox/lang/zh_cn.json`、`en_us.json`
- Modify: `v26_1_2|v26_2|v1_21_1/src/main/java/com/reclizer/csgobox/{v26_1_2|v26_2|v1_21_1}/gui/terminal/TerminalChatRegion.java`

- [ ] **Step 1: lang 修改**（zh_cn.json 的 `csgobox.terminal.accept`/`sys.accepted`/`sys.rejected` 键与新增键；en_us.json 同键）：
```jsonc
// zh_cn.json
"csgobox.terminal.accept": "接受报价 %s",        // 原 "接受"
"csgobox.terminal.sys.accepted": "%s已接受报价。", // 原 "已成交"
"csgobox.terminal.sys.rejected": "%s已拒绝报价。", // 原 "已拒绝"
"csgobox.terminal.card.rejected": " - 已拒绝",   // 新增
"csgobox.terminal.card.accepted": " - 已接受",   // 新增
"csgobox.terminal.name": "毁灭之手终端机",        // 新增（区域 11，Task 6 使用）
// en_us.json
"csgobox.terminal.accept": "Accept offer %s",
"csgobox.terminal.sys.accepted": "%s accepted the offer.",
"csgobox.terminal.sys.rejected": "%s rejected the offer.",
"csgobox.terminal.card.rejected": " - Rejected",
"csgobox.terminal.card.accepted": " - Accepted",
"csgobox.terminal.name": "Hand of Ruin Terminal",
```
（`sys.failed` 无 %s 保留原样——多传参数安全。）
- [ ] **Step 2: v26_1_2 基准 `TerminalChatRegion.java`**（按内容定位）：
  - `drawOfferCard` 头部行（`head` 构建处）加状态后缀：
```java
if (oe.status() == NegotiationModel.OFFER_REJECTED) {
    head += Component.translatable("csgobox.terminal.card.rejected").getString();
} else if (oe.status() == NegotiationModel.OFFER_ACCEPTED) {
    head += Component.translatable("csgobox.terminal.card.accepted").getString();
}
```
  - 头部行渲染改用防溢出 clamp（`RenderFontTool.drawStringClamped(gg, font, head, ix, iy, 0, 0, 1.5F, CARD_W - 124 - 8, finalRound ? dimColor : TerminalPalette.RARITY_TEXT)`）——替换该行的 `row(...)` 调用（现 scale 1.5F，WIP 已拆行；name/wear/price 三行保留 `row(...)`）。
  - 系统消息玩家名：新增助手并替换 entryHeight 与 drawSystem 两处的 `Component.translatable(se.textKey()).getString()`：
```java
private static String sysText(NegotiationModel.SystemEntry se) {
    net.minecraft.world.entity.player.Player p = Minecraft.getInstance().player;
    return Component.translatable(se.textKey(),
            p == null ? "?" : p.getName().getString()).getString();
}
```
- [ ] **Step 3: v26_2 / v1_21_1 同两处编辑**（`drawStringClamped` 三平台均已有——WIP 已引入）
- [ ] **Step 4: 三平台 clean 编译**（26.1.2 / 26.2 / 1.21.1，`./gradlew :<mod>:clean :<mod>:compileJava -Pactive_versions=<v>`）
- [ ] **Step 5: 提交**：`feat(terminal): offer-card status tags + player name in system lines + accept button price`

### Task 3: 胶囊填充实色 + 拒绝按钮 armed 红框

**Files:**
- Modify: `common/src/main/java/com/reclizer/csgobox/terminal/TerminalPalette.java`
- Modify: `{3 平台}/.../gui/terminal/TerminalActionBar.java`

- [ ] **Step 1: TerminalPalette**：`HOLD_ACCEPT = 0xFF398A46;`、`HOLD_REJECT = 0xFFB03434;`（HTML `--accept`/`--reject-red`，替换现 0xFF3ECF6E/0xFFC96A5F）
- [ ] **Step 2: v26_1_2 `TerminalActionBar.java`**（按内容定位）：
  - 接受胶囊调用：`PILL_GREEN_FILL` → `HOLD_ACCEPT`
  - 拒绝胶囊调用：`PILL_GRAY_FILL` → `HOLD_REJECT`；border 参数按下态红框：
```java
int rejectBorder = pressPill == Pill.REJECT
        ? TerminalPalette.HOLD_REJECT : TerminalPalette.PILL_GRAY_BORDER;
```
  - `drawCapsule` 无需改（fill/border 已是参数）。
- [ ] **Step 3/4: v26_2 + v1_21_1 合入、三平台 clean 编译**
- [ ] **Step 5: 提交**：`feat(terminal): solid green/red capsule fills + armed red reject border`

### Task 4: 聊天区滚轮滚动

**Files:**
- Modify: `{3 平台}/.../gui/terminal/TerminalChatRegion.java` + `{3 平台}/.../gui/TerminalScreen.java`

- [ ] **Step 1: v26_1_2 `TerminalChatRegion`**（按内容定位 render 循环）：
  - 字段：`private int scrollOffset; private int maxScroll;`
  - 公开方法：
```java
/** 滚轮：scrollY>0 = 上滚（看更早）。范围由下一次 render 钳制。 */
public void scrolled(double scrollY) {
    scrollOffset += (int) Math.round(scrollY * 20);
}
```
  - `render()` 重构（替换现有"从底向上遇界 break"的循环）。**修订记录（评审 Critical）**：初版锚定公式 `y1-4-scrollOffset` 方向反转——只会把最新窗口上移缩小、更早条目永远不可见；`pinned = scrollOffset >= maxScroll` 语义也相反。正确实现（已合入 257f54f+b8111ce）：
```java
        // chat stream: newest at the bottom, only the visible window;
        // scrollOffset = 从最新条目回退的像素数（>0 表示滚回看更早）
        List<Object> entries = model.history();
        int start = Math.max(0, entries.size() - MAX_ENTRIES);
        int viewportH = y1 - bodyTop - 4;
        boolean pinned = scrollOffset <= 0;
        int totalH = 0;
        for (int i = entries.size() - 1; i >= start; i--) {
            totalH += entryHeight(gg, entries.get(i), x1 - x0 - 24) + GAP;
        }
        maxScroll = Math.max(0, totalH - viewportH);
        if (pinned) {
            scrollOffset = 0;
        } else {
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
        int bottom = y1 - 4 + scrollOffset;
        for (int i = entries.size() - 1; i >= start; i--) {
            Object e = entries.get(i);
            int h = entryHeight(gg, e, x1 - x0 - 24);
            if (bottom - h >= y1 - 4) { // 完全滚出面板底部：跳过但仍消耗高度
                bottom -= h + GAP;
                continue;
            }
            if (bottom - h < bodyTop + 2) {
                break;
            }
            drawEntry(gg, x0 + 8, bottom - h, x1 - x0 - 16, h, nowMs, model, e);
            bottom -= h + GAP;
        }
```
- [ ] **Step 2: v26_1_2 `TerminalScreen`**：`extractRenderState` 中聊天区矩形（`lx0..ly1`）存字段 `chatX0,chatY0,chatX1,chatY1`；新增：
```java
@Override
public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    if (mouseX >= chatX0 && mouseX <= chatX1 && mouseY >= chatY0 && mouseY <= chatY1) {
        chatRegion.scrolled(scrollY);
        return true;
    }
    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
}
```
- [ ] **Step 3/4: v26_2（同签名）、v1_21_1（同签名）合入 + 三平台 clean 编译**
- [ ] **Step 5: 提交**：`feat(terminal): scrollable chat history (wheel, auto-follow at bottom)`

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

### Task 7: 中央展示 = 终端箱池实际物品 3D 模型

**Files:**
- Modify: `{3 平台}/.../gui/TerminalScreen.java`、`{3 平台}/.../event/ClickEvent.java`、`{3 平台}/.../gui/terminal/TerminalOfferRegion.java`

**决策记录：** 终端机绑定 `csgobox:terminal` 箱定义（BoxDefaults 生成，池 = 真实 MC 物品）。中央物品 = 按报价稀有度映射箱等级（purple→grade4 钻石系、blue→grade3 黄金系），每轮随机取样一次并缓存；池为空回退铁剑。卡片仍显示虚构皮肤文案。

- [ ] **Step 1: v26_1_2 `TerminalScreen`**：构造器接栈并构建等级池：
```java
public TerminalScreen(ItemStack terminalStack) {
    super(Component.translatable("gui.csgobox.terminal.title"));
    this.model.start(System.currentTimeMillis());
    offerRegion.setGradePools(buildGradePools(terminalStack));
}

@SuppressWarnings("unchecked")
private static java.util.List<ItemStack>[] buildGradePools(ItemStack terminalStack) {
    java.util.List<ItemStack>[] pools = new java.util.List[6]; // index = gradeLevel 1..5
    ItemCsgoBox.getDefinition(terminalStack).ifPresent(def ->
            def.grades().forEach(g -> {
                int lvl = BoxDefinition.gradeLevel(g.id());
                if (lvl > 0 && lvl < pools.length) {
                    pools[lvl] = g.items();
                }
            }));
    return pools;
}
```
  新增 imports：`com.reclizer.csgobox.v26_1_2.box.BoxDefinition`、`com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox`、`net.minecraft.world.item.ItemStack`。
- [ ] **Step 2: v26_1_2 `ClickEvent`**：`new TerminalScreen()` → `new TerminalScreen(heldItem.copy())`（v26_2 同位置但 `setScreenAndShow`；v1_21_1 同 44 行 `setScreen`）
- [ ] **Step 3: v26_1_2 `TerminalOfferRegion`**（按内容定位）：
  - 字段：`private java.util.List<ItemStack>[] gradePools;`、`private final java.util.Map<Integer, ItemStack> roundItemCache = new java.util.HashMap<>();`、`private final java.util.Random itemRnd = new java.util.Random();`
  - 方法：
```java
public void setGradePools(java.util.List<ItemStack>[] pools) {
    this.gradePools = pools;
    this.roundItemCache.clear();
}

/** HTML rarity → box grade: purple=restricted(4), blue=mil_spec(3)。 */
private static int gradeForOffer(NegotiationModel.Offer offer) {
    return "purple".equals(NegotiationModel.SKIN_RARITY[offer.skinIdx()]) ? 4 : 3;
}

/** 每轮一次取样（缓存），等级为空时向下退级，全空回退铁剑。 */
private ItemStack offerItem(NegotiationModel.Offer offer) {
    return roundItemCache.computeIfAbsent(offer.round(), r -> {
        for (int g = gradeForOffer(offer); g >= 1; g--) {
            java.util.List<ItemStack> pool = gradePools != null && g < gradePools.length
                    ? gradePools[g] : null;
            if (pool != null && !pool.isEmpty()) {
                return pool.get(itemRnd.nextInt(pool.size())).copy();
            }
        }
        return new ItemStack(Items.IRON_SWORD);
    });
}
```
  - `render()` 物品块：`ItemStack stack = new ItemStack(Items.IRON_SWORD);` → `ItemStack stack = offerItem(offer);`
- [ ] **Step 4/5/6: v26_2、v1_21_1 合入**（v1_21_1 注意 `ResourceLocation`/`GuiGraphics` 与本任务无关，`ItemCsgoBox.getDefinition`/`BoxDefinition.gradeLevel` 同包存在）**+ 三平台 clean 编译**
- [ ] **Step 7: 提交**：`feat(terminal): center display shows real box-pool item 3D per rarity tier`

### Task 8: 全量验证 + 代码审查

- [ ] `./gradlew :common:test -Pactive_versions=26.1.2`
- [ ] 三平台 `clean compileJava`（26.1.2 / 26.2 / 1.21.1）
- [ ] `scripts/check-animops-drift.sh`（未新增 op，应通过）
- [ ] 运行时冒烟清单：①♞ 水印；②滚轮回看 + 底部自动跟随；③拒绝后" - 已拒绝"、450ms 下一轮；接受立即成交 + " - 已接受"；④系统行玩家名；⑤接受按钮"接受报价 ¥17.76"；⑥胶囊绿/红实色 + 拒绝按下红框；⑦中央真实 MC 3D 物品按轮次稳定、拖拽/自旋正常；⑧区域 11 "毁灭之手终端机"；⑨倒计时冒号变暗；⑩圆形徽章 2.5s 换卡
- [ ] docs/CODE-REVIEW.md 自查清单
- [ ] 提交：`chore(terminal): verification pass`（如无代码改动可跳过）
