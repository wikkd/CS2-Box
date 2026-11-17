# Task 1 Report: OfferEntry 状态 + 时序对齐 + 模型测试

## 实现内容

按 brief Step 3 修改 `common/src/main/java/com/reclizer/csgobox/terminal/NegotiationModel.java`：

- **OfferEntry 第三字段**：`public record OfferEntry(Offer offer, long atMs, int status) {}`
- **卡片状态常量**：`public static final int OFFER_PENDING = 0, OFFER_REJECTED = 1, OFFER_ACCEPTED = 2;`（置于 OfferEntry record 之后，新增 `// ---- offer card status ----` 分区）
- **becomePending**：`history.add(new OfferEntry(offer, nowMs, OFFER_PENDING));`
- **markOfferStatus(int)**（私有，倒序扫描 history 定位最近 OfferEntry 并替换）：
  - `acceptNow()` 状态改完后调用 `markOfferStatus(OFFER_ACCEPTED)`
  - `rejectNow()` 状态改完后调用 `markOfferStatus(OFFER_REJECTED)`
- **时序对齐 HTML**：`REJECT_BUSY_MS = 450L`、`ACCEPT_BUSY_MS = 0L`；删除 `NEXT_ROUND_MS`（全仓 grep 确认零引用，含 java/html/js/ts）
- **javadoc 状态机更新**：`ACCEPT_BUSY --(0ms)--> CLOSED (next tick)`；`REJECT_BUSY --(450ms)--> round<5 ? TYPING(round+1) : FAILED`

测试文件 `common/src/test/java/com/reclizer/csgobox/terminal/NegotiationModelTest.java` 按 brief Step 1 逐字替换（原文件为旧 900ms 时序测试，与新时序不兼容，如 `accept` 测试断言 tick(2899) 仍在 ACCEPT_BUSY——已被新文件取代）。

## 测试结果

- **RED**：写入测试文件后 `./gradlew :common:test -Pactive_versions=26.1.2` → BUILD FAILED，6 个编译错误（OFFER_PENDING/OFFER_ACCEPTED/OFFER_REJECTED 找不到符号 + OfferEntry.status() 未定义）——符合预期（编译失败亦可）。
- **GREEN**：实现后同命令 → BUILD SUCCESSFUL。`TEST-com.reclizer.csgobox.terminal.NegotiationModelTest.xml`：`tests="7" skipped="0" failures="0" errors="0"`，7 个用例全部通过。
- `:common:checkCommonArchitecture` 挂载在 compileJava 上，随编译通过，CONSTRAINT-001 无违例（未新增任何 import）。

## 改动文件

- `common/src/main/java/com/reclizer/csgobox/terminal/NegotiationModel.java`（修改）
- `common/src/test/java/com/reclizer/csgobox/terminal/NegotiationModelTest.java`（重写为 brief 内容）

## 自审发现

- **完整性**：brief Step 3 全部 6 项逐一落实，diff 与 brief 给出的代码逐字一致。
- **兼容性**：全仓 grep 确认无外部代码使用 2 参 `OfferEntry` 构造器；平台层 `TerminalOfferRegion.java:151` 仅用 `lastOfferEntry().atMs()`，不受影响；`NEXT_ROUND_MS` 零引用，删除安全。
- **纪律**：common 层无 MC/NeoForge import（仅既有 `java.util.*`）；提交按文件级 `-- <paths>` 限定，未吞并 wiki/ 与 IDE 文件（提交前 `git status` 已确认脏文件仅 wiki/ 与 IDE 配置）。
- **测试**：TDD 红→绿证据完整（RED 编译失败日志 + GREEN 7/7）。

## Concerns

- 旧 `NegotiationModelTest` 中针对 900ms busy 的断言（accept 需 2 tick 才 CLOSED 等）被新时序自然淘汰，属预期行为变更，无遗留问题。
- `ACCEPT_BUSY_MS = 0` 使 ACCEPT_BUSY 成为瞬时状态（下一 tick 即 CLOSED），若后续平台层 UI 依赖该状态做进场动画，需确认消费侧渲染逻辑（不在本任务范围，common 无消费方）。

## Review Fix (2026-08-10): Restore pre-existing coverage in NegotiationModelTest

提交 `0b1e945 test(terminal): restore pre-existing NegotiationModel coverage + strengthen finalRejectFails`（仅限该测试文件，文件级 `git add -- <path>`）。

### 变更内容

- 保留简报新增的 7 个测试（startAndPending / acceptFlow / acceptWhileTypingIgnored / rejectFlow / finalRejectFails / timings / countdown），全部原样保留。
- 从 `git show 911b744:.../NegotiationModelTest.java` 恢复旧测试独有覆盖并适配新时序：
  - `typingLock` — 恢复；魔法数 1100 改为 `NegotiationModel.TYPING_MS` 常量，tick(TYPING_MS-1) 仍 TYPING → tick(TYPING_MS) 进入 PENDING，断言 pending round/skinIdx。
  - `generation` — 恢复；start() 再次调用后 generation 递增、round 归 1、TYPING、pending 为 null，新定时（TYPING_MS 后 PENDING）生效。
  - `capAndCounter` — 恢复（cap 默认 CAP_UNLIMITED、setCap(64) 读回、counterLabel 三态 preparing/offer/done）。
  - `scriptData` — 恢复（LINES/ROUND_LINE/ROUND_SKIN/SKIN_NAME_KEYS/CAPS/SKIN_PRICE 长度与下标范围）。
  - `rejectAdvances` — 恢复并适配：900ms busy 魔法数改为 `REJECT_BUSY_MS`（450），增加 busy 边界（450-1 仍 REJECT_BUSY）与 round 2 line.1 / PENDING skinIdx=2（ROUND_SKIN）断言。
  - 旧 `accept` 测试的 offer 稳定性（assertSame(before, pending)）与 sys.accepted 历史断言并入 `acceptFlow`；旧 `start` 的 textKey/history size/pending-null 并入 `startAndPending`。
  - 旧 `countdown` 的 floor-at-zero 断言并入新 `countdown`（长跳清空后不再为负）。
- 加强 `finalRejectFails`：末条断言从仅 `assertInstanceOf(SystemEntry.class)` 改为同时 `assertTrue(((SystemEntry) last).failed())`。
- 丢弃的旧断言：无独有覆盖被丢。旧 900ms busy 专属时序（accept 后 2 tick 才 CLOSED）随 `REJECT_BUSY_MS=450` / `ACCEPT_BUSY_MS=0` 自然淘汰，由新 `timings` + 流式断言取代。
- 未改 `NegotiationModel.java`（测试未暴露模型 bug）。

### 验证

```
./gradlew :common:test -Pactive_versions=26.1.2
BUILD SUCCESSFUL in 7s
```

- NegotiationModelTest 12/12 通过（TEST-...NegotiationModelTest.xml: tests="12"），其余 common 测试（BoxJsonSchemaValidatorTest 24、EasingTest、TerminalAnimsTest 等）全绿。
