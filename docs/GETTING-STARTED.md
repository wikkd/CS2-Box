<!-- generated-by: gsd-doc-writer -->
# CS2-Box 快速入门

> 5 分钟跑起来 CS2-Box 客户端 + 体验一次开箱。详细的开发配置见 [docs/DEVELOPMENT.md](./DEVELOPMENT.md),架构见 [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md)。

## 前置要求

| 要求 | v1_21_1 | v26_1_2 / v26_2 |
|---|---|---|
| Java JDK | 21 | 25(`--enable-preview`) |
| Minecraft | 1.21.1 | 26.1.2 / 26.2 |
| NeoForge | 21.1.115+ | 26.1.2.94 / 26.2.0.7-beta(loader 11+) |
| Gradle | 9.5.1(wrapper 自动下载) | 9.5.1(wrapper 自动下载) |
| NeoGradle | 7.1.38 | 7.1.38 |

Java 版本必须与平台对应。Gradle Wrapper 自动下载对应 Gradle 版本。

验证 Java:

```bash
java -version  # v1_21_1 应输出 21.x.x;v26_1_2 应输出 25.x.x
```

## 安装步骤

1. **克隆仓库**:

```bash
git clone https://github.com/wikkd/CS2-Box.git
cd CS2-Box
```

2. **选择活动版本**(默认 `26.1.2`):

编辑 `gradle.properties`:

```properties
active_versions=26.1.2  # 或 1.21.1
```

3. **验证 Gradle Wrapper**:

```bash
./gradlew --version
```

4. **构建项目**:

```bash
./gradlew build
```

成功构建后,JAR 位于 `v1_21_1/build/libs/csgobox-1.21.1-1.0.6.jar` 或 `v26_1_2/build/libs/csgobox-26.1.2-1.0.6.jar`。

## 首次运行

### 启动开发客户端

```bash
# v26_1_2(默认)
./gradlew :v26_1_2:runClient

# v1_21_1
./gradlew :v1_21_1:runClient
```

启动后:

1. 创建或加载一个世界
2. 用 `/csbox give @p csgobox:csgo_box 1` 获取一个宝箱
3. 用 `/csbox give @p csgobox:csgo_key0 3` 获取 3 把铁钥匙
4. 手持宝箱右键打开预览界面(2 行 × 10 列物品网格)
5. 放入对应钥匙,点开启按钮开始滚动动画

### 启动专用服务端

```bash
./gradlew :v26_1_2:runServer   # 或 :v1_21_1:runServer
```

服务端启动后,从客户端连接 `localhost:25565` 即可加入带模组的世界。

## 常用命令(`/csbox)

| 命令 | 功能 |
|---|---|
| `/csbox list` | 列出所有已注册箱子及等级概要 |
| `/csbox give <box-id> <count> [@p]` | 给自己/指定玩家发放箱子 |
| `/csbox reload` | 重新加载 `config/csbox/*.json` 箱子定义(需配合 `/reload`) |

详细命令列表见 `command/CsboxCommand.java`。

## 常见配置问题

### Java 版本不匹配

**症状**:构建失败,提示 `Unsupported class file major version` 或 `release version N not supported`。

**解决**:确保当前 Java 版本与目标平台一致。

```bash
# macOS 切换
export JAVA_HOME=$(/usr/libexec/java_home -v 21)  # 或 25

# 检查
java -version
```

### Gradle 内存不足

**症状**:macOS 上构建卡住或 OOM。

**解决**:在 `gradle.properties` 中调整:

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
```

### 配置不生效

**症状**:修改 `config/csgobox.toml` 后游戏里没变化。

**解决**:

- TOML:`/reload` 命令或重启游戏
- JSON:`config/csbox/*.json` 改动**必须重启**(因为 `BoxJsonLoader.loadAll()` 只在 `ServerStartingEvent` 触发)

### 配方加载失败

**症状**:日志出现 `Pack version declaration mismatch` 或 `Couldn't parse data file 'csgobox:csgo_key*'`。

**解决**:v26_1_2 的 `pack.mcmeta` 必须用 `supported_formats` 字段(无 `min_format`/`max_format`);csgo_key 配方的 `ingredients` 字段必须用裸字符串(不是 `{"item": ...}` 包装)。

## 下一步

- 阅读 [docs/ARCHITECTURE.md](./ARCHITECTURE.md) 了解模块拓扑与渲染管线
- 阅读 [docs/CONFIGURATION.md](./CONFIGURATION.md) 查看完整配置选项
- 阅读 [docs/TESTING.md](./TESTING.md) 了解如何运行集成测试
- 查看 `config/csbox/weapon_supply_box.json` 学习箱子数据格式
- 在 `.planning/ROADMAP.md` 查看多加载器重构进度