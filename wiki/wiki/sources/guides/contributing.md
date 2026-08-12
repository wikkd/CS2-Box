---
type: source
title: CONTRIBUTING.md — 贡献指南
source_path: CONTRIBUTING.md
date_ingested: 2026-08-03
tags: [contribution, workflow]
key_concepts: [multiloader-architecture, platform-mirror-discipline]
---

# CONTRIBUTING.md（贡献指南）

## Summary
代码贡献流程、开发环境配置、PR 指南。

## Key takeaways
- 开发环境配置、构建矩阵（3 平台开关）、项目结构
- 代码规范：4 空格、LF 行尾、120 列、公共 API 加 Javadoc、Record 不可变、Codec/StreamCodec
- **`common/` 不得 import `net.minecraft.*` / `net.neoforged.*`**
- GUI 代码先在 v1_21_1 落地，再迁移到 v26_1_2
- 修改 `common/` 后必须在所有平台重新构建验证
- PR 使用 Conventional Commits；`CONFIG` 不写 null 守卫

## Connections
- [[multiloader-architecture]] / [[platform-mirror-discipline]] / [[development]]