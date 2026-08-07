---
type: concept
title: 教程系统
updated: 2026-08-03
tags: [tutorial, download, networking]
---

# 教程系统

## Overview
教程 markdown 从网络拉取（不硬编码在 JAR），文件名嵌入 mod 版本（`_tutorial_v1.0.6.md`），版本升级时旧教程自动删除并下载新版。1.0.8 起整体收敛到 `common/`。

## Details
- **源列表**：`config/csbox/_tutorial_sources.json`，默认指向 `https://gitee.com/hou-xiangling/CS2-Box/raw/main/docs/tutorials/`；仅玩家手动创建时读取，默认不写盘
- **下载**：`TutorialFetcher`（Java 11+ `HttpClient`，`Redirect.ALWAYS` 跟随 302——Gitee raw URL 走 ADAS 网关必须，5s 连接/8s 请求超时，异常全 catch 不冒泡）
- **离线安全**：`writeTutorialIfMissing` 整体 try-catch，任何失败只记 WARN，游戏正常启动
- **版本管理**：`BoxDefaults.writeTutorialIfMissing` + `refreshTutorials`（`/csbox tutorial refresh` 强制重下）；版本不匹配时 `deleteStaleTutorials` 按 `^_tutorial_v.*\.md$` 白名单**直接删除**（1.0.8 取代 OS 回收站 + `.trash/` 两级回收机制，无回收站）
- **安全边界**：白名单绝不触碰 `notes.md` 用户文件、`_tutorial_sources.json`、旧版无版本号 `_tutorial.md`
- 分发仓库：Gitee 公开仓库（国内访问速度）；玩家可 fork 改源列表做镜像

## Sources
- [[changelog]]

## Related
- [[box-defaults]] / [[multiloader-architecture]]
