# compat-packs — 官方联动数据包示例

「装 X 模组就有它的货」：每个子目录是一个**示例箱子配置包**，演示如何把其它模组的
物品放进 CS2-Box 的 5 档稀有度体系。

## 安装（所有包通用）

```bash
# 箱子/终端 JSON 复制进配置目录（文件名即箱子 ID）
cp compat-packs/<pack>/<box>.json config/csbox/
# 价格表片段必须合并进全局表（直接 cp 会整表覆盖、丢掉其它包的定价）：
python scripts/merge-prices.py compat-packs/<pack>/_prices.json config/csbox/_prices.json
```

进游戏执行 `/csbox reload` 即时生效，然后 `/csbox give @p csgobox:<文件名>` 领取。
**新增终端机（`"type": "terminal"`）配置文件后需重启游戏**（动态物品在注册阶段
创建），已有配置内容变更 `/csbox reload` 即可。

## 可用包

| 包 | 目标模组 | 平台限制 | 说明 |
|---|---|---|---|
| `apotheosis-pack/` | Apotheosis（神化） | 无 | 高稀有度掉宝石与高品质武器 |
| `irons-spells-pack/` | Iron's Spells 'n Spellbooks | 无 | 法术卷轴 / 升级球 / 法系材料 |
| `tacz-pack/` | TACZ（永恒枪械工坊） | 仅 `v1_21_1` / `forge_1_20_1` / `fabric_1_21_1` | 枪械 / 弹药 |
| `superb-warfare-pack/` | Superb Warfare（卓越前线） | `v1_21_1` / `forge_1_20_1`（官方）、`fabric_1_21_1`（ReFabricated 移植） | 枪械（普通物品，无需 NBT）/ 弹药 / 载具召唤箱 / 预览适配测试箱（`preview_adapt_test.json`） |
| `slashblade-resharped-pack/` | SlashBlade:Resharped（拔刀剑·重锻） | `v1_21_1`（NeoForge 2.1.9 线）/ `forge_1_20_1`（Forge 1.9.65 线） | 24 把数据驱动名刀（`blade_state` 身份字段 + `VariantId` 变体定价）+ 耀魂材料，双版本条目形态分文件，含终端机 |
| `immersive-aircraft-pack/` | Immersive Aircraft | `v1_21_1`（1.7.0 NF）/ `forge_1_20_1`（1.5.0 Forge）/ `fabric_1_21_1`（1.7.0）/ `v26_1_2` + `v26_2`（1.7.0 NF，唯一有 26.x 构建的模组） | 7 种载具（实体代理 3D 预览）+ 全套升级零件，含终端机 |
| `modular-golems-pack/` | Modular Golems | `v1_21_1` / `forge_1_20_1`（2.7.x） | 升级卡白/蓝/紫三档 + 先锋甲四件套，含终端机 |
| `prefab-pack/` | Prefab（预制建筑） | `v1_21_1`（1.0.9 NF）/ `forge_1_20_1`（1.10.0.2） | 蓝图建筑（整栋结构 3D 预览：29 个专属结构映射）+ 镰刀/迅捷军刀/压缩方块，含终端机 |
| `kaleidoscope-cookery-pack/` | Kaleidoscope Cookery | `v1_21_1` / `forge_1_20_1` / `fabric_1_21_1` | A 类零适配代表：8 个带 `elements` 几何的 3D 食物/器具 + 平面料理，含终端机 |
| `kaleidoscope-grilling-pack/` | Kaleidoscope Grilling（森罗物语：烟火） | `v1_21_1` / `forge_1_20_1` | 烧烤菜系：烤串/调料/农产（6 个 `elements` 真 3D），含终端机 |
| `kaleidoscope-tavern-pack/` | Kaleidoscope Tavern（森罗物语：酒馆） | `v1_21_1` / `forge_1_20_1` | 酒馆家具 + 基酒/鸡尾酒（酒桶真 3D），含终端机 |
| `kaleidoscope-world-liquor-pack/` | Kaleidoscope World Liquor（森罗酒馆：世界名酒） | `v1_21_1` / `forge_1_20_1`；`v26_1_2` + `v26_2`（26.1.2 官方构建） | 现实名酒 + 鸡尾酒，含终端机 |
| `kaleidoscope-chinesefood-pack/` | Kaleidoscope ChineseFood（森罗厨房：国味） | `v1_21_1` / `forge_1_20_1` | 中华菜肴 + 年俗（黄花鱼刷怪蛋走实体预览），含终端机 |
| `kaleidoscope-doll-pack/` | Kaleidoscope Doll（森罗玩偶） | `v1_21_1` / `forge_1_20_1`（另有 Fabric 线） | 玩偶抽奖机/礼品盲盒（5 个 `elements` 真 3D），含终端机 |
| `kaleidoscope-end-pack/` | Kaleidoscope End（森罗物语：末地） | `v1_21_1` / `forge_1_20_1` | 龙宴料理/可疑方块（另需前置库「森罗万象」），含终端机 |
| `kaleidoscope-nether-pack/` | Kaleidoscope Nether（森罗物语：下界） | `v1_21_1` / `forge_1_20_1` | 下界菜系 + 原始武器，含终端机 |
| `kaleidoscope-twilight-pack/` | Kaleidoscope Twilight（森罗物语：暮色） | `v1_21_1` / `forge_1_20_1` | 暮色森林题材菜系（硬依赖暮色森林本体），含终端机 |
| `kaleidoscope-dim-wine-pack/` | Kaleidoscope: Dimensions Wine（森罗酒馆：次元酒窖） | `v1_21_1` / `forge_1_20_1` | 次元酒名 + 汽水/啤酒花，含终端机 |
| `kaleidoscope-bloodwine-pack/` | Kaleidoscope: Bloodwine（森罗酒馆：血酒） | `v1_21_1` / `forge_1_20_1` | 血酒系列（可与 Vampirism 联动），含终端机 |
| `create-aeronautics-pack/` | Create: Aeronautics | 仅 `v1_21_1` | 航空材料与螺旋桨（A 类零适配），含终端机 |
| `create-pack/` | Create（蓝图预览） | `v1_21_1`（6.x）；`forge_1_20_1` 有 6.0.8 待策略链移植 | `create:schematic` 结构预览（>8192 方块/缺文件降级平面），含终端机 |
| `test-pack-fabric-1.21.1/` | 无（原版 1.21 内容） | 仅 `fabric_1_21_1` | `components` 数据组件 / 1.21 新物品与新附魔 / 标签展开 |
| `test-pack-forge-1.18.2/` | 无（原版 ≤1.18 内容） | 为 `forge_1_18_2` 编写验证 | `tag` NBT 直写（药水/自定义名）/ 标签展开 |


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

> 校验工具：`/csbox validate [box]` 干跑；`scripts/check-ids.py` 扫配置的 id/tag/loot_table
> （可 `--registry` 附 jar 抽取的注册表深度比对）；`scripts/merge-prices.py` 合并价格表；
> `scripts/boxgen.py` 生成新箱子。

## 诚实声明

这些包是**联动机制演示**。2026-11 后新增的包（superb-warfare / immersive-aircraft /
modular-golems / prefab / kaleidoscope-cookery / create-aeronautics / create，及森罗物语系列
10 个附属包 grilling / tavern / world-liquor / chinesefood / doll / end / nether /
twilight / dim-wine / bloodwine）物品 id 均以对应发布 jar 的 `assets/*/models/item/`
清单 × `zh_cn` lang 键交叉实测抽取（森罗系列另核对了 1.21.1 与 1.20.1 双线共有集，
拔刀剑 24 刀逐把抄录 `data/slashblade/slashblade/*.json` 定义并经 RCON 组件语法实测）；
更早的包（apotheosis / irons-spells / tacz）的 id 为示意值，正式使用前请用
`/csbox nbt hand` 或目标模组的 JEI/REI 核对真实注册表 id（版本间可能有差异）。
未装目标模组的客户端上，相关 id 会显示为丢失材质物品，可正常开出、无崩溃
（带 `requires` 门控的包会整箱跳过）。

> 森罗物语家族中 `kaleidoscope-compat`（兼容桥，无自有物品）、
> `kaleidoscope-texture-expansion`（汤锅材质拓展，零物品模型）、
> `kaleidoscope-contraption`（零物品模型）没有可发放内容，无需数据包。

## 如何贡献新包

1. 建 `compat-packs/<mod>/`，放 1–2 个箱子 JSON（schema 见 `docs/CONFIGURATION.md`）。
2. 写 `README.md`：目标模组、平台限制、安装命令、需核对的物品 id。
3. 每个 JSON 在装与不装目标模组两种环境下各开箱验证一次。
4. 提 PR（见 `CONTRIBUTING.md`）。
