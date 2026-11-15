# 沉浸式飞行（Immersive Aircraft）联动数据包

演示「装了 Immersive Aircraft 就有它的货」的箱子与终端机配置。物品 id 以
`immersive_aircraft-neoforge-1.7.0+1.21.1` 发布 jar 实测为准。

> **平台适配矩阵**：IA 官方支持 NeoForge 1.21.1（对应 `v1_21_1`）、Forge 1.20.1
> （对应 `forge_1_20_1`，1.5.0 线）、Fabric 1.21.1（对应 `fabric_1_21_1`）以及
> NeoForge 26.1.2 / 26.2（对应 `v26_1_2` / `v26_2`，1.7.0 线，唯一有 26.x 构建
> 的适配模组）；无 Forge 26.x 构建，`forge_26_1_2` / `forge_26_2` 不适配。
> 本配置带 `"requires": ["immersive_aircraft"]` 门控：未装 IA 时整箱跳过并在
> `/csbox info error` 报错。

## 文件

| 文件 | 内容 |
|---|---|
| `ia_crate.json` | 沉浸式飞行军火箱（csbox）：grade5 双战舰（warship / cargo_airship），grade4 载客飞行器（gyrodyne / biplane / airship），grade3 轻型载具与武器，grade1-2 升级零件 |
| `ia_terminal.json` | 沉浸式飞行终端（terminal）：同一物品池的终端报价，手持右键进入交易 |
| `_prices.json` | 全局价格表片段：IA 全部物品平价（载具 > 改装件 > 基础零件） |

## 安装（1.21.1 示例）

```bash
cp compat-packs/immersive-aircraft-pack/ia_crate.json compat-packs/immersive-aircraft-pack/ia_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/immersive-aircraft-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:ia_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建，
  `/csbox reload` 只刷新已有物品的配置数据），然后 `/csbox give @p csgobox:ia_terminal`，
  手持右键打开终端。

## 3D 预览说明

IA 的载具物品（airship / biplane / gyrodyne / cargo_airship / warship /
quadrocopter / bamboo_hopper）本身是平面图标，3D 外形只在实体上。CS2-Box 自
2.0.4 起在 `v1_21_1` 的 `ItemPreviewStrategies` 内置了 IA 策略：物品 id 即实体 id
（命名空间门控 + 实体注册表 containsKey 兼当模组在场证明），终端机报价大图与
检视屏里直接渲染真实载具模型；升级零件物品没有对应实体，走默认 baked-model 路径。
其他平台待策略链移植后同样生效。
