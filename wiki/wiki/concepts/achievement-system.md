---
type: concept
title: 成就系统
updated: 2026-08-03
tags: [achievements, statistics, triggers]
---

# 成就系统

## Overview
通过 Minecraft 原生 `CriteriaTriggers` + `Stats.CUSTOM` 统计实现，无 Capability、无存档迁移影响。

## Details
- **成就**：
  - `全新的开始`（A Fresh Start）— 首次主动右键开箱解锁（Mob 掉落不算）；root 节点
  - `导购`（Shopkeeper）— 隐藏紫色挑战（`frame: "challenge"`, `hidden: true`），累计主动开 200 箱；绿宝石图标
- **数据源**：`Stats.CUSTOM` 统计 `csgobox:opened_boxes`（`awardStat` 累加，批量开 K 箱 +K）
- **Trigger**：`OpenedBoxTrigger`（`csgobox:opened_box`）同一 trigger 类驱动两个成就——`TriggerInstance.count` 字段实现"任意 vs 阈值"二合一
- **开关**：`enableAchievements`（默认 true）；关闭时统计仍累加（保留进度），`trigger` 跳过调用
- **注册**：`csgobox:advancement/root.json` 节点下追加
- 1.0.7 曾加 `/csbox scoreboard`（DUMMY 手动同步），1.0.8 已**彻底原版化移除**：改为 `/scoreboard objectives add <名> minecraft:custom:csgobox:opened_box` 原版指令直接读自定义统计，不再手动写分

## Sources
- [[architecture]] / [[changelog]]

## Related
- [[opened-box-trigger]] / [[server-authoritative-rng]]
