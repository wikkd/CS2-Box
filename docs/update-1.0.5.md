# CS2 Box 1.0.5 更新说明

日期：2026-06-28

## 概述

1.0.5 版本移除了第三方 Cloth Config 依赖，并用 NeoForge 原生 `ModConfigSpec` API 替代。无游戏性变更。从 1.0.4 升级的玩家需删除旧的 `config/csgobox.toml` 文件；新文件生成为 `config/csgobox-common.toml`。

预期构建产物：

```text
build/libs/csgobox-1.0.5.jar
```

## 变更内容

- **移除 Cloth Config**：模组不再声明 `me.shedaniel.cloth:cloth-config-neoforge` 依赖。配置加载和持久化使用 NeoForge 内置的 `ModConfigSpec`。
- **新配置文件路径**：`config/csgobox-common.toml`。旧的 `config/csgobox.toml` 被忽略，如从早期版本存在请手动删除。
- **扁平化字段访问**：Java 代码现通过 `CONFIG.fieldName` 而非 `CONFIG.section.fieldName` 读取配置。TOML 文件保留相同的逻辑分组（`[general]`、`[advanced]`、`[sound]`、`[animation]`）。
- **默认值不变**：每个字段较 v1.0.4 保留相同默认值。
- **移除废弃编辑器原型文档**：`docs/ui-redesign-report.md` 和 `docs/box-editor-prototype.html` 已删除，因为编辑器功能已不在计划中。

## 兼容性

- 现有存档：不受影响。
- 现有 `config/csbox/*.json` 箱子配置：不受影响。
- 1.0.4 的现有 `config/csgobox.toml`：被忽略，如存在请手动删除。
- 翻译文件：不受影响。

## 手动测试清单

- 以无 `config/csbox` 目录的方式启动游戏，确认自动生成 `weapon_supply_box.json`。
- 打开一个已配置的箱子，确认物品动画与获得的奖励一致。
- 在动画过程中按 ESC，然后打开另一个箱子。
- 测试以 `minecraft:air` 作为钥匙的箱子和需要真实钥匙的箱子。
- 确认新的 `config/csgobox-common.toml` 被生成且修改后生效。
- 确认启动期间 `latest.log` 中不出现任何 `cloth` 或 `shedaniel` 相关条目。

## 部署说明

部署到手动测试实例时，先移除旧的 `csgobox-*.jar` 文件。同时加载多个版本可能产生误导性结果。

推荐部署目标：

```text
<minecraft instance>/mods/csgobox-1.0.5.jar
```

## 配方更新附录

下界合金钥匙（`csgobox:csgo_key3`）仅可通过锻造台升级获得：

- 模板：`minecraft:netherite_upgrade_smithing_template`
- 基物品：`csgobox:csgo_key2`（钻石钥匙）
- 添加材料：`minecraft:netherite_ingot` × 1

配方文件：`data/csgobox/recipes/csgo_key3_smithing.json`。

**1.0.5 修正**：原工作台 3x 下界合金锭合成配方（`data/csgobox/recipes/csgo_key3.json`）已移除，以与锻造台升级流程保持一致并保持下界合金钥匙的稀有度。无需数据迁移；现有存档继续正常工作。
