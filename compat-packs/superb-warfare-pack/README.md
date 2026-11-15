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
| `sbw_crate.json` | 卓越前线军火箱（csbox）：高稀有度掉特异武器与狙击枪（`superbwarfare:secondary_cataclysm` / `super_star_shooter` / `awm` / `javelin`），中档步枪/机枪，低档手枪、土制武器与配套弹药（`handgun_ammo` / `rifle_ammo` / `shotgun_ammo` / `sniper_ammo` / `heavy_ammo`），grade2 附一个 T-90 载具召唤箱（`sbw_crate_1201.json` 为 Forge 1.20.1 变体，仅载具条目写法不同） |
| `sbw_terminal.json` | 卓越前线军火终端（terminal）：5 档报价更多枪型（含 `qbz_95` / `svd` / `m_98b` / `ntw_20` / `devotion` / `sentinel` 等），手持右键进入交易 |
| `_prices.json` | 全局价格表：SBW 物品为普通 id 平价（无 `#变体` 子键需求；箱子产出的都是默认新枪） |

## 安装（1.21.1 示例）

```bash
cp compat-packs/superb-warfare-pack/*.json config/csbox/
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:sbw_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建，`/csbox reload`
  只刷新已有物品的配置数据），然后 `/csbox give @p csgobox:sbw_terminal`，手持右键打开终端。

## 3D 预览说明

SBW 枪械模型用 separate-transforms 把 GUI 透视指到平面图标，3D 模型只在其余透视渲染
（与展示框所见相同）。CS2-Box 自 2.0.x 起在 `v1_21_1` / `forge_1_20_1` / `fabric_1_21_1`
的 `AnimRenderOps` 内置了对应的 3D 预览分支（命名空间门控 + `FIXED` 透视，零依赖、
未装 SBW 不生效）：开箱滚动条 / 检视屏里的 SBW 枪械按展示框视角显示 3D 模型；
Shader 包（Iris/Oculus）下按既有策略降级为 2D 图标。

## 载具召唤箱条目（2.0.4 起，1.21.1 / 1.20.1）

SBW 的载具是**实体**（坦克/直升机/舰船等），玩家通过**召唤箱子**
（`superbwarfare:container`）召唤：载具类型存在召唤箱子的方块实体数据里
（`EntityType` 字符串字段，值如 `superbwarfare:t_90a`）。在 CS2-Box 箱子 JSON 里
给该物品补上这段 NBT，就得到「开出即得指定载具召唤箱」的条目。

CS2-Box 的预览分支检测到这段数据后，按 **SBW 自己的物品栏显示法**渲染：
SBW 物品栏里召唤箱显示的"载具模型"实际是它自带的一张载具图
（`assets/superbwarfare/textures/vehicle_icon/container/<载具id>.png`，由 SBW
的物品装饰器整格画上去），预览分支在装了 SBW 的客户端运行时直接引用同款贴图
（只引用不搬运），未装 SBW、id 无法解析或该载具没有出图时回退箱子本体图标。

两个版本的数据格式不同（物品 NBT 存储时代的差异），**同一个条目在两个版本要写不同的
JSON**——预览分支两处存储位都读，但箱子 JSON 语法是各平台 codec 各自解析：

- 1.21.1（`v1_21_1` / `fabric_1_21_1`）：必须走 `components` 显式写法。SBW
  `ContainerBlockItem` 读 `minecraft:block_entity_data` 组件；组件 codec
  （`CustomData.CODEC_WITH_ID`）**要求复合里有 `"id"` 字段**（SBW 原生物品经
  `BlockItem.setBlockEntityData` → `BlockEntity.addEntityType` 写入的正是
  方块实体类型 id），缺 `id` 整个组件被丢弃并告警。顶层 `tag` 写法无效
  （非 TACZ 的 `tag` 会整包当 DataComponentPatch 解码，`EntityType` 不是组件 id，
  解析失败被丢弃并告警）：
  ```json
  { "id": "superbwarfare:container", "count": 1,
    "components": { "minecraft:block_entity_data": { "id": "superbwarfare:container",
      "EntityType": "superbwarfare:t_90a" } } }
  ```
- 1.20.1（`forge_1_20_1`）：没有组件系统，走 legacy `tag`——SBW 读
  `tag.BlockEntityTag.EntityType`（Forge 1.20.1 `BlockItem.setBlockEntityData`
  同样经 `addEntityType` 写入 `id`，带上与 SBW 原版数据完全一致）：
  ```json
  { "id": "superbwarfare:container", "count": 1,
    "tag": { "BlockEntityTag": { "id": "superbwarfare:container",
      "EntityType": "superbwarfare:t_90a" } } }
  ```
  （顶层 `tag.EntityType` 也是 CS2-Box 预览分支兼容读取的写法，但嵌套
  `BlockEntityTag` 与 SBW 自己放箱子时的数据形态一致，召唤行为最保真。）

常用载具实体 id（0.8.9.2 实测，完整列表见 SBW jar 的 `ModEntities`）：`t_90a`
（主战坦克）、`ah_6`（直升机）、`a_10a`（攻击机）、`bradley`（步战车）、`mortar`
（迫击炮）、`speedboat`（快艇）、`truck`（卡车）、`type_63`（火箭炮）、
`laser_tower`（激光塔）、`kirov`（基洛夫飞艇，模型 948KB，预览自动缩放）。

