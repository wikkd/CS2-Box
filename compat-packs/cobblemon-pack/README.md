# Cobblemon（宝可梦）箱子包

- **目标模组**：Cobblemon Forge 1.5.2+1.20.1（前置 **Kotlin for Forge**，mods.toml
  `modLoader="kotlinforforge"` 硬依赖）
- **平台限制**：**dev runClient/runServer 无法联测**——KFF 在 FG7 dev 无法注册语言提供者
  （create_jetpack 同先例）；重映射产物以 `!` 休眠件形态保留在重映射清单，本包数据照常交付，
  装载验证与 L4 在生产环境（玩家装 KFF + Cobblemon）进行
- **物品池**：大师球 grade5；远古球系/化石球系/击掌柜 grade4；现代功能球系 grade3；
  进化石/携带物 grade2；树果/杏子/伤药与基础球 grade1
- **id 来源**：1.5.2 发布 jar `models/item/` 清单实测（`*_ball_model` 为球体几何内件，不入池）
- **文件**：`cobblemon_crate.json` + `cobblemon_terminal.json`
- **安装**：JSON 复制进 `config/csbox/`，价格片段 merge-prices 合并；箱子在 cobblemon 缺席时
  自动跳过装载（requires 门控），装上模组 + KFF 后 `/csbox reload` 生效
