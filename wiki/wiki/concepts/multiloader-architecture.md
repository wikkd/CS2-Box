---
type: concept
title: Multiloader 架构
updated: 2026-08-03
tags: [architecture, multiloader, common]
---

# Multiloader 架构（9 平台矩阵）

## Overview
9 个平台模块 + 1 个 common 共享层的 multiloader 结构。`active_versions` 决定单次 Gradle 构建哪个平台。

## Details
- **平台矩阵**：`v1_21_1`/`v1_21_3`/`v1_21_4`/`v1_21_5`/`v1_21_8`/`v1_21_10`/`v1_21_11`（NeoForge 21.x，Java 21 旧 API）+ `v26_1_2`/`v26_2`（NeoForge 26.x decoupled API，Java 25 `--enable-preview`）
- **依赖方向**：`平台 → common`；common 不依赖任何平台
- **CONSTRAINT-001**：`common/` 不得 import `net.minecraft.*` / `net.neoforged.*`（编译环境无 MC classpath）
- **共享资源**：`common/src/main/resources/` 由平台 `srcDir project(':common').file('src/main/resources')` 引入；v26.x 额外 `duplicatesStrategy = EXCLUDE`
- **构建约束**：NeoGradle userdev 无法同一次 Gradle 调用并行多 MC 版本（IDEA 扩展冲突，历史限制）→ `-Pactive_versions=<v>`
- **B 类迁移边界**：BoxDefinition/GradeGroup/CsboxConfig/CsboxPlayerData/BoxRegistry 依赖 ItemStack/ModConfigSpec/Component/StreamCodec，common 无 MC classpath，需 IItemStack/IComponent/IModConfig 抽象（6-10h 工程），保留平台层

## Platform notes
- v1_21_11 的 GuiGraphics 已是 decoupled API（曾 clean 编译 80 错误，以 v26_1_2 为蓝本重写）
- v26_2 无 `Options.hideGui` → `HudVisibility` 包装（见 [[hud-visibility]]）

## Sources
- [[readme]] / [[changelog]] / [[architecture]] / [[development]] / [[getting-started]] / [[release]]

## Related
- [[platform-mirror-discipline]] / [[rendering-pipeline]] / [[version-sync]]
