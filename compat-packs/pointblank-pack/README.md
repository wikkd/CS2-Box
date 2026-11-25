# Point Blank（点防御）联动数据包

演示「装了 Point Blank 就有它的货」的箱子配置。Point Blank 的枪械是
**数据驱动的普通注册物品**（`data/pointblank/items/*.json` 声明，61 把枪全部
是裸 id、零 NBT 变体），箱子/终端条目直接写 `pointblank:<枪名>` 即可，比
TACZ 的 `GunId` tag 形态简单得多。

本文档基于 **Point Blank 2.2.0**（2026-09-11，Modrinth `og4KPYmA`）的
发布 jar 实测：61 个枪械定义（`data/pointblank/items/`）、151 个物品模型
（`assets/pointblank/models/item/`）逐一核对，价格表 147 键全在 jar 清单内。

## 预览适配（零代码）

Point Blank 的物品渲染形态与我们既有管线天然对齐，**不需要任何平台代码**：

- **枪械**：物品模型走 `forge:separate_transforms`——gui 透视指向平面图标
  （`item/ak47.icon`），其余透视落到 `builtin/entity` 基模。与 SBW 枪械
  完全同构。
- **配件 / 弹药**：纯 `builtin/entity`，物品栏直接经 BEWLR 渲染真 3D。
- BEWLR 后面是 `GunItemRenderer`（geckolib `GeoItemRenderer` 子类），顶点
  经传入的 `MultiBufferSource` 输出，`isSupportedItemDisplayContext` 恒
  `true`（FIXED 可渲染）——预览走既有的 BEWLR 重路由（`renderFixedContextModel3D`
  + FIXED 顶点探针 + auto-fit 居中），三个策略链平台（v1_21_1 /
  forge_1_20_1 / fabric_1_21_1）全部命中。
- **26.x 平台**（v26_1_2 / v26_2）：物品状态管线原生处理 builtin/entity，
  配件/弹药得真 3D；枪械 gui 透视吃平面图标（预期行为，同 SBW 无 26.x
  构建时的降级形态）。
- 探针 0 顶点 / 渲染异常时自动降级平面图标，与整条管线一致。

## 平台矩阵与 geckolib 前置

Point Blank 2.2.0 覆盖全部五个主力平台（Modrinth 实测）：forge_1_20_1、
v1_21_1（NeoForge 1.21.1）、fabric_1_21_1、v26_1_2（NeoForge 26.1.2）、
v26_2 若跟进 26.1.2 线亦可。geckolib 是 **required 前置**（未 jarjar 内嵌），
各版本要求：forge 1.20.1 = `[4.4.7,)`、NeoForge 1.21.1 = `[4.9.2,)`、
Fabric 1.21.1 = `>=4.5.8`。

> **已知冲突**：SBW 0.8.9.2（NeoForge 1.21.1）jarjar 内嵌 geckolib 4.7.5，
> 低于 Point Blank 要求的 `[4.9.2,)`——1.21.1 NeoForge 上**两者装一起会
> 在启动期失败**（JarJar 只会解析出 4.7.5 一个候选）。等任一上游更新
> geckolib 后自然解除；1.20.1（`[4.4.7,)`）与 Fabric（独立安装 geckolib）
> 无此问题。

## 文件

| 文件 | 内容 |
|---|---|
| `pb_crate.json` | Point Blank 军火箱（csbox）：grade5 掉标枪导弹/转管机枪/GM6 狙击/XM29 实验步枪，低档掉手枪、弹药（`ammo9mm` / `ammo556`）与枪合金锭 |
| `pb_terminal.json` | Point Blank 军火终端（terminal）：5 档报价枪械 + 弹药（PB 自带贸易经济，终端形态贴合其玩法） |
| `_prices.json` | 全局价格表片段：61 枪 + 16 弹 + 配件/瞄具/枪口 + 皮肤 + 材料共 147 键 |

## 安装（1.20.1 示例）

```bash
cp compat-packs/pointblank-pack/pb_crate.json compat-packs/pointblank-pack/pb_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/pointblank-pack/_prices.json config/csbox/_prices.json
```

`_prices.json` 必须**合并**而不是覆盖（直接 `cp *.json` 会把全局价格表冲成
只剩 Point Blank 片段，其他箱子的物品全部变成未定价）。

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:pb_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在 RegisterEvent 注册阶段
  创建，`/csbox reload` 只刷新已有物品的配置数据），然后
  `/csbox give @p csgobox:pb_terminal`，手持右键打开终端。

## 价格口径

- 锚点：PB 自带贸易价（`tradePrice`，仅 9 把枪有值：glock17=1000、
  citoricxs=2000 等）× 2 换算进我们经济刻度。
- 无贸易价的枪按强度对齐 tacz-pack / superb-warfare-pack 既有刻度：
  手枪 1600-3000、冲锋枪 3200-6800、霰弹 4500-8000、突击步枪 5000-8000、
  精准/狙击 8500-16000、机枪 10000-13000、重型/发射器 14000-22000。
- 弹药 150-900（338lapua/50bmg 高一档 800-900），配件/瞄具 400-2600，
  皮肤统一 3000，材料按合成链深度 50-2500。
