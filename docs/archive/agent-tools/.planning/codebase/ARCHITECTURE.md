<!-- refreshed: 2026-06-29 -->
# 架构

**分析日期:** 2026-06-29

## 系统概览

```text
┌─────────────────────────────────────────────────────────────────┐
│                    入口: 右键点击箱子物品                          │
│       `event/ClickEvent.java` (客户端 @SubscribeEvent)            │
└────────────────────────────┬────────────────────────────────────┘
                             │ 打开
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     GUI 层（仅客户端）                             │
│   `gui/CsboxScreen.java`         -> 预览 & 打开按钮               │
│   `gui/CsboxProgressScreen.java` -> 服务端驱动的动画               │
│   `gui/CsLookItemScreen.java`    -> 3D 结果展示                   │
└────────────────────────────┬────────────────────────────────────┘
                             │ 发送数据包
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   数据包层（双向）                                  │
│   `packet/PacketRequestBoxItems`  (C -> S, 预览请求)              │
│   `packet/PacketSyncBoxItems`     (S -> C, 预览数据)              │
│   `packet/PacketCsgoProgress`     (C -> S, 打开请求)              │
│   `packet/PacketBoxOpenResult`    (S -> C, 动画 + 奖励)           │
│   `packet/PacketValidation.java`  (共享防御性辅助方法)             │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  服务端权威层                                      │
│   `packet/PacketCsgoProgress.handleServer` (核心逻辑)             │
│   - 验证手持箱、钥匙、冷却状态                                     │
│   - 通过 `RandomItem` 计算动画条 + 获胜物品                        │
│   - 消耗钥匙，减少箱子数量，给予奖励                                │
│   - 奖励自定义统计 `csgobox:opened_boxes`                         │
│   - 触发 `OpenedBoxTrigger` 以推进进度                            │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                       领域模型                                     │
│   `box/BoxDefinition` (record, 可 JSON 序列化)                    │
│   `box/GradeGroup`     (record, 每个稀有度等级一个)                │
│   `box/BoxRegistry`    (内存映射表, 从 JSON 加载)                  │
│   `box/BoxJsonLoader`  (文件系统读写, 默认数据生成)                 │
│   `item/ItemCsgoBox`   (Item, 通过 box_id 引用箱子)               │
│   `item/ItemCsgoKey`   (Item, 钥匙变种 0..3)                     │
│   `capability/CsboxPlayerData` + `ModCapability`                  │
│   `advancement/OpenedBoxTrigger`  + `ModLoadedTrigger`            │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  副作用 / 输出                                                    │
│  - 生物死亡时掉落物品    (`event/ModEvents.livingDeath`)           │
│  - 自定义统计持久化      (`Stats.CUSTOM` registry)                 │
│  - 进度推进             (`CriteriaTriggers`)                      │
│  - 玩家数据附着         (`CsboxPlayerData`, codec)                │
│  - 保存的配置 + 箱子 JSON (`config/csgobox.toml`,                  │
│                            `config/csbox/*.json`)                 │
└─────────────────────────────────────────────────────────────────┘
```

## 组件职责

| 组件 | 职责 | 文件 |
|-----------|----------------|------|
| `CsgoBox` | Mod 入口；注册 registry、数据包、配置、音效、附着数据 | `src/main/java/com/reclizer/csgobox/CsgoBox.java` |
| `ItemCsgoBox` | 箱子物品；读写 `box_id` 数据组件，显示内容物提示 | `src/main/java/com/reclizer/csgobox/item/ItemCsgoBox.java` |
| `ItemCsgoKey` | 钥匙物品（4 个等级：铁/金/钻石/下界合金） | `src/main/java/com/reclizer/csgobox/item/ItemCsgoKey.java` |
| `ModItems` | 物品的 `DeferredRegister` + 自定义创造标签页 `EQUIPMENT_TAB` | `src/main/java/com/reclizer/csgobox/item/ModItems.java` |
| `BoxDefinition` | 不可变的箱子规格（id、名称、钥匙、掉落率、掉落实体、等级） | `src/main/java/com/reclizer/csgobox/box/BoxDefinition.java` |
| `GradeGroup` | 每个稀有度等级（id、显示名称、颜色、权重、物品） | `src/main/java/com/reclizer/csgobox/box/GradeGroup.java` |
| `BoxRegistry` | 内存中的 `LinkedHashMap`，存放已加载的箱子 | `src/main/java/com/reclizer/csgobox/box/BoxRegistry.java` |
| `BoxJsonLoader` | 文件系统的加载/保存器；文件夹为空时生成默认的 `weapon_supply_box.json` | `src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java` |
| `ModEvents` | 生物死亡 -> 箱子掉落；玩家登录 -> `ModLoadedTrigger` | `src/main/java/com/reclizer/csgobox/event/ModEvents.java` |
| `ClickEvent` | 客户端右键 -> 打开 `CsboxScreen` | `src/main/java/com/reclizer/csgobox/event/ClickEvent.java` |
| `CsboxScreen` | 预览 GUI：展示内容物、钥匙、打开/返回按钮 | `src/main/java/com/reclizer/csgobox/gui/CsboxScreen.java` |
| `CsboxProgressScreen` | 服务端驱动的滚动动画，介于预览和奖励之间 | `src/main/java/com/reclizer/csgobox/gui/CsboxProgressScreen.java` |
| `CsLookItemScreen` | 最终 3D 可旋转奖励展示 | `src/main/java/com/reclizer/csgobox/gui/CsLookItemScreen.java` |
| `PacketRequestBoxItems` | C->S：请求当前手持箱子的预览数据 | `src/main/java/com/reclizer/csgobox/packet/PacketRequestBoxItems.java` |
| `PacketSyncBoxItems` | S->C：当前手持箱子的预览物品/等级/权重/钥匙信息 | `src/main/java/com/reclizer/csgobox/packet/PacketSyncBoxItems.java` |
| `PacketCsgoProgress` | C->S：客户端请求实际打开当前手持箱子 | `src/main/java/com/reclizer/csgobox/packet/PacketCsgoProgress.java` |
| `PacketBoxOpenResult` | S->C：50 个物品的动画条 + 获胜索引 + 最终奖励 | `src/main/java/com/reclizer/csgobox/packet/PacketBoxOpenResult.java` |
| `PacketValidation` | 防御性辅助方法：列表大小检查、防御性复制、队列修剪 | `src/main/java/com/reclizer/csgobox/packet/PacketValidation.java` |
| `RandomItem` | 纯确定性的加权稀有度物品选取（无网络通信） | `src/main/java/com/reclizer/csgobox/utils/RandomItem.java` |
| `IconListTools` | GUI 渲染：物品框、稀有度背景 | `src/main/java/com/reclizer/csgobox/utils/IconListTools.java` |
| `GuiItemMove` | 预览/结果屏幕使用的 3D 可旋转物品渲染 | `src/main/java/com/reclizer/csgobox/utils/GuiItemMove.java` |
| `RenderFontTool` | 缩放的 `FormattedCharSequence` 绘制 | `src/main/java/com/reclizer/csgobox/utils/RenderFontTool.java` |
| `ColorTools` | ARGB 工具 + 等级到颜色的映射 | `src/main/java/com/reclizer/csgobox/utils/ColorTools.java` |
| `OverlayColor` | 常量背景颜色（`0xFF333333`） | `src/main/java/com/reclizer/csgobox/utils/OverlayColor.java` |
| `EntityChineseMap` | 原版实体 ID 的硬编码中文显示名称 | `src/main/java/com/reclizer/csgobox/utils/EntityChineseMap.java` |
| `ModSounds` | `cs_dita`、`cs_open`、`cs_finish` 的 `DeferredRegister` | `src/main/java/com/reclizer/csgobox/sounds/ModSounds.java` |
| `CsboxCommand` | `/csbox ...` 管理员命令树（list/info/add/set/give/reload） | `src/main/java/com/reclizer/csgobox/command/CsboxCommand.java` |
| `CsboxConfig` | 含 `[general]`、`[advanced]`、`[sound]`、`[animation]` 分组的 `ModConfigSpec` | `src/main/java/com/reclizer/csgobox/config/CsboxConfig.java` |
| `CsboxPlayerData` | 附着到玩家的 Record（seed、mode、item、grade） | `src/main/java/com/reclizer/csgobox/capability/CsboxPlayerData.java` |
| `ModCapability` | 注册 `csgobox:player_data` attachment type | `src/main/java/com/reclizer/csgobox/capability/ModCapability.java` |
| `OpenedBoxTrigger` | `csgobox:opened_box` 的 `SimpleCriterionTrigger`，支持 `count` 阈值 | `src/main/java/com/reclizer/csgobox/advancement/OpenedBoxTrigger.java` |
| `ModLoadedTrigger` | `csgobox:mod_loaded` 的始终为真的触发器；驱动 `csgobox:root` 标签页 | `src/main/java/com/reclizer/csgobox/advancement/ModLoadedTrigger.java` |

## 模式概述

**总体：** 分层事件驱动的客户端/服务端 Mod，带有一个精简的共享领域模型。

**关键特征：**
- **服务端权威随机结果。** 客户端从不决定获胜物品；它仅渲染服务端发送的内容。
- **注册表驱动的内容。** 箱子定义存放在 `BoxRegistry`（内存中）和磁盘上的 `config/csbox/*.json` 中；其余代码仅通过 `ResourceLocation` 引用箱子。
- **Data Component 替代 NBT。** 箱子标识存储为类型化的 `csgobox:box_id` `DataComponentType<ResourceLocation>`（在 `ItemCsgoBox.BOX_ID` 中设置，持久化 + 网络同步）。`config/csbox/*.json` 中遗留的 NBT 风格 `tag` 字符串仍被兼容接受（`BoxJsonLoader.java:362-372`）。
- **自定义统计作为进度的真实来源。** 服务端奖励 `Stats.CUSTOM.get(csgobox:opened_boxes)`（`PacketCsgoProgress.java:164`）；`OpenedBoxTrigger.TriggerInstance.matches()` 读取该统计值，使得一个触发器类既能驱动无条件的"第一个箱子"进度，也能驱动 `count=200` 的"购物狂"进度。
- **防御性网络层。** 所有四个数据包在将数据暴露给消费者之前，都通过 `PacketValidation`（大小上限、列表一致性、防御性复制）运行其字段。

## 分层

**入口/事件层：**
- 目的：监听 NeoForge 事件并将其分发给正确的子系统。
- 位置：`src/main/java/com/reclizer/csgobox/event/`（`ClickEvent.java`、`ModEvents.java`）
- 包含：标记有 `@EventBusSubscriber(modid = CsgoBox.MODID)` 的 `@SubscribeEvent` 静态处理器。
- 依赖：`CsgoBox`、`BoxRegistry`、`BoxDefinition`、`ItemCsgoBox`。
- 被使用：仅 NeoForge 游戏事件总线。

**网络/数据包层：**
- 目的：在客户端和服务端之间编排类型化的载荷，带有大小限制和验证的 record。
- 位置：`src/main/java/com/reclizer/csgobox/packet/`
- 包含：实现 `CustomPacketPayload` 的 `record` 载荷，每个都有自己的 `Type`、`STREAM_CODEC`，以及 `handle`（客户端）或 `handleServer`（服务端）。
- 依赖：`ItemCsgoBox`、`BoxRegistry`、`RandomItem`、`OpenedBoxTrigger`、`CsboxPlayerData`。
- 被使用：`CsgoBox.registerPayloads()` 注册所有载荷，`CsboxScreen` / `CsboxProgressScreen` 发送/等待它们。

**GUI/渲染层：**
- 目的：用户看到的所有 `net.minecraft.client.gui.screens.Screen` 子类。
- 位置：`src/main/java/com/reclizer/csgobox/gui/`
- 包含：三个屏幕（`CsboxScreen`、`CsboxProgressScreen`、`CsLookItemScreen`）。导入 `RenderSystem`、`GuiGraphics`、`PoseStack` 等。
- 依赖：`CsgoBox.CONFIG`、数据包队列（`PacketSyncBoxItems.sPendingResponses`、`PacketBoxOpenResult.sPendingResults`）、工具渲染器。
- 被使用：`ClickEvent` 打开 `CsboxScreen`；`CsboxScreen` 转换到 `CsboxProgressScreen`；`CsboxProgressScreen` 转换到 `CsLookItemScreen`。

**领域/箱子层：**
- 目的：箱子内容模型和持久化。
- 位置：`src/main/java/com/reclizer/csgobox/box/`
- 包含：`BoxDefinition`、`GradeGroup`、`BoxRegistry`、`BoxJsonLoader`。
- 依赖：Gson、`FMLPaths`、`BuiltInRegistries`。
- 被使用：`ItemCsgoBox`（通过 id 解析箱子）、`ModEvents`（遍历 `BoxRegistry.getAll()`）、`PacketRequestBoxItems.handle`（发送箱子预览）、`PacketCsgoProgress.handleServer`（计算动画 + 奖励）、`CsboxCommand`（对箱子定义的 CRUD）。

**能力层：**
- 目的：每个玩家的持久状态（当前打开的 seed、模式、飞行中的奖励、等级）。
- 位置：`src/main/java/com/reclizer/csgobox/capability/`
- 包含：`CsboxPlayerData` record + 注册 `AttachmentType` 的 `ModCapability`。
- 被使用：`PacketCsgoProgress.handleServer` 写入 seed + 奖励，以便之后重连时可以恢复。`mode` 字段被当前逻辑保留/未使用。

**进度层：**
- 目的：由 `Stats.CUSTOM` + `CriteriaTriggers` 追踪的原版风格进度系统。
- 位置：`src/main/java/com/reclizer/csgobox/advancement/`
- 包含：`OpenedBoxTrigger`（带可选的 `count` 阈值）、`ModLoadedTrigger`（始终为真），两者都 `extends SimpleCriterionTrigger`。
- 被使用：`CsgoBox` 在 `Registries.TRIGGER_TYPE` 下注册两者，并在公共设置阶段解析 `Stats.CUSTOM.get(STAT_ID)`。

**命令层：**
- 目的：管理员（/op 等级 2）游戏内箱子 CRUD。
- 位置：`src/main/java/com/reclizer/csgobox/command/CsboxCommand.java`
- 包含：单个类，带有一个静态的 `@SubscribeEvent register(RegisterCommandsEvent)`，构建完整的 Brigadier 树。
- 依赖：`BoxRegistry`、`BoxJsonLoader`、`BoxDefinition`、`GradeGroup`、`ItemCsgoBox`、`ModItems`。

**物品层：**
- 目的：两种物品类型及其数据组件。
- 位置：`src/main/java/com/reclizer/csgobox/item/`
- 包含：`ItemCsgoBox`（箱子）、`ItemCsgoKey`（钥匙，所有 4 个等级共享此类）、`ModItems`（延迟注册 + 创造标签页）。

**配置层：**
- 目的：运行时可用户配置的旋钮。
- 位置：`src/main/java/com/reclizer/csgobox/config/CsboxConfig.java`
- 包含：单个类，带有按组分组的 `ModConfigSpec.*Value` 字段。构造函数在 `CsgoBox` 的静态初始化器中被急切调用，以便所有 `.get()` 调用同步工作——这是 v1.0.5 中 `init()` 修复的内容，详见 `CHANGELOG.md`。

**工具层：**
- 目的：纯辅助方法，无 I/O。
- 位置：`src/main/java/com/reclizer/csgobox/utils/`
- 包含：`RandomItem`、`IconListTools`、`GuiItemMove`、`RenderFontTool`、`ColorTools`、`OverlayColor`、`EntityChineseMap`。
- 被使用：GUI 和数据包层。

## 数据流

### 主要请求路径（玩家打开箱子）

1. **触发** - 玩家手持 `ItemCsgoBox` 时右键。`event/ClickEvent.onRightClick` 播放 `cs_open` 并推送 `CsboxScreen`。
2. **预览请求（C->S）** - `CsboxScreen` 构造函数发送 `PacketRequestBoxItems`，带有一个客户端生成的 `syncRequestId`，并记住当前手持箱子的 `expectedBoxId`。
3. **预览数据（S->C）** - `PacketRequestBoxItems.handle` 读取手持箱子，构建 `items + grades + weights + key`，并向请求的玩家发送 `PacketSyncBoxItems`。
4. **预览渲染** - `CsboxScreen.containerTick` 调用 `PacketSyncBoxItems.consumeMatching(syncRequestId, expectedBoxId)`，匹配成功后填充物品网格、钥匙数量和等级列表。`renderBg` 随后通过 `IconListTools` 绘制边框。
5. **打开点击** - 用户点击绿色"OPEN"按钮。`CsboxScreen.mouseClicked` 验证玩家背包中有所需的钥匙，生成一个 `openRequestId`，转换到 `CsboxProgressScreen`，并发送 `PacketCsgoProgress(openRequestId)`。
6. **权威服务端掷骰** - `PacketCsgoProgress.handleServer`：
   - 如果未手持 `ItemCsgoBox`、玩家已死亡/被移除、冷却中处于激活状态、箱子为空、没有权重或没有可用钥匙，则拒绝。
   - 生成 `serverSeed = SecureRandom.nextLong()`。
   - 通过 `RandomItem` 预计算 50 个物品的动画条，失败则回退。
   - 通过 `SECURE_RANDOM.nextInt` 在 `[35, 44]` 范围内选取 `winningIndex`。
   - 消耗一把钥匙，将手持箱子的数量减少 1。
   - 奖励 `Stats.CUSTOM.get(csgobox:opened_boxes)` 增加 1。
   - 触发 `OpenedBoxTrigger.INSTANCE.trigger(sp)`（当 `enableAchievements` 为 true 时）。
   - 将包含获胜物品 + 动画条的 `PacketBoxOpenResult` 发送回玩家。
7. **动画消费** - `CsboxProgressScreen.tick` 调用 `PacketBoxOpenResult.consumeMatching(expectedRequestId)`，将 50 个物品的条填充到 `itemInput / gradeInput`，然后驱动缓动函数。
8. **最终揭示** - 当动画完成时，打开 `CsLookItemScreen(resultItem, resultGrade)`。
9. **结果关闭** - 用户在 `CsLookItemScreen` 中点击 BACK 关闭屏幕并返回游戏。

### 次要流程：生物掉落

1. 任何生物死亡触发 `LivingDeathEvent`。`ModEvents.livingDeath` 遍历 `BoxRegistry.getAll()` 中的每个 `BoxDefinition`。
2. 按定义的掷骰：除非实体 id 在 `dropEntities` 中，否则跳过。有效概率 = `entityDropRate * lootingMultiplier`（抢夺每级 +50%，最多到 2.5 倍）* `CsgoBox.CONFIG.globalDropRatePercent() / 100F` 全局上限。限制在 `1.0` 以内。
3. 成功时，生成一个 `ItemCsgoBox`，通过 `ItemCsgoBox.setBoxId` 设置 `boxId`。

### 次要流程：进度 / 统计追踪

1. 每次成功打开，服务端执行 `sp.awardStat(CsgoBox.OPENED_BOXES_STAT, 1)`。
2. 如果 `CsgoBox.CONFIG.enableAchievements()` 为 true，则 `OpenedBoxTrigger.INSTANCE.trigger(sp)` 在客户端匹配 `csgobox:first_box`（无 count）和 `csgobox:shopper`（count=200）。
3. `ModLoadedTrigger` 在 `PlayerEvent.PlayerLoggedInEvent` 上触发，以便授予 `csgobox:root` 进度节点，这是在进度 UI 中显示 CS2 Box 标签页的唯一可靠方式。

### 次要流程：命令驱动的配置编辑

1. 管理员执行 `/csbox add <box> <grade> hand <count>`。
2. `CsboxCommand.addHandItem` 读取手持物品，构建新的 `GradeGroup`，调用 `BoxDefinition.withUpdatedGrade` -> `BoxRegistry.register(updatedBox)` -> `BoxJsonLoader.saveToFile(updatedBox)` 以原子方式写入。
3. 后续重载使用 `/csbox reload` -> `BoxRegistry.clear()` + `BoxJsonLoader.loadAll()`。

**状态管理：**
- 箱子定义：持久化（磁盘上的 JSON）+ 内存缓存（`BoxRegistry`）。
- 每个玩家状态：`CsboxPlayerData` attachment（通过 `DataComponentPatch` codec 持久化）。
- 动画请求匹配：进程内 FIFO 队列，通过 `PacketValidation.trimQueue` 修剪至每个 8 个条目。
- 每个玩家的打开冷却：内存中的 `HashMap<UUID, Long> OPEN_BLOCKED_UNTIL_TICK`。不持久化；服务端重启时重置。

## 关键抽象

**BoxDefinition (record)：**
- 目的：不可变的、可序列化的箱子类型规范。
- 模式：Java `record` + Mojang `Codec` + 自定义 `StreamCodec<RegistryFriendlyByteBuf>`。包含一个 `Builder` 用于代码驱动的构造。

**GradeGroup (record)：**
- 目的：箱子内的一个稀有度等级。
- 模式：Java `record` + `Codec` + `STREAM_CODEC` + `StreamCodec.composite`。

**CustomPacketPayload (record)：**
- 目的：四个数据包各自是一个实现 `CustomPacketPayload` 的 Java `record`，带有自己的 `Type<>` 和 `STREAM_CODEC`。

**SimpleCriterionTrigger 子类：**
- 目的：`OpenedBoxTrigger` 和 `ModLoadedTrigger` 的模式——继承 `SimpleCriterionTrigger<T extends SimpleInstance>`。

## 入口点

**Mod 入口：** `CsgoBox.java` - `@Mod(CsgoBox.MODID)`，注册配置、延迟注册、载荷处理器、公共设置、自定义统计、条件触发器。
**GUI 入口：** `ClickEvent.java` - `PlayerInteractEvent.RightClickItem`（仅客户端），播放打开音效并打开 `CsboxScreen`。
**生物掉落入口：** `ModEvents.java` - `LivingDeathEvent`（两侧），按箱子掷掉落概率。
**命令入口：** `CsboxCommand.java` - `RegisterCommandsEvent`，构建 `/csbox ...` Brigadier 命令树。

## 架构约束

- **线程：** 所有 NeoForge 载荷处理器使用 `context.enqueueWork(...)` 弹回主线程。静态队列仅在主线程上修改。
- **全局状态：** `BoxRegistry.BOX_REGISTRY`、`sPendingResults`、`sPendingResponses`、`OPEN_BLOCKED_UNTIL_TICK`、`OPENED_BOXES_STAT`。全部为单进程，无同步。
- **循环导入：** 未检测到；依赖单向流动（事件 -> 数据包 -> 领域）。
- **箱子标识稳定性：** 箱子的 `ResourceLocation` 是其 JSON 文件的基本名。重命名 JSON 文件 = 新的箱子 id = 所有现有的手持物品失效。
- **端对称性：** Mod 是 `BOTH` 端的。仅客户端的类显式引用 `Dist.CLIENT`。

## 反模式

### 从未被调用的 Init()
**现象：** `CsboxConfig` 使用延迟的 `init()` 模式，但没有调用者调用它，导致所有配置字段在运行时保持默认值 `false/0/null`。
**正确做法：** 在构造函数中内联 `.get()` 调用。

### 客户端信任的 RNG
**现象：** 动画条和最终奖励必须来自服务端，绝不来自客户端。
**正确做法：** 服务端生成 `serverSeed`，计算所有动画物品 + 获胜索引。客户端仅消费和显示。

### 渲染代码中的库存扫描
**现象：** `countKeys()` 和 `tryConsumeKeys()` 扫描所有库存槽位。
**正确做法：** 目前可以接受（最多 36 个槽位）。如果扩展，重构为索引查找。

## 错误处理

**策略：** 开发时大声失败，游戏时软失败。
**模式：** 网络反序列化显式边界检查；服务端拒绝通过 `sendRejected` 发送空结果；JSON 解析错误按文件捕获；路径遍历被记录警告后拒绝。

## 横切关注点

**日志：** SLF4J，标准 `info/warn/error/debug` 级别。
**验证：** 集中在 `packet/PacketValidation.java`。
**认证：** 仅 Minecraft 会话；命令层需要 op 等级 2。

---

*架构分析：2026-06-29*
