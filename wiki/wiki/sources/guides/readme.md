---
type: source
title: README.md — 项目概览
source_path: README.md
date_ingested: 2026-08-03
tags: [overview, platform-matrix, features]
key_concepts: [multiloader-architecture, platform-mirror-discipline, server-authoritative-rng, dynamic-box-item-registration, achievement-system]
key_entities: [csgo-box, csbox-config]
---

# README.md（项目概览）

## Summary
CS2-Box 是 CS:GO 风格开箱玩法的 Minecraft NeoForge 模组，当前 9 平台共享 `mod_version=1.0.7`（工作基线已含 1.0.8 WIP）。手持箱子右键开预览、放钥匙点开启、服务端权威 RNG 决定结果。

## Key takeaways
- 5 档稀有度（consumer/industrial/mil_spec/restricted/classified），每档独立权重
- `config/csbox/*.json` 即箱子定义，文件名 = 箱子 ID，无需重新编译
- 4 把钥匙梯度，`csgo_key3` 仅锻造台升级获得
- 9 平台矩阵：7 个 1.21.x（Java 21 旧 API）+ 2 个 26.x（Java 25 `--enable-preview`，decoupled API）
- `common/src/main/resources/` 由所有平台 `srcDir` 共享；v26.x 额外 `duplicatesStrategy = EXCLUDE`
- 已禁用范围：Cloth Config 回归、Forge 1.20.1 backport、玩家间交易

## Connections
- [[multiloader-architecture]] / [[server-authoritative-rng]] / [[achievement-system]]
- [[architecture]] / [[development]] / [[configuration]] 等 source
