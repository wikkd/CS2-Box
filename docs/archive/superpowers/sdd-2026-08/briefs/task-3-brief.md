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

