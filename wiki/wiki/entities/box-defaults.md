---
type: entity
title: BoxDefaults.java
kind: class
platform: common
updated: 2026-08-03
---

# BoxDefaults.java

## Overview
教程下载/版本管理核心类，1.0.8 起收敛到 `common/box/`（B 类迁移 #1/6 收尾，9 平台副本删除）。

## Responsibilities
- `writeTutorialIfMissing`：首次启动时若 `config/csbox/` 为空，下载教程并写入默认 `weapon_supply_box.json`（含 `_tutorial` 字段，loader 忽略）
- `refreshTutorials`：`/csbox tutorial refresh` 强制重下当前版本教程（覆盖已存在）
- `deleteStaleTutorials`：版本不匹配时按 `^_tutorial_v.*\.md$` 白名单直接 `Files.delete`（1.0.8 取代 OS 回收站 + `.trash/` 两级回收——`canUseOsTrash`/`moveToOsTrash`/`moveToFallbackTrash`/`pruneFallbackTrash`/`tryMoveOrCopy`/`uniqueFallbackPath` 全部移除）。单文件失败仅 warn 不中断
- 安全边界：绝不触碰 `_tutorial_sources.json`、`notes.md`、旧版无版本号 `_tutorial.md`

## Sources
- [[changelog]]

## Related
- [[tutorial-system]]