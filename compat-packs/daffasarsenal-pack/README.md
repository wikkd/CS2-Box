# Daffa's Arsenal 联动数据包

TACZ 内容包（印尼系军火，57 枪 + 6 弹药 + 18 附件，含大量彩蛋枪）。枪械仍是
`tacz:modern_kinetic_gun` 的 `GunId` 变体（命名空间 `daffas_arsenal`），弹药
`tacz:ammo` + `AmmoId`、附件 `tacz:attachment` + `AttachmentId`——与 tacz-pack
同形态，只是 id 换命名空间。本文档基于 **Daffa's Arsenal 3.8.0.2**
（Modrinth `11K9aTv9`）发布 jar 实测：57 枪 6 弹 18 附件逐一核对，价格表 81 键
全部在 jar 的 `addon/daffas/data/daffas_arsenal/index/` 清单内（57 枪全定价，
无遗漏）。

> TACZ 生态摸底结论（2026-11-22）：头部包里 **daffasarsenal** 是真内容包
> （收编见本包）；**taczaddon**（mafyU404，6.9M 下载）是纯机制附加
> （弹药消耗编排等，无枪械定义，不收编）；**lr-tactical**（4.1M）是独立注册
> 的战术医疗 mod（非 TACZ 枪包，归独立模组候选另议）。

## 文件

| 文件 | 内容 |
|---|---|
| `daffa_crate.json` | Daffa 军火箱（csbox）：grade5 掉 auxur 重狙 / wawan20002 / mg338 / 彩蛋重型，低档掉手枪与 `acp45`/`water` 弹药 |
| `daffa_terminal.json` | Daffa 军火终端（terminal）：5 档报价枪械 + 弹药 + 附件（含 `scope_wawan2000` 等枪包专属瞄具） |
| `_prices.json` | 全局价格表片段：`tacz:modern_kinetic_gun#daffas_arsenal:<枪>` / `tacz:ammo#daffas_arsenal:<弹>` / `tacz:attachment#daffas_arsenal:<附件>` 共 81 键 |

## 安装（1.20.1 示例）

```bash
cp compat-packs/daffasarsenal-pack/daffa_crate.json compat-packs/daffasarsenal-pack/daffa_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/daffasarsenal-pack/_prices.json config/csbox/_prices.json
```

`_prices.json` 必须**合并**而不是覆盖（直接 `cp *.json` 会把全局价格表冲成
只剩 Daffa 片段）。

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:daffa_crate`。
- 终端机：**新增配置文件后需重启游戏**，然后
  `/csbox give @p csgobox:daffa_terminal`，手持右键打开终端。

## 门控

`requires: ["tacz", "daffas_arsenal"]`（jar 实测 mod id 带下划线）——只装 TACZ 不装 Daffa's Arsenal 时
整箱跳过并在 `/csbox info error` 报错（GunId 变体只有在枪包加载后才存在）。

## 价格口径

对齐 tacz-pack 刻度：手枪 2000-3000、冲锋枪 4800-5600、霰弹 4500-6400、
突击步枪 6000-8000、机枪 10000-13000、狙击 12000-15000、重型彩蛋
12000-14000；弹药 acp45=200 / elektrikal=400 / flechette=600 / 40mm=800 /
500=.50BMG=900 / water=100；附件 400-2600（枪包专属高端瞄具上限 2600）。
