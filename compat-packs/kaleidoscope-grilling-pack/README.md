# 森罗烟火（kaleidoscope_grilling）联动数据包

森罗物语系列附属模组的箱子与终端机配置示例。物品 id 以发布 jar 的
`assets/*/models/item` × `zh_cn` lang 键交叉实测为准（1.21.1 NeoForge 与
1.20.1 Forge 双线共有物品；单线特供不入包）。

> 依赖本体「森罗物语：厨房」（装载器强制，无需额外门控）。 本配置带 `"requires": ["kaleidoscope_grilling"]` 门控：模组未装时整箱
> 跳过并在 `/csbox info error` 报错。

## 文件

| 文件 | 内容 |
|---|---|
| `kg_crate.json` | 森罗烟火军火箱（csbox）：5 档池见下 |
| `kg_terminal.json` | 森罗烟火终端（terminal）：同一物品池的终端报价 |
| `_prices.json` | 全局价格表片段（21 条，全部入表满足 v2.0.1 全定价规则） |

## 安装（1.21.1 示例）

```bash
cp compat-packs/kaleidoscope-grilling-pack/kg_crate.json compat-packs/kaleidoscope-grilling-pack/kg_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/kaleidoscope-grilling-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:kg_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建），然后
  `/csbox give @p csgobox:kg_terminal`。

## 预览说明

6 个带 `elements` 几何模型物品（秘制/迷之/未完成烤串、待摇晃的调料、空调料瓶、黑暗烧烤）全部集中在 grade5/grade4/grade3，走默认 baked-model 真 3D；烤串本体为 `fixed_skewers` 模型链平面贴图，预览与原版物品一致；`skewer_plate`（有烤串的盘子）是 `builtin/entity` 物品，检视屏走 BEWLR 重路由。
