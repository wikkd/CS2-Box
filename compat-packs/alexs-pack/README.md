# Alex's Mobs + Alex's Caves 箱子包

- **目标模组**：Alex's Mobs 1.22.9 / Alex's Caves 2.0.2（1.20.1 Forge 实测）+ 共同前置 Citadel 2.6.3
- **平台限制**：AM 还有 1.18.2 构建；AC 仅 1.20.1 线
- **物品池**：虚空蠕虫/震龙王/巨兽龙 BOSS 蛋上 grade5（注意两家蛋 id 均为 `spawn_egg_<mob>`
  前缀形态，与常规 `<mob>_spawn_egg` 相反，编纂箱子时勿混用）；AM 装备/AM 洞穴武器分档
- **id 来源**：两 jar `models/item/` × `zh_cn` lang 交叉实测；蛋 id 全部逐条对照模型清单
- **文件**：`alexs_crate.json` + `alexs_caves_crate.json` + `alexs_terminal.json`（双模组合用终端）
- **安装**：三个 JSON 复制进 `config/csbox/`，价格片段 merge-prices 合并；终端机配置需重启生效
