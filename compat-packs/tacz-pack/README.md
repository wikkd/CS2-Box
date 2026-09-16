# TACZ（永恒枪械工坊）联动数据包

演示「装了 TACZ 就有它的货」的箱子配置：**真枪型**（带 `GunId` 的
`tacz:modern_kinetic_gun`，开出来可直接开火）与配套弹药，含普通箱与终端机两种。
把 JSON 复制到 `config/csbox/`，然后 `/csbox reload`。

> **平台限制**：TACZ 仅 `v1_21_1`（NeoForge 1.21.1）与 `forge_1_20_1`（Forge 1.20.1）
> 两个平台有集成（检视视口）。本配置带 `"requires": ["tacz"]` 门控：**未装 TACZ 时
> 整箱跳过并在 `/csbox info error` 报错**，不会再出现紫黑块/裸枪。

## 文件

| 文件 | 内容 |
|---|---|
| `gun_crate.json` | 军火枪械箱（csbox）：高稀有度掉真枪型 TACZ 枪械（`tacz:ak47` / `tacz:m4a1` / `tacz:ai_awp` / `tacz:kar98` / `tacz:rpk` / `tacz:sks_tactical` / `tacz:m1911` / `tacz:glock_17`），低档掉配套弹药（`tacz:ammo` + `AmmoId`）与火药材料 |
| `tacz_terminal.json` | TACZ 军火终端（terminal）：5 档报价 TACZ 枪械 + 弹药，放置后右键进入交易；报价物品可用「检视」预览 / TACZ 第一人称检视 |
| `_prices.json` | 全局价格表：`tacz:modern_kinetic_gun#<GunId>` / `tacz:ammo#<AmmoId>` 子键区分各枪/弹售价（v2.0.1 起终端价格统一由价格表管理，箱子 JSON 里不再写 `price`） |

## 安装（1.20.1 示例）

```bash
cp compat-packs/tacz-pack/*.json config/csbox/
```

- 箱子：进游戏 `/csbox reload`，然后 `/give @p csgobox:gun_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在 RegisterEvent 注册阶段创建，
  `/csbox reload` 只刷新已有物品的配置数据），然后
  `/give @p csgobox:tacz_terminal`，放置方块右键进入交易。

## 各平台正确写法（重要）

- **forge_1_20_1（1.20.1，纯 NBT）**：TACZ 枪械 NBT 存 ItemStack **顶层 tag**，
  配置里用 `tag` 字段（SNBT 字符串）：
  ```json
  { "id": "tacz:modern_kinetic_gun", "count": 1,
    "tag": "{GunId:\"tacz:ak47\"}" }
  ```
  弹药同理：`{ "id": "tacz:ammo", "count": 64, "tag": "{AmmoId:\"tacz:762x39\"}" }`。
   `tag` 同时支持 **SNBT 字符串**与 **JSON 对象**两种写法（JSON 布尔自动转 byte、
   数字自动转 int，`GunCurrentAmmoCount` 写成字符串也会被纠正）：
   ```json
   { "id": "tacz:modern_kinetic_gun", "count": 1,
     "tag": { "GunId": "tacz:ak47", "GunCurrentAmmoCount": 30 } }
   ```
   加载器采用容错校验：**缺 `GunId` 的 TACZ 枪保留并告警**（裸枪无法开火，
   `/csbox info error` 可见），`GunFireMode` 大小写写错会自动纠正，附件/弹药数量等
   小差异自动修复或仅告警，不会整条作废；`GunId` 指向未加载的枪包只告警不拒绝。

- **v1_21_1（1.21.1，DataComponent）**：TACZ 键位于 `minecraft:custom_data` 内层，
  配置里用 `components` 字段，**不要用 `tag`**（顶层 `{GunId:...}` 会被当组件名解析，
  开出来是裸枪）：
  ```json
  { "id": "tacz:modern_kinetic_gun", "count": 1,
    "components": { "minecraft:custom_data": { "GunId": "tacz:ak47" } } }
  ```

- **枪械 id 以 TACZ 注册表为准**（1.20.1 官方内置 54 把，如 `tacz:ak47`、
  `tacz:m4a1`、`tacz:ai_awp`、`tacz:deagle`、`tacz:hk_mp5a5`、`tacz:vector45`、`tacz:rpg7`；
  弹药 id 如 `tacz:9mm` / `tacz:45acp` / `tacz:556x45` / `tacz:762x39` / `tacz:12g`）。
  想要自定义皮肤/附件/火控，推荐工作流：TACZ 里组装好枪 → `/csbox nbt hand`
  复制序列化片段 → 原样粘贴，**不要手改键名**（`GunId` / `GunFireMode` /
  `GunCurrentAmmoCount` / `AmmoId` / 附件键大小写严格）。

- 开出的枪在 `v1_21_1` / `forge_1_20_1` 可进检视视口（`CsLookItemScreen` 内 3D 检视；
  终端机报价面板「检视」胶囊对 TACZ 枪同样触发第一人称检视）。

- **查已加载 ID（推荐工作流）**：装好 TACZ 后进游戏跑
  `/csbox tacz list [guns|ammo|attachments] [namespace] [json]`，直接浏览当前安装枪包的
  真实注册 ID 与元信息（枪械类别/用弹/弹匣容量、弹药堆叠/反查用枪、附件槽位），
  第三方枪包无需查文档猜 ID；`json` 模式输出可点击复制的 JSON 数组，供配置/工具引用。