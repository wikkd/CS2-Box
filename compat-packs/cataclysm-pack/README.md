# 灾变（L_Ender's Cataclysm）箱子包

- **目标模组**：L_Ender's Cataclysm（mc 1.20.1 Forge 线 3.31 实测）+ 前置 Lionfish API 3.0
- **平台限制**： Cataclysm 有 1.20.1/1.21.1（NeoForge）构建；本包 JSON 为 1.20.1 通用形态
- **物品池**：BOSS 刷怪蛋（利维坦/咒翼灵骸/远古遗魂）上 grade5，炼狱装备/巨兽武器中高段，
  渊灵/骸龙/魂尸系列蛋与黑钢材料铺底
- **id 来源**：3.31 发布 jar `assets/cataclysm/models/item/` 清单 × `zh_cn` lang 键交叉实测
- **文件**：`cataclysm_crate.json` + `cataclysm_terminal.json`（新增终端配置需重启游戏）
- **安装**：两个 JSON 复制进 `config/csbox/`，价格片段用 `python scripts/merge-prices.py
  compat-packs/cataclysm-pack/_prices.json config/csbox/_prices.json` 合并（勿直接整表覆盖）
- **价格**：初值按既有经济锚定（diamond 武器 20000 档、mob 蛋 500-3000），可按服调整
