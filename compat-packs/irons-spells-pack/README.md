# Iron's Spells 'n Spellbooks 联动数据包

演示「装了 Iron's Spells（法术与魔法书）就有它的货」的箱子配置。把 JSON 复制到
`config/csbox/`，然后 `/csbox reload`。

> **诚实声明**：物品 id 为示意（`irons_spellbooks:scroll` 的具体法术由
> `irons_spellbooks:spell` 组件决定；`upgrade_orb` 类型同理）。正式使用前用
> `/csbox nbt hand` 或 JEI/REI 核对真实 id 与组件。

## 文件

| 文件 | 内容 |
|---|---|
| `spellbook_crate.json` | 普通箱：高稀有度掉法术卷轴 / 升级球，低档掉法系材料 |

## 安装

```bash
cp compat-packs/irons-spells-pack/*.json config/csbox/
```

进游戏 `/csbox reload`，然后 `/give @p csgobox:spellbook_crate`。

## 说明

- **卷轴带法术**：卷轴必须有 `irons_spellbooks:spell` 组件才有意义（否则是空卷轴）。
  示例只演示「能开出来」，具体法术请在组件里写 `"irons_spellbooks:spell": {...}`。
- 未装 Iron's Spells 时相关 id 显示为丢失材质，可正常开出。
