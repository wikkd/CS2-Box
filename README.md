# CS2 Box - NeoForge 1.21.1 非官方迁移版

> 注意：本项目由 AI 辅助开发，可能存在未知问题，使用前请自行评估风险。

## 项目说明

本模组为 [ChloePrime/CS2-Box](https://github.com/ChloePrime/CS2-Box)（Forge 1.20.1 版本）的 **非官方 NeoForge 1.21.1 迁移版**。

原作者：Reclizer（原始概念与实现）
Forge 1.20.1 版本作者：ChloePrime
NeoForge 1.21.1 迁移：wikkd

### 功能简介

- 添加 CS:GO / CS2 风格的开箱系统。
- 支持通过 `config/csbox/*.json` 配置自定义箱子。
- 支持箱子名称、钥匙、掉落实体、掉落概率、等级权重和奖励物品池。
- 支持 Minecraft 1.21.1 `components` 物品数据，并兼容旧版 `tag` 字符串配置。
- 开箱结果由服务端决定，客户端只负责预览和动画展示。
- 开箱动画使用服务端下发的动画物品列表，最终落点与实际奖励一致。
- 启动时可自动生成带英文 `_tutorial` 教程字段的默认 JSON。

## 当前版本

- **Mod 版本**: 1.0.5
- **Minecraft**: 1.21.1
- **NeoForge**: 21.1.115+
- **Java**: 21

构建产物位于 `build/libs/`。当前版本默认产物名为：

```text
csgobox-1.0.5.jar
```

## 配置文件

箱子配置位于：

```text
config/csbox/*.json
```

当 `config/csbox` 下没有任何 `.json` 文件时，模组会自动生成：

```text
config/csbox/weapon_supply_box.json
```

自动生成的默认 JSON 包含 `_tutorial` 字段。JSON 不支持 `//` 注释，因此 `_tutorial` 使用合法 JSON 对象提供英文教程。加载器只读取已知配置字段，`_tutorial` 会被忽略，不影响箱子加载。

常用字段：

- `name`: 箱子显示名称。
- `key`: 开箱钥匙物品 ID，使用 `minecraft:air` 表示不需要钥匙。
- `drop`: 默认实体掉落概率，范围 `0.0` 到 `1.0`。
- `random`: 五个等级权重，顺序为 `grade1` 到 `grade5`。
- `entity`: 可写纯实体 ID 列表，也可写 `[实体ID, 掉落率]` 交替列表。
- `grade1` 到 `grade5`: 奖励物品列表，`grade1` 最低，`grade5` 最高。

物品格式示例：

```json
{
  "id": "minecraft:diamond_sword",
  "count": 1
}
```

带 1.21.1 components 的物品示例：

```json
{
  "id": "minecraft:diamond_sword",
  "count": 1,
  "components": {
    "minecraft:custom_name": "{\"text\":\"Example Sword\",\"italic\":false}"
  }
}
```

## 钥匙合成

钥匙物品 `csgobox:csgo_key0/1/2/3` 的合成方式如下：

| 钥匙 | 合成方式 | 材料 |
|---|---|---|
| 铁钥匙 (key0) | 工作台 crafting_shaped | 3x `minecraft:iron_ingot`（3x1 竖列） |
| 金钥匙 (key1) | 工作台 crafting_shaped | 3x `minecraft:gold_ingot`（3x1 竖列） |
| 钻石钥匙 (key2) | 工作台 crafting_shaped | 3x `minecraft:diamond`（3x1 竖列） |
| 下界合金钥匙 (key3) | 锻造台 smithing_transform | 1x `csgobox:csgo_key2`（base） + 1x `minecraft:netherite_ingot`（addition） + 1x `minecraft:netherite_upgrade_smithing_template`（template） |

下界合金钥匙仅可通过锻造台升级获得：使用钻石钥匙 (`csgo_key2`) + 下界合金升级模板 + 一颗下界合金锭升级。每次升级消耗 1 个下界合金升级模板（原版行为）。钻石钥匙为锻造台的 base 物品，严格限定为 `csgobox:csgo_key2`。

## 测试重点

手动测试时建议覆盖：

- 默认 JSON 自动生成和加载。
- 新版 object item 与旧版 JSON string item。
- 缺失物品 ID、空等级、异常权重、奇数实体掉落配置。
- 有钥匙、无钥匙、错误钥匙。
- 开箱动画最终展示物品与实际获得物品一致。
- ESC 退出动画后再次开箱不会卡死。
- 空箱错误提示不被 3D 箱子模型遮挡。
- 下界合金钥匙的锻造台合成路径（key2 + 模板 + 下界合金锭），确认工作台 3x 下界合金锭合成已被移除。
- 成就系统：首次开箱 → 「全新的开始」toast；开箱 200 次 → 「导购」紫色 challenge toast；在 `config/csgobox.toml` 设 `enableAchievements = false` → 关闭后再开箱不再弹任何 csgobox toast，但 `csgobox:opened_boxes` 统计仍累加。

## 许可声明

本项目基于 **MIT 许可证** 发布 - 详见 [LICENSE](./LICENSE) 文件。

```
MIT License

Copyright (c) 2024 Reclizer

特此授予任何人免费获得本软件副本及相关文档文件（以下简称"软件"）的权利，
包括不受限制地使用、复制、修改、合并、出版、分发、再许可和/或销售软件副本的权利，
并允许获得软件的人这样做，但须满足以下条件：

上述版权声明和本许可声明应包含在软件的所有副本或实质性部分中。

本软件按"原样"提供，不提供任何明示或暗示的担保，
包括但不限于适销性、特定用途适用性和非侵权性的担保。
在任何情况下，作者或版权持有人均不对任何索赔、损害或其他责任负责，
无论是合同行为、侵权行为还是其他行为，均由软件或其使用或其他交易引起。
```

## EULA 合规声明

本项目严格遵守 **Minecraft 最终用户许可协议（EULA）** 及 **Mojang 模组使用条款**：

- ✅ **免费发布** — 本项目完全免费，不收取任何费用
- ✅ **不包含游戏本体代码** — 本模组为独立附加内容，不包含 Mojang/Microsoft 的专有代码或资产
- ✅ **遵循社区规范** — 本模组不违反 Minecraft 社区准则
- ✅ **基于官方 API** — 使用 NeoForge（Minecraft 官方认可的模加载器）开发
- ✅ **尊重知识产权** — 所有第三方资源均遵循其原始许可证
- ❌ **不用于商业盈利** — 本项目不允许用于任何商业目的或付费分发

## 免责声明

- 本模组与 **Valve Corporation**、**Counter-Strike** 系列游戏 **没有任何关联**，也未获得其认可或赞助
- 本模组与 **Mojang Studios**、**Microsoft Corporation** **没有任何关联**，也未获得其认可或赞助
- 模组中使用到的 CS:GO / CS2 风格设计元素仅作为致敬，所有相关商标归其各自所有者所有
- 本模组由 AI 辅助生成，作者不对使用过程中可能产生的任何问题承担责任
- 使用本模组造成的任何存档损坏、数据丢失或其他损失，作者 **概不负责**

## 社区规定

- 允许在整合包中使用本模组，但须注明出处
- 允许修改源代码，但修改后的版本须保持 MIT 许可证
- 允许分发本模组的二次开发版本，但须明确标注为衍生作品
- **禁止** 以任何形式出售本模组或其修改版本
- **禁止** 将本模组用于任何违反 Minecraft EULA 的用途

## 环境要求

- **Minecraft**: 1.21.1
- **NeoForge**: 21.1.115+
- **Java**: 21
- **Gradle**: 8.11

## 构建指南

```bash
./gradlew build
```

构建产物位于 `build/libs/` 目录。

如果只需要快速验证编译：

```bash
./gradlew compileJava
```

## 致谢

- 感谢 Reclizer 创造此模组
- 感谢 ChloePrime 维护 Forge 1.20.1 版本
- 感谢 NeoForge 团队提供模加载器
