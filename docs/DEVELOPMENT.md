<!-- generated-by: gsd-doc-writer -->
# 开发指南

本文档涵盖 CS2 Box Minecraft 模组的本地开发配置、构建命令和贡献指南。

## 前置要求

- **Java 开发工具包（JDK）**：版本 21 或更高
- **Gradle**：通过 Gradle Wrapper 附带（无需系统安装）
- **Minecraft**：版本 1.21.1
- **NeoForge**：版本 21.1.115（loader 版本 4+）

验证 Java 安装：

```bash
java -version
# 应输出：openjdk version "21.x.x" 或更高版本
```

## 本地配置

### 1. 克隆仓库

```bash
git clone https://github.com/wikkd/CS2-Box.git
cd CS2-Box
```

### 2. 构建项目

首次构建会下载 NeoForge 依赖，可能需要几分钟：

```bash
./gradlew build
```

这会编译所有 Java 源代码并在 `build/libs/` 生成 JAR 文件。

### 3. 运行模组进行开发

启动带模组的 Minecraft：

```bash
# 运行客户端（创造模式单人游戏）
./gradlew runClient

# 运行专用服务端
./gradlew runServer

# 运行带游戏测试的版本
./gradlew runGameTestServer
```

游戏客户端会打开，可以进行游戏内功能测试。

### 4. 生成数据文件

某些功能需要数据生成（进度 JSON、战利品表、合成表）：

```bash
./gradlew runData
```

这会从源码树中的数据提供者重新生成 `src/generated/resources/` 中的文件。

## 构建命令

所有命令使用 Gradle Wrapper（`./gradlew`），它会自动下载正确版本的 Gradle。

| 命令 | 说明 |
|---------|-------------|
| `./gradlew build` | 编译源码并构建可分发 JAR |
| `./gradlew runClient` | 启动带模组的 Minecraft 客户端 |
| `./gradlew runServer` | 启动专用服务端实例 |
| `./gradlew runGameTestServer` | 在测试服务器中运行集成测试 |
| `./gradlew runData` | 运行数据生成器（进度、战利品表、合成表） |
| `./gradlew clean` | 删除构建产物 |
| `./gradlew tasks --all` | 列出所有可用的 Gradle 任务 |

### 构建产物

成功构建后，JAR 文件位于：

```
build/libs/CsgoBox-<version>.jar
```

## 代码规范

本项目使用标准 Java 规范：

- **缩进**：4 空格（不使用 Tab）
- **换行**：Unix 风格（LF）
- **编码**：UTF-8

项目中没有配置格式检查工具。贡献代码时请与周围代码保持一致。

### 关键约定

- **包结构**：`com.reclizer.csgobox.<模块>`，模块包括 `advancement`、`box`、`capability`、`command`、`config`、`event`、`gui`、`item`、`packet`、`sounds` 或 `utils`
- **注册**：使用 NeoForge 的注册系统注册方块、物品和其他游戏对象
- **配置**：使用 `CsboxConfig` 处理模组特定设置

## 分支约定

项目中没有正式的分支命名规范。请使用描述性分支名：

- `feat/<功能名>` 用于新功能
- `fix/<问题名>` 用于 bug 修复
- `docs/<主题>` 用于文档更改

主分支为 `main`。

## Pull Request 流程

提交更改时：

1. 从 `main` 创建功能分支
2. 确保模组构建成功（`./gradlew build`）
3. 使用 `./gradlew runClient` 在游戏中测试更改
4. 如修改了数据提供者，运行数据生成（`./gradlew runData`）
5. 提交包含清晰更改描述的 Pull Request

## 项目结构

```
src/main/java/com/reclizer/csgobox/
├── CsgoBox.java          # 主模组入口
├── advancement/          # 成就/触发器定义
├── box/                 # 核心宝箱功能
├── capability/          # 玩家 capability 处理
├── command/             # 控制台命令
├── config/              # 配置系统
├── event/               # 事件监听器
├── gui/                 # UI 界面和组件
├── item/                # 自定义物品定义
├── packet/              # 网络数据包（客户端/服务端同步）
├── sounds/              # 音效事件定义
└── utils/               # 工具类

src/main/resources/
├── assets/csgobox/      # 材质、模型、语言文件
├── data/csgobox/        # 进度、战利品表、合成表
└── META-INF/            # NeoForge 模组清单
```

## IDE 配置

### IntelliJ IDEA

1. 打开项目根目录
2. 出现提示时选择"Import Gradle Project"
3. 等待 Gradle 同步完成
4. 运行配置会自动生成（如 `runClient`、`runServer` 等）

### Eclipse

```bash
./gradlew eclipse
```

然后导入生成的 Eclipse 项目文件。

## 故障排除

### JDK 版本错误导致构建失败

确保已安装并选择 JDK 21：
```bash
# 检查 Java 版本
java -version

# 如需要，从 https://adoptium.net/ 安装 JDK 21
```

### Gradle Daemon 问题

项目在 `gradle.properties` 中禁用了 daemon。如遇问题：
```bash
./gradlew --stop
./gradlew build
```

### 修改资源后材质缺失

运行数据生成器确保所有资源正确处理：
```bash
./gradlew runData
```

## 相关文档

- [README.md](README.md) - 项目概述
- [CONFIGURATION.md](CONFIGURATION.md) - 配置选项
- [ARCHITECTURE.md](ARCHITECTURE.md) - 系统设计
