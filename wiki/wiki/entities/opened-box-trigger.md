---
type: entity
title: OpenedBoxTrigger.java
kind: class
platform: all
updated: 2026-08-03
---

# OpenedBoxTrigger.java

## Overview
`csgobox:opened_box` 成就触发器，同一 trigger 类驱动"首次开箱"和"200 箱"两个成就。

## Responsibilities
- `trigger()` 在每次主动开箱后调用（批量开箱 × K）
- `TriggerInstance.count` 字段实现"任意 vs 阈值"二合一：count=0 代表"首次触发"（A Fresh Start），count=200 代表"导购"
- 数据走 `Stats.CUSTOM` 统计 `csgobox:opened_boxes`
- `enableAchievements=false` 时 `trigger` 跳过调用，但统计仍累加
- 配合 `advancement/root.json` 注册

## Sources
- [[architecture]] / [[changelog]]

## Related
- [[achievement-system]] / [[box-opened-event]]