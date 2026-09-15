# Apotheosis 联动数据包

演示「装了 Apotheosis（神化）就有它的货」的箱子配置。把本目录下的 JSON 复制到
`config/csbox/`（文件名即箱子 ID），然后 `/csbox reload`。

> **诚实声明**：本示例演示联动机制，物品 id 为示意（`apotheosis:gem` 的宝石
> 具体种类由 NBT/组件决定，版本间可能有差异）。正式使用前请用 `/csbox nbt hand`
> 或对方模组的 JEI/REI 核对真实注册表 id。

## 文件

| 文件 | 内容 |
|---|---|
| `gem_crate.json` | 普通箱：高稀有度档掉 Apotheosis 宝石 + 词缀化装备（原版武器，装上 Apotheosis 后可被其 loot 体系词缀化） |

## 安装

```bash
cp compat-packs/apotheosis-pack/*.json config/csbox/
```

进游戏 `/csbox reload`，然后 `/give @p csgobox:gem_crate`。

## 说明

- **词缀化**：CS2-Box 本身不施加 Apotheosis 词缀（那是「稀有度映射协议」的后续工作）。
  本包让「高稀有度掉 Apotheosis 宝石 + 高品质武器」，词缀由 Apotheosis 自己的 loot
  机制在别处生成。
- 未装 Apotheosis 时 `apotheosis:gem` 会显示为丢失材质物品（可正常开出，无 id 崩溃）。
