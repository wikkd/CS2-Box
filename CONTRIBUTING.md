<!-- generated-by: gsd-doc-writer -->
# 为 CS2 Box 贡献代码

## 开发环境配置

请参阅 [GETTING-STARTED.md](./docs/GETTING-STARTED.md) 了解前置要求和首次运行说明，[DEVELOPMENT.md](./docs/DEVELOPMENT.md) 了解本地开发配置。

### 快速配置

1. 确保已安装 **Java 21** 和 **Gradle 8.11**
2. 克隆仓库
3. 运行 `./gradlew build` 验证构建正常
4. 运行 `./gradlew runClient` 启动带模组的 Minecraft

### 项目结构

```
src/main/java/com/reclizer/csgobox/
    |-- CsgoBox.java          # 模组入口，事件总线注册
    |-- advancement/          # 自定义进度触发器
    |-- box/                  # 核心宝箱系统：定义、注册、加载
    |-- capability/           # NeoForge 玩家数据附件
    |-- command/              # 服务端控制台命令
    |-- config/               # TOML 配置处理
    |-- event/                # 事件订阅（客户端/服务端）
    |-- gui/                  # 客户端 UI 界面
    |-- item/                 # 物品定义和注册
    |-- packet/               # 网络协议处理
    |-- sounds/               # 音效事件定义
    |-- utils/                # 共享工具类
```

## 代码规范

- **语言**：Java 21（通过 toolchain 严格强制）
- **构建系统**：Gradle + NeoForged userdev 插件
- **代码风格**：
  - 遵循标准 Java 命名规范
  - 使用 4 空格缩进
  - 最大行长度：120 字符
  - 公共 API 添加 Javadoc 注释
  - 使用 `Record` 类处理不可变数据结构（参见 `BoxDefinition.java`）
  - 使用 NeoForge 的 `Codec` 和 `StreamCodec` 进行数据序列化
- **构建命令**：
  - `./gradlew build` - 完整构建
  - `./gradlew compileJava` - 快速编译检查
  - `./gradlew runClient` - 运行带模组的 Minecraft
  - `./gradlew runServer` - 运行带模组的专用服务端
  - `./gradlew runGameTestServer` - 运行游戏测试

## 分支约定

- `main` - 稳定发布分支
- 功能分支命名：`feat/描述`、`fix/描述`、`docs/描述`
- 所有 PR 应指向 `main` 分支

## PR 指南

1. **Fork 并创建分支**：从 `main` 创建描述性名称的分支（如 `feat/add-new-box-type`）
2. **保持更改专注**：每个 PR 一个功能或修复
3. **测试更改**：
   - 运行 `./gradlew build` 确保编译成功
   - 使用 `./gradlew runClient` 进行手动测试
   - 用不同配置验证宝箱功能
4. **更新文档**：如更改影响行为：
   - 更新 `/docs/` 中的相关文档
   - 更新配置文件中的 JSON 示例
5. **提交信息格式**（推荐）：
   - `feat: add new box type`
   - `fix: resolve key validation issue`
   - `docs: update configuration guide`
6. **提交 PR**：附上清晰的更改描述和测试说明

## 问题报告

在 [GitHub Issues 页面](https://github.com/ChloePrime/CS2-Box/issues) 报告问题。

报告 bug 时请包含：
- Minecraft 版本（必须是 1.21.1）
- NeoForge 版本
- 模组版本
- 重现步骤
- 预期与实际行为
- 相关日志文件（来自 `.minecraft/logs/`）

功能请求请描述：
- 功能的使用场景
- 如何让用户受益
- 任何现有的替代方案

## 游戏测试框架

CS2 Box 使用 NeoForge 内置的 GameTest 框架。测试应放在：

```
src/test/java/com/reclizer/csgobox/
```

运行游戏测试：
```bash
./gradlew runGameTestServer
```

## 配置系统

配置文件为 TOML 格式：
- `config/csgobox.toml` - 主模组配置
- `config/csbox/*.json` - 宝箱定义

添加新配置选项时：
1. 在 `config/CsboxConfig.java` 中添加字段
2. 使用 `ConfigItem` 或 `ConfigRange` 注解
3. 在 [CONFIGURATION.md](./docs/CONFIGURATION.md) 中记录

## 宝箱定义 JSON 格式

宝箱定义使用 Mojang 的 DataComponent 系统。修改 `BoxDefinition.java` 时：
1. 用新字段更新 `BoxDefinition` 记录
2. 更新 JSON 序列化的 `CODEC`
3. 更新网络传输的 `StreamCodec`
4. 如需要，在 GUI 中添加对应字段

## 许可证

为 CS2 Box 贡献代码即表示您同意您的贡献将采用 MIT 许可证。参见 [LICENSE](./LICENSE) 文件了解详情。
