---
type: entity
title: CsboxConfig.java
kind: class
platform: all
updated: 2026-08-03
---

# CsboxConfig.java

## Overview
NeoForge `ModConfigSpec` 配置类，TOML 路径 `config/csgobox.toml`，COMMON 类型。

## Responsibilities
- 4 个 TOML 分组：`[general]`（animationSpeed/globalDropRatePercent）、`[advanced]`（loadDefaultBoxes/enableDebugLogging/enableAchievements/**bulkOpenCount**）、`[sound]`（3 个音量）、`[animation]`（totalAnimationTicks/animationSpeedMultiplier/showItemNames）
- 注册为 `ModConfig.Type.COMMON`；`ModConfigEvent.Reloading` 记录日志
- `bulkOpenCount`（0=无上限，默认 0）服务端权威

## Cross-platform differences
- 9 平台各 1 份副本（B 类文件，未迁 common——依赖 `ModConfigSpec`）

## 关键纪律
- builder 每个 `define*()` 后必须 `.get()` 内联求值（v1.0.5 的 `init()` 延迟填充从未被调用导致所有配置失效的 bug）
- `CONFIG` 永不为 null，**删除所有 `CONFIG != null` 守卫**

## Sources
- [[configuration]] / [[changelog]]

## Related
- [[csgo-box]] / [[bulk-opening-pipeline]]
