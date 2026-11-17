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

