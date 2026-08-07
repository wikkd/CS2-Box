---
type: source
title: docs/RELEASE.md — 发布流程
source_path: docs/RELEASE.md
date_ingested: 2026-08-03
tags: [release, versioning, quality-gate]
key_concepts: [multiloader-architecture, platform-mirror-discipline]
---

# docs/RELEASE.md（发布流程）

## Summary
1.0.7+ 多平台发布流程与质量门。

## Key takeaways
- **版本号四处同步**：`gradle.properties` `mod_version=` / `neoforge.mods.toml`（`${mod_version}` 模板注入，勿手改）/ `CHANGELOG.md` / `README.md`
- 构建矩阵：9 平台逐个 `jar`（每次 Gradle 调用一个版本）
- **质量门**：9 平台 clean 编译（防增量缓存假象）+ `:common:test`（BoxJsonSchemaValidatorTest 24 用例）+ 运行时回归（开箱动画/批量/成就/命令/动态 item/GUI 像素断言）
- 可选 `minifyJar` ProGuard 混淆，`proguard-rules.pro` 需同步反射面
- 发布收尾：文档更新、教程推送 Gitee、`git tag v<mod_version>`

## Connections
- [[multiloader-architecture]] / [[platform-mirror-discipline]] / [[tutorial-system]]
