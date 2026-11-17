# Final Whole-Branch Review — Fix Report (Important findings ×2)

- Date: 2026-08-10
- Base: be96205 (task/terminal-t7 merged)
- Commit: `7c1ea6f` fix(terminal): armed reject border not overridden by hover; widen en head clamp
- Scope: 6 files (TerminalActionBar / TerminalChatRegion × v1_21_1, v26_1_2, v26_2)

## Fix 1: Armed 红框被 hover 白框覆盖（TerminalActionBar ×3）

`drawCapsule` 中按下拒绝键时鼠标必然悬停，原 `if (hover && !busy && enabled)` 使白框无条件覆盖 reject 的 armed 红框。改为按下时不覆盖：

```java
if (hover && !pressing && !busy && enabled) {
    borderC = 0xFFFFFFFF;
}
```

- 3 平台同构，每平台仅该行一处修改（2 行 diff）。
- `pressing` 参数在 drawCapsule 签名中已存在（供 fill 进度使用），无需改签名。

## Fix 2: en 语言卡片状态标签被 clamp 截断（TerminalChatRegion ×3）

原 head 行 clamp 宽度 `CARD_W - 124 - 8`（=130px @ scale 1.5）放不下 en 的 "Round 1 Offer - Rejected"（约 155px+），被截成 "Round 1 Offer…"，en 状态标签不可见。修复为把聊天列可用宽度传入 drawOfferCard：

1. `drawEntry` 调用处：`drawOfferCard(gg, x, y, nowMs, oe)` → `drawOfferCard(gg, x, y, availW, nowMs, oe)`
2. 签名加 `int availW` 参数
3. head 行 clamp：`CARD_W - 124 - 8` → `availW - 124 - 8`

其余（name/wear/price 三行 row()、flip-in alpha 调制、卡片宽度 CARD_W）未动。availW 即 `x1 - x0 - 16`（列内宽），列右侧有大量空闲空白，允许溢出到列右缘，与未 clamp 时代码行为一致。

## 平台镜像纪律执行

- v26_1_2 基准先改先编译；v26_2 / v1_21_1 定点合入（同内容逐处 edit，无整文件覆盖）。
- 改动前后 diff 抽查：三平台 hunk 逐行一致（仅 v1_21_1 的 `GuiGraphics` 与 26.x 的 `GuiGraphicsExtractor` 类型名差异，属既有适配）。

## 编译证据（clean 编译，杜绝增量缓存假象）

| 平台 | 命令 | 结果 |
|---|---|---|
| v26_1_2 | `./gradlew :v26_1_2:clean :v26_1_2:compileJava -Pactive_versions=26.1.2` | BUILD SUCCESSFUL (47s) |
| v26_2 | `./gradlew :v26_2:clean :v26_2:compileJava -Pactive_versions=26.2` | BUILD SUCCESSFUL (45s) |
| v1_21_1 | `./gradlew :v1_21_1:clean :v1_21_1:compileJava -Pactive_versions=1.21.1` | BUILD SUCCESSFUL (25s) |

## 提交与工作区卫生

- 单 commit `7c1ea6f`，`git show --stat` 确认恰 6 个目标文件（15+/15-），无其他文件混入。
- 初始 diff 中见到的 CsgoBox.java ×3（+14）在工作区并存——已查明系并发会话于 20:47:59 先行提交为 `5dab064`（fix(registry) duplicate registration），本 commit 父为 5dab064，未吞并、未包含。
- wiki/ 等其他未提交文件保持原状，未纳入本 commit。

## 自审

- Fix 1 逻辑核对：reject 按下 → `pressing=true` → 白框不再覆盖 `HOLD_REJECT` armed 红框；hover 但未按下仍显示白框（预期）；busy 时 border 仍为 0x33FFFFFF 半透明（不受影响）。
- Fix 2 参数传递核对：drawEntry 已有 `availW` 形参（原仅用于 drawLine/drawSystem），传入 drawOfferCard 后 `availW - 124 - 8` 恒 ≥ 原 `CARD_W - 124 - 8`（列宽 > 卡片宽 + 16 padding），en/zh 均不再截断，中文本无变化。
- 未改动 AnimRenderOps / 公共 API，无需跑 check-animops-drift.sh；common 未触碰。
