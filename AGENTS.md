# CS2-Box — Agent 指南

## 构建

```bash
./gradlew build        # 完整构建 -> build/libs/csgobox-<version>.jar
./gradlew clean build  # 清理后重建
./gradlew compileJava # 快速编译检查
```

- **Java**: 21（由 toolchain 强制）。`org.gradle.java.home` 已设置为 macOS 路径。
- **产物**: `build/libs/csgobox-<mod_version>.jar`
- **NeoForge**: 21.1.115（非 vanilla Forge）。全程使用 `net.neoforged.*` 包。

## 配置系统

**不使用 Cloth Config。** 使用 NeoForge 原生 `ModConfigSpec`。

```java
// CsboxConfig.java — builder.define* 返回 ConfigValue<T>，需调用 .get()
this.openSoundVolume = builder.defineInRange("openSoundVolume", 100, 0, 100).get();

// CsgoBox.java — static 初始化，CONFIG 为 public static final
public static final CsboxConfig CONFIG;
public static final ModConfigSpec CONFIG_SPEC;
static {
    var pair = new ModConfigSpec.Builder().configure(CsboxConfig::new);
    CONFIG = pair.getLeft();
    CONFIG_SPEC = pair.getRight();
}

// 注册（在构造方法中，不在 static 块中）
ModLoadingContext.get().getActiveContainer().registerConfig(
    ModConfig.Type.COMMON, CONFIG_SPEC, "csgobox.toml");
```

- TOML 路径：`config/csgobox.toml`（与 v1.0.4 之前的命名一致）
- TOML 中的 `[general]`、`[advanced]`、`[sound]`、`[animation]` 分组保留，但 Java 端访问是**扁平化**的（`CONFIG.fieldName`，不是 `CONFIG.section.fieldName`）
- `CONFIG` 是 `final`——永不为 null。删除所有 `CONFIG != null` 守卫检查。

## 包结构

| 包 | 用途 |
|---|---|
| `box/` | BoxDefinition、BoxRegistry、BoxJsonLoader |
| `config/` | 基于 ModConfigSpec 的 CsboxConfig |
| `event/` | LivingDeathEvent（生物掉落投骰） |
| `advancement/` | OpenedBoxTrigger（自定义 criteria trigger）+ 成就注册 |
| `gui/` | CsboxScreen、CsboxProgressScreen、CsLookItemScreen |
| `item/` | ItemCsgoBox、ItemCsgoKey、ModItems |
| `packet/` | 4 个自定义网络同步数据包 + StreamCodec |
| `command/` | `/csbox` 命令及子命令 |

## 关键物品

- `csgobox:csgo_key0`（铁）、`csgo_key1`（金）、`csgo_key2`（钻石）
- `csgobox:csgo_key3`（下界合金）：**仅锻造台配方**
  - 锻造台：`csgo_key2` + `netherite_upgrade_smithing_template` + `netherite_ingot`
- 配方文件路径：`src/main/resources/data/csgobox/recipe/`（**注意是单数 `recipe`**，与 Minecraft 数据包规范一致；RecipeManager 通过 `Registries.elementsDirPath(Registries.RECIPE)` 扫描此目录）

## 配方 JSON 格式

Smithing transform：
```json
{
  "type": "minecraft:smithing_transform",
  "template": { "item": "minecraft:netherite_upgrade_smithing_template" },
  "base": { "item": "csgobox:csgo_key2" },
  "addition": { "item": "minecraft:netherite_ingot" },
  "result": { "count": 1, "id": "csgobox:csgo_key3" }
}
```

## 数据包

使用 NeoForge `StreamCodec`（字段超过 6 个时需手动编写 `.of()` encode/decode）。

## CI / 版本号管理

- 版本号位于 `gradle.properties`（`mod_version=`）和 `neoforge.mods.toml`（`version=`）。两处必须一致；`build.gradle` 通过 `mod_version` 读取。
- CHANGELOG 和 README 中也涉及版本——升级时需同步更新这三处。

## 关键文件

- `CsgoBox.java:36-44` — CONFIG static 初始化（勿改动初始化顺序）
- `CsboxConfig.java` — 所有配置字段；builder 调用在每个 `define*` 上使用 `.get()`
- `ModEvents.java:41` — 生物掉落率直接使用 `CONFIG.globalDropRatePercent`（无需 null 检查）
- `advancement/OpenedBoxTrigger.java` — `csgobox:opened_box` trigger 注册 + `Stats.CUSTOM` 累加入口（`awardStat` 路径）
