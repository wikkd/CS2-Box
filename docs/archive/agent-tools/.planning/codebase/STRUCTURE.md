<!-- refreshed: 2026-06-29 -->
# 代码库结构

**分析日期:** 2026-06-29

## 目录布局

```
[project-root]/
├── .claude/             # Claude Code 本地设置
├── .planning/           # GSD 规划制品
│   └── codebase/        # 代码库映射文档
├── build/               # Gradle 构建输出
├── docs/                # 面向开发者的项目文档
├── gradle/              # Gradle wrapper
├── runs/                # 生成的运行配置
├── src/
│   ├── main/java/com/reclizer/csgobox/  # 所有 Mod Java 源码
│   └── main/resources/  # 资源、数据、Mod 元数据
├── AGENTS.md            # 项目约定/Agent 指南
├── CHANGELOG.md         # 更新日志（中文）
├── CONTRIBUTING.md      # 贡献指南
├── README.md            # 项目自述文件
├── build.gradle         # 构建脚本
└── gradle.properties    # 版本锁定
```

## 目录用途

| 目录 | 用途 | 关键文件 |
|------|------|----------|
| `box/` | 箱子领域模型 | `BoxDefinition.java`、`BoxRegistry.java`、`BoxJsonLoader.java`、`GradeGroup.java` |
| `capability/` | 玩家持久化状态 | `CsboxPlayerData.java`、`ModCapability.java` |
| `command/` | 管理员命令树 | `CsboxCommand.java` |
| `advancement/` | 进度触发器 | `OpenedBoxTrigger.java`、`ModLoadedTrigger.java` |
| `event/` | 事件总线订阅 | `ModEvents.java`、`ClickEvent.java` |
| `gui/` | 客户端 Screen 子类 | `CsboxScreen.java`、`CsboxProgressScreen.java`、`CsLookItemScreen.java` |
| `item/` | 物品类型和注册 | `ItemCsgoBox.java`、`ItemCsgoKey.java`、`ModItems.java` |
| `packet/` | 网络数据包 | `PacketBoxOpenResult.java`、`PacketCsgoProgress.java` 等 5 个文件 |
| `sounds/` | 音效事件注册 | `ModSounds.java` |
| `utils/` | 工具类 | `RandomItem.java`、`IconListTools.java` 等 7 个文件 |
| `config/` | 配置类 | `CsboxConfig.java` |

**`src/main/resources/` 下资源：**
- `META-INF/neoforge.mods.toml` - 模组元数据
- `assets/csgobox/` - 客户端资产（语言、模型、纹理、音效）
- `assets/minecraft/` - 原版命名空间覆盖（着色器注册）
- `data/csgobox/` - 服务端数据（配方、进度）
  - `advancement/root.json`、`first_box.json`、`shopper.json`
  - `recipe/csgo_key0.json`、`csgo_key1.json`、`csgo_key2.json`、`csgo_key3_smithing.json`
- `pack.mcmeta` - 资源包描述文件（`pack_format: 34`）

## 命名约定

- **Java 类：** PascalCase（`BoxDefinition.java`）
- **JSON 资源：** snake_case（`en_us.json`、`csgo_box.json`）
- **常量：** UPPER_SNAKE_CASE（`MAX_ITEMS`）
- **枚举常量：** UPPER_SNAKE_CASE（`AnimationSpeed.SLOW`）
- **包名：** 小写单数（`box`、`item`、`gui`）
- **Mod id：** `csgobox`

## 如何添加新代码

- **新箱子：** 在 `config/csbox/<your_box>.json` 放入 JSON 文件，重载即可。
- **新物品：** 在 `ModItems.ITEMS` 添加 Supplier，创建模型/纹理/语言条目。
- **新 GUI：** 在 `gui/` 中添加 `extends Screen` 类。
- **新数据包：** 在 `packet/` 中添加 `record implements CustomPacketPayload`，在 `CsgoBox.registerPayloads` 注册。
- **新触发器：** 在 `advancement/` 中添加 `extends SimpleCriterionTrigger`。
- **新配置：** 在 `CsboxConfig.java` 中添加 `ModConfigSpec.*Value` 字段。
- **新进度：** 在 `data/csgobox/advancement/` 中添加 JSON。
- **新命令子命令：** 在 `CsboxCommand.register()` 中追加 `.then(...)`。

---

*结构分析：2026-06-29*
