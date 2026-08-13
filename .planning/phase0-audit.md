> ⚠️ 已归档（历史规划快照）：本文档为过去的重构/审计规划产物，不再维护；当前进度与状态以 CHANGELOG.md 与 README.md 为准。

# Phase 0 基线审计记录

日期：2026-06-29

## 1. 现状清单（git status + 文件扫描）

### 1.1 模块结构

| 目录 | 状态 | 内容 |
|---|---|---|
| `common/` | 已建 | `src/main/java/com/reclizer/csgobox/platform/*`（10 个接口 + Platform 单例）；`src/main/resources/` 已收齐 |
| `v1_21_1/` | 已建 | 30 个 Java 文件，包前缀 `com.reclizer.csgobox.v1_21_1.*`；`src/main/resources/` 仅剩 `META-INF/neoforge.mods.toml` + `pack.mcmeta` |
| `v26_1_2/` | **缺失** | `settings.gradle` 已 include，但目录不存在 → `./gradlew projects` 必然失败 |

### 1.2 资源分布

- `common/src/main/resources/assets/csgobox/{lang,models,sounds,textures}`：齐全
- `common/src/main/resources/assets/minecraft/shaders/{post,program}`：齐全（含 fade_in_blur）
- `common/src/main/resources/data/csgobox/{advancement,recipe}`：齐全（5 个 JSON）
- `v1_21_1/src/main/resources/META-INF/neoforge.mods.toml`：平台元数据
- `v1_21_1/src/main/resources/pack.mcmeta`：平台元数据

资源分层已就绪，平台模块只承载 mod 元数据。

### 1.3 构建配置

- 根 `build.gradle`：`id 'base'`，`allprojects.repositories` 缺 `maven.neoforged.net/releases` → lwjgl patch 解析风险
- `settings.gradle`：静态 `include 'common'`, `'v1_21_1'`, `'v26_1_2'`；`v26_1_2` 缺目录
- `common/build.gradle`：`java-library` ✓
- `v1_21_1/build.gradle`：NeoGradle 7.0.171 ✓，`processResources` 模板展开 ✓
- `gradle.properties`：`mc_version_*` / `neo_version_*` / `neogradle_version_*` / `active_versions` 齐全；**缺 `pack_format_*`**

### 1.4 平台契约

`common` 已存在 9 个 `platform/*` 接口，覆盖 IPlatform/ITagParser/IIdentifier/IRegistry/IPayloadContext/IMouseButtonEvent/IAttachmentRegistrar/IGuiGraphics/IPoseStack + Platform 单例。`IPoseStack` 设计可能需扩展（目前 push/pop/translate/scale/rotate；26.1.2 是 `Matrix3x2fStack`，2D 接口已匹配，但语义上确认 rotate 只保留 Y 轴）。

## 2. 类级归属表（v1_21_1 → common 候选）

判定：A 可直接迁 / B 拆分后迁 / C 必须留平台

| 类 | 现路径 | 分类 | 备注 |
|---|---|---|---|
| `CsgoBox` | `v1_21_1/.../CsgoBox.java` | C | 平台入口，`@Mod` + 静态 `CONFIG` 初始化顺序约束 |
| `box/BoxDefinition` | `box/BoxDefinition.java` | B | record+Codec，含 `ResourceLocation`/`Component`/`ItemStack` → 拆分：纯 record+CODEC 留 common，引用平台部分留 `IPlatform`/`IIdentifier` |
| `box/BoxRegistry` | `box/BoxRegistry.java` | B | in-memory store，依赖 `CsgoBox.LOGGER` → 拆 Logger 改走 `Platform.get().logDebug()` |
| `box/BoxJsonLoader` | `box/BoxJsonLoader.java` | B | 文件 IO + JSON + ItemStack 解析 → 抽 `IPlatform.parseId`/`getItem`/`parseTag` |
| `box/GradeGroup` | `box/GradeGroup.java` | B | record+ItemStack → 同 BoxDefinition |
| `config/CsboxConfig` | `config/CsboxConfig.java` | B | 强依赖 `ModConfigSpec` → 抽 `IConfigSpec` 平台接口，由 v1_21_1 实现 |
| `capability/CsboxPlayerData` | `capability/CsboxPlayerData.java` | B | record+ItemStack+Codec → 拆纯 record+CODEC；attachment 留在 `ModCapability`（平台） |
| `capability/ModCapability` | `capability/ModCapability.java` | C | `DeferredRegister<AttachmentType<?>>` 平台绑定 |
| `command/CsboxCommand` | `command/CsboxCommand.java` | C | 大量 `Commands.*` / `CommandSourceStack` 平台 API，迁移成本远超价值，留平台 |
| `advancement/ModLoadedTrigger` | `advancement/ModLoadedTrigger.java` | C | `SimpleCriterionTrigger` 平台绑定 |
| `advancement/OpenedBoxTrigger` | `advancement/OpenedBoxTrigger.java` | C | 同上 + `Stat<ResourceLocation>` 强依赖 |
| `sounds/ModSounds` | `sounds/ModSounds.java` | C | `DeferredRegister<SoundEvent>` 平台绑定 |
| `packet/PacketBoxOpenResult` | `packet/PacketBoxOpenResult.java` | C | `CustomPacketPayload` + `StreamCodec<RegistryFriendlyByteBuf, ...>` 平台绑定 |
| `packet/PacketCsgoProgress` | `packet/PacketCsgoProgress.java` | C | 同上 + 重度 MC 玩家/物品交互 |
| `packet/PacketRequestBoxItems` | `packet/PacketRequestBoxItems.java` | C | 同上 |
| `packet/PacketSyncBoxItems` | `packet/PacketSyncBoxItems.java` | C | 同上 |
| `packet/PacketValidation` | `packet/PacketValidation.java` | **A** | 纯 list 校验工具，仅用 `Mth.clamp`，迁入 common 后改用 `Math.clamp`（Java 21 原生） |
| `event/ModEvents` | `event/ModEvents.java` | C | `LivingDeathEvent` 等平台事件订阅 |
| `event/ClickEvent` | `event/ClickEvent.java` | C | 客户端事件 + 屏幕打开 |
| `gui/CsboxScreen` | `gui/CsboxScreen.java` | C | 26.1.2 API 差异集中点 |
| `gui/CsboxProgressScreen` | `gui/CsboxProgressScreen.java` | C | 同上 |
| `gui/CsLookItemScreen` | `gui/CsLookItemScreen.java` | C | 同上 |
| `item/ItemCsgoBox` | `item/ItemCsgoBox.java` | C | 平台 Item 注册 + `appendHoverText` 签名差异 |
| `item/ItemCsgoKey` | `item/ItemCsgoKey.java` | C | 平台 Item |
| `item/ModItems` | `item/ModItems.java` | C | `DeferredRegister<Item>` 平台绑定 |
| `utils/ColorTools` | `utils/ColorTools.java` | **A** | 纯整数色工具，无 MC 依赖 |
| `utils/EntityChineseMap` | `utils/EntityChineseMap.java` | B | key 为 `ResourceLocation` → 改用 `String` key（在 common 用 `IIdentifier.toShortString()`）或保持 `IIdentifier` 抽象 |
| `utils/OverlayColor` | `utils/OverlayColor.java` | **A** | 纯色常量 |
| `utils/GuiItemMove` | `utils/GuiItemMove.java` | C | 26.1.2 矩阵变化点 |
| `utils/IconListTools` | `utils/IconListTools.java` | C | 26.1.2 矩阵变化点 |
| `utils/RenderFontTool` | `utils/RenderFontTool.java` | C | 26.1.2 字体 API 变化点 |
| `utils/RandomItem` | `utils/RandomItem.java` | B | `Random` 纯逻辑可迁；但 `precomputeGradeMap` 接收 `Map<ItemStack, Integer>` → 业务部分迁，调用平台 `getItem` |

### 2.1 统计

- A 类：3（`ColorTools` / `OverlayColor` / `PacketValidation` 改造后）
- B 类：6（`BoxDefinition` / `BoxRegistry` / `BoxJsonLoader` / `GradeGroup` / `CsboxConfig` / `CsboxPlayerData` / `EntityChineseMap` / `RandomItem`，需要拆分或替换平台类型）
- C 类：21（必须留平台）

### 2.2 第一批可迁入 common（A + 简化 B）

**第一批优先**：
1. `ColorTools`
2. `OverlayColor`
3. `PacketValidation`（改名 `PacketValidationCommon` 或保留 `PacketValidation`，去除 `Mth` 改 `Math`）

**第二批视平台接口到位后迁**：
1. `BoxDefinition` + `BoxRegistry`（拆出 ResourceLocation → IIdentifier）
2. `GradeGroup`
3. `CsboxPlayerData`（拆 record + CODEC）
4. `EntityChineseMap`（用 String 命名空间 key）
5. `CsboxConfig`（需新 IConfigSpec 平台接口）

**默认暂缓 / 留平台**：
- `BoxJsonLoader`（重度 IO+解析，需 IRegistry）
- `CsboxCommand`（CommandDispatcher 平台 API）
- `RandomItem`（依赖 `Map<ItemStack, Integer>`）
- `ModCapability` / `ModSounds` / `ModItems`（DeferredRegister 平台绑定）
- `ModLoadedTrigger` / `OpenedBoxTrigger`（SimpleCriterionTrigger 平台）
- 所有 packet 类
- 所有 gui / event / item 类

## 3. 构建阻塞点清单

1. **根仓库缺 `maven.neoforged.net/releases`** → 可能在解析 lwjgl-freetype natives-macos-patch 时失败
2. **`v26_1_2/` 目录缺失但已静态 include** → `./gradlew projects` 配置阶段失败
3. **`gradle.properties` 缺 `pack_format_*`** → `processResources` 模板里如有引用则失败（当前未引用，但规范要求）
4. **common 仅有接口骨架** → 需 Phase 2 填入业务

## 4. 失败处理预案

- 若 `./gradlew projects` 失败因 `v26_1_2` 缺失 → Phase 1 任务 1.5.2 改为动态 include
- 若 v1_21_1 解析依赖失败 → 检查 NeoForged 仓库是否可达
