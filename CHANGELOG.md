# 更新日志

## [1.0.6] - 2026-07-02

### 概述
本版本完成 **MC 26.2 平台扩展** + **教程系统** 两条主线,涉及 3 个平台模块 (`v1_21_1` / `v26_1_2` / `v26_2`) + 新增 `common/` 业务代码共享层。源码 + 教程文档全部中文;Gitee 公开仓库承担教程分发。MIT 许可证保持不变(2024 Reclizer)。

### 新增

#### 多平台 / 26.2 beta 支持
- **v26_2 平台模块(第三个版本模块)。** 通过 `settings.gradle` 动态 include(`active_versions=26.2` 时启用),从 `v26_1_2/` 完整复制并包名重命名(`v26_1_2 → v26_2`,`Platform26 → Platform26V2`,独立 `IPlatform` 实现 `mcVersion()` 返回 `"26.2"`)。资源文件 `META-INF/neoforge.mods.toml`、`pack.mcmeta`、`assets/csgobox/items/*.json`、`data/csgobox/recipe/*.json`、`data/csgobox/advancement/*.json` 一并迁移。Gradle `settings.gradle` 与 `gradle.properties` 新增 8 个 `*_26_2` 变量。
- **26.2 decoupled API 破坏性变更适配** (`commit 4c9a004`,38 → 0 compile error)。`PictureInPictureRenderer` 构造器不再接收 `MultiBufferSource`(新签名为 `renderToTexture(state, poseStack, SubmitNodeCollector)`,`featureRenderDispatcher.renderAllFeatures()` 由父类负责触发);`Minecraft.setScreen(Screen)` → `Minecraft.setScreenAndShow(Screen)`;advancement `CriterionTrigger` 从抽象类改为 interface,`SimpleCriterionTrigger.trigger` 改为 protected(`Predicate<TriggerInstance>` 强制类型转换);`GameRenderer.getLighting()` → `lighting()`;`Options.hideGui` 字段整体移除(运行时回归阶段用户确认 HUD-overlay 降级可接受)。
- **`v26_2/gui/pip/Icon3DRenderer.java` 完全重写** (106 → 99 LOC)。3D 旋转保留:`scale(1,-1,-1)` + `Axis.{XP,YP,ZP}.rotationDegrees` 驱动 `rotXDeg/rotYDeg/rotZDeg`。运行时回归验证 `CsboxScreen` 与 `CsLookItemScreen` 的鼠标拖拽 3D 旋转均工作正常。
- **`common/` 业务代码共享层**。首批 A 类文件(无 MC 依赖)迁移:`common/utils/ColorTools.java` + `common/utils/OverlayColor.java`,git 识别为 rename(`v1_21_1 → common`),8 个 caller 的 import 同步更新。后续阶段 1 完整 B 类迁移(见 `multiloader-execution-spec.md`)将 `BoxDefinition / BoxRegistry / GradeGroup / CsboxConfig` 等业务核心统一到 `common/`,本版本暂保留 3 平台各一份。
- **真实 NeoForge 26.2 版本号 pin** 到 `gradle.properties`:`neo_version_26_2=26.2.0.7-beta`(26.2 当前最新,跳过 0.4/0.5)、`neogradle_version_26_2=7.1.38`(与 v26_1_2 同)、`neoform_release=26.2-1`、`loader_version_range_26_2=[11,)`、`pack_format_26_2=81`(按 Mojang 惯例 +1 每小版本)。
- **三模块 build 矩阵**。`./gradlew :v1_21_1:compileJava` + `:v26_1_2:compileJava` + `:v26_2:compileJava` 全部 BUILD SUCCESSFUL;`:v26_2:jar` 产出 `csgobox-26.2-1.0.6.jar`(pack_format=81)。
- **`.planning/` 规划制品**。`multiloader-refactor-plan.md`、`multiloader-execution-spec.md`、`phase0-audit.md`、`csbox-gui-26.1.2-fix-guide.md`、`runtime-verification-checklist.md` 等 5 篇主题文档 + `PROJECT.md / REQUIREMENTS.md / ROADMAP.md / STATE.md` 状态卡 + `intel/` 子目录 19 个 classifications + 3 张 synthesis(`SYNTHESIS/context/constraints`),由 `/gsd-ingest-docs` 自动 ingest 后保留。

#### 教程系统(教程 JSON 自动下载 + 版本管理 + 跨平台回收站)
- **网络下载教程文档**。教程 markdown 改为从网络拉取,不再硬编码在 JAR 里。新增 `box/TutorialSources.java`(读取 `config/csbox/_tutorial_sources.json` 源列表,默认指向 `https://gitee.com/hou-xiangling/CS2-Box/raw/main/docs/tutorials/`,玩家可手动创建该 JSON 加镜像 / 调超时)+ `box/TutorialFetcher.java`(Java 11+ `HttpClient`,`HttpClient.Redirect.ALWAYS` 自动跟随 302,5s 连接 / 8s 单请求超时,异常全部 catch 不冒泡)。`BoxJsonLoader.loadAll()` 在创建 `config/csbox/` 后调用 `BoxDefaults.writeTutorialIfMissing()`。
- **教程文档自带版本号**。文件名嵌入 mod 版本,例如 `_tutorial_v1.0.6.md` / `_tutorial_v1.0.6_zh_cn.md`,源仓库路径 `gitee.com/hou-xiangling/CS2-Box/docs/tutorials/` 已上传对应文件。下次 mod 升 1.0.7 时,玩家机器上的 `_tutorial_v1.0.6*.md` 会被自动清理并下载新版。
- **跨平台回收站 + 安全过滤**。`BoxDefaults.moveStaleTutorials()` 三重安全:(1) 优先调用 `java.awt.Desktop.moveToTrash()` 走系统原生回收站(Windows Recycle Bin / macOS Finder Trash / Linux XDG Trash);(2) 无 GUI(headless server)或 `java.desktop` 缺失时降级到 `config/csbox/.trash/` 子文件夹,文件名冲突自动加毫秒时间戳前缀;(3) 严格正则白名单 `^_tutorial_v.*\.md$`,绝不触碰 `notes.md` 等用户文件、`_tutorial_sources.json` 配置、旧版无版本号 `_tutorial.md` 等。跨分区移动自动 `Files.move` → `Files.copy + Files.delete` 降级。
- **离线安全**。整个 `writeTutorialIfMissing` 体被 `try { ... } catch (Exception e)` 包裹,任何意外(网络异常、HttpClient 构造失败、磁盘写入失败)只记 WARN 日志,游戏正常启动运行。
- **教程文档内容**(中英双版本,源仓库 `docs/tutorials/`)。涵盖字段说明(顶层字段 / entity / 物品对象 / 等级 / 钥匙 / 校验规则 / 故障排查)、JSON 示例(自定义名 / 附魔 / 玩家头颅)、锻造台 `csgo_key3` 唯一获取路径说明。

#### JSON 加载错误玩家可见纠错
- **`box/LoadError.java`** 记录单条 JSON 加载失败:`Path file / String boxId / String reason / int line / int column / Throwable cause`,自带 `toChatMessage()` 返回红色 `Component`(行/列已知时显示 `"第 N 行第 M 列"`)。
- **`box/BoxJsonLoader.java` 错误收集**:`LAST_LOAD_ERRORS` 静态列表,`loadAll()` 开头清空,`loadFromFile()` 把所有异常分支(`JsonSyntaxException` / `Identifier.parse` 失败 / 空箱子跳过)收集为 `LoadError` 而非只写日志。Gson 2.13+ 不再提供 `JsonSyntaxException.getLocation()`,用正则 `at line (\d+) column (\d+)` 从错误消息里抓取行/列。新增公开 API `getLastLoadErrors() / hasLoadErrors()`。
- **`event/LoadErrorAnnouncer.java`** 在 `PlayerEvent.PlayerLoggedInEvent` 触发,根据 `ErrorChatAudience` 配置(`OP_ONLY` 默认 / `EVERYONE`)向玩家推送加载错误。v26.x 用 `sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`,v1_21_1 用 `sp.hasPermissions(2)`。
- **`config/CsboxConfig.java` 新增** `ErrorChatAudience` 枚举 + `jsonErrorAudienceValue`(`defineEnum` 落到 `CONFIG_SPEC`,玩家可在 `config/csgobox.toml` 改)。
- **`command/CsboxCommand.java` 新增** `/csbox errors` 子命令,OP 权限,显示当前所有加载错误(无错误时显示绿色 "当前无箱子加载错误")。

### 决定
- **B 类 6 文件不迁 `common/`(显式选择)。** `BoxDefinition / BoxRegistry / GradeGroup / CsboxConfig / CsboxPlayerData / EntityChineseMap` 全部 `import net.minecraft.*` 或 `import net.neoforged.*`,违反 `.planning/intel/constraints.md` CONSTRAINT-001。它们保留在每个平台模块的副本中(共 3 份)。若未来要做 B 类重构,需要新增平台抽象接口(`IIdentifier / IItemStack / IComponent / IModConfig`)或在 common 中允许数据包装类携带 MC 引用,工作量为 6-10 小时。
- **教程分发仓库选择 Gitee(用户私有仓库)而非 GitHub**。原因:国内访问速度 + 用户主仓库路径 `gitee.com/hou-xiangling/CS2-Box`。教程 markdown 文件单独存放于 `docs/tutorials/` 子目录,与 mod 源码解耦。玩家可在 `config/csbox/_tutorial_sources.json` 里加 GitHub / 自建 CDN 作为镜像。
- **回收站优先 OS 原生**(`Desktop.moveToTrash()`)。原因:玩家可在熟悉的 OS UI(Finder Trash / Windows Recycle Bin / Linux 文件管理器)恢复,跨平台 API 自 Java 9 起稳定;headless server 与极简 JVM 自然降级到 `.trash/` 文件夹。
- **教程文件名带版本号**(例 `_tutorial_v1.0.6.md`)。原因:教程内容可能随版本更新而调整(mod 字段、命令、配方可能变更),带版本号保证玩家读到的文档与 mod 行为匹配;升级时旧版本自动移到回收站。

### 修复
- **`TutorialFetcher` HttpClient 默认不跟随 302 重定向。** Gitee raw URL 返回 HTTP 302(走 ADAS 网关),Java HttpClient 默认行为是失败。修复:构造时显式 `.followRedirects(HttpClient.Redirect.ALWAYS)`。
- **`BoxJsonLoader` 把教程源文件当箱子加载。** `tutorial_sources.json` 文件名不以 `_` 开头时会被 loader 当箱子解析并报 "All items failed to parse"。修复:文件名前加 `_` 前缀(`_tutorial_sources.json`),自动被 loader 跳过(`Files.newDirectoryStream` 跳过 `_` 开头文件)。
- **教程源配置 JSON 自动写入 config/csbox/ 产生冗余 clutter。** 第一版会在首次启动自动写 `_tutorial_sources.json`,玩家认为是无用文件。修复:改为"只在玩家手动创建时读取,默认情况下不写盘",99% 玩家看到干净 `config/csbox/`。
- **删除老 `_tutorial.md` 等旧版无版本号文件。** Gitee 仓库旧版文件已删除;`/tmp/cs2-tutorials-v2` worktree 用 `git mv` 重命名为带版本号文件名,提交 `e29200f` 推送到 gitee。

### 移除
- **教程硬编码常量。** `BoxDefaults.TUTORIAL_CONTENT_EN` / `TUTORIAL_CONTENT_ZH`(原约 200 行 Java 嵌入 markdown)整段删除,改为运行时从 Gitee 拉取。
- **HUD-overlay 隐 GUI 功能在 26.2 不可用**(NeoForge 26.2 移除 `Options.hideGui` 字段)。开箱时 hotbar/血条仍可见,用户确认接受此降级,延后到 26.2 stable + 出现等价 API 时再补。
- **测试用 JSON 文件清理。** `runs/client/config/csbox/test_*.json`(LoadError 功能验证时临时写入的)从 3 平台 `runs/` 全部删除。

### 更改
- **配置可见性默认值**:`jsonErrorAudience` 默认 `OP_ONLY`(非 OP 玩家登录时收不到加载错误推送)。OP 自身执行 `/csbox errors` 不受此配置影响(命令本身已 OP-only)。
- **配置文件命名**:`config/csgobox.toml`(1.0.5 后续调整后保持此名)。所有玩家配置文件路径不变。
- **PIP 3D 旋转在 26.2 的实现**:`v26_2/gui/pip/Icon3DRenderer.java` 因 `PictureInPictureRenderer` API 变动完全重写,3D 鼠标拖拽行为与 v26_1_2 视觉一致。

### 备注
- **构建产物**:
  - `csgobox-1.21.1-1.0.6.jar`(MC 1.21.1 + NeoForge 21.1.115)
  - `csgobox-26.1.2-1.0.6.jar`(MC 26.1.2 + NeoForge 26.1.2.76)
  - `csgobox-26.2-1.0.6.jar`(MC 26.2 + NeoForge 26.2.0.7-beta,**注意 26.2 仍 beta,生产环境慎用**)
- **教程文档源**:`gitee.com/hou-xiangling/CS2-Box/docs/tutorials/`,公开仓库,无需认证。玩家想自托管可 fork 该仓库后改 `_tutorial_sources.json`。
- **SSH 密钥**(`~/.ssh/id_ed25519_gitee`,用户机器本地)用于教程维护者推送更新到 Gitee,与 mod 运行时下载无关(下载走 HTTPS 公开访问)。
- **运行时回归**:v1_21_1 / v26_1_2 / v26_2 三平台客户端均已验证箱子加载、开箱动画、PIP 3D 旋转、成就触发、`/csbox reload`、`/csbox errors`、教程下载/版本升级迁移/回收站恢复。

### 未完成
- 一旦 26.2 发布 stable release(去掉 `-beta` 后缀),需要重新刷 `neo_version_26_2` 并验证 `mc_version_range_26_2` 是否需要向前兼容 beta。
- 阶段 1 完整 B 类迁移:common 业务代码(B 类文件保留平台层重复)、P1-1 容器化布局、P1-3 per-item 视觉基线、P2-2 三档设计 token(均显式延期)。
- 教程系统可考虑增量:`/csbox tutorial refresh` 命令手动刷新;`.trash/` 自动清理(保留最近 N 份或 N 天内);启动时打印回收站内容提示。

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
