<!-- generated-by: gsd-doc-writer -->
# CS2 Box 快速入门

## 前置要求

开发环境需满足以下要求：

| 要求 | 版本 | 说明 |
|-------------|---------|-------|
| Java JDK | 21 | NeoForge 21.1 开发必需，早期版本不兼容 |
| Minecraft | 1.21.1 | 客户端和服务端必须都是 1.21.1 |
| NeoForge | 21.1.115+ | 通过 Minecraft 1.21.1 官方 NeoForge 安装器安装 |
| Gradle | 8.11 | 项目已包含 Gradle Wrapper，无需单独安装 |

验证 Java 安装：

```bash
java -version
# 应输出：openjdk version "21.x.x" ...
```

## 安装步骤

1. 克隆仓库：

```bash
git clone https://github.com/wikkd/CS2-Box.git
cd CS2-Box
```

2. 设置开发环境：

```bash
# Gradle Wrapper 会自动处理依赖解析
./gradlew --version
```

3. 构建项目以验证配置：

```bash
./gradlew build
```

这会编译所有 Java 源代码并将模组打包为 JAR 到 `build/libs/csgobox-<版本>.jar`。

## 首次运行

在开发环境中启动带模组的 Minecraft：

```bash
./gradlew runClient
```

这会启动带模组加载的 Minecraft 客户端。然后你可以：

1. 创建或加载一个世界
2. 使用 `/csbox give csgobox:csgo_box 1 @p` 获取一个宝箱
3. 使用 `/csbox give csgobox:csgo_key0 3 @p` 获取钥匙
4. 手持宝箱右键打开预览界面
5. 放入对应钥匙点击开启按钮开始开箱动画

对于带模组的服务端：

```bash
./gradlew runServer
```

## 常见配置问题

### Java 版本不匹配

**问题**：构建失败，提示 `Unsupported class file major version 67` 或类似错误。

**解决方案**：确保已安装 JDK 21 并设为当前 Java 版本。`build.gradle` 配置了使用 JDK 21 toolchain，但系统必须先安装 JDK 21。

```bash
# 检查当前 Java 版本
which java
java -version
```

### Gradle Daemon 内存问题

**问题**：构建卡住或内存不足（macOS）。

**解决方案**：项目在 `gradle.properties` 中禁用了 Gradle daemon。如遇内存问题，配置如下：

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
```

### 配置文件位置问题

**问题**：修改 `config/csgobox.toml` 后没有生效。

**解决方案**：模组在启动时读取配置。修改配置后，重启游戏或使用 `/csbox reload` 热重载宝箱定义。

## 下一步

- 阅读 [ARCHITECTURE.md](./ARCHITECTURE.md) 了解模组内部结构
- 阅读 [CONFIGURATION.md](./CONFIGURATION.md) 查看详细配置选项
- 查看 `src/main/resources/data/csgobox/` 中的数据包和合成表示例
- 查看 `src/test/` 中的测试文件（如存在）了解测试模式
