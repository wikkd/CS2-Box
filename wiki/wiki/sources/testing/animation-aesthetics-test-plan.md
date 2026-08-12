---
type: source
title: 1.21.1 抽卡动画审美测试脚本 实施计划
source_path: docs/superpowers/plans/2026-08-07-animation-aesthetics-test.md
date_ingested: 2026-08-10
tags: [testing, animation, aesthetics, plan]
key_concepts: [superpowers-testing, rendering-pipeline]
key_entities: [anim-render-ops, csgo-box]
---

# 1.21.1 抽卡动画审美测试脚本 — 实施计划

> source: `docs/superpowers/plans/2026-08-07-animation-aesthetics-test.md`（对应设计 [[animation-aesthetics-test]]）

## Summary

[[animation-aesthetics-test]] 的任务分解实现计划（agentic 逐任务执行）。目标：为 v1_21_1 写一键脚本，自动触发抽卡动画、在 3 关键帧用本地 Ollama 视觉模型做 5 维度审美检查，输出报告与回归退出码。

## Key takeaways

- **架构**：Python 脚本复用 `mc_tools/csxlib`（`McpClient` + `BoxEnv`，经 MCP 41501 端口控已启动的 1.21.1 客户端）：触发开箱 → 全量连拍 → 挑 3 关键帧 → 每帧 5 维度审美 prompt（主 `gemma4:12b`）→ FAIL 疑点 `qwen3-vl:8b` 复核 → 写 `report.md` → 退出码 0/1/2。
- **约束**：脚本位置 `scripts/test_animation_aesthetics.py`；依赖注入同 `record_open_animation.py`（`sys.path.insert` 指向 `mc_tools/scripts`）；默认端口 41501、Ollama `:11434`、输出 `build/animation_aesthetics/`；prompt 固定行格式 `[维度] PASS/FAIL/WARN`，脚本按前缀解析；不改模组代码/不改 `mc_tools`；动画参数只读参考（145 tick、F1=0.5s/F2=5.5s/F3 切屏后 0.3s）。
- **任务流**：Task1 脚本骨架+关键帧连拍采集（`capture_frames`/`pick_key_frames`/`main`）→ Task2 单帧审美分析（`analyze_frame` 主+复核）→ Task3 报告与退出码 → Task4 `clean` 子命令（见 [[clean-test-shots]]）。

## Connections

- 概念：[[superpowers-testing]] · [[rendering-pipeline]]
- 实体：[[anim-render-ops]] · [[csgo-box]]
- 设计：[[animation-aesthetics-test]] · [[clean-test-shots]]
