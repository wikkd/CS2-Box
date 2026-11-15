# 森罗物语：厨房（Kaleidoscope Cookery）联动数据包

演示「装了森罗物语就有它的货」的箱子与终端机配置。物品 id 以
`kaleidoscopecookery-1.6.0-neoforge+mc1.21.1` 发布 jar 实测为准（231 个物品）。

> **平台适配矩阵**：官方支持 1.21.1 与 1.20.1（Forge/Fabric 双线）；对应
> `v1_21_1` 与 `forge_1_20_1`（Fabric 1.21.1 官方线停更，`fabric_1_21_1`
> 以实装版本为准）。无 26.x 构建。本配置带
> `"requires": ["kaleidoscope_cookery"]` 门控：未装时整箱跳过并在
> `/csbox info error` 报错。

## 文件

| 文件 | 内容 |
|---|---|
| `kc_crate.json` | 森罗物语军火箱（csbox）：grade5 集中该模组仅有的带 `elements` 几何模型物品（3D 茶壶 / 烤肉柱 / 八仙桌），grade1-4 平面料理与器具 |
| `kc_terminal.json` | 森罗物语终端（terminal）：同一物品池的终端报价，手持右键进入交易 |
| `_prices.json` | 全局价格表片段：料理与器具平价（3D 模型物品偏高） |

## 安装（1.21.1 示例）

```bash
cp compat-packs/kaleidoscope-cookery-pack/kc_crate.json compat-packs/kaleidoscope-cookery-pack/kc_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/kaleidoscope-cookery-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:kc_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建，
  `/csbox reload` 只刷新已有物品的配置数据），然后 `/csbox give @p csgobox:kc_terminal`，
  手持右键打开终端。

## 3D 预览说明

森罗物语是机制 A 类「普通 baked model」的代表：231 个物品模型里仅 8 个带
`elements` 实体几何（`teapot_3d` / `shawarma_spit` / `eight_immortals_table` /
`cold_cut_ham_slices_block` / `fruit_basket_full` / `stockpot_lid_normal` /
`stockpot_lid_using` / `trash_can`），这些物品在开箱 3D 与检视屏里走默认
baked-model 路径直接渲染真实几何，**零适配代码**；其余 223 个为平面贴图
料理（箱子 grade1-2 的主力），预览与原版物品一致。本包同时验证默认路径
auto-fit 对非 1 格几何的居中表现。
