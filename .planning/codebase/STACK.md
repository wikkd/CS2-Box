<!-- refreshed: 2026-06-29 -->
# 技术栈

**分析日期：** 2026-06-29

## 语言

- **主要：** Java 21 - 模组全部逻辑
- **次要：** JSON（配置/配方/进度/翻译/模型/音效）、Groovy（`build.gradle`）、TOML（运行时配置）、GLSL JSON（着色器注册）

## 运行时

- **环境：** Minecraft 1.21.1（Mojang 官方映射）、JDK 21
- **包管理器：** Gradle wrapper + NeoForged userdev 7.0.171。无单独 Maven。
- **锁文件：** 无。版本在 `gradle.properties` 中固定。

## 框架

- **核心：** NeoForge 21.1.115 + Minecraft 1.21.1 原版代码
- **加载器约定：** `modLoader="javafml"`，`loader_version_range=[4,)`
- **构建/开发：** NeoForged userdev 提供 runs（client/server/gameTestServer/data），Eclipse + IDEA 插件，`maven-publish`
- **测试：** GameTest 框架已注册但未使用（`src/` 下无 GameTest 类）

## 关键依赖

- **关键：** `net.neoforged:neoforge:21.1.115`
- **基础设施：** Cloth Config 已移除，使用原生 `ModConfigSpec`。Google Gson 通过 NeoForge 传递引入。
- **无外部运行时服务。** 100% 离线。

## 配置

- **环境配置：** 无 `.env`。所有配置为 `config/csgobox.toml`（ModConfigSpec 写入）+ `config/csbox/*.json`（BoxJsonLoader 加载）。
- **构建配置：** `gradle.properties`、`build.gradle`、`settings.gradle`、`neoforge.mods.toml`、`pack.mcmeta`。
- **资源处理：** `processResources` 扩展模板变量 + `purgeStaleBuildResources` 清理 macOS 重复文件。

## 平台要求

- **开发：** macOS 目标（`gradle.properties` 硬编码 JDK 21 路径）。所有 `JavaExec` 强制使用 JDK 21 toolchain。Gradle daemon 禁用。
- **生产：** 产物 `csgobox-1.0.5.jar`，`BOTH` 端（服务器 + 客户端）。需要 Minecraft 1.21.1 + NeoForge >=21.1.115。
- **分发仓库：** Maven Central、BlameJared Maven、Modmaven、Curse Maven、Latvian Maven（限定 `dev.latvian.mods`）。

---

*技术栈分析：2026-06-29*
