# 更新日志

## [未发布] — Multiloader 扩展（v26.2 骨架 + common/utils 首批迁移）

### 新增
- **v26_2 平台模块骨架。** 新增第三个版本模块 `v26_2/`,通过 `settings.gradle` 动态 include(`active_versions=26.2` 时启用)。当前状态:34 个 Java 文件 + 8 个资源文件从 `v26_1_2/` 复制并完成包名 `v26_1_2 → v26_2` 重命名,`Platform26 → Platform26V2`(独立 `IPlatform` 实现,`mcVersion()` 返回 `"26.2"`),`CsgoBox` 静态块改为 `Platform.set(new Platform26V2())`。NeoForge 26.2 / NeoGradle 占位版本号(等待用户提供真实 release coordinate)。
- **`common/utils/ColorTools.java` 与 `common/utils/OverlayColor.java`。** 两个纯 A 类文件(无 `net.minecraft.*` / `net.neoforged.*` 依赖)从 `v1_21_1/` 与 `v26_1_2/` 重复实现中抽出至 `common/utils/`,git 识别为 rename(v1_21_1 → common)。8 个 caller 的 import 同步更新(`v{1_21_1,26_1_2}.utils.ColorTools → utils.ColorTools`)。
- **`.planning/` 目录新增 11 个规划文档 + `intel/` 子目录。** `multiloader-refactor-plan.md`、`multiloader-execution-spec.md`、`phase0-audit.md`、`csbox-gui-26.1.2-fix-guide.md`、`runtime-verification-checklist.md` 等 5 篇主题文档,加 `PROJECT.md / REQUIREMENTS.md / ROADMAP.md / STATE.md / INGEST-CONFLICTS.md` 5 张同步状态,加 `intel/{SYNTHESIS,context,constraints}.md` + 19 个 classifications JSON。这些由 `/gsd-ingest-docs` 自动 ingest 后保留在仓库中,供 `/gsd-plan-phase` 等下游命令消费。

### 决定
- **B 类 6 文件不迁 `common/`(显式选择)。** `BoxDefinition / BoxRegistry / GradeGroup / CsboxConfig / CsboxPlayerData / EntityChineseMap` 全部 `import net.minecraft.*` 或 `import net.neoforged.*`,违反 `.planning/intel/constraints.md` CONSTRAINT-001。它们保留在每个平台模块的副本中(共 3 份)。若未来要做 B 类重构,需要新增平台抽象接口(`IIdentifier / IItemStack / IComponent / IModConfig`)或在 common 中允许数据包装类携带 MC 引用,工作量为 6-10 小时。

### 已落地(2026-07-01,基于直接 curl `maven.neoforged.net/releases/`)
- **NeoForge 26.2 真实版本号已 pin 到 `gradle.properties`。** 26.2 仍处于 beta 阶段,Mojang / NeoForged 尚未发布 `26.2.0` stable。当前最新:`26.2.0.7-beta`(已发布的 beta 序列是 `26.2.0.0/0.1/0.2/0.3/0.6/0.7-beta`,跳过了 0.4/0.5)。NeoForm 对应 release 是 `26.2-1`(已 stable)。NeoGradle userdev 最新仍是 `7.1.38`,与 v26_1_2 相同(尚未发布 7.2.x)。FML loader `11.0.13` 与 26.1.2.76 一致,所以 `loader_version_range_26_2 = [11,)` 保持不变。`pack_format_26_2 = 81`(按 Mojang 惯例 +1 每小版本,26.1.x = 80)。
- **Gradle 解析 + neoForm 流水线已跑通 v26_2。** `./gradlew :v26_2:compileJava`(在 `active_versions=26.2` 下)成功解析 NeoForge 26.2.0.7-beta、NeoForm 26.2-1、NeoGradle 7.1.38,完整 neoForm 流水线(cacheVersionManifest / decompile / patch / recompile)全部执行。`compileJava` 任务失败,37 个 API 破坏性变更错误(stage 4 工作):`MultiBufferSource` 被移除、`Minecraft.setScreen(Screen)` 签名变更、`RegisterEvent.register(ResourceKey<Registry<CriterionTrigger<?>>>, Identifier, Supplier)` 不再接受三参数形式、`Icon3DRenderer` 构造器签名变化(改为接收 `BufferSource` 参数)等。

### 未完成(等 stage 4-6)
- v26_2 GUI / PIP 系统在 26.2 decoupled API 下的实际编译适配(参见上一节列出的破坏性变更)。
- 三模块 build 矩阵验证 + 运行时回归(PIP 3D 旋转在 v26_2 必须工作)。
- 26.2 一旦发布 stable release(去掉 `-beta` 后缀),需要重新刷 `neo_version_26_2` 并验证 `mc_version_range_26_2` 是否需要向前兼容 beta。

## [1.0.5] - 2026-06-29

### 新增
- **成就系统（`全新的开始`）。** 在原版进度面板中加入 `CS2 Box` 标签页，第一个成就「全新的开始」(`A Fresh Start`) 在玩家首次主动开启任意 CS:GO 箱子时解锁，与原版成就一致：弹出 toast，聊天栏显示 `wikkd has completed the advancement [CS2 Box] 全新的开始`，不发放任何奖励。Mob 掉落的箱子不算"开箱"，需玩家右键主动开启。数据通过 Minecraft 原生 `CriteriaTriggers` 持久化，无需新增 Capability，存档迁移无影响。后续若扩展更多成就，沿用 `csgobox:advancement/root.json` 节点下追加 JSON 即可。
- **隐藏紫色挑战「导购」(`Shopkeeper`)。** 玩家累计主动开启 200 个 CS:GO 箱子时解锁；图标为绿宝石，框色为紫色（`frame: "challenge"`），满足条件前在进度面板中不显示该节点（`hidden: true`）。数据走 Minecraft 原生统计系统 `csgobox:opened_boxes`（`Stats.CUSTOM`），无需新增 Capability，`TriggerInstance` 新增 `count` 字段实现"任意 vs 阈值"二合一（`csgobox:opened_box` 同一个 trigger 类同时驱动两个成就）。奖励与原版成就一致 —— 无。
- **配置开关 `enableAchievements`（默认 `true`）。** 在 `config/csgobox-common.toml` 的 `[advanced]` 段新增 `enableAchievements: boolean = true`，玩家可手动关闭整个成就系统。关闭期间 `csgobox:opened_boxes` 统计仍累加（保留进度），`OpenedBoxTrigger.trigger` 跳过调用；重新开启后，后续开箱即恢复触发，统计进度不丢。

### 修复
- **`CsboxConfig` 字段初始化修复。** 早先 v1.0.5 (commit 862ab1f) 中的 `CsboxConfig` 类采用 `init()` 延迟填充模式，但 `init()` 整个代码库中从未被调用，导致所有配置驱动的字段在运行时读取为 0/false/null：生物 CS:GO box 掉落、调试日志、默认 box 自动加载、物品名称预览、音效（打开/tick/揭晓）全部失效；`switch (CONFIG.animationSpeed)` 在首次动画 tick 抛出 `NullPointerException`，任何玩家开箱即崩溃。按 `AGENTS.md` 第 21 行约定，将 `.get()` 内联到构造器中，删除死代码 `init()`。该问题在 `.planning/v1.0.5-REVIEW.md` 中被记录为 CR-001/CR-002/CR-003。

### 移除
- **完全移除 Cloth Config 依赖。** 模组不再依赖 `me.shedaniel.cloth:cloth-config-neoforge`。配置现通过 NeoForge 原生 `ModConfigSpec` API 持久化，存储为 `config/csgobox-common.toml`。

### 新增
- **`csgobox:csgo_key3` 的锻造台升级路径。** 玩家在锻造台中使用 `minecraft:netherite_upgrade_smithing_template` 和一个下界合金锭，将钻石钥匙 (`csgobox:csgo_key2`) 升级为下界合金钥匙。
  - 配方文件：`data/csgobox/recipe/csgo_key3_smithing.json`
  - **v1.0.5 修正**：此配方现为下界合金钥匙的唯一获取方式。原工作台 3x 下界合金锭合成配方（`data/csgobox/recipe/csgo_key3.json`）已移除。

### 更改
- 配置文件路径从 `config/csgobox.toml` 迁移至 `config/csgobox-common.toml`。现有玩家需手动删除旧文件以避免混淆，数值不会自动迁移。
- **v1.0.5 后续**：配置文件路径从 `config/csgobox-common.toml` 改回 `config/csgobox.toml`，与 1.0.4 之前的命名一致。现有玩家需手动将旧文件重命名（或删除以重置为默认值），数值不会自动迁移。
- 扁平化 `CONFIG` 字段访问。Java 调用方现使用 `CONFIG.fieldName` 而非 `CONFIG.section.fieldName`。TOML 端仍按 `[general]`、`[advanced]`、`[sound]`、`[animation]` 分组。

### 备注
- 构建产物为 `csgobox-1.0.5.jar`。
- 字段语义和默认值较 v1.0.4 无变化。
- 实际 tag `v1.0.5` 指向当前 commit。原 `release: v1.0.5` 提交 (862ab1f) 由于缺少 `CsboxConfig.java` 无法从 tag 干净编译，未被打 tag。

## [1.0.4] - 2026-06-19

### 新增
- 默认生成的箱子 JSON 现包含英文 `_tutorial` 对象。文档涵盖文件名映射、钥匙、掉落率、随机权重、实体格式、等级列表、物品对象、`components`、旧版 `tag` 及推荐工作流程。
- 在 `PacketBoxOpenResult` 中添加服务端授权的动画物品数据，使客户端动画条与最终奖励使用同一服务端选中的结果。
- 为预览和开箱结果数据包添加请求 ID 匹配，防止过期客户端响应被错误屏幕消费。

### 修复
- 修复了集成服务器游戏中客户端 GUI 从错误线程打开的问题。箱子界面现仅为本地客户端玩家打开，并调度到客户端线程。
- 修复了 `RenderFontTool` 在屏幕字体临时为 null 时崩溃的问题，改用 `Minecraft.getInstance().font` 回退。
- 修复了服务端拒绝开箱请求（如短冷却、钥匙缺失、空箱或物品无效）时动画永远等待的问题。服务端现发送匹配的空白结果，客户端可正常退出。
- 修复了中奖物品位于动画条开头附近时动画速度行为异常的问题。现从动画窗口后期选取中奖索引，使动画开始快、接近奖励时减速。
- 修复了空箱警告文字被 3D 箱子模型遮挡的问题，改为在模型上方使用前景叠加层绘制警告。

### 更改
- 开箱冷却改为短效防双击保护，而非完整动画时长，因此用 ESC 取消动画不会阻塞下次手动测试。
- 在边界处复制可变 `ItemStack` 和集合数据，防止意外修改原始配置数据。
- `RandomItem` 对 null 和空输入进行了防御性处理，并使用 long 类型总权重以避免溢出。
- `CsboxProgressScreen` 现直接使用帧间渲染插值因子，而非将速度混入插值量。

### 备注
- 现有 JSON 文件不会被覆盖。`_tutorial` 对象仅在模组在空的 `config/csbox` 目录中自动生成新默认 JSON 时出现。
- 当前 Gradle 模组版本为 `1.0.4`，预期发布 jar 为 `csgobox-1.0.4.jar`。

## [1.0.2] - 2026-06-01

### 新增
- **NeoForge 1.21.1 移植** — 从 Forge 1.20.1（ChloePrime/CS2-Box）完整迁移至 NeoForge 21.1.115+
- **`/csbox` 命令系统** — 游戏内箱子管理命令：
  - `/csbox list` — 列出所有已注册箱子及等级概要
  - `/csbox info <box>` — 显示特定箱子的详细配置
  - `/csbox add <box> <grade> hand <count>` — 将手持物品添加到箱子的等级池
  - `/csbox give <box> [count] [player]` — 向玩家给予箱子物品
  - `/csbox reload` — 从 KubeJS 脚本重新加载箱子定义
  - 全面的 TAB 补全支持（箱子 ID 和等级 ID）
- **箱子 JSON 加载器（`BoxJsonLoader`）** — 运行时从 `config/csbox/*.json` 加载箱子配置，同时支持 `components`（DataComponent）和旧版 `tag`（NBT）物品格式
- **实体掉落率系统** — 通过 JSON 配置中的 `entity_drop_rates` 覆盖单个实体掉落率；抢夺附魔加成（每级 ×0.5，上限 100%）
- **KubeJS 集成** — 基于脚本的箱子创建 API：
  - `BoxBuilderJS` / `GradeBuilderJS` — 箱子与等级的流式构建器
  - `CsboxRegistryEventJS` — 注册自定义箱子的 KubeJS 事件
  - `DefaultBoxes.js` — 内置默认箱子定义
  - `KubeJsPlugin` — 兼容 KubeJS 2101.x 的插件入口点
- **箱子注册表 API** — `BoxDefinition`、`GradeGroup`、`BoxRegistry` — 不可变数据模型，采用安全运行时修改的"读-重建-替换"模式
- **`PacketBoxOpenResult`** — 专用服务端→客户端数据包，保证开箱后数据传递，解决 UI 渲染竞态条件
- **中文（zh_cn）翻译** — 完整的本地化，包括命令消息和界面字符串

### 修复
- **等级映射反转** — JSON 配置中的等级（grade5 = 最稀有，grade1 = 普通）在显示时被错误映射：AWP/下界合金装备显示为"grade 1"（蓝色），垃圾物品却显示为"grade 5"（金色）。已在 `ItemCsgoBox.getItemGroup()` 和 `RandomItem.randomItemsGrade()` 中修复。
- **客户端-服务端数据同步** — 解决了服务端数据可能在屏幕创建前到达导致 UI 渲染失败的问题。专用数据包确保显示前 100% 送达。
- **物品栈污染** — 配置中的 `ItemStack` 实例现于存储/修改前调用 `.copy()`，防止原始配置数据被破坏。
- **JSON 实体列表解析** — 纯实体 ID 数组（如 `["minecraft:zombie"]`）时崩溃。现同时支持交替的 `[id, rate]` 格式和纯 `[id]` 格式（回退至全局掉落率）。

### 更改
- **移除废弃类** — `CsgoBoxCraftMenu`、`CsgoBoxCraftScreen`、`RecModMenus`、`RecModScreens`、`ItemOpenBox`、`PacketUpdateMode`、`ItemNBT`
- **移除合成配方/模型** — `csgo_box_craft` 配方、模型和纹理已移除
- **更新依赖** — NeoForge 21.1.115、Cloth Config 15.0.130、KubeJS 2101.7.2-build.368、Rhino 2101.2.7-build.82
- **Gradle toolchain** — 需 JDK 21（通过 `org.gradle.java.home`）
- **StreamCodec** — 字段超过 6 个的类使用手动 `StreamCodec.of()` encode/decode（NeoForge 1.21.1 要求）

### 环境要求
- Minecraft 1.21.1
- NeoForge 21.1.115+
- Java 21
