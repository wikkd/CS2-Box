---
type: source
title: docs/TEST-HELPER-MOD-SPEC.md — 测试辅助模组需求规格
source_path: docs/TEST-HELPER-MOD-SPEC.md
date_ingested: 2026-08-03
tags: [testing, test-helper, spec]
---

# docs/TEST-HELPER-MOD-SPEC.md（测试辅助模组需求规格）

## Summary
为改善 CS2-Box 自动化测试而设计的「测试辅助模组」需求规格。把「眼睛」搬进游戏内部，以结构化日志输出状态供外部脚本消费。

## Key takeaways
- **痛点 P0**：当前 GUI 靠颜色猜（`CsboxScreen` 与创造物品栏 CS2-Box 标签页像素区分非常脆弱）、手上物品完全不可知（靠像素猜主手物品反复误判）、输入有无生效没有任何反馈、点击坐标纯几何推算脆弱
- **设计原则**：日志是唯一事实来源（`[CSBOXTEST]` 前缀），聊天空闲可读，零侵入，单机优先，坐标一律帧缓冲
- 核心命令 `/cst status`：输出 screen 类名/标题、mouse_fb、mainhand/offhand/hotbar/背包
- 输入事件日志：订阅 `InputEvent.Key` / `MouseButton`，发输入后 grep 确认送达
- 等价命令：`/cst shot`（绕开 F2/fn 注入难题）、`/cst pause`、`/cst drop`
- 可点击元素导出：对原版组件反射 getX/getY；模组自绘退化到只输出 Screen 类名
- 消费协议：`key=value` 空格分隔，禁用 JSON 嵌套避免转义地狱
- 建议实现顺序：status → 输入事件日志 → 等价命令 → 事件埋点 → 可点击元素

## Connections
- [[runtime-ui-testing]] / [[testing]] / [[release]]