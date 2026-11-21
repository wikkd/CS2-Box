# Simply Swords 箱子包

- **目标模组**：Simply Swords 1.70.2-1.20.1（Forge；前置 architectury + fzzy_config + simplytooltips）
- **平台限制**：SS 三线（1.18.2/1.20.1/1.21.1）全有构建，本包 1.20.1 形态；**dev 无法联测**——前置 fzzy_config 强依赖 Kotlin for Forge（FG7 dev 死路，Cobblemon 同因），重映射产物为 `!` 休眠件，生产环境验证
- **物品池**：传奇武器（觉醒巫妖刃/苍穹/紫晶秘银）grade5，血潮/霜落/烬刃等史诗 grade4，
  remnant 形态（只有 contained/tampered/empowered 三型，无 dormant/decaying——id 实测）与
  netherite 系 grade3；SS **没有** wooden/stone/golden 低阶材质（铁起）
- **id 来源**：1.70.2 发布 jar `models/item/` 清单逐条实测；gobber_compat 子路径条目依赖
  gobber 模组，未收录
- **文件**：`ss_crate.json` + `ss_terminal.json`
