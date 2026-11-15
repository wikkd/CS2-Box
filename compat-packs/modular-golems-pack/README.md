# 模组傀儡（Modular Golems）联动数据包

演示「装了 Modular Golems 就有它的货」的箱子与终端机配置。物品 id 以
`modulargolems-3.1.44` 发布 jar 实测为准（同一升级卡的普通/蓝/紫三色是
独立注册物品，id 直接带后缀）。

> **平台适配矩阵**：Modular Golems 官方支持 NeoForge 1.21.1（对应
> `v1_21_1`）与 Forge 1.20.1（对应 `forge_1_20_1`，2.7.x 线）；无 Fabric 与
> 26.x 构建，其余平台不适配。本配置带 `"requires": ["modulargolems"]` 门控：
> 未装时整箱跳过并在 `/csbox info error` 报错。

## 文件

| 文件 | 内容 |
|---|---|
| `mg_crate.json` | 模组傀儡军火箱（csbox）：grade5 紫色顶级升级（远古残骸沙暴 / 湮灭等离子 / 湮灭炸弹 / 朱砂），grade4 先锋甲四件套 + 蓝色升级，grade3 战斧与白色升级，grade1-2 配置卡与槽位扩展 |
| `mg_terminal.json` | 模组傀儡终端（terminal）：同一物品池的终端报价，手持右键进入交易 |
| `_prices.json` | 全局价格表片段：白 < 蓝 < 紫同卡三档定价 + 先锋甲四件独立定价 |

## 安装（1.21.1 示例）

```bash
cp compat-packs/modular-golems-pack/mg_crate.json compat-packs/modular-golems-pack/mg_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/modular-golems-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:mg_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建，
  `/csbox reload` 只刷新已有物品的配置数据），然后 `/csbox give @p csgobox:mg_terminal`，
  手持右键打开终端。

## 3D 预览说明

Modular Golems 自带 `GolemBEWLR` 自定义渲染器且无 GUI 早退门（考古实证），
走 CS2-Box 的默认 baked-model 路径即可全透视渲染，**无需专门适配**；
`barbaric_vanguard_*` 若注册为 `ArmorItem` 则经盔甲代理路径渲染穿戴模型
（L4 复核）。本包的主要用途是默认路径 + 价格表 + 终端机的真机回归。
