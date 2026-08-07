---
type: source
title: docs/MANUAL-TESTING-v1.0.5.md — 1.0.5 手动测试用例
source_path: docs/MANUAL-TESTING-v1.0.5.md
date_ingested: 2026-08-03
tags: [testing, manual, v1.0.5]
key_concepts: [achievement-system, server-authoritative-rng]
key_entities: [csbox-config, opened-box-trigger]
---

# docs/MANUAL-TESTING-v1.0.5.md（1.0.5 手动测试用例）

## Summary
v1.0.5 的 33 个手动测试用例，覆盖 CsboxConfig 修复回归、配置路径、成就系统、Mob 掉落语义、`enableAchievements` 开关、锻造台配方、命令系统、性能与稳定性冒烟、跨场景集成。

## Key takeaways
- TC-1.x（7 条）：CsboxConfig init() 修复核心回归（开箱不崩溃、实体掉落、调试日志、默认 box、物品名、音效、动画速度）
- TC-3/4.x：成就系统（全新的开始 / 导购 200 箱 hidden 挑战）
- TC-5.x：Mob 掉落不计开箱（主手右键才触发，副手不触发）
- TC-6.x：`enableAchievements` 开关语义（关闭时统计仍累加）
- TC-7.x：`csgo_key3` 锻造台唯一路径（工作台配方已删除）
- 通过判定：P0 = 开箱崩溃不可发布；P0 = 首次开箱/锻造台功能不可用
- 测试记录模板：版本、环境、通过/失败计数

## Connections
- [[changelog]] / [[achievement-system]] / [[csbox-config]] / [[opened-box-trigger]]