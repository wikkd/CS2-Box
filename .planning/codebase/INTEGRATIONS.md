<!-- refreshed: 2026-06-29 -->
# 外部集成

**分析日期：** 2026-06-29

本模组是一个独立的客户端/服务端 Minecraft 模组。它完全运行在 Minecraft JVM 内部，不调用任何第三方 SaaS、HTTP API、遥测端点或远程服务。所有"集成"均针对原版 Minecraft / NeoForge 运行时表面。

## API 与外部服务

- **HTTP / REST：** 无。`src/` 中没有 `java.net.http`、`HttpClient` 等。
- **遥测 / 分析：** 无可选加入的数据收集。
- **身份验证：** 无。仅依赖 Minecraft 内置会话。
- **SaaS / 付费服务：** 无。

## 数据存储

- **数据库：** 无 SQL 或 NoSQL 层。
- **文件存储：** 仅本地文件系统。
  - `config/csgobox.toml` - 运行时配置（`ModConfigSpec`）
  - `config/csbox/*.json` - 箱子定义（`BoxJsonLoader` 加载）
  - 路径遍历保护：`BoxJsonLoader.deleteFile()` 验证路径是否在 `csbox/` 目录内。
- **缓存：** 无。`BoxRegistry` 是进程内 `LinkedHashMap`；`/csbox reload` 清空并重新填充。

## 身份验证与授权

- **认证：** Minecraft 内置 Mojang 账户。
- **授权：** 命令需 `hasPermission(2)`（op 等级 2）。服务端对开箱结果权威，`requestId` 仅用于匹配，不用于安全。

## 监控与可观测性

- **错误跟踪：** 无 Sentry、Bugsnag。
- **日志：** SLF4J via `LogUtils.getLogger()`。标准 info/warn/error/debug。无结构化日志。

## CI/CD 与部署

- **托管：** 无后端。模组以 `.jar` 分发。
- **CI 流水线：** 无（无 `.github/workflows/`）。
- **发布产物：** `build/libs/csgobox-1.0.5.jar`。`maven-publish` 声明但未配置。

## 游戏引擎集成点

**NeoForge 事件总线：** 模组总线和游戏总线上注册了 7 个事件处理器。

**Minecraft 注册表：** 通过 `Registries.ITEM`、`Registries.SOUND_EVENT`、`Registries.CREATIVE_MODE_TAB`、`Registries.CUSTOM_STAT`、`Registries.TRIGGER_TYPE`、`Registries.DATA_COMPONENT_TYPE` 注册。

**NeoForge 载荷网络：** 4 个 `CustomPacketPayload` 数据包，注册在 `CsgoBox.java:86-92`。验证集中在 `PacketValidation.java`。

**NeoForge 玩家数据附件：** 一个 `csgobox:player_data` 类型，通过 `CsboxPlayerData.CODEC` 持久化。

**NeoForge 配置系统：** `config/csgobox.toml`，四个部分。

## 环境配置

- 无运行时必需的环境变量。`JAVA_HOME` 在构建时可能被设置，但 `build.gradle:31-33` 用 JDK 21 toolchain 覆盖。无密钥、凭据。

## Webhook 与回调

- 无入站/出站 HTTP。唯一的"回调"是 NeoForge 事件监听器（7 个已注册）。

---

*集成审计：2026-06-29*
