<!-- generated-by: gsd-doc-writer -->
# CS2-Box 开发指南

> 本文档涵盖 CS2-Box Minecraft 模组的本地开发配置、构建命令、代码规范、分支约定与贡献流程。

## 并发构建与多 Agent 协作（开发规则）

工作区允许同时有多个 agent 会话/维护进程参与开发。为避免构建互相踩踏，**所有自动化流程（含 AI agent）必须遵守以下规则**：

1. **启动 Gradle 构建前先探测**：只要命中任一信号，即视为「其他 agent 进程正在编译/改代码」，**本轮不启动编译**：
   - 系统进程列表中存在命令行含 `gradle` 的 `java.exe`（Windows `tasklist` / `wmic` 可查）；
   - `~/.gradle/daemon/<版本>/` 下 daemon 日志的 mtime 在最近 1–2 分钟内仍在更新；
   - 工作区任意 `*/src/` 源码文件近 60 秒内出现成批 mtime 变化；
   - 同一文件两次读取内容不一致（正被其他进程改写）。
2. **命中后**：停止编译动作，向用户报告探测到的信号与冲突风险，**等待用户发起下一轮对话**再继续；不得自动重试，不得绕过（clean / 移走文件 / 修改他人领域文件）强行编译。
3. **确认可编译时**：优先 `--no-build-cache` / `--rerun-tasks` 或先删除编译输出，避免陈旧 `UP-TO-DATE` / `FROM-CACHE` 快照掩盖真实结果；报告时注明重编译方式与平台清单。
4. 单次 Gradle 调用只能构建一个 MC 版本（`-Pactive_versions=<v>`）；六平台全量验证需串行执行。

## 前置要求

| 平台 | Java | Minecraft | NeoForge / Forge | Gradle | NeoGradle / ForgeGradle |
|---|---|---|---|---|---|
| `v1_21_1/` | 21 | 1.21.1 | 21.1.248 | 9.5.1 | 7.1.38 |
| `v26_1_2/` | 25 `--enable-preview` | 26.1.2 | 26.1.2.95 | 9.5.1 | 7.1.38 |
| `v26_2/` | 25 `--enable-preview` | 26.2 | 26.2.0.59 | 9.5.1 | 7.1.38 |
| `forge_26_1_2/` | 25 `--enable-preview` | 26.1.2 | MinecraftForge 26.1.2-64.1.0 | 9.5.1 | ForgeGradle 7.0.31 |

Gradle Wrapper 自带,无需系统安装。

## 本地配置

### 1. 克隆仓库

```bash
git clone https://github.com/wikkd/CS2-Box.git
cd CS2-Box
```

### 2. 选择活动版本

`gradle.properties`:

```properties
active_versions=26.1.2  # 可选：1.21.1 / 26.2 / forge-26.1.2
```

### 3. 构建项目

首次构建会下载 NeoForge 依赖,可能需要几分钟:

```bash
./gradlew :v26_1_2:build
```

构建产物:

NeoForge 平台（jar 名 `csgobox-<mc>-<mod_version>.jar`）：

- `v1_21_1/build/libs/csgobox-1.21.1-2.0.0.jar`
- `v26_1_2/build/libs/csgobox-26.1.2-2.0.0.jar`
- `v26_2/build/libs/csgobox-26.2-2.0.0.jar`

Forge 平台（jar 名 `csgobox-forge-<mc>-<mod_version>.jar`）：

- `forge_1_20_1/build/libs/csgobox-forge-1.20.1-2.0.0-srg.jar`（**发布产物**；`renameJar` 的 SRG 重映射版，1.20.1 生产必需）
- `forge_26_1_2/build/libs/csgobox-forge-26.1.2-2.0.0.jar`
- `forge_26_2/build/libs/csgobox-forge-26.2-2.0.0.jar`

### 4. 运行开发客户端

```bash
# 客户端(单人创造模式)
./gradlew :v26_1_2:runClient

# 专用服务端
./gradlew :v26_1_2:runServer

# GameTest 集成测试
./gradlew gameTestServer
```

## 构建命令

| 命令 | 说明 |
|---|---|
| `./gradlew build` | 编译所有模块(由 `active_versions` 决定) |
| `./gradlew :v26_1_2:compileJava` | 仅编译 v26_1_2,快速检查语法 |
| `./gradlew :v26_1_2:runClient` | 启动 v26_1_2 客户端 |
| `./gradlew :v26_1_2:runServer` | 启动 v26_1_2 专用服务端 |
| `./gradlew :v1_21_1:runClient` | 启动 v1_21_1 客户端 |
| `./gradlew :forge_1_20_1:renameJar -Pactive_versions=forge-1.20.1` | Forge 1.20.1 生产 jar（SRG 重映射，产出 `-srg.jar`；直接 `jar` 产物只供 dev） |
| `./gradlew gameTestServer` | 在测试服务器中运行集成测试 |
| `./gradlew clean` | 删除所有 build/ 目录 |
| `./gradlew tasks --all` | 列出所有可用 Gradle 任务 |

## 代码规范

- **缩进**:4 空格(不用 Tab)
- **行尾**:Unix 风格(LF)
- **编码**:UTF-8
- **Java 命名**:标准 Java 命名约定
- **最大行长度**:120 字符
- **公共 API**:必须加 Javadoc
- **不可变数据**:用 `Record` 类(参见 `BoxDefinition.java`)
- **数据序列化**:用 NeoForge `Codec`(持久化)和 `StreamCodec`(网络流)

### Multiloader 关键约束

- **`common/` 不允许** `import net.minecraft.*` 或 `import net.neoforged.*` / `net.minecraftforge.*`（`:common:checkCommonArchitecture` 自动拦截）
- 所有 GUI / Attachment / 网络上下文 / 注册表访问留在平台模块
- 修改 `common/` 后**必须**在全部受影响平台重新构建验证；**每次 Gradle 调用只能构建一个 MC 版本**，用 `-Pactive_versions=<v>` 指定（`26.1.2` / `26.2` / `1.21.1` / `forge-26.1.2`）
- 验证一律带 `clean`（增量缓存可能造假象）：

```bash
./gradlew :common:test                                          # common 单测 + 架构检查
./gradlew :v26_1_2:clean :v26_1_2:compileJava -Pactive_versions=26.1.2
./gradlew :v26_2:clean   :v26_2:compileJava   -Pactive_versions=26.2
./gradlew :v1_21_1:clean :v1_21_1:compileJava -Pactive_versions=1.21.1
./gradlew :forge_26_1_2:clean :forge_26_1_2:compileJava -Pactive_versions=forge-26.1.2
```

- **镜像纪律**：跨平台改动先改基准 `v26_1_2`，再定点合入其余平台；**禁止整文件覆盖** `v26_2` 与 `forge_26_1_2`（有 API 适配差异），纯新增文件用 `scripts/mirror.sh new <rel-path>`，forge 同步用 `scripts/port-forge-2612.py`（仅机械转换）+ 手工适配
- `CONFIG` 是 `public static final`,**不要写 `null` 守卫**

### 多平台 API 差异

| 维度 | v1_21_1 | v26_1_2 |
|---|---|---|
| Screen 渲染入口 | `render(GuiGraphics,...)` | `extractRenderState(GuiGraphicsExtractor,...)` |
| 矩阵 API | `PoseStack` | `Matrix3x2f`(`guiGraphics.pose()`) |
| 渲染管道 | 静态 `RenderSystem` | decoupled `RenderPipelines` |
| 3D 物品预览 | `BakedModel` 管线 | `Icon3DRenderer`(PIP 3D) |
| Blit 签名 | 9-arg overload | `guiGraphics.blit(RenderPipeline,...)` |

GUI 代码先以 v26_1_2 为基准落地,再定点合入其余平台。

## 分支约定

- `main` — 稳定发布分支
- 功能分支命名:`feat/描述`、`fix/描述`、`docs/描述`、`refactor/描述`
- 所有 PR 指向目标开发分支

## Pull Request 流程

1. 从目标分支创建功能分支
2. 在改动的模块下运行 `./gradlew :<module>:build` 确保编译成功
3. 用 `./gradlew :<module>:runClient` 在游戏中测试更改
4. 跨平台验证(如改动影响 `common/`)
5. 提交包含清晰更改描述的 PR

## 项目结构

```
CS2-Box/
├── common/                              # 跨版本业务逻辑 + 共享资源
│   ├── src/main/java/com/reclizer/csgobox/
│   │   ├── box/                         # BoxGrades / BoxRegistryStore / BoxStripGenerator / schema 校验 / 教程下载
│   │   ├── logic/                       # GradeMap / OddsCalculator / OpenBlockGuard / 终端谈判模型
│   │   ├── config/                      # CsboxConfigDefaults（六平台配置默认值唯一来源）
│   │   ├── terminal/                    # 谈判算法（平台无关）
│   │   └── utils/                       # ColorTools / OverlayColor / GuiRegion 等
│   ├── src/test/java/                   # JUnit 5 单测
│   └── src/main/resources/              # 共享资源(纹理、配方、advancement、lang、音效)
│
├── v1_21_1/                             # MC 1.21.1 / NeoForge 21.1.248 / Java 21
│   └── src/main/java/com/reclizer/csgobox/v1_21_1/
│       ├── CsgoBox.java                 # 模组入口
│       ├── advancement/                 # OpenedBoxTrigger / ModLoadedTrigger
│       ├── capability/                  # 玩家数据附件
│       ├── command/                     # /csbox 命令
│       ├── config/                      # CsboxConfig (ModConfigSpec)
│       ├── event/                       # ClickEvent / ModEvents
│       ├── gui/                         # CsboxScreen / CsboxProgressScreen / CsLookItemScreen
│       ├── item/                        # ItemCsgoBox / ItemCsgoKey / ModItems
│       ├── packet/                      # 网络协议接线
│       └── utils/                       # 共享工具类
│
├── v26_1_2/                             # MC 26.1.2 / NeoForge 26.1.2.95 / Java 25
│   └── src/main/java/com/reclizer/csgobox/v26_1_2/
│       ├── (同 v1_21_1 结构)
│       ├── gui/pip/                     # 独有:Icon3DRenderer / Icon3DRenderState
│       └── utils/                       # 独有:ButtonPalette / RenderFontTool
│
├── forge_26_1_2/                        # MC 26.1.2 / MinecraftForge 64.1.0 / Java 25（实验模块）
│   └── src/main/java/com/reclizer/csgobox/forge_26_1_2/
│       ├── (与 v26_1_2 特性同步,loader 为 MinecraftForge)
│       └── 手工适配:入口 / Networking / ModItems / GUI / AnimRenderOps 等
│
├── settings.gradle                       # 模块注册 + active_versions 切换
├── gradle.properties                     # mod_version / pack_format / active_versions
└── docs/                                 # 项目文档
```

## IDE 配置

### IntelliJ IDEA

1. 打开项目根目录
2. 出现提示时选择 "Import Gradle Project"
3. 等待 Gradle 同步完成
4. 运行配置自动生成(`runClient`、`runServer` 等)

### Eclipse

```bash
./gradlew eclipse
```

然后导入生成的 Eclipse 项目文件。

### VS Code

需安装 `Extension Pack for Java` + `Gradle for Java` 扩展。

## 故障排除

### JDK 版本错误导致构建失败

```bash
# 检查 Java 版本
java -version

# 如需要,从 https://adoptium.net/ 安装 JDK 21 / 25
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### Gradle Daemon 问题

```bash
./gradlew --stop
./gradlew :v26_1_2:build
```

### 资源缺失

`common/src/main/resources/` 由六个平台共享。如果 v26_1_2 启动时找不到 `csgo_background.png`,检查 `v26_1_2/build.gradle` 的 `sourceSets.main.resources.srcDirs` 是否包含 common 路径。

## 相关文档

- [README.md](../README.md) — 项目概述
- [docs/ARCHITECTURE.md](./ARCHITECTURE.md) — 系统设计
- [docs/CONFIGURATION.md](./CONFIGURATION.md) — 配置选项
- [docs/TESTING.md](./TESTING.md) — 测试指南
- [CONTRIBUTING.md](../CONTRIBUTING.md) — 贡献流程
