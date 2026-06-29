<!-- generated-by: gsd-doc-writer -->
# CS2 Box 架构

## 系统概述

CS2 Box 是一个 NeoForge 1.21.1 Minecraft 模组，实现了 CS:GO/CS2 风格的宝箱系统。玩家可以通过开箱获得随机物品，物品稀有度分为 5 个等级（grade1-grade5，从普通到稀有）。模组采用客户端-服务端架构，开箱逻辑由服务端权威执行，客户端负责界面展示和开箱动画。

## 组件图

```
CsgoBox (主入口)
    |
    +-- 宝箱系统
    |       +-- BoxRegistry（单例宝箱定义）
    |       +-- BoxDefinition（不可变的宝箱配置记录）
    |       +-- BoxJsonLoader（从 config/*.json 加载宝箱）
    |       +-- GradeGroup（物品等级分组）
    |
    +-- 物品系统
    |       +-- ItemCsgoBox（带数据组件的宝箱物品）
    |       +-- ItemCsgoKey（锁定宝箱的钥匙物品）
    |       +-- ModItems（物品注册）
    |
    +-- Capability 系统
    |       +-- ModCapability（NeoForge 附件注册）
    |       +-- CsboxPlayerData（玩家特定状态）
    |
    +-- 网络层
    |       +-- PacketCsgoProgress（客户端 → 服务端开箱请求）
    |       +-- PacketBoxOpenResult（服务端 → 客户端结果）
    |       +-- PacketSyncBoxItems（服务端 → 客户端物品列表）
    |       +-- PacketRequestBoxItems（客户端 → 服务端物品请求）
    |       +-- PacketValidation
    |
    +-- GUI（客户端）
    |       +-- CsboxScreen（宝箱预览/交互界面）
    |       +-- CsboxProgressScreen（开箱动画）
    |       +-- CsLookItemScreen（物品查看）
    |
    +-- 事件
    |       +-- ClickEvent（右键点击处理，客户端）
    |       +-- ModEvents（服务端事件处理）
    |
    +-- 进度
    |       +-- OpenedBoxTrigger（自定义进度触发器）
    |       +-- ModLoadedTrigger（模组加载检测）
    |
    +-- 配置
    |       +-- CsboxConfig（TOML 配置）
    |
    +-- 命令
    |       +-- CsboxCommand（服务端命令）
    |
    +-- 音效
            +-- ModSounds（音效事件注册）
```

## 数据流

### 开箱流程

1. **客户端交互**：玩家手持 CSGO 宝箱物品右键点击
2. **ClickEvent**（`event/ClickEvent.java`）：客户端处理器检测点击并打开 `CsboxScreen`
3. **CsboxScreen**（`gui/CsboxScreen.java`）：显示宝箱预览界面，向服务端请求物品数据
4. **PacketRequestBoxItems**：客户端向服务端发送宝箱内容请求
5. **PacketSyncBoxItems**：服务端返回宝箱定义（物品、等级、钥匙需求）
6. **用户确认**：玩家点击"开启"按钮
7. **PacketCsgoProgress**：客户端向服务端发送开箱请求（包含请求 ID）
8. **服务端处理**（`packet/PacketCsgoProgress.java`）：
   - 验证玩家是否持有宝箱和钥匙（如需要）
   - 使用 `SecureRandom` 实现服务端随机性
   - 通过 `RandomItem` 工具计算动画物品和中奖物品
   - 将结果存储到玩家 capability
   - 记录统计并触发进度（如启用）
9. **PacketBoxOpenResult**：服务端发送结果（中奖物品、动画序列）到客户端
10. **CsboxProgressScreen**：客户端播放开箱动画并显示结果
11. **背包更新**：服务端将实际物品给予玩家

### 配置加载流程

1. **模组初始化**：`CsgoBox` 构造函数注册事件监听器
2. **FMLCommonSetupEvent**：如果 `loadDefaultBoxes` 启用，触发 `BoxJsonLoader.loadAll()`
3. **BoxJsonLoader**：读取所有 `config/csbox/*.json` 文件，使用 Mojang Codec 解析
4. **BoxRegistry**：按 `ResourceLocation` 存储解析后的 `BoxDefinition` 对象
5. **BoxDefinition**：包含宝箱名称、钥匙、掉率、等级和物品池的不可变记录

## 核心抽象

### BoxDefinition（box/BoxDefinition.java）
包含完整宝箱配置的不可变记录：
- `id`：唯一的 ResourceLocation 标识符
- `name`：宝箱的显示组件
- `keyItem`：所需钥匙的 ResourceLocation（或 `minecraft:air`）
- `dropRate`：基础实体掉落概率（0.0-1.0）
- `grades`：按稀有度等级组织的 GradeGroup 物品列表
- `entityDropRates`：每个实体掉落概率的覆盖值

同时实现 `Codec`（用于 JSON 序列化）和 `StreamCodec`（用于网络传输）。

### ItemCsgoBox（item/ItemCsgoBox.java）
Minecraft Item 子类，具有以下特性：
- `BOX_ID` DataComponentType：通过 ResourceLocation 将 ItemStack 链接到 BoxDefinition
- `getDefinition()`：从 ItemStack 的数据组件获取 BoxDefinition
- `getItemGroup()`：返回所有可能奖励及其等级
- `getRandom()`：返回等级权重数组用于概率计算

### PacketCsgoProgress（packet/PacketCsgoProgress.java）
服务端数据包处理器：
- 验证玩家状态和物品持有
- 使用 `SecureRandom` 实现服务端权威随机性
- 通过 `OPEN_BLOCKED_UNTIL_TICK` map 管理开箱冷却
- 计算动画序列和中奖物品
- 更新玩家 capability 中的结果
- 触发统计和进度奖励

### CsboxPlayerData（capability/CsboxPlayerData.java）
玩家 capability 数据记录，存储：
- `seed`：服务端生成的用于动画同步的随机种子
- `grade`：中奖物品等级（1-5）
- `item`：中奖的 ItemStack
- 基于 Codec 的序列化用于网络同步

### ModCapability（capability/ModCapability.java）
NeoForge AttachmentType 注册，用于持久化玩家数据，使用 `CsboxPlayerData.CODEC` 进行序列化。

## 目录结构说明

```
src/main/java/com/reclizer/csgobox/
    |-- CsgoBox.java          # 模组入口点，事件总线注册
    |-- advancement/          # 自定义进度触发器
    |-- box/                  # 核心宝箱系统：定义、注册、加载
    |-- capability/          # NeoForge 玩家数据附件
    |-- command/             # 服务端控制台命令
    |-- config/              # TOML 配置处理
    |-- event/               # 事件订阅（客户端/服务端）
    |-- gui/                 # 客户端 UI 界面
    |-- item/                # 物品定义和注册
    |-- packet/              # 网络协议处理
    |-- sounds/              # 音效事件定义
    |-- utils/               # 共享工具类（渲染、随机等）

src/main/resources/
    |-- assets/csgobox/      # 材质、模型、音效、语言文件
    |-- data/csgobox/        # 数据包（合成表、进度）
```

- **box/**：包含独立于 Minecraft 系统的纯数据结构，便于测试
- **capability/**：将玩家状态管理与游戏逻辑分离
- **gui/**：客户端专用渲染代码，与服务端逻辑隔离
- **packet/**：网络协议定义，包含客户端和服务端处理器
- **event/**：事件订阅按分发类型（客户端 vs 服务端）分离
- **utils/**：共享工具类，避免跨模块代码重复
