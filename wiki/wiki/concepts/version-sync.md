---
type: concept
title: 版本号同步
updated: 2026-08-03
tags: [release, versioning]
---

# 版本号同步

## Overview
升级 mod 版本时**四处必须一致**，这是发布流程的核心纪律。

## Details
| 文件 | 位置 | 说明 |
|---|---|---|
| `gradle.properties` | `mod_version=` | 单一事实源 |
| 各平台 `META-INF/neoforge.mods.toml` | `version="${mod_version}"` | 模板变量自动注入，**勿手改** |
| `CHANGELOG.md` | 新版本条目 | |
| `README.md` | 版本提及 | |

- 9 平台共享同一 `mod_version`；jar 名 `csgobox-<mc>-<mod_version>.jar`
- 26.2 仍 beta（最新 26.2.0.40-beta），保持 26.2.0.7-beta 不升级——stable 发布后需重刷 `neo_version_26_2` 并验证 `mc_version_range_26_2` 兼容性
- 发布：9 平台 jar → tag `git tag v<mod_version>`

## Sources
- [[release]]

## Related
- [[multiloader-architecture]]
