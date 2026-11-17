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
  - `render()` 重构（替换现有"从底向上遇界 break"的循环）：
```java
List<Object> entries = model.history();
int start = Math.max(0, entries.size() - MAX_ENTRIES);
int viewportH = y1 - bodyTop - 4;
boolean pinned = scrollOffset >= maxScroll;
int totalH = 0;
for (int i = entries.size() - 1; i >= start; i--) {
    totalH += entryHeight(gg, entries.get(i), x1 - x0 - 24) + GAP;
}
maxScroll = Math.max(0, totalH - viewportH);
if (pinned) {
    scrollOffset = maxScroll;
} else {
    scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
}
int bottom = y1 - 4 - scrollOffset;
for (int i = entries.size() - 1; i >= start; i--) {
    Object e = entries.get(i);
    int h = entryHeight(gg, e, x1 - x0 - 24);
    if (bottom - h < bodyTop + 2) break;
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

