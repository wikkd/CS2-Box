---
type: source
title: docs/RUNTIME-UI-TESTING.md — GUI 自动化测试工作流
source_path: docs/RUNTIME-UI-TESTING.md
date_ingested: 2026-08-03
tags: [testing, automation, gui, visual]
---

# docs/RUNTIME-UI-TESTING.md（GUI 自动化测试工作流）

## Summary
macOS 上 CGEvent 鼠标 + AppleScript 键盘 + PIL 像素断言驱动的模组自绘 GUI 回归验证方案。

## Key takeaways
- 方案选型：CGEvent（外部驱动现有客户端）+ PIL 帧缓冲断言 → **采用**；Mineflayer MCP（看不到模组 Screen）→ 备选；GameTest（无渲染断言）→ 互补
- 窗口布局关键：macOS 窗口 Z 序吞点击 → 必须 `set frontmost` 游戏窗口；28px 标题栏偏移量（曾踩坑）
- 坐标映射：`screenX = windowX + fbX/2`，`screenY = 253 + fbY/2`
- 确定性世界状态：`/time set day` + `/gamerule doDaylightCycle false` + `/weather clear`（日落光照变化污染全帧 diff）
- 像素断言表：5 个断言点（139 灰面板、绿色按钮、检视屏工具栏、快捷栏选中槽、HUD）
- 键盘注入：CGEvent 对 LWJGL 无效→必须 AppleScript `key code`；Esc=53、数字1=18、F2=120
- 问题诊断速查表：12 条踩坑经验
- 工程化后续：固化驱动脚本到 `scripts/ui-driver/`、轻量 MCP 包装、基线截图回归

## Connections
- [[testing]] / [[achievement-system]] / [[release]]