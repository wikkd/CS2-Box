---
type: concept
title: Superpowers UI 自动化测试体系
updated: 2026-08-10
tags: [testing, ui, automation, vision-model]
---

# Superpowers UI 自动化测试体系

## Overview

CS2-Box 的 GUI / 动画 / 上线前质量保障工具集（代号 "superpowers"），目标是在无文字的动画与 GUI 画面上建立**可重复、可自动评分**的回归能力。核心由视觉模型（本地 Ollama）驱动，辅以全量 E2E 套件。配套计划见 [[visual-timeline-plan]]、[[superpowers-specs]]，落地脚本在 `scripts/` 与 `mc_tools/scripts/csxlib`。

## Details

- **视觉时间线（Visual Timeline）**：把开箱动画按时间轴拆解、逐帧标注，支撑关键帧选取与审美比对（设计 791 行、8 Task）。
- **带标签截图（Tagged Screenshots）**：自动化测试时给截图打语义标签，便于检索与回归比对。
- **动画审美测试** [[animation-aesthetics-test]]：仅 3 关键帧（F1/F2/F3）、5 维度（居中/间距/镜头/背景/闪烁）、双模型复核（`gemma4:12b` 主审 + `qwen3-vl:8b` 复核），v1_21_1 专属。
- **清理测试截图** [[clean-test-shots]]：`test_animation_aesthetics.py clean` 子命令，默认删 `shots/*.png` 留 `report.md`。
- **全量上线检查** [[fullcheck-suite]]：一条命令对全部 10 平台跑 E2E（开箱主流程 T1-T9 / 磨损耐久 / 管理命令 / 动态 box / 成就 / 箱子 JSON 变体+错误自检 / GUI 全量审美评分），报告落 `build/fullcheck/`。
- **运行时 UI 测试** [[runtime-ui-testing]]：既有运行时 GUI 自动化工作流（MCP 端口 + 进世界 + 导航原语），是上述套件的前置底座。
- **与渲染门面的关系**：测试锚定在 [[anim-render-ops]] 暴露的稳定原语上，门面收敛后跨平台动画表现一致，测试只需在代表平台重点覆盖。

## Platform notes

- 动画审美测试当前写死 v1_21_1（平台间动画代码同构，后续推广另议）；FullCheck 套件依赖 `testhelper` mod 移植到 10 平台（前置工程）。
- 视觉模型依赖本地 Ollama，不进 CI；FullCheck 10 平台 2-4 小时，本地执行不在 CI 矩阵。

## Sources

- [[animation-aesthetics-test]] · [[clean-test-shots]] · [[fullcheck-suite]] · [[runtime-ui-testing]] · [[visual-timeline-plan]] · [[superpowers-specs]]

## Related

- [[anim-render-ops]] · [[rendering-pipeline]] · [[box-json-loader]] · [[csgo-box]]
