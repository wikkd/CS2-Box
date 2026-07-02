<!-- generated-by: gsd-doc-writer -->
# v1.0.5 手动测试用例

本文档针对 CS2-Box 模组 **v1.0.5**（对应 git tag `v1.0.5`，commit `d2122c4`，构建产物 `csgobox-1.0.5.jar`）的手动测试用例。

测试目标：验证 v1.0.5 新增/修复/移除的功能在真实游戏环境中按预期工作，并捕获回归缺陷。

**测试方式：** 在 NeoForge 1.21.1 客户端 + 集成服务器（开发模式 `runs/`）中执行；单人世界可作为客户端流程的快速回路。

---

## 0. 测试准备

### 0.1 环境前置

- [ ] JDK 21 已安装并设为默认（`gradle.properties` 中已锁定 `org.gradle.java.home`）
- [ ] 客户端运行目录 `runs/client/` 已存在（含 `config/csgobox.toml`、`mods/`）
- [ ] 服务端运行目录 `runs/server/` 已存在
- [ ] 构建产物：`./gradlew build` 成功，jar 输出于 `build/libs/csgobox-1.0.5.jar`
- [ ] **建议清理旧配置：** 首次执行测试前删除 `runs/client/config/csgobox.toml` 和 `runs/server/config/csgobox.toml`，避免历史脏数据影响

### 0.2 测试资源

- [ ] 一份超平坦创造世界（`/gamemode creative`）
- [ ] 一份普通生存世界（用于测试实体掉落与首次成就）
- [ ] 命令方块或 `/give` 命令获取测试物品

### 0.3 关键命令速查

| 命令 | 用途 |
| --- | --- |
| `/gamemode creative` | 切换创造模式 |
| `/give @s csgobox:csgo_box` | 获得 CS:GO 箱子（默认 box） |
| `/give @s csgobox:csgo_key0` | 获得 0 级钥匙 |
| `/give @s csgobox:csgo_key2` | 获得钻石钥匙（锻造前置） |
| `/give @s minecraft:netherite_ingot` | 锻造台添加物 |
| `/give @s minecraft:netherite_upgrade_smithing_template` | 锻造模板 |
| `/advancement revoke @s only csgobox:first_box` | 撤销 first_box 成就（保留其他） |
| `/advancement revoke @s through csgobox:root` | 撤销 csgobox:root 及其所有子成就（重置整个 CS2 Box 标签页） |
| `/advancement revoke @s everything` | 撤销该玩家所有命名空间的所有成就（含 csgobox） |
| `/advancement grant @s only csgobox:root` | 调试用：强制解锁 root（仅诊断） |
| `/csbox list` | 查看已注册箱子 |
| `/csbox info csgobox:weapon_supply_box` | 查看默认箱子详情（默认 box 文件名是 `weapon_supply_box.json`） |
| `/csbox info weapon_supply_box` | 同上；`info` 自动补全 `csgobox:` 命名空间 |

### 0.4 测试用例填写约定

每条用例记录：

- ✅ 通过  ❌ 失败  ⚠️ 部分通过（含备注）
- 失败时填写：复现步骤、实际表现、log 关键片段（`runs/client/logs/latest.log`）

---

## 1. 回归测试：CsboxConfig init() 修复（CRITICAL）

**对应变更：** CHANGELOG `[1.0.5] ### 修复` 段 — `CsboxConfig` 字段初始化修复

**背景：** v1.0.5 之前的临时发布 `862ab1f` 中 `CsboxConfig` 采用 `init()` 延迟填充模式，但 `init()` 从未被调用，导致所有配置驱动字段在运行时为 0/false。`switch (CONFIG.animationSpeed)` 在首次动画 tick 抛 `NullPointerException`，任何玩家开箱即崩溃。本次修复将 `.get()` 内联到构造器，删除 `init()`。

### TC-1.1 开箱不崩溃（默认配置）

| 项 | 内容 |
| --- | --- |
| 前置 | 全新 `runs/client/config/csgobox.toml`（默认值） |
| 步骤 | 1. 启动客户端进入超平坦创造世界<br>2. `/give @s csgobox:csgo_box` ×1 + `/give @s csgobox:csgo_key0` ×1<br>3. 手持箱子按右键打开界面<br>4. 选择物品按"开启"完成动画 |
| 预期 | ① 界面正常打开 ② 动画正常播放（约 145 tick） ③ 揭晓界面正常显示获奖物品 ④ **无崩溃、无 NPE** ⑤ `latest.log` 无 `NullPointerException` |
| 结果 | ☐ |

### TC-1.2 实体掉落使用全局配置（globalDropRatePercent=100）

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置；`globalDropRatePercent = 100` |
| 步骤 | 1. 切换生存模式<br>2. 用附魔抢夺 III 的剑击杀一只僵尸<br>3. 检查掉落物是否包含 `csgobox:csgo_box` |
| 预期 | ① 箱子确实可能掉落（默认 drop rate 列表中僵尸命中时）② 抢夺 III 倍率生效：若原概率 5%，实际约 5%×2.5=12.5% ③ `enableDebugLogging = true` 时日志中打印实际 effectiveRate |
| 结果 | ☐ |

### TC-1.3 调试日志开关生效

| 项 | 内容 |
| --- | --- |
| 前置 | 编辑 `runs/client/config/csgobox.toml`：`enableDebugLogging = true` |
| 步骤 | 1. 启动客户端<br>2. 触发任意一次开箱<br>3. 查看 `latest.log` |
| 预期 | 出现 `csgobox:` 前缀的详细 DEBUG 日志（`CONFIG.enableDebugLogging()` 读到 `true`，而非旧版的 `false`） |
| 结果 | ☐ |

### TC-1.4 默认 box 自动加载（loadDefaultBoxes=true）

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置；`loadDefaultBoxes = true` |
| 步骤 | 1. 启动客户端<br>2. 执行 `/csbox list` |
| 预期 | 至少列出 `csgobox:weapon_supply_box`（由 `config/csbox/weapon_supply_box.json` 自动生成） |
| 结果 | ☐ |

### TC-1.5 物品名称预览（showItemNames=true）

| 项 | 内容 |
| --- | --- |
| 前置 | `showItemNames = true` |
| 步骤 | 1. 打开 `CsboxScreen` 浏览物品<br>2. 鼠标悬停任意物品 |
| 预期 | 显示物品悬浮提示（tooltip），`showItemNames()` 读取为 `true` |
| 结果 | ☐ |

### TC-1.6 三种音效音量生效

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置（`openSoundVolume=100`、`tickSoundVolume=50`、`finishSoundVolume=100`） |
| 步骤 | 1. 开启一个箱子，完整播放动画<br>2. 听三个时机：① 开箱瞬间 ② 滚动 tick ③ 揭晓瞬间 |
| 预期 | 三个音量分别符合配置默认值；音量非 0（旧 bug：均为 0 = 无声） |
| 结果 | ☐ |

### TC-1.7 动画速度三档可切换

| 项 | 内容 |
| --- | --- |
| 前置 | 分别设置 `animationSpeed = SLOW / NORMAL / FAST` |
| 步骤 | 1. 每次设置后启动客户端，开启一个箱子<br>2. 记录从打开到揭晓的墙钟时间 |
| 预期 | SLOW > NORMAL > FAST；任一档下不抛 NPE（旧 bug 触发点：`switch (CONFIG.animationSpeed)` 在 NPE 时崩） |
| 结果 | ☐ |

---

## 2. 配置文件路径回归

**对应变更：** CHANGELOG `[1.0.5] ### 更改` 段 — 路径 `csgobox.toml` ↔ `csgobox-common.toml` 来回切了两次，最终保留 `csgobox.toml`

### TC-2.1 配置文件位置正确

| 项 | 内容 |
| --- | --- |
| 前置 | 全新环境 |
| 步骤 | 1. 启动客户端一次（不修改任何配置）<br>2. 关闭<br>3. 检查 `runs/client/config/` |
| 预期 | 存在 `csgobox.toml`，**不存在** `csgobox-common.toml` |
| 结果 | ☐ |

### TC-2.2 TOML 节分组与字段名正确

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置 |
| 步骤 | 1. 打开 `runs/client/config/csgobox.toml`<br>2. 验证分组与字段 |
| 预期 | ① 包含 `[general]` / `[advanced]` / `[sound]` / `[animation]` 四段<br>② 字段全部存在：`animationSpeed`、`globalDropRatePercent`、`loadDefaultBoxes`、`enableDebugLogging`、`enableAchievements`、`openSoundVolume`、`tickSoundVolume`、`finishSoundVolume`、`totalAnimationTicks`、`animationSpeedMultiplier`、`showItemNames`<br>③ 默认值与源码一致 |
| 结果 | ☐ |

### TC-2.3 扁平化字段访问无 NoSuchFieldError

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置 |
| 步骤 | 1. 启动客户端并完成 TC-1.1（开箱一次）<br>2. 关闭，再启动，反复 3 次 |
| 预期 | 每次启动日志无 `NoSuchFieldError` / `IllegalAccessError`（扁平化 `CONFIG.fieldName` 访问正确） |
| 结果 | ☐ |

---

## 3. 成就系统：全新的开始（first_box）

**对应变更：** CHANGELOG `[1.0.5] ### 新增` 段 — "全新的开始"

### TC-3.1 Root 标签页出现

| 项 | 内容 |
| --- | --- |
| 前置 | 全新存档，**未解锁过任何 CS2 Box 成就** |
| 步骤 | 1. 进入新世界<br>2. `Esc` → 进度 → 查看标签页列表 |
| 预期 | ① 进度面板顶部出现 **CS2 Box** 标签页（图标：chest，背景纹理 `csgobox:textures/gui/advancements/backgrounds/background_root.png`）<br>② 点击进入后看到根成就节点（无 toast / 无聊天公告，因 `show_toast: false`） |
| 结果 | ☐ |

### TC-3.2 主动开箱触发"全新的开始"

| 项 | 内容 |
| --- | --- |
| 前置 | 全新存档，未解锁 first_box |
| 步骤 | 1. `/give @s csgobox:csgo_box` ×1<br>2. `/give @s csgobox:csgo_key0` ×1<br>3. 手持箱子右键主动开启（**不是从地上捡起触发，而是右键箱子物品**）<br>4. 等待动画完成 |
| 预期 | ① 右上方弹出 toast "全新的开始"<br>② 聊天栏显示 `<player> has completed the advancement [CS2 Box] 全新的开始`<br>③ 进度面板 first_box 节点变成已解锁（无框色变更，因为无 `frame` 字段即默认 `task`）<br>④ **不发放任何物品奖励**（`rewards: {}`） |
| 结果 | ☐ |

### TC-3.3 first_box 不在未开启时解锁

| 项 | 内容 |
| --- | --- |
| 前置 | 全新存档 |
| 步骤 | 1. `/give @s csgobox:csgo_box`<br>2. **不开启箱子**，直接退出世界 |
| 预期 | 进度面板中 first_box **仍处于未解锁**（灰色） |
| 结果 | ☐ |

### TC-3.4 first_box 仅触发一次（持久化）

| 项 | 内容 |
| --- | --- |
| 前置 | TC-3.2 已解锁 first_box |
| 步骤 | 1. 退出存档<br>2. 重新进入存档<br>3. 查看进度面板 |
| 预期 | first_box 仍处于已解锁状态（数据通过 Minecraft 原生 `CriteriaTriggers` 持久化，无需新增 Capability） |
| 结果 | ☐ |

---

## 4. 成就系统：导购（shopper，200 次）

**对应变更：** CHANGELOG `[1.0.5] ### 新增` 段 — "隐藏紫色挑战 Shopkeeper"

### TC-4.1 shopper 满足条件前不显示

| 项 | 内容 |
| --- | --- |
| 前置 | 全新存档，未解锁 shopper，主动开箱 < 200 次 |
| 步骤 | 1. 进入新世界<br>2. 主动开启箱子 1 次<br>3. 查看 CS2 Box 标签页的进度树 |
| 预期 | shopper 节点 **不可见**（`hidden: true` 在条件未满足时不渲染） |
| 结果 | ☐ |

### TC-4.2 shopper 达到 200 次后出现

| 项 | 内容 |
| --- | --- |
| 前置 | 已解锁 first_box；用 `/advancement revoke @s only csgobox:first_box` 单独撤销 first_box；需主动开箱 200 次 |
| 步骤 | 1. `/scoreboard objectives add dummy` 替代方案：用 `/advancement grant @s only csgobox:shopper` 模拟终点<br>2. 或：执行 `/give @s csgobox:csgo_box` ×200 + 钥匙，开启 200 次<br>3. 完成后查看 CS2 Box 标签页 |
| 预期 | ① shopper 节点变为可见<br>② 图标：绿宝石（`minecraft:emerald`）<br>③ 框色：**紫色**（`frame: "challenge"`）<br>④ 弹出 toast "导购"<br>⑤ 聊天栏公告<br>⑥ **不发放奖励** |
| 结果 | ☐ |

### TC-4.3 csgobox:opened_boxes 统计累加

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置；`enableAchievements = true` |
| 步骤 | 1. 进入世界<br>2. 主动开启箱子 5 次<br>3. 执行 `/scoreboard objectives setdisplay sidebar` （或 `csgobox:opened_boxes` 自定义统计）<br>4. 或 `/stats` 命令查看 |
| 预期 | `csgobox:opened_boxes` 统计值 = 5（使用 Minecraft `Stats.CUSTOM`） |
| 结果 | ☐ |

---

## 5. 成就触发机制：Mob 掉落 vs 玩家主动

**对应变更：** CHANGELOG `[1.0.5] ### 新增` 段 — "Mob 掉落的箱子不算'开箱'，需玩家右键主动开启"

### TC-5.1 Mob 死亡掉落箱子不算"开箱"

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置；`globalDropRatePercent` 适中（如 200 提高测试效率）；全新存档 |
| 步骤 | 1. 进入世界，**未解锁** first_box<br>2. 找到配置了 `drop_entities` 的生物（如僵尸），击杀足够多只<br>3. **不拾取**掉落的箱子 |
| 预期 | ① 第一次掉落出现时（先解锁 first_box 的不是击杀，而是从地上捡起后右键）—— 实际验证：<br>&nbsp;&nbsp;a. 把掉落的箱子 **右键** 主动开启 → 触发 first_box<br>&nbsp;&nbsp;b. 把掉落的箱子 **捡起但不开**，或仅放在展示框里 → 不触发 first_box<br>② 击杀本身不触发 first_box |
| 结果 | ☐ |

### TC-5.2 主动右键 vs Offhand 不触发

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置 |
| 步骤 | 1. `/give @s csgobox:csgo_box` ×1<br>2. 把它放到 **副手**<br>3. 右键点击（Offhand 右键） |
| 预期 | ① 屏幕 **不**打开 `CsboxScreen`（`ClickEvent.onRightClick` 在 `event.getHand() != MAIN_HAND` 时直接 return）<br>② first_box **不**解锁 |
| 结果 | ☐ |

### TC-5.3 服务端权威：客户端不会绕过 trigger

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置；多人/集成服务器环境 |
| 步骤 | 1. 客户端以客户端身份登录服务端（不是 LAN 单人）<br>2. 玩家 A 主动开箱 1 次<br>3. 检查服务端日志（A、B 双方进度面板） |
| 预期 | ① A 立即解锁 first_box<br>② B 看不到 A 的 toast（`announce_to_chat: true` 仅自己可见——Minecraft 原生机制：聊天公告对所有玩家可见，需注意本测试预期为：A 自己看到 toast；其他玩家看聊天文本）<br>③ 服务端 `OpenedBoxTrigger.trigger(ServerPlayer)` 仅由 `ClickEvent` 调用，且只在主手 |
| 结果 | ☐ |

---

## 6. enableAchievements 配置开关

**对应变更：** CHANGELOG `[1.0.5] ### 新增` 段 — `enableAchievements` 默认 `true`

### TC-6.1 enableAchievements=true（默认）

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置 |
| 步骤 | 1. 进入世界<br>2. 主动开箱 1 次 |
| 预期 | ① 触发 first_box（行为同 TC-3.2）<br>② 统计 `csgobox:opened_boxes` 累加 |
| 结果 | ☐ |

### TC-6.2 enableAchievements=false（关闭）

| 项 | 内容 |
| --- | --- |
| 前置 | 编辑 `csgobox.toml`：`enableAchievements = false` |
| 步骤 | 1. 启动客户端进入新存档<br>2. 主动开箱 1 次 |
| 预期 | ① **不**触发 first_box（`OpenedBoxTrigger.trigger` 跳过调用）<br>② 进度面板中 first_box 仍不可见（root 节点可见——root 由 `mod_loaded` 触发，不受此开关影响，因为 ModLoadedTrigger 自身也会做 `enableAchievements` 检查，见 ModEvents:67）<br>③ **统计 `csgobox:opened_boxes` 仍然累加**（这是关键回归点：关闭期间 stats 不丢） |
| 结果 | ☐ |

### TC-6.3 关闭 → 开启后统计继续

| 项 | 内容 |
| --- | --- |
| 前置 | TC-6.2 已执行，stats = 1（即使成就未触发） |
| 步骤 | 1. 把 `enableAchievements` 改回 `true`<br>2. 重启客户端<br>3. 主动开箱 1 次<br>4. 查看统计 |
| 预期 | ① 触发 first_box（开关打开后下次开箱即生效）<br>② 统计 `csgobox:opened_boxes` = 2（之前的进度保留，不丢） |
| 结果 | ☐ |

### TC-6.4 enableAchievements=false 时 root 是否可见

| 项 | 内容 |
| --- | --- |
| 前置 | `enableAchievements = false` |
| 步骤 | 1. 进入新存档<br>2. 查看 CS2 Box 标签页 |
| 预期 | root 标签页 **可能仍可见**（因为 ModEvents:67 在 enableAchievements=false 时不调用 ModLoadedTrigger.INSTANCE.trigger），即 root 节点不出现；first_box 因 parent=root 也连带不出现 |
| 结果 | ☐ |

> **注意：** 这是预期差异，需用 `enableAchievements=false` 实际验证 root 是否隐藏。若设计意图是 root 也隐藏（推荐），那当前行为是正确的；若意图是 root 始终可见，则 v1.0.5 存在设计偏差。

---

## 7. 锻造台配方：csgo_key3 升级

**对应变更：** CHANGELOG `[1.0.5] ### 新增` 段 — "csgo_key3 的锻造台升级路径"

**注意：** 配方 ID = `csgo_key3_smithing`（之前命名为 `csgo_key3.json` 的工作台 3x 下界合金锭配方已 **删除**）

### TC-7.1 csgo_key2 通过锻造台升级为 csgo_key3

| 项 | 内容 |
| --- | --- |
| 前置 | 至少 1 个 `csgobox:csgo_key2`、1 个 `netherite_upgrade_smithing_template`、1 个 `netherite_ingot` |
| 步骤 | 1. 放置锻造台<br>2. 模板槽放入 `netherite_upgrade_smithing_template`<br>3. 底物槽放入 `csgobox:csgo_key2`<br>4. 添加物槽放入 `netherite_ingot`<br>5. 输出槽出现 `csgobox:csgo_key3`，shift+点击取出 |
| 预期 | ① 输出生成 1 个 csgo_key3<br>② 取出 csgo_key3 后，原材料消耗：模板/底物/添加物各 -1<br>③ `csgo_key3_smithing.json` 文件存在（`src/main/resources/data/csgobox/recipe/csgo_key3_smithing.json`） |
| 结果 | ☐ |

### TC-7.2 csgo_key3 升级后可开启箱子

| 项 | 内容 |
| --- | --- |
| 前置 | TC-7.1 已生成 csgo_key3 |
| 步骤 | 1. `/give @s csgobox:csgo_box`<br>2. 手持 csgo_key3 右键箱子 |
| 预期 | ① 打开开箱界面<br>② 钥匙被消耗（旧 CS:GO 模拟器逻辑：消耗一把钥匙开启）<br>③ 成功完成开箱动画 |
| 结果 | ☐ |

### TC-7.3 工作台 3x netherite 配方不存在（回归）

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置 |
| 步骤 | 1. 打开工作台<br>2. 尝试 3x netherite_ingot 合成 csgo_key3 |
| 预期 | ① 输出槽 **不**出现 csgo_key3（旧配方已删除）<br>② 文件系统中 `data/csgobox/recipe/csgo_key3.json`（不带 `_smithing` 后缀）**不存在**（仅 `csgo_key3_smithing.json`） |
| 结果 | ☐ |

### TC-7.4 csgo_key3 升级仅消耗 1 个 csgo_key2

| 项 | 内容 |
| --- | --- |
| 前置 | TC-7.1 准备材料 |
| 步骤 | 1. 锻造台中按 TC-7.1 操作<br>2. 取出 csgo_key3 |
| 预期 | ① csgo_key2 -1（不是 0 或 2）<br>② netherite_upgrade_smithing_template -1（配方消耗模板——Minecraft 原生 smithing_transform 行为）<br>③ netherite_ingot -1 |
| 结果 | ☐ |

---

## 8. 已知问题回归

**来源：** `.planning/codebase/CONCERNS.md`

### TC-8.1 配置文件名变更不自动迁移

| 项 | 内容 |
| --- | --- |
| 前置 | 残留旧 `csgobox-common.toml`（模拟 v1.0.5 早期版本遗留） |
| 步骤 | 1. 在 `runs/client/config/` 中**手工**创建 `csgobox-common.toml` 含自定义值（如 `animationSpeed = "FAST"`）<br>2. 同时保留默认生成的 `csgobox.toml`<br>3. 启动客户端 |
| 预期 | ① `csgobox-common.toml` 被忽略（v1.0.5 最终只读 `csgobox.toml`）<br>② 自定义值丢失，行为按 `csgobox.toml` 默认走 |
| 结果 | ☐ |

> **结论确认：** 这是已知缺陷（CHANGELOG 第 22-23 行明确告知玩家需手动迁移），本测试仅作为现状记录，不算回归失败。

### TC-8.2 高并发开箱无 ConcurrentModificationException

| 项 | 内容 |
| --- | --- |
| 前置 | 集成服务器，2 个玩家同时操作 |
| 步骤 | 1. 两个玩家同时右键箱子（间隔 < 100ms）<br>2. 各开 50 次 |
| 预期 | ① 服务端无 `ConcurrentModificationException`（最坏情况偶发可记录；不应持续复现）<br>② `OPEN_BLOCKED_UNTIL_TICK` 冷却机制生效，不会因同时请求导致物品刷出 |
| 结果 | ☐ |

### TC-8.3 反复开/关界面无内存泄漏

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置 |
| 步骤 | 1. 反复开/关 `CsboxScreen` 100 次<br>2. 期间不完成开箱<br>3. 观察 F3 内存或 `latest.log` |
| 预期 | ① 无 OOM<br>② 无未关闭的 GL 上下文错误<br>③ 帧率不持续下降 |
| 结果 | ☐ |

### TC-8.4 空箱警告可见（v1.0.4 修复回归）

| 项 | 内容 |
| --- | --- |
| 前置 | 一个配置为空的箱子（如临时编辑 `config/csbox/weapon_supply_box.json`，将 `grades` 列表设为空） |
| 步骤 | 1. 打开该箱子 |
| 预期 | ① 显示警告文字（不是被 3D 模型遮挡）<br>② 文字位于模型上方前景层 |
| 结果 | ☐ |

### TC-8.5 ESC 取消动画可立即重开

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置 |
| 步骤 | 1. 开启箱子<br>2. 动画播放到中段时按 ESC 取消<br>3. 立即再次右键箱子 |
| 预期 | ① 第二次开启不被冷却阻塞（CHANGELOG 1.0.4 修复：冷却改为短效防双击） |
| 结果 | ☐ |

---

## 9. 命令系统：/csbox

### TC-9.1 /csbox list

| 项 | 内容 |
| --- | --- |
| 步骤 | 1. 玩家在聊天栏输入 `/csbox list` |
| 预期 | 列出所有已注册箱子 ID + 等级概要（含至少 `csgobox:weapon_supply_box`） |
| 结果 | ☐ |

### TC-9.2 /csbox info

| 项 | 内容 |
| --- | --- |
| 步骤 | 1. `/csbox info csgobox:weapon_supply_box`（默认 box 文件名 `weapon_supply_box.json`，对应 ID `csgobox:weapon_supply_box`） |
| 预期 | 显示该箱子的详细配置（等级列表、物品、权重、掉落率） |
| 结果 | ☐ |

### TC-9.3 /csbox give / add / reload

| 项 | 内容 |
| --- | --- |
| 步骤 | 1. `/csbox give @s csgobox:weapon_supply_box 5`<br>2. `/csbox add csgobox:weapon_supply_box grade1 hand 3`<br>3. 修改 `config/csbox/weapon_supply_box.json`<br>4. `/csbox reload` |
| 预期 | ① `give` 给予 5 个 weapon_supply_box 物品<br>② `add` 将手持物品添加到 grade1 池中<br>③ `reload` 不抛异常，重新加载 JSON |
| 结果 | ☐ |

### TC-9.4 TAB 补全

| 项 | 内容 |
| --- | --- |
| 步骤 | 1. 聊天栏输入 `/csbox info `<Tab>` |
| 预期 | 弹出已注册箱子 ID 候选 |
| 结果 | ☐ |

---

## 10. 性能与稳定性冒烟

### TC-10.1 长时间开箱无明显卡顿

| 项 | 内容 |
| --- | --- |
| 前置 | 默认配置 |
| 步骤 | 1. 连续开箱 50 次不退出界面<br>2. 观察 FPS 与 `latest.log` |
| 预期 | ① 帧率无显著下降<br>② 无 `OPEN_BLOCKED_UNTIL_TICK` 内存累积告警（玩家长期在线） |
| 结果 | ☐ |

### TC-10.2 服务端 24 小时内存稳定

| 项 | 内容 |
| --- | --- |
| 前置 | 集成服务器，1 玩家在线 |
| 步骤 | 1. 启动服务端并维持 24h（最小化测试：1h 也可）<br>2. 期间反复开/关箱子 |
| 预期 | ① 堆内存无持续增长<br>② 无 OOM<br>③ 服务端无未捕获异常 |
| 结果 | ☐ |

---

## 11. 跨场景集成测试

### TC-11.1 升级路径全流程

| 项 | 内容 |
| --- | --- |
| 步骤 | 1. csgo_key0 → csgo_key1（如果存在配方）<br>2. csgo_key1 → csgo_key2<br>3. csgo_key2 → csgo_key3（锻造台）<br>4. csgo_key3 开箱 |
| 预期 | ① 每级升级后钥匙可正常使用<br>② csgo_key3 能成功开启箱子 |
| 结果 | ☐ |

### TC-11.2 多人服务端成就独立

| 项 | 内容 |
| --- | --- |
| 前置 | 集成服务器，2 玩家 |
| 步骤 | 1. 玩家 A 主动开箱 1 次<br>2. 玩家 B 主动开箱 1 次<br>3. 检查双方进度面板 |
| 预期 | ① A 触发 first_box 不影响 B 的统计<br>② B 也独立触发 first_box |
| 结果 | ☐ |

### TC-11.3 KubeJS 自定义箱子可被 /csbox 识别

| 项 | 内容 |
| --- | --- |
| 前置 | 安装 KubeJS 并配置 `DefaultBoxes.js` 自定义箱子 |
| 步骤 | 1. 启动客户端<br>2. `/csbox list`<br>3. 选择自定义箱子右键开箱 |
| 预期 | ① 自定义箱子出现在列表中<br>② 可正常开启，物品来自 KubeJS 脚本 |
| 结果 | ☐ |

---

## 12. 测试通过判定

### 通过条件（必须全部满足）

- [ ] TC-1.1 ~ TC-1.7 全部通过（**核心 v1.0.5 修复回归**）
- [ ] TC-2.x、TC-3.x、TC-4.x 全部通过（配置、成就基础）
- [ ] TC-5.x 全部通过（Mob 掉落 vs 主动开箱语义）
- [ ] TC-6.x 全部通过（enableAchievements 开关语义）
- [ ] TC-7.x 全部通过（锻造台 csgo_key3）

### 不通过判定（即 P0 缺陷）

- 任一用例导致服务端崩溃或玩家数据损坏
- TC-1.1（开箱崩溃）失败 = **P0 Blocker**，不可发布
- TC-3.2 / TC-7.1 失败 = **P0**，对应功能完全不可用
- TC-5.x / TC-6.x 语义偏差 = **P1**，需在 README 明确说明
- TC-8.x / TC-10.x 性能问题 = **P2**，视严重程度决定是否延期

### 测试记录模板

| 项 | 内容 |
| --- | --- |
| 测试版本 | csgobox-1.0.5.jar（commit d2122c4） |
| 测试环境 | macOS / Windows / Linux + JDK 21 + NeoForge 21.1.x |
| 测试日期 | YYYY-MM-DD |
| 测试人 | <name> |
| 通过用例数 | __ / 33 |
| 失败用例 | 列出 |
| 备注 | |

---

## 附录 A：变更点速查（CHANGELOG 对应）

| 用例 | CHANGELOG 段落 |
| --- | --- |
| TC-1.x | `### 修复` 段 — `CsboxConfig` 字段初始化 |
| TC-2.x | `### 更改` 段 — 配置文件路径 + 扁平化访问 |
| TC-3.x, TC-4.x | `### 新增` 段 — 成就系统 |
| TC-5.x | `### 新增` 段 — Mob 掉落不计开箱 |
| TC-6.x | `### 新增` 段 — `enableAchievements` 开关 |
| TC-7.x | `### 新增` 段 — `csgo_key3` 锻造台配方 |
| TC-7.3 | `### 新增` 段 — 工作台 3x netherite 配方移除 |

## 附录 B：相关源码索引

| 文件 | 用途 |
| --- | --- |
| `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java` | 配置定义（v1.0.5 关键修复点） |
| `src/main/java/com/reclizer/csgobox/advancement/ModLoadedTrigger.java` | root 标签页触发 |
| `src/main/java/com/reclizer/csgobox/advancement/OpenedBoxTrigger.java` | opened_box 触发（含 count 字段） |
| `src/main/java/com/reclizer/csgobox/event/ModEvents.java` | Mob 掉落 + PlayerLoggedIn 触发 root |
| `src/main/java/com/reclizer/csgobox/event/ClickEvent.java` | 主动开箱入口（客户端） |
| `src/main/resources/data/csgobox/advancement/root.json` | root 成就 |
| `src/main/resources/data/csgobox/advancement/first_box.json` | 全新的开始 |
| `src/main/resources/data/csgobox/advancement/shopper.json` | 导购（hidden: true, count=200） |
| `src/main/resources/data/csgobox/recipe/csgo_key3_smithing.json` | csgo_key3 锻造台配方 |
| `runs/client/config/csgobox.toml` | 客户端默认配置 |
| `runs/server/config/csgobox.toml` | 服务端默认配置 |