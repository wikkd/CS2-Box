# 森罗物语：厨房（Kaleidoscope Cookery）联动数据包

演示「装了森罗物语就有它的货」的箱子与终端机配置。物品 id 以
`kaleidoscopecookery-1.6.0-neoforge+mc1.21.1` 发布 jar 实测为准（231 个物品）。

> **平台适配矩阵**：官方支持 1.21.1 与 1.20.1（Forge/Fabric 双线）；对应
> `v1_21_1` / `forge_1_20_1`，`fabric_1_21_1` 用 **Refabricated 1.6.0.9**
> （Fabric 社区移植，1.21.1；本包 21 个 id 全在）。官方 Fabric 1.21.1 线
> 停更在 1.0.1，该版本缺 12 个本包 id（3D 食物/器具为主），不要退回。
> 无 26.x 构建。本配置带
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
`elements` 实体几何（`teapot_3d` / `shawawa_spit` / `eight_immortals_table` /
`cold_cut_ham_slices_block` / `fruit_basket_full` / `stockpot_lid_normal` /
`stockpot_lid_using` / `trash_can`），这些物品在开箱 3D 与检视屏里走默认
baked-model 路径直接渲染真实几何，**零适配代码**；其余 223 个为平面贴图
料理（箱子 grade1-2 的主力），预览与原版物品一致。本包同时验证默认路径
auto-fit 对非 1 格几何的居中表现。

## 1.20.1 基准部署（2026-11-16，测试基准平台）

1.20.1 Forge 线 jar 全部是 SRG 生产形态（FG7 dev 运行时为官方成员名），
进 dev 前须重映射：`scripts/remap-1201.sh` 统一管线批量处理
`local-repo/com/kaleidoscope/1.20.1/` 的 14 jar（本体 1.6.0 + 12 附属 +
curios 5.14.1 前置，Modrinth 拉取；twilight/chinesefood 两个不装载的
不在 `remap-1201-manifest.txt` 清单内）。Create 6.0.8（contraption 的
硬前置）同在清单（本体 + jarjar 内 Ponder/flywheel/Registrate 三层）。
管线同拔刀剑（`TaczDeobf` + refmap 重写）。

2026-11-16 实测：dev server `Done (2.193s)`、33 箱全载零告警、RCON 探针
9 箱（kc/kg/kt/kwl/kd/ke/kn/kdw/kbw）`/csbox info` 全绿。**不装载 2 个**：

- `kaleidoscopechinesefood`（中华料理）：与本体同装必炸——`ModFoods`
  的食物效果 supplier（`lava_swim` 等）被本体 `BowlFoodBlockItem` 在
  block 注册阶段立即 `.get()`，而 mob_effect 注册晚于 block，Forge 47
  `RegistryObject.get()` 严格 requireNonNull → NPE。上游注册顺序 bug
  （1.21.1 NeoForge 不触发——Holder 语义不同），生产环境同装同样会炸。
- `kaleidoscope-twilight`（暮色联动）：同型——`KTTeacups` 的 `fire_breath`
  效果 supplier 被本体 `TeacupItem` 构造期消费。另需暮色森林本体。

两者移出 dev 后其余 9 包（含本体）全链路通过。附属 jar 装载还需
`kaleidoscope_tavern`（end/nether/dim-wine/bloodwine 的 mandatory 链）。
