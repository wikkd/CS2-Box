---
type: concept
title: EntityChineseMap 迁移
updated: 2026-08-03
tags: [common, migration, b-class]
---

# EntityChineseMap 迁移

## Overview
实体中文名称映射表，String key 纯数据版。1.0.8 从 9 平台副本迁移到 `common/` 层。

## Details
- B 类迁移 #1/6 的组成部分
- 纯数据无 MC 依赖，可以安全放在 common
- 文件位置：`common/utils/EntityChineseMap.java`
- 9 平台副本已删除，统一由 common 提供

## 剩余 B 类文件（未迁移）
- `BoxDefinition` / `BoxRegistry` / `GradeGroup` / `CsboxConfig` / `CsboxPlayerData` — 依赖 ItemStack/ModConfigSpec/Component/StreamCodec，common 编译环境无 MC classpath
- 需平台抽象接口（IItemStack/IComponent/IModConfig），ROADMAP 1.1B 判定 6-10h 工程

## Sources
- [[changelog]]

## Related
- [[multiloader-architecture]] / [[tutorial-system]]