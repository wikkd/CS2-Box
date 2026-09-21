# compat-packs — 官方联动数据包示例

「装 X 模组就有它的货」：每个子目录是一个**示例箱子配置包**，演示如何把其它模组的
物品放进 CS2-Box 的 5 档稀有度体系。

## 安装（所有包通用）

```bash
# 把某个包的 JSON 复制进配置目录（文件名即箱子 ID）
cp compat-packs/<pack>/*.json config/csbox/
```

进游戏执行 `/csbox reload` 即时生效，然后 `/give @p csgobox:<文件名>` 领取。

## 可用包

| 包 | 目标模组 | 平台限制 | 说明 |
|---|---|---|---|
| `apotheosis-pack/` | Apotheosis（神化） | 无 | 高稀有度掉宝石与高品质武器 |
| `irons-spells-pack/` | Iron's Spells 'n Spellbooks | 无 | 法术卷轴 / 升级球 / 法系材料 |
| `tacz-pack/` | TACZ（永恒枪械工坊） | 仅 `v1_21_1` / `forge_1_20_1` | 枪械 / 弹药 |


## v2.0.1 新字段速览

示例包已改用/示范新配置能力（详见 `docs/CONFIGURATION.md` 与 `docs/box-schema/box.schema.json`）：

| 字段 | 作用 | 示例 |
|---|---|---|
| `weight` | 档内物品权重（0 = 临时禁用，不用删） | `{"id": "...", "weight": 3}` |
| `count: [min,max]` | 数量区间（开箱时随机） | `{"id": "...", "count": [2, 5]}` |
| `"tag": "#minecraft:swords"` | 物品标签引用（加载时展开） | `{"tag": "#minecraft:swords"}` |
| `loot_table` | 战利品表引用（开箱时掷表） | `{"loot_table": "minecraft:chests/simple_dungeon"}` |
| `enchant` | 随机附魔快捷 | `{"enchant": {"level": [3,5]}}` |
| `requires` | 目标模组未装时整箱跳过（推荐联动包都加） | `"requires": ["apotheosis"]` |
| `enabled` | 一键整箱开关 | `"enabled": false` |
| `discount`/`stock` | 终端售价折扣 / 库存 | `"discount": 0.2, "stock": 10` |
| `_prices.json`（价格表） | 全局统一物品售价（武库点数），`id#变体` 子键区分 NBT 变体；值可为固定整数或 `[min, max]` 范围（每次终端机报价/拆解随机取整） | `{"minecraft:diamond_sword": 1500, "tacz:modern_kinetic_gun#tacz:ak47": 2000, "minecraft:arrow": [200, 400]}` |
| `max_per_player`/`cooldown_seconds`/`permission` | 开箱约束 | `"max_per_player": 5, "cooldown_seconds": 300` |
| `icon` | 每箱图标（整数 CMD / 字符串 model id） | `"icon": "minecraft:item/barrel"` |

> 校验工具：`/csbox validate [box]` 干跑；`scripts/check-ids.py` 扫配置的 id/tag/loot_table；
> `scripts/boxgen.py` 生成新箱子。

## 诚实声明

这些包是**联动机制演示**，物品 id 与组件为示意值。正式使用前请用
`/csbox nbt hand` 或目标模组的 JEI/REI 核对真实注册表 id（版本间可能有差异）。
未装目标模组的客户端上，相关 id 会显示为丢失材质物品，可正常开出、无崩溃。

## 如何贡献新包

1. 建 `compat-packs/<mod>/`，放 1–2 个箱子 JSON（schema 见 `docs/CONFIGURATION.md`）。
2. 写 `README.md`：目标模组、平台限制、安装命令、需核对的物品 id。
3. 每个 JSON 在装与不装目标模组两种环境下各开箱验证一次。
4. 提 PR（见 `CONTRIBUTING.md`）。
