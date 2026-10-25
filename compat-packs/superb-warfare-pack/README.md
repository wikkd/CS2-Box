# 卓越前线（Superb Warfare）联动数据包

演示「装了卓越前线就有它的货」的箱子配置。Superb Warfare 的枪械是**普通注册物品**
（`superbwarfare:ak_47` 等 43 把，id 以 0.8.9.2 双平台发布 jar 实测为准），**不需要任何
NBT**：枪械数据（弹药/等级/配件槽）由 SBW 自身在物品入包 tick 时自动初始化，开出即可用。
把 JSON 复制到 `config/csbox/`，然后 `/csbox reload`。

> **平台限制**：Superb Warfare 官方支持 NeoForge 1.21.1（对应 `v1_21_1`）与
> Forge 1.20.1（对应 `forge_1_20_1`）；Fabric 1.21.1 经非官方移植
> Superb Warfare ReFabricated（对应 `fabric_1_21_1`，内容不保证与官方一致）。
> 本配置带 `"requires": ["superbwarfare"]` 门控：**未装 SBW 时整箱跳过并在
> `/csbox info error` 报错**。SBW 运行时依赖 Kotlin for Forge（用户侧自行安装，
> geckolib/sbm 等已由 SBW jarjar 自捆绑）。

## 文件

| 文件 | 内容 |
|---|---|
| `sbw_crate.json` | 卓越前线军火箱（csbox）：高稀有度掉特异武器与狙击枪（`superbwarfare:secondary_cataclysm` / `super_star_shooter` / `awm` / `javelin`），中档步枪/机枪，低档手枪、土制武器与配套弹药（`handgun_ammo` / `rifle_ammo` / `shotgun_ammo` / `sniper_ammo` / `heavy_ammo`） |
| `sbw_terminal.json` | 卓越前线军火终端（terminal）：5 档报价更多枪型（含 `qbz_95` / `svd` / `m_98b` / `ntw_20` / `devotion` / `sentinel` 等），放置后右键进入交易 |
| `_prices.json` | 全局价格表：SBW 物品为普通 id 平价（无 `#变体` 子键需求；箱子产出的都是默认新枪） |

## 安装（1.21.1 示例）

```bash
cp compat-packs/superb-warfare-pack/*.json config/csbox/
```

- 箱子：进游戏 `/csbox reload`，然后 `/give @p csgobox:sbw_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建，`/csbox reload`
  只刷新已有物品的配置数据），然后 `/give @p csgobox:sbw_terminal`，放置方块右键进入交易。

## 3D 预览说明

SBW 枪械模型用 separate-transforms 把 GUI 透视指到平面图标，3D 模型只在其余透视渲染
（与展示框所见相同）。CS2-Box 自 2.0.x 起在 `v1_21_1` / `forge_1_20_1` / `fabric_1_21_1`
的 `AnimRenderOps` 内置了对应的 3D 预览分支（命名空间门控 + `FIXED` 透视，零依赖、
未装 SBW 不生效）：开箱滚动条 / 检视屏里的 SBW 枪械按展示框视角显示 3D 模型；
Shader 包（Iris/Oculus）下按既有策略降级为 2D 图标。
