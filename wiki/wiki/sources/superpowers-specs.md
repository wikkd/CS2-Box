---
type: source
title: docs/superpowers/specs/ — UI 自动化测试设计文档
source_path: docs/superpowers/specs/
date_ingested: 2026-08-03
tags: [testing, automation, visual-timeline, design]
---

# docs/superpowers/specs/（UI 自动化测试设计文档）

## Summary
Visual Timeline 测试流程 + 标签截图方案的设计文档合集。由 testhelper 辅助模组驱动，记录 CS2-Box GUI 的全流程回归测试。

## Key takeaways
### Visual Timeline 设计（2026-08-02-visual-timeline-design.md）
- 测试开始每秒一张截图 + 事件日志增量拉取 → 按秒对齐的混合时间线（timeline.md）
- 架构 A：`record_visual_test.sh` 录像器 + `analyze_timeline.py` 视觉批处理
- ToolRunner 注入操作事件化：`mc_click`/`mc_scroll`/`mc_key` 写 `tool_action` 事件
- 视觉模型 qwen3-vl 本地 Ollama 逐张描述（断点续跑）
- 疑点分析：规则级（时间戳断裂、同屏超时）+ 视觉级（对齐/重叠/溢出/截断）

### Tagged Screenshots 设计（2026-08-02-tagged-screenshots-design.md）
- `mc_shot` 加 tag 参数 → `tagged/tag_<epoch>_<tag>.png`
- `mc_sleep` 非阻塞记事件 → 时间线区分"故意等待"与"卡顿"
- 13 个打标点（E1-E11 关键操作后各一张标签截图）
- `t_shot()` / `t_sleep()` 辅助函数

## Connections
- [[runtime-ui-testing]] / [[test-helper-mod-spec]] / [[testing]]