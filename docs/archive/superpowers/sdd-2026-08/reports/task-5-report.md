# Task 5 Report: 聊天区 ♞ 水印（TerminalChatRegion ×3 平台）

## Status: DONE

## Commit
- `6e7f70d` feat(terminal): chess-knight chat watermark

## 改动
- 文件：`TerminalChatRegion.java` ×3（v26_1_2 / v26_2 / v1_21_1，`gui/terminal` 包）
- 插入位置：`render()` 中 `drawDotGrid(...)` 之后、`// title strip` 之前（按内容定位，未依赖行号；水印先于任何条目绘制，被条目自然覆盖）
- 内容：任务简报代码片段逐字合入，三平台逐字节一致（基线三文件除包名/import 外完全同构，diff 后 v26_1_2 vs v26_2 归一化差异为 0）

## API 偏差（唯一一项）
- 简报片段用 `f.height`，但**三平台 Font API 均无该字段**（全仓库无任何 `.height` 于 Font 的使用）；本仓库既有惯例是 `font.lineHeight`（`CsboxScreen`、`CsboxConfirmScreen` 等大量使用）。故三平台统一改用 `f.lineHeight`，未用 9 常量（lineHeight 存在且值同为 9，语义更精确）。三平台一致，无平台间差异。
- `RenderFontTool.drawString(gg, font, FormattedCharSequence, x, y, ox, oy, scale, color)` 签名三平台已核对一致；`fcs(...)`、`bodyTop`、`Font`/`Minecraft.getInstance().font` 均存在。

## 编译
- v26_1_2 (26.1.2)：clean compileJava ✅
- v26_2 (26.2)：clean compileJava ✅
- v1_21_1 (1.21.1)：clean compileJava ✅（TACZ local-repo 已复制入 worktree）

## 自审
- `git diff --stat`：3 文件 +24 行，无杂散改动
- commit 文件级限定，未吞并无关文件

## 运行时验证（未执行，属运行验证任务）
- Step 6：♞ 可见性依赖 MC 运行时字体（Unifont 含 U+265E 字形；若缺 → 豆腐块，备选烘焙纹理），本任务仅编译验证。
