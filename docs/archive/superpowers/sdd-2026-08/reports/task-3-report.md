# Task 3 Report: 胶囊填充实色 + 拒绝按钮 armed 红框

**Status: DONE** — commit `fd9aa9e` on `task/terminal-t3`

## 改动

### 1. `common/.../terminal/TerminalPalette.java`（共享，一次改）
- `HOLD_ACCEPT = 0xFF3ECF6E` → `0xFF398A46`（HTML `--accept`）
- `HOLD_REJECT = 0xFFC96A5F` → `0xFFB03434`（HTML `--reject-red`）
- 纯数据常量，无 MC import，CONSTRAINT-001 不受影响。

### 2. `TerminalActionBar.java` ×3 平台（v26_1_2 基准 → v26_2 / v1_21_1 定点合入）
按内容定位（render() 中两处 drawCapsule 调用，行号已随排版打磨漂移）：
- accept 胶囊 fillColor：`PILL_GREEN_FILL` → `TerminalPalette.HOLD_ACCEPT`
- reject 胶囊 fillColor：`PILL_GRAY_FILL` → `TerminalPalette.HOLD_REJECT`；新增按下态红框：
  ```java
  int rejectBorder = pressPill == Pill.REJECT
          ? TerminalPalette.HOLD_REJECT : TerminalPalette.PILL_GRAY_BORDER;
  ```
  作为 border 参数传入（对应 HTML `.pill.reject.armed`）
- `drawCapsule` 内部未动（fill/border 本就为参数传入）

### 三平台一致性
- 三份 ActionBar 改动区域逐字节一致；全文件 diff 仅剩既有的 `GuiGraphics`(1.21.1) vs `GuiGraphicsExtractor`(26.x) 签名差异（未触碰）。

## 编译证据（均 clean + compileJava，BUILD SUCCESSFUL）
- `:v26_1_2` -Pactive_versions=26.1.2 ✓
- `:v26_2` -Pactive_versions=26.2 ✓
- `:v1_21_1` -Pactive_versions=1.21.1 ✓（首次失败：隔离 worktree 缺 `local-repo/com/tacz/`，从主工作区拷贝后通过——非代码问题）
- 三平台编译均自动挂载 `:common:checkCommonArchitecture`，通过。

## 自审
- 色值逐字节对照简报 ✓
- 行为核对：reject 未按下时 border 保持 `PILL_GRAY_BORDER`，按下（armed）时为 `HOLD_REJECT` 红框 ✓；disabled/busy 状态下 drawCapsule 内部覆盖 border 的既有逻辑不受影响 ✓
- 提交文件级限定 4 paths，未吞并无关文件 ✓

## Concerns
- 无。仅环境备注：worktree 需复制主工作区 `local-repo/`（gitignored）才能编译 v1_21_1；网络直拉 maven.neoforged.net 的 TACZ pom 失败（connection reset），故用本地拷贝。
