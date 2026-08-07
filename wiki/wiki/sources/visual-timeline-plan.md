---
type: source
title: docs/superpowers/plans/2026-08-02-visual-timeline.md — Visual Timeline 实现计划
source_path: docs/superpowers/plans/2026-08-02-visual-timeline.md
date_ingested: 2026-08-03
tags: [testing, automation, visual-timeline, plan]
---

# docs/superpowers/plans/2026-08-02-visual-timeline.md（Visual Timeline 实现计划）

## Summary
791 行详细实现计划，覆盖 ToolRunner 事件化、录像器、分析器、时间线生成、疑点分析、测试脚本打标改造。由 subagent-driven-development 执行。

## Key takeaways
- 架构：`record_visual_test.sh`（bash 编排 + 每秒录像循环）→ `analyze_timeline.py`（视觉批处理 + 时间线合并 + 疑点分析）
- 3 个组件：ToolRunner 注入操作事件化（`tool_action`）、录像器（`mc_logs` 增量 + `mc_shot` 每秒）、分析器（逐张 qwen3-vl 描述 + 按秒合并 + 疑点）
- E1-E11 全流程测试脚本（约 5-10 分钟），含 13 个标签截图打标点
- 8 个 Task 拆分：事件化（1）→ 录像器（2）→ 分析器（3）→ 时间线生成（4）→ 疑点分析（5）→ 测试脚本打标（6）→ 冒烟（7）→ 验收（8）

## Connections
- [[superpowers-specs]] / [[runtime-ui-testing]] / [[test-helper-mod-spec]]