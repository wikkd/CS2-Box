---
type: source
title: docs/DEVELOPMENT.md — 开发指南
source_path: docs/DEVELOPMENT.md
date_ingested: 2026-08-03
tags: [build, dev-workflow, conventions]
key_concepts: [multiloader-architecture]
---

# docs/DEVELOPMENT.md（开发指南）

## Summary
本地开发配置、构建命令、代码规范、分支约定。

## Key takeaways
- 单次 Gradle 调用只能构建一个 MC 版本（`active_versions` 切换，NeoGradle userdev IDEA 扩展冲突历史限制）
- 常用命令：`:<module>:compileJava` 快速语法检查、`:<module>:runClient`、`gameTestServer`
- 代码规范：4 空格缩进、LF 行尾、UTF-8、120 列、Record 不可变数据、Codec/StreamCodec 序列化
- multiloader 约束：common 改动后必须在所有平台重新构建验证；`CONFIG` 不写 null 守卫
- GUI 代码先在 v1_21_1 落地再迁移 v26_1_2（镜像顺序）
- 分支：`main` 稳定 / `multiloader-refactor` 迁移分支

## Connections
- [[multiloader-architecture]] / [[platform-mirror-discipline]]
