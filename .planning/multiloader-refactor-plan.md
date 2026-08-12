# CS2-Box MultiLoader 重构完整计划

日期：2026-06-29

## 2026-08 执行结果（MultiLoader 代码去重重构）

按《MultiLoader 代码去重重构》计划（阶段 0–6）全部完成，共 4 个阶段提交 + 1 个阶段 5 暴露问题的修复提交（阶段 4 无重复资源故跳过提交）：

| 提交 | 内容 |
|---|---|
| `da45a85` refactor: remove forked platform copies of schema validator and tutorial fetcher | 删除 4 平台 12 个过时副本（BoxJsonSchemaValidator/TutorialFetcher/TutorialSources）；审计确认平台 BoxJsonLoader 早已 import common 版，副本为纯死代码，直接删除 |
| `e1f1b41` refactor: sink box grade/strip/registry logic into common | 新增 common `BoxGrades`/`BoxRegistryStore<K,V>`/泛型 `BoxStripGenerator` + 3 个 JUnit；4 平台 BoxDefinition/BoxRegistry/packet/GUI 引用切换，净删除 243 行 |
| `9e54011` refactor: centralize open cooldown guard in common | 新增 common `logic/OpenBlockGuard` + `OpenBlockGuardTest`（阻塞窗口/到期放行/惰性移除/tick 清理/并发安全）；4 平台 packet 与 ModEvents#serverTick 切换，语义逐字保留 |
| `6259892` refactor: share command ops and config defaults via common | 新增 common `config/CsboxConfigDefaults`，4 平台 CsboxConfig builder 统一引用（枚举默认以常量名字符串存储 + `valueOf` 解析） |
| `ab77bf9` fix(distsafe): move client screen openers out of ItemCsgoBox/ItemTerminal | 阶段 5 fullcheck 暴露的 **pre-existing 回归**（0e596c1 终端机会话经济引入，晚于 2026-08-09 全绿 fullcheck）：`ItemCsgoBox` import client-only Screen 导致 26.1.2/26.2 专用服务器启动即 `NoClassDefFoundError: Screen` 崩溃。提取各平台 `gui/BoxScreenOpener`，item 类惰性委托，服务端类加载不再触碰 client 类；4 平台定点适配（v26_2 保留 `setScreenAndShow`、v1_21_1 保留 `Screen.hasShiftDown`） |

**放弃项与审计结论**：
- **CsboxCommandOps 未拆**（计划 YAGNI 条款）：审计 261 行 `CsboxCommand`，全部 handler 与 Component/CommandSourceStack 强耦合（translatable/sendSuccess/withStyle），MC 无关纯逻辑不足 30 行（仅 MAX_NBT_CHARS 常量 + 截断分支），不强拆。
- **阶段 4 无删除**：平台资源均为版本敏感物——`assets/csgobox/items/*.json` 是 1.21.4+ item 模型定义（v1_21_1 无此目录，三 26.x 平台间完全一致但 common 无此格式需求）；`data/csgobox/advancement/root.json` 平台版与 common 版 background 路径格式不同（版本格式适配，EXCLUDE 语义下平台副本优先，非隐性分叉）；lang/models/textures 抽查无平台副本。故未提交 `chore: dedupe shared resources into common`。
- **forge 行为归一**（阶段 1 顺带）：`forge_26_1_2` BoxDefinition 的 `DEFAULT_WEIGHTS {625,125,25,5,2}` 与 MAX 上限 256/16/256 为 v1.0.6 发行快照旧值，按特性同步纪律统一到 common `BoxGrades` 值（`{625,125,25,6,4}` 与 1024/64/1024）。
- **已知 forge 同步缺口（不在本次范围）**：forge items/ 缺 `armory_point.json`、`premium_supply_box.json`（1.0.6 发行后新增，下次 forge 同步补齐）。

**验证结果**（修复提交后全部重跑）：
- `:common:test` 全部通过（含新增 BoxGradesTest/BoxRegistryStoreTest/BoxStripGeneratorTest/OpenBlockGuardTest），checkCommonArchitecture 随编译自动拦截通过
- 4 平台 `clean jar` 全部成功，体积较基线下降：v26_1_2 603K→597K、v26_2 602K→596K、v1_21_1 601K→594K、forge_26_1_2 605K→599K（`ab77bf9` 新增 BoxScreenOpener 后各 +约12K，仍低于基线）
- `scripts/check-animops-drift.sh` 3 平台 OK（13 ops）
- `scripts/test-forge-2612.sh` 门禁 7/7 PASS
- `:v26_1_2:test` PlatformSmokeTest 3/3 PASSED
- fullcheck（box_variants/achievements/e2e_open/dynamic_box/aesthetic × 3 平台）：
  - **修复前**：26.1.2/26.2 专用服务器启动即崩（`CsgoBox.<init>` → `ItemCsgoBox` 类加载触发 client-only `Screen`），1.21.1 服务器正常但客户端 MCP 无法就绪
  - **修复后**：26.1.2 / 26.2 / forge_26_1_2 专用服务器逐一手动启动验证全部成功（`Done (0.095–0.190s)!`，box JSON 正常加载，无 NoClassDefFoundError）
  - **环境限制（未完成项）**：本机无活动显示器——诊断证据：`pmset -g log` 有 Clamshell Sleep（合盖休眠）记录、无 `AppleBacklightDisplay` 背光节点、`osascript` 桌面查询挂起、`launchctl asuser 501` 下 GLFW 仍报 `glfwGetPrimaryMonitor failed`（沙箱内外均复现）。依赖真实客户端窗口的套件（e2e_open/aesthetic/achievements 的客户端部分）无法执行，`build/fullcheck/SUMMARY.md` 未产出全绿报告。各平台客户端测试环境本身完整（testhelper MCP mod 与 toml 均在 `<module>/runs/client/`）。**需开盖激活屏幕后补跑一次**：`python3 scripts/fullcheck/run_full_check.py --platform 1.21.1,26.1.2,26.2 --only box_variants,achievements,e2e_open,dynamic_box,aesthetic`

---

## 目标

将当前单版本来源的 NeoForge 模组，重构为可持续维护的 MultiLoader 结构：

- `common/`：跨版本业务逻辑、资源、接口契约
- `v1_21_1/`：MC 1.21.1 / NeoForge 21.1.115 平台实现
- `v26_1_2/`：MC 26.1.2 / NeoForge 26.1.2.76 平台实现

最终产出：

- `v1_21_1/build/libs/csgobox-1.21.1-1.0.5.jar`
- `v26_1_2/build/libs/csgobox-26.1.2-1.0.5.jar`

## 当前状态基线

以当前工作树为准，不以历史计划的“已完成”描述为准。

已确认：

- 根 Gradle 已拆为多模块模式，存在 `common/` 与 `v1_21_1/`
- `common/src/main/resources/` 已承载跨版本资源
- `common/src/main/java/` 目前只有 `platform/*` 接口，业务代码尚未迁入
- `v1_21_1/` 目前仍承载几乎全部原始业务代码与平台代码
- `settings.gradle` 已 include `v26_1_2`，但仓库内尚不存在 `v26_1_2/`
- 根 `build.gradle` 的通用仓库未包含 `https://maven.neoforged.net/releases`
- 当前工作树有大量未提交迁移改动，后续必须避免回滚这些已有移动

这意味着项目目前处于“结构已拆开一半，但职责尚未真正分层”的状态。

## 不可违反的约束

- 保持 Java 21 作为当前主构建 toolchain，除非 `v26_1_2` 实测必须单独升到 25
- 保持 NeoForge 原生 `ModConfigSpec`，不引入 Cloth Config
- `config/csgobox.toml` 文件名不变
- 配方路径保持 `data/csgobox/recipe/`
- `CsgoBox.java` 中配置初始化顺序的语义必须保留到新平台入口
- `CONFIG` 为 `final`，删除所有无意义的 `null` 守卫
- `common` 编译期不得直接引用 `net.minecraft.*` 或 `net.neoforged.*`
- GUI 渲染、附件注册、网络上下文、注册表访问等版本敏感逻辑留在平台模块
- 不破坏现有 1.0.5 功能：开箱动画、掉落、命令、成就、四把钥匙、网络同步

## 目标架构

### 模块职责

`common`

- 纯 Java library
- 持有跨版本资源
- 持有业务模型、算法、配置定义、命令业务、触发器业务、包体业务、平台抽象接口

`v1_21_1`

- NeoGradle 7.0.171
- NeoForge 21.1.115
- 负责 `@Mod` 入口、DeferredRegister、AttachmentType 注册、Screen 实现、网络接线、事件订阅、平台接口实现

`v26_1_2`

- NeoGradle 7.1.38
- NeoForge 26.1.2.76
- 基于同一套 `common` 业务，吸收 `docs/port-26.1.2.md` 中 API 变化

### 包划分

`common/src/main/java/com/reclizer/csgobox/`

- `box/`
- `capability/`：只保留数据模型
- `command/`
- `config/`
- `packet/`
- `sounds/`
- `advancement/`
- `utils/`：仅无 MC 依赖的工具
- `platform/`

`v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/`

- `platform/`
- `gui/`
- `item/`
- `capability/`
- `event/`
- `utils/`：仅 1.21.1 渲染相关
- `CsgoBoxMod.java` 或等价平台入口

`v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/`

- 结构与 `v1_21_1` 对齐

## 工作流总览

按以下顺序推进，避免边拆边坏：

1. 先修构建基础设施
2. 再完成 `common` 真实抽离
3. 再把 `v1_21_1` 收敛为平台层
4. 然后创建并移植 `v26_1_2`
5. 最后做双版本构建与人工验证

## 阶段计划

### 阶段 0：冻结基线与审计

目标：在继续改动前，明确“哪些文件已经迁移、哪些仍错误放置、哪些会阻塞构建”。

任务：

- 记录当前 `git status --short`
- 盘点 `common` 中缺失的业务类
- 盘点 `v1_21_1` 中应当下沉到 `common` 的类
- 检查 `settings.gradle`、根 `build.gradle`、`gradle.properties` 与实际模块目录是否一致
- 检查当前 `CsgoBox.java` 平台入口和计划中的目标入口命名是否一致

完成标准：

- 形成明确迁移清单
- 没有“以为已经迁移，实际没有”的盲区

### 阶段 1：修复 Gradle 基础设施

目标：让现有多模块骨架至少可被 Gradle 正常加载和解析依赖。

任务：

- 在根 `allprojects.repositories` 中加入 `https://maven.neoforged.net/releases`
- 如有必要补充备用镜像，但默认以 NeoForged 官方仓库为主
- 修复 `settings.gradle`
  - 二选一：
  - 若短期内还未创建 `v26_1_2/`，则改为根据 `active_versions` 动态 include
  - 或立即补齐 `v26_1_2/` 目录骨架，再保留静态 include
- 检查 `pluginManagement.repositories` 是否足够支撑两个 NeoGradle 版本
- 验证 `:common:compileJava`
- 验证 `:v1_21_1:compileJava` 至少能够走到源码编译阶段

完成标准：

- 不再出现 `lwjgl-freetype ... natives-macos-patch.jar` 找不到
- `settings.gradle` 与实际目录一致，不因缺失模块在配置阶段失败

风险：

- 中国大陆网络环境可能导致官方仓库解析不稳定
- NeoGradle 7.1.38 未来可能需要额外插件仓库

### 阶段 2：定义 common 边界并迁移纯业务代码

目标：把真正跨版本的业务代码从 `v1_21_1` 移入 `common`。

迁移候选：

- `box/BoxDefinition.java`
- `box/BoxRegistry.java`
- `box/BoxJsonLoader.java`
- `box/GradeGroup.java`
- `capability/CsboxPlayerData.java`
- `command/CsboxCommand.java`
- `config/CsboxConfig.java`
- `packet/PacketBoxOpenResult.java`
- `packet/PacketCsgoProgress.java`
- `packet/PacketRequestBoxItems.java`
- `packet/PacketSyncBoxItems.java`
- `packet/PacketValidation.java`
- `sounds/ModSounds.java`
- `advancement/ModLoadedTrigger.java`
- `advancement/OpenedBoxTrigger.java`
- `utils/ColorTools.java`
- `utils/EntityChineseMap.java`
- `utils/OverlayColor.java`

每个类迁移时执行：

- 判断是否含有 `net.minecraft.*` / `net.neoforged.*` 直接依赖
- 不能保留直接依赖时，改成 `platform/*` 接口
- 必要时拆分类：保留纯业务部分到 `common`，把平台绑定部分留在版本模块

需要新增或补全的抽象：

- `ITagParser`
- `IPayloadContext`
- `IRegistry`
- `IAttachmentRegistrar`
- `IMouseButtonEvent`
- `IGuiGraphics`
- `IIdentifier`
- `IPlatform`
- 如迁移中发现当前接口不够，再增补最小必要接口，不做过度抽象

验证：

- `rg "import net\\.(minecraft|neoforged)" common/src/main/java` 应为空
- `./gradlew :common:compileJava` 通过

完成标准：

- `common` 承载全部跨版本业务主逻辑
- `v1_21_1` 不再保存这些业务类的主副本

### 阶段 3：收敛 v1_21_1 为平台实现层

目标：让 `v1_21_1` 只承担 1.21.1 特有实现，不再重复业务逻辑。

任务：

- 新建或重命名平台入口，替代现有从原单模块直接搬来的入口类
- 绑定 `Platform.set(...)` 或等价初始化逻辑
- 实现 `platform/*Impl`
  - Identifier/ResourceLocation 封装
  - TagParser 封装
  - Registry 封装
  - 网络上下文封装
  - 鼠标按钮事件封装
  - Attachment 注册封装
- 保留并整理版本特有代码：
  - `gui/*`
  - `item/ItemCsgoBox.java`
  - `item/ItemCsgoKey.java`
  - `item/ModItems.java`
  - `capability/ModCapability.java`
  - `event/ModEvents.java`
  - `event/ClickEvent.java`
  - 渲染相关 `utils/*`
- 修正 `processResources`，确保合并 `common` 资源与本模块资源
- 确认产物命名为 `csgobox-1.21.1-1.0.5.jar`

验证：

- `./gradlew :v1_21_1:compileJava`
- `./gradlew :v1_21_1:processResources`
- `./gradlew :v1_21_1:jar`

完成标准：

- `v1_21_1` 可独立打包
- JAR 内同时包含本模块资源和 `common` 资源

### 阶段 4：建立 v26_1_2 模块骨架

目标：创建第二个平台模块，但先不急于一次性移植完全部差异。

任务：

- 创建 `v26_1_2/build.gradle`
- 创建 `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/`
- 创建 `v26_1_2/src/main/resources/META-INF/neoforge.mods.toml`
- 创建 `v26_1_2/src/main/resources/pack.mcmeta`
- 从 `v1_21_1` 复制平台层结构作为起点
- 让 `v26_1_2` 先能被 Gradle 识别和配置

建议：

- 初始阶段只复制平台相关目录，不复制已迁入 `common` 的业务类
- 避免再次形成 “整份 1.21.1 代码复制一套” 的技术债

验证：

- `./gradlew projects`
- `./gradlew :v26_1_2:tasks`

完成标准：

- `v26_1_2` 模块已真实存在，且 Gradle 可加载

### 阶段 5：应用 26.1.2 API 差异

目标：基于 `docs/port-26.1.2.md` 完成 26.1.2 平台适配。

必须处理的差异：

1. `Screen` 构造器变化
2. `TagParser.parseTag` -> `parseCompoundFully`
3. `appendHoverText` 新签名
4. `AttachmentType` 序列化机制变化
5. `PoseStack` -> `Matrix3x2fStack`
6. `GuiGraphics.renderItem` 新增 `seed`
7. 网络回复方式与 `PacketDistributor` 变化
8. 鼠标事件按钮访问方式变化
9. 注册表访问返回值变化
10. `Entity.level()` / `ServerLevel` 处理变化
11. `Container.items` 私有化影响复核

实现策略：

- 能通过 `common` 抽象吸收的差异，优先走接口
- GUI 2D/3D 矩阵差异保留在 `v26_1_2/gui` 和渲染工具中解决
- 不为了“接口纯粹”把图形 API 强行抽象到 common

验证：

- `./gradlew :v26_1_2:compileJava`

完成标准：

- `v26_1_2` 完成编译期 API 适配

### 阶段 6：双版本资源、元数据与版本治理收口

目标：确保两个平台模块共享一致的 mod 元数据，但保留各自版本参数。

任务：

- 校验根 `gradle.properties` 是否完整包含：
  - `mc_version_1_21_1`
  - `neo_version_1_21_1`
  - `neogradle_version_1_21_1`
  - `mc_version_26_1_2`
  - `neo_version_26_1_2`
  - `neogradle_version_26_1_2`
  - `pack_format_1_21_1`
  - `pack_format_26_1_2`
- 保证 `neoforge.mods.toml` 模板渲染变量完整
- 检查 `README.md`、`CHANGELOG.md` 中版本说明是否与多平台产物一致
- 明确 `active_versions` 的用途：
  - 仅控制 CI 执行
  - 或同时控制 settings 动态 include

完成标准：

- 版本号、产物名、资源模板变量一致

### 阶段 7：构建验证

目标：确保两个版本都能独立产物化。

验证顺序：

1. `./gradlew :common:compileJava`
2. `./gradlew :v1_21_1:compileJava`
3. `./gradlew :v1_21_1:jar`
4. `./gradlew :v26_1_2:compileJava`
5. `./gradlew :v26_1_2:jar`
6. `./gradlew build`

需要检查：

- 产物文件名是否正确
- JAR 是否包含 `assets/csgobox/**`
- JAR 是否包含 `data/csgobox/recipe/**`
- `META-INF/neoforge.mods.toml` 中版本变量是否正确展开
- 不存在旧路径残留导致的重复资源或脏资源

完成标准：

- 双版本构建成功
- 两个 JAR 均可用于后续手工加载测试

### 阶段 8：运行与手工回归

目标：编译通过后，验证功能没有在重构中丢失。

最低回归范围：

- 物品注册：四把钥匙 + 箱子
- 锻造台下界合金钥匙配方
- 开箱主界面
- 滚动动画与结果同步
- Tooltip 展示
- 生物掉落
- `/csbox` 命令
- 成就触发与统计累加
- 配置文件生成与读取：`config/csgobox.toml`

优先级：

- 先验证 `v1_21_1`
- 再验证 `v26_1_2`

完成标准：

- 核心用户功能与 1.0.5 行为一致

### 阶段 9：清理与提交

目标：提交一个可审阅、可回滚、可继续演进的结果。

任务：

- 删除不再使用的旧 `src/main/java` 与旧 `src/main/resources` 残留
- 清理错误命名、过时注释、临时调试代码
- 再次确认未误删用户已有改动
- 生成最终 `git diff --stat`
- 提交到 `multiloader-refactor` 或当前工作分支

建议提交拆分：

1. `build: stabilize multiloader gradle layout`
2. `refactor: move shared logic into common`
3. `feat: finish 1.21.1 platform module`
4. `feat: add 26.1.2 platform module`
5. `docs: update multiversion build metadata`

## 文件级迁移清单

### 应迁入 common

- `box/BoxDefinition.java`
- `box/BoxRegistry.java`
- `box/BoxJsonLoader.java`
- `box/GradeGroup.java`
- `capability/CsboxPlayerData.java`
- `command/CsboxCommand.java`
- `config/CsboxConfig.java`
- `packet/PacketBoxOpenResult.java`
- `packet/PacketCsgoProgress.java`
- `packet/PacketRequestBoxItems.java`
- `packet/PacketSyncBoxItems.java`
- `packet/PacketValidation.java`
- `sounds/ModSounds.java`
- `advancement/ModLoadedTrigger.java`
- `advancement/OpenedBoxTrigger.java`
- `utils/ColorTools.java`
- `utils/EntityChineseMap.java`
- `utils/OverlayColor.java`

### 保留在平台模块

- `CsgoBox` / 平台入口类
- `gui/*`
- `item/ItemCsgoBox.java`
- `item/ItemCsgoKey.java`：若仍强依赖 MC Item，可保留在平台层；若可只留文本与属性业务，可再评估拆分
- `item/ModItems.java`
- `capability/ModCapability.java`
- `event/ModEvents.java`
- `event/ClickEvent.java`
- `utils/GuiItemMove.java`
- `utils/IconListTools.java`
- `utils/RenderFontTool.java`
- `utils/RandomItem.java`

## 关键技术决策

### 1. 不追求“所有类都进 common”

只把真正共享且可脱离 MC API 的逻辑放进 `common`。GUI、渲染、事件桥接、注册表桥接留在平台层，避免抽象失控。

### 2. 根仓库统一声明依赖仓库

`maven.neoforged.net/releases` 放在根 `allprojects.repositories`，避免每个子模块重复维护，且解决 lwjgl patch 工件解析问题。

### 3. settings 应与模块实际存在状态一致

不要维持“配置已 include，但目录不存在”的中间状态。否则后续任何构建和 IDE 导入都不稳定。

### 4. 先保 1.21.1 可打包，再做 26.1.2

`v1_21_1` 是当前唯一真实代码来源，也是回归基线。若先并行做 26.1.2，会放大排错面。

## 风险清单

- `common` 迁移过程中可能暴露大量隐式 MC 依赖，超出初始接口设计
- `CsboxCommand`、Trigger、Packet 逻辑可能并不是真正“纯业务”，需要二次拆分
- `ItemCsgoKey` 是否适合进 `common` 需要以源码为准，不应机械照搬旧计划
- 26.1.2 的 GUI 2D 矩阵替代可能导致动画视觉退化
- `settings.gradle` 动态 include 若实现粗糙，IDE 导入体验会变差
- `active_versions` 同时用于 CI 和 settings 时，语义要保持单一
- 若 26.1.2 最终必须 Java 25，则需要单独处理子模块 toolchain，而不是抬高整个仓库

## 每阶段验收门槛

- 阶段 1 后：`common` 与 `v1_21_1` 至少可以被 Gradle 正常配置
- 阶段 2 后：`common` 无 MC 依赖且可编译
- 阶段 3 后：`v1_21_1` 可独立出 JAR
- 阶段 5 后：`v26_1_2` 可编译
- 阶段 7 后：双版本均可出 JAR
- 阶段 8 后：关键玩法回归通过

## 推荐执行顺序

1. 修仓库配置与 settings 一致性
2. 修 `v1_21_1` 依赖解析
3. 迁移 `common` 业务代码
4. 把 `v1_21_1` 瘦身到平台层
5. 建立 `v26_1_2`
6. 套用 `docs/port-26.1.2.md`
7. 双版本构建与回归

## 本计划与外部计划的主要修正

- 外部计划把 `common` 视为“基本完成”，但当前仓库并非如此
- 外部计划默认 `v26_1_2` 只是未创建的下一步；当前仓库却已经在 settings 中声明了它，这需要优先收口
- 外部计划默认第一阻塞只有 lwjgl 依赖；当前实际还有模块声明与目录不一致的问题
- 外部计划里部分“应迁入 common”的类是否能无改动迁入，需要以源码中的实际 API 依赖为准
