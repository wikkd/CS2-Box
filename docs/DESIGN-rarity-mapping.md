# RFC：稀有度映射协议（跨模组品级）

> 状态：**RFC 草案，待评审**。核心事实来自深度研究（2026-09）：
> - Apotheosis 26.1 已组件化：`AffixHelper.setRarity` + `apotheosis:rarity` 组件，
>   稀有度由数据驱动动态注册表 `RarityRegistry` 管理（common/uncommon/rare/epic/mythic）。
> - 原版 1.20.5+ 有 `minecraft:rarity` 组件（决定名字颜色）；1.20.1 只有 Item 级枚举。
> - **无现成「A 品级→B 品级」协议**，需自建。
> 本 RFC 只定义协议与数据面，实现排期待评审后决定。

## 1. 问题

CS2-Box 有 5 档稀有度（consumer → classified，权重驱动）。玩家开出的物品目前只带
`csgobox:grade`（1..5 整数，供拆解计价）。其它模组（Apotheosis 词缀、Iron's Spells 卷轴、
原版 1.20.5+ rarity）各有自己的品级体系，互不认识。目标：**开出的物品带上一个
「语义级稀有度」，可被目标模组读取 / 消费**。

## 2. 协议草案（最小集）

### 2.1 语义载体

- 新增物品组件 `csgobox:rarity`（String：`consumer/industrial/mil_spec/restricted/classified`），
  与现有 `GRADE` 组件并列（冗余写，便于外部只读）。
- 1.20.1 回退写 ItemStack tag `csgobox.rarity`（**双写**，复用现有 `GRADE`/`CUSTOM_DATA`
  双写先例）。
- 无需 BlockEntity NBT——箱子内容物本身就是 ItemStack。

### 2.2 映射表（数据驱动）

`config/csbox/rarity_map.json`（或 datapack `data/csgobox/csbox/rarity_map.json`）：

```json
{
  "defaults": { "color": { "consumer": "0xAAAAAA", "classified": "0xFFAA00" } },
  "targets": {
    "apotheosis": { "classified": "apotheosis:mythic", "restricted": "apotheosis:epic", "mil_spec": "apotheosis:rare" },
    "vanilla":    { "classified": "epic", "restricted": "rare", "mil_spec": "uncommon" }
  }
}
```

- 缺目标模组 → 忽略该列；目标品级缺失/重载后回退 common 默认（不编造对方不存在的品级）。
- 默认无映射表时只写 `csgobox:rarity` 语义，不做跨模组翻译。

### 2.3 写入方

- 服务端开箱生成点写入（与 `GRADE` / nameColor 同一出口），客户端只读。
- 装 Apotheosis 时可选调 `AffixHelper.setRarity`（弱依赖：`compileOnly` + `isLoaded` +
  反射兜底，失败静默回退）。

### 2.4 语义 vs 表现

- `csgobox:rarity` 是**语义层**；名字颜色是**表现层**（现有 `nameColor` 注入 style）。
- 协议只写语义，颜色不双源——避免两套颜色打架。

## 3. 六平台同步

- 26.1/26.2/1.21.1 写组件，1.20.1 写 tag。
- `PacketBoxOpenResult` 等已处理 `CUSTOM_DATA`，新组件纳入同一打包/剥离路径。
- 旧档迁移：读旧 tag → 补写组件（幂等）。

## 4. 风险

- Apotheosis rarity 是动态注册表，reload 后 `DynamicHolder` 可能 unbound → 弱依赖需容错。
- 双写横跨六平台，测试矩阵大（`GRADE`/`CUSTOM_DATA` 先例可复用）。
- **无先例**，协议需自建并文档化；对无公开 API 的模组（原版 1.20.1）只能写 custom_data，
  对方无法原生读取 → 明确「写语义 vs 写表现」边界。

## 5. 评审问题

1. 语义载体用组件 `csgobox:rarity` 还是直接写目标模组的组件？
2. 映射表放 `config/` 还是 datapack（服务端权威 vs 客户端可读）？
3. 是否本期就接 Apotheosis 弱依赖，还是先只出语义组件？
4. 与「联动数据包（compat-packs/）」的关系：是否让 compat-packs 的箱子显式声明
   产出品级映射？

## 6. 工作量估算（评审后）

- 组件 + tag 双写：小。
- 映射表加载/校验：中。
- Apotheosis 弱依赖：中-高。
- 六平台回归：高。
