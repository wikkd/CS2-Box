---
type: source
title: 1.21.1 抽卡动画审美测试脚本
source_path: docs/superpowers/specs/2026-08-07-animation-aesthetics-test-design.md
date_ingested: 2026-08-10
tags: [testing, animation, aesthetics, vision-model]
key_concepts: [superpowers-testing, rendering-pipeline]
key_entities: [anim-render-ops, csgo-box]
---

# 1.21.1 抽卡动画审美测试脚本 — 设计

> source: `docs/superpowers/specs/2026-08-07-animation-aesthetics-test-design.md`

## Summary

2026-08-07 确认（方案 A）。新增 `scripts/test_animation_aesthetics.py`：启动 1.21.1 客户端自动触发开箱动画 + 本地 Ollama 视觉模型自动分析。填补 `record_open_animation.py`（只能连拍无审美校验）与 `mc_tools verify_screen`（文本断言，不适用无文字动画）之间的空白。仅分析 **3 个关键帧**（最快回归），5 维度审美评分。

## Key takeaways

- **关键帧时间轴**（以 `CsboxProgressScreen` 出现为 T0，动画默认 145 tick≈7.25s）：F1 起始帧（T0+0.5s，查居中/间距/镜头/背景）、F2 减速中段（T0+5.5s，查镜头内与条带错位/鬼影）、F3 停止帧（切 `CsLookItemScreen` 后 0.3s，查中奖卡居中 golden line/无残留闪烁）。时间轴参数化（`--f1/--f2/--f3=-1` 自动检测）。
- **5 维度**（每帧同 prompt，模型逐维度输出 `PASS/FAIL/WARN`）：居中、间距、镜头（放大镜圆形裁剪干净/无双重图像/无锯齿）、背景（模糊暗化正常）、闪烁（鬼影/残影）。
- **双模型复核降误报**：主审 `gemma4:12b` → FAIL 疑点由 `qwen3-vl:8b-instruct` 复核，两模型一致才定 FAIL。
- **产物**：`build/animation_aesthetics/report.md`（元信息 + 3 帧小图 + 每维度状态 + FAIL 复核结论）+ 全量连拍帧（默认保留供人工复查）。退出码 0=全 PASS / 1=有 FAIL / 2=仅 WARN（可接 CI）。
- **YAGNI**：不做全量逐帧 AI 分析、不做其他平台、不做流畅度曲线统计、不修改模组代码。

## Connections

- 概念：[[superpowers-testing]] · [[rendering-pipeline]]
- 实体：[[anim-render-ops]] · [[csgo-box]]
- 参考：[[fullcheck-suite]] · [[clean-test-shots]] · [[runtime-ui-testing]]
