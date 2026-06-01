# /csbox 命令行工具 — 技术规范文档

> **版本**: 2.0.0  
> **适用范围**: CS2 Box 模组 (csgobox) NeoForge 1.21.1  
> **文档目的**: 定义 `/csbox` 命令的完整技术规范，用于运行时管理箱子（Box）配置，包括等级物品的动态添加与查询

---

## 目录

1. [技术需求](#1-技术需求)
2. [命令规范](#2-命令规范)
3. [使用示例](#3-使用示例)
4. [帮助功能](#4-帮助功能)
5. [输入验证与输出格式](#5-输入验证与输出格式)

---

## 1. 技术需求

### 1.1 开发环境

| 项目 | 规格 |
|------|------|
| 目标平台 | Minecraft NeoForge 1.21.1 |
| Java 版本 | JDK 21 (`JavaLanguageVersion.of(21)`) |
| Gradle | 8.11+ (wrapper) |
| 依赖 | NeoForge `21.1.115`, Cloth Config (optional), KubeJS `2101.7.2-build.368` (compileOnly) |
| 源文件位置 | `src/main/java/com/reclizer/csgobox/command/CsboxCommand.java` |
| MODID | `csgobox` |

### 1.2 核心依赖与 API

命令系统基于 Mojang Brigadier 框架，通过 NeoForge 事件总线注册。核心操作对象为模组内部的箱子配置数据模型：

```java
// 关键导入 — Brigadier 命令框架
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

// 关键导入 — 模组内部 API
import com.reclizer.csgobox.api.box.BoxDefinition;
import com.reclizer.csgobox.api.box.BoxRegistry;
import com.reclizer.csgobox.api.box.GradeGroup;

// 关键导入 — 物品与 NBT
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
```

### 1.3 操作对象模型

命令操作的核心数据模型层级：

```
BoxRegistry                              — 全局箱子注册表 (Map<ResourceLocation, BoxDefinition>)
  └── BoxDefinition                      — 单个箱子定义
        ├── id: ResourceLocation         — 箱子唯一标识 (如 csgobox:weapon_supply_box)
        ├── name: Component              — 显示名称
        ├── keyItem: ResourceLocation    — 开箱钥匙物品 ID
        ├── dropRate: float              — 全局掉落率
        ├── dropEntities: List<RL>       — 掉落此箱子的实体列表
        ├── entityDropRates: Map<RL,Float> — 实体独立掉落率
        ├── texture: Optional<RL>        — 自定义纹理
        ├── sound: Optional<RL>          — 自定义开箱音效
        └── grades: List<GradeGroup>     — 等级组列表
              └── GradeGroup             — 单个等级组
                    ├── id: String       — 等级标识 (如 "consumer", "mil_spec")
                    ├── displayName: String — 等级显示名 (如 "消费级")
                    ├── color: int       — 颜色 (ARGB)
                    ├── weight: int      — 权重 (抽奖概率 = weight / totalWeight)
                    └── items: List<ItemStack> — 该等级包含的物品列表
```

### 1.4 实现约束

| 约束 | 说明 |
|------|------|
| **服务端执行** | 命令仅在服务端注册和执行（`@EventBusSubscriber` 无 Dist 限制），通过 `event.getDispatcher()` 注册 |
| **权限等级** | 所有子命令要求权限等级 2（相当于 `/give` 级别，OP + cheat enabled） |
| **线程安全** | 所有操作在服务端主线程执行，无需额外同步 |
| **ItemStack 复制** | 手持物品通过 `.copy()` 创建副本后存储到 GradeGroup，避免影响玩家手中原物品 |
| **不可变重建** | BoxDefinition 和 GradeGroup 均为 record（不可变）。修改操作需读取 → 重建新实例 → 重新注册，替换旧定义 |
| **同步策略** | 箱子配置修改后立即生效于后续开箱操作；已存在的箱子物品不受影响（它们仅保存 box_id 引用） |
| **输出语言** | 所有反馈消息使用 `Component.translatable()` / `Component.literal()`，支持本地化 |
| **错误恢复** | 任何操作失败时仅反馈错误消息，不影响箱子注册表现有数据 |

### 1.5 注册架构

```java
@EventBusSubscriber(modid = CsgoBox.MODID)
public class CsboxCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("csbox")
                .requires(source -> source.hasPermission(2))
                .then(/* 子命令在此注册 */)
        );
    }
}
```

### 1.6 语言文件扩展 (`assets/csgobox/lang/en_us.json`)

```json
{
  "commands.csgobox.help.title": "---- /csbox Box Management Help ----",

  "commands.csgobox.help.list": "/csbox list",
  "commands.csgobox.help.list.desc": "List all registered boxes with their grade groups",
  "commands.csgobox.help.info": "/csbox info <box>",
  "commands.csgobox.help.info.desc": "Show detailed information about a specific box",
  "commands.csgobox.help.add": "/csbox add <box> <grade> hand <count>",
  "commands.csgobox.help.add.desc": "Add the item in your main hand to a box's grade group",
  "commands.csgobox.help.give": "/csbox give <box> [count] [player]",
  "commands.csgobox.help.give.desc": "Give a box item to yourself or another player",
  "commands.csgobox.help.reload": "/csbox reload",
  "commands.csgobox.help.reload.desc": "Reload box definitions from KubeJS script",

  "commands.csgobox.help.footer": "Permission level 2 required. Item in main hand will be consumed.",

  "commands.csgobox.list.header": "Registered Boxes (%d total):",
  "commands.csgobox.list.entry": "  %s - %s (%d grades)",
  "commands.csgobox.list.empty": "No boxes registered.",

  "commands.csgobox.info.header": "Box: %s (%s)",
  "commands.csgobox.info.key": "  Key: %s",
  "commands.csgobox.info.drop_rate": "  Drop Rate: %.0f%% (global)",
  "commands.csgobox.info.drop_entities": "  Drop Entities: %s",
  "commands.csgobox.info.grades": "  Grades (%d):",
  "commands.csgobox.info.grade_entry": "    [%s] \"%s\" weight=%d items=%d",
  "commands.csgobox.info.not_found": "Box not found: %s",

  "commands.csgobox.add.success": "Added %s x%d to box [%s] grade [%s]",
  "commands.csgobox.add.empty_hand": "You must hold an item in your main hand",
  "commands.csgobox.add.no_box": "Box not found: %s",
  "commands.csgobox.add.no_grade": "Grade not found in box %s: %s",
  "commands.csgobox.add.available_grades": "Available grades: %s",

  "commands.csgobox.give.success": "Gave %s x%d to %s",
  "commands.csgobox.give.received": "You received %s x%d from %s",

  "commands.csgobox.reload.success": "Reloaded %d box definitions",
  "commands.csgobox.reload.kubejs_unavailable": "KubeJS is not loaded, cannot reload",

  "commands.csgobox.error.invalid_count": "Invalid count: %d. Must be 1-64",
  "commands.csgobox.error.invalid_box": "Invalid box ID: %s",
  "commands.csgobox.error.invalid_grade": "Invalid grade ID: %s"
}
```

---

## 2. 命令规范

### 2.1 命令树结构

```
/csbox
├── help                                                     — 显示帮助信息
├── list                                                     — 列出所有已注册箱子及等级概要
├── info <box>                                               — 查看指定箱子的完整配置详情
├── add <box> <grade> hand <count>                           — 将手持物品添加到箱子等级 ★NEW
├── give <box> [count] [player]                              — 给予玩家指定箱子物品
└── reload                                                   — 重新加载 KubeJS 箱子配置
```

### 2.2 `/csbox list` — 列出所有箱子

**功能**: 列出当前 BoxRegistry 中所有已注册的箱子，显示其 ID、显示名称及包含的等级组数量。

**语法**:
```
/csbox list
```

**执行流程**:
```
1. 从 BoxRegistry.getAll() 获取所有 BoxDefinition
2. 若注册表为空 → 反馈空列表消息
3. 遍历每个 BoxDefinition，输出：
   - 箱子 ID (命名空间格式)
   - 显示名称
   - 等级组数量
```

**输出示例**:
```
Registered Boxes (1 total):
  csgobox:weapon_supply_box - 武器供应箱 (5 grades)
```

---

### 2.3 `/csbox info` — 查看箱子详情

**功能**: 查看指定箱子的完整配置信息，包括钥匙物品、掉落率、掉落实体列表、所有等级组及其物品。

**语法**:
```
/csbox info <box>
```

**参数**:

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `box` | `ResourceLocationArgument` | ✅ | 箱子 ID，格式 `namespace:box_id`，支持 TAB 补全已有箱子 |

**执行流程**:
```
1. 解析 box → 从 BoxRegistry.get(id) 获取 BoxDefinition
2. 若不存在 → 反馈错误消息并列出可用箱子
3. 输出：显示名称、钥匙物品
4. 输出：全局掉落率、实体独立掉率
5. 输出：掉落实体列表
6. 遍历 grades，输出每个等级组：ID、显示名、权重、物品数、物品列表
```

**输出示例**:
```
Box: csgobox:weapon_supply_box (武器供应箱)
  Key: csgobox:csgo_key0
  Drop Rate: 100% (global)
  Drop Entities: minecraft:zombie, minecraft:skeleton, ...
  Grades (5):
    [consumer] "消费级" weight=625 items=14
    [industrial] "工业级" weight=125 items=11
    [mil_spec] "军规级" weight=25 items=5
    [restricted] "受限" weight=5 items=8
    [classified] "保密" weight=2 items=11
```

**错误处理**:

| 错误场景 | 处理方式 | 消息 |
|----------|----------|------|
| box ID 不存在 | 反馈并提示可用箱子 | `commands.csgobox.info.not_found` |

---

### 2.4 `/csbox add <box> <grade> hand <count>` — 添加手持物品到箱子等级 ★核心命令

**功能**: 将玩家主手中持有的物品复制指定数量，添加到目标箱子指定等级的物品池中。该物品将在后续开箱时作为该等级的候选掉落之一。玩家手中的物品**不会被消耗**（仅读取复制）。

**语法**:
```
/csbox add <box> <grade> hand <count>
```

**参数**:

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|------|--------|------|
| `box` | `ResourceLocationArgument` | ✅ | — | 目标箱子 ID，如 `csgobox:weapon_supply_box`，支持 TAB 补全 |
| `grade` | `StringArgument` (word) | ✅ | — | 目标等级标识符，如 `consumer`、`mil_spec`，支持 TAB 补全当前箱子已有等级 |
| `hand` | **字面量** `hand` | ✅ | — | 关键字，表示物品来源为玩家主手。输入命令时玩家必须手持物品 |
| `count` | `IntegerArgument` (1-64) | ❌ | 1 | 要添加到等级池的物品数量（复制数量） |

**参数取值约束**:

| 参数 | 取值范围/格式 | 说明 |
|------|---------------|------|
| `box` | `[a-z0-9_.-]+:[a-z0-9/_.-]+` | 标准 ResourceLocation 格式。必须在 BoxRegistry 中存在 |
| `grade` | 任意字母数字字符串 | 必须在目标箱子的 `grades` 列表中已存在（用于定位等级组） |
| `hand` | 字面量 `hand` | 只能输入 `hand`，不可替换为其他值 |
| `count` | 整数 1 ≤ count ≤ 64 | 超出范围时 Brigadier 自动拦截并提示 |

**执行流程**:
```
1. 解析 box → 从 BoxRegistry 获取 BoxDefinition
   ├─ 不存在 → 反馈 commands.csgobox.add.no_box + 可用列表
   └─ 存在 → 继续
2. 获取玩家主手物品 (player.getMainHandItem())
   ├─ 为空 (isEmpty) → 反馈 commands.csgobox.add.empty_hand
   └─ 不为空 → 继续
3. 解析 grade → 在 BoxDefinition.grades() 中查找匹配的 GradeGroup
   ├─ 不存在 → 反馈 commands.csgobox.add.no_grade + 可用等级列表
   └─ 存在 → 继续
4. 解析 count → 校验 1-64
5. 对手持物品执行 .copy() 创建独立副本
   副本设置数量为 count（若手持物品不足以提供，使用 Math.min）
6. 重建 GradeGroup（不可变 record）：
   - 创建新的 items 列表 = [原 items..., 新 ItemStack × count]
   - 构造新 GradeGroup(newId, newName, color, weight, newItems)
7. 重建 BoxDefinition（不可变 record）：
   - 替换对应位置的 GradeGroup
   - 构造新 BoxDefinition(...)
8. BoxRegistry.register(newBoxDefinition) 覆盖旧定义
9. 反馈成功消息：物品名称、数量、箱子名、等级名
```

**`hand` 关键字说明**:
- `hand` 是一个**字面量参数**（literal argument），不是字符串参数
- 输入时直接写 `hand`，Brigadier 会自动校验
- 该字面量的存在表示"从主手获取物品"，未来可扩展 `offhand` 支持副手

**NBT 保留规则**:
- 手持物品的全部 NBT 标签（附魔、命名、耐久、自定义数据等）会通过 `.copy()` 完整保留到副本中
- 添加到等级池的物品保留其原始 NBT，开箱时将按原样掉落

**错误处理**:

| 错误场景 | 处理方式 | 消息键 |
|----------|----------|--------|
| box ID 不存在于注册表 | 反馈错误 + 提示可用箱子列表 | `commands.csgobox.add.no_box` |
| 等级 ID 不在该箱子中 | 反馈错误 + 提示该箱子可用等级列表 | `commands.csgobox.add.no_grade` |
| 玩家主手为空 | 反馈错误，提示必须手持物品 | `commands.csgobox.add.empty_hand` |
| count < 1 或 count > 64 | Brigadier 自动拦截 | `commands.csgobox.error.invalid_count` |
| 手持物品数量不足 count | 使用实际手持数量（WARN 日志 + 成功消息标注实际数量） | (成功消息中体现) |

---

### 2.5 `/csbox give` — 给予箱子物品

**功能**: 给予指定玩家一个已配置的箱子物品（ItemCsgoBox），该物品携带对应箱子的 `box_id` DataComponent。

**语法**:
```
/csbox give <box> [count] [player]
```

**参数**:

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|------|--------|------|
| `box` | `ResourceLocationArgument` | ✅ | — | 箱子 ID |
| `count` | `IntegerArgument` (1-64) | ❌ | 1 | 给予数量 |
| `player` | `EntityArgument.player()` | ❌ | 执行者自身 | 目标玩家 |

**执行流程**:
```
1. 解析 box → 从 BoxRegistry 获取
2. 创建 ItemStack(ModItems.ITEM_CSGOBOX.get(), count)
3. ItemCsgoBox.setBoxId(boxId, stack) 设置 DataComponent
4. targetPlayer.getInventory().add(stack)
5. 反馈成功消息（给予者和接收者均可见）
```

**错误处理**:

| 错误场景 | 处理方式 |
|----------|----------|
| box 不存在 | 反馈错误 + 可用列表 |
| 目标玩家不在线 | EntityArgument 自动拦截 |

---

### 2.6 `/csbox reload` — 重新加载箱子配置

**功能**: 触发 KubeJS 脚本重新执行箱子注册逻辑，覆盖当前 BoxRegistry 内容。

**语法**:
```
/csbox reload
```

**执行流程**:
```
1. 检查 KubeJS 是否加载 (ModList.get().isLoaded("kubejs"))
2. 若未加载 → 反馈 kubejs_unavailable
3. 清空 BoxRegistry.clear()
4. 触发 KubeJS CsboxRegistryEventJS 重新执行
5. 反馈 reload.success 消息
```

**注意**: 重载后，通过 `/csbox add` 手动添加的物品将丢失（除非 KubeJS 脚本中也包含），因为 reload 会完全重建注册表。

**错误处理**:

| 错误场景 | 处理方式 |
|----------|----------|
| KubeJS 未加载 | 反馈 `commands.csgobox.reload.kubejs_unavailable` |

---

### 2.7 `/csbox help` — 帮助信息

**功能**: 显示所有子命令的列表及简要说明。

**语法**:
```
/csbox help
```

详细规范见 [第 4 节](#4-帮助功能)。

---

## 3. 使用示例

### 3.1 基础查询操作

```mcfunction
# 查看所有已注册的箱子
/csbox list

# 查看武器供应箱的完整配置
/csbox info csgobox:weapon_supply_box
```

### 3.2 添加手持物品到箱子等级 ★核心功能

```mcfunction
# 手持一把钻石剑，添加到武器供应箱的"保密"等级，数量 1
/csbox add csgobox:weapon_supply_box classified hand 1

# 手持附魔弓，添加到"受限"等级，数量 5
/csbox add csgobox:weapon_supply_box restricted hand 5

# 手持命名物品"Excalibur"，添加到"军规级"等级
/csbox add csgobox:weapon_supply_box mil_spec hand 1

# 手持自定义 NBT 物品（如附魔苹果），添加到"消费级"
/csbox add csgobox:weapon_supply_box consumer hand 10
```

### 3.3 给予箱子物品

```mcfunction
# 给自己 1 个武器供应箱
/csbox give csgobox:weapon_supply_box

# 给自己 5 个武器供应箱
/csbox give csgobox:weapon_supply_box 5

# 给玩家 Steve 3 个武器供应箱
/csbox give csgobox:weapon_supply_box 3 Steve
```

### 3.4 边界情况与错误示例

```mcfunction
# 空手执行 add → 错误
/csbox add csgobox:weapon_supply_box consumer hand 1
# Error: You must hold an item in your main hand

# 不存在的箱子 → 错误
/csbox add csgobox:nonexistent_box consumer hand 1
# Error: Box not found: csgobox:nonexistent_box

# 不存在的等级 → 错误 + 列出可用等级
/csbox add csgobox:weapon_supply_box legendary hand 1
# Error: Grade not found in box csgobox:weapon_supply_box: legendary
# Available grades: consumer, industrial, mil_spec, restricted, classified

# count 超出范围 → 自动拦截
/csbox add csgobox:weapon_supply_box consumer hand 128
# Number must be between 1 and 64

# 查看不存在的箱子信息 → 错误
/csbox info csgobox:nonexistent
# Error: Box not found: csgobox:nonexistent
```

### 3.5 完整工作流示例

```mcfunction
# 1. 查看当前箱子配置
/csbox list

# 2. 查看"保密"等级当前有哪些物品
/csbox info csgobox:weapon_supply_box
# ... 查看 classified 下的 items 列表 ...

# 3. 手持一把附魔钻石剑，添加到"保密"等级
/csbox add csgobox:weapon_supply_box classified hand 1
# [CSBox] Added Diamond Sword x1 to box [武器供应箱] grade [保密]

# 4. 获取箱子物品供测试
/csbox give csgobox:weapon_supply_box 1
# 用钥匙打开箱子验证新物品是否出现在奖池中

# 5. KubeJS 重载 (谨慎使用，会清除手动添加的物品)
# /csbox reload
```

---

## 4. 帮助功能

### 4.1 触发方式

- **显式调用**: `/csbox help`
- **错误调用自动提示**: 当用户输入不完整或错误的子命令时，Brigadier 自动提示可用子命令

### 4.2 输出内容

```
---- /csbox Box Management Help ----
/csbox list
      List all registered boxes with their grade groups
/csbox info <box>
      Show detailed information about a specific box
/csbox add <box> <grade> hand <count>
      Add the item in your main hand to a box's grade group
/csbox give <box> [count] [player]
      Give a box item to yourself or another player
/csbox reload
      Reload box definitions from KubeJS script
/csbox help
      Show this help message
-------------------------------------
Permission level 2 required. Item in main hand will be consumed.
```

### 4.3 样式规范

| 元素 | 风格 |
|------|------|
| 标题/分隔线 | `ChatFormatting.GOLD` + `ChatFormatting.STRIKETHROUGH` |
| 命令语法 | `ChatFormatting.AQUA` |
| 命令说明 | `ChatFormatting.GRAY` |
| 页脚提示 | `ChatFormatting.DARK_GRAY` |

### 4.4 参数约定符号

| 符号 | 含义 |
|------|------|
| `<arg>` | 必需参数 |
| `[arg]` | 可选参数 |
| `hand` | 字面量关键字，表示物品来源为玩家主手 |
| `namespace:box_id` | 箱子命名空间 ID |

---

## 5. 输入验证与输出格式

### 5.1 输入验证矩阵

#### Box 参数验证

| 检查 | 规则 | 实现 |
|------|------|------|
| 格式 | `namespace:box_id` | `ResourceLocationArgument.id()` |
| 存在性 | 必须在 `BoxRegistry` 中存在 | 手动校验 + `SuggestionProvider` 补全 |
| 补全建议 | 从 `BoxRegistry.getIds()` 动态获取 | `BOX_ID_SUGGESTIONS` |

#### Grade 参数验证

| 检查 | 规则 | 实现 |
|------|------|------|
| 类型 | 字符串 (word) | `StringArgumentType.word()` |
| 存在性 | 必须在目标箱子的 grades 中存在 | 手动校验，遍历 `boxDef.grades()` 按 `GradeGroup.id()` 匹配 |
| 补全建议 | 从目标箱子的 grades 动态获取 | `GRADE_SUGGESTIONS` 动态 SuggestionProvider |

#### Hand 参数验证

| 检查 | 规则 | 实现 |
|------|------|------|
| 语法 | 字面量 `hand` | `Commands.literal("hand")` |
| 运行时检查 | 玩家主手不为空 | `player.getMainHandItem().isEmpty()` 判断 |

#### Count 参数验证

| 检查 | 规则 | 实现 |
|------|------|------|
| 类型 | 整数 | `IntegerArgumentType.integer(1, 64)` |
| 范围 | 1 ≤ count ≤ 64 | Brigadier 自动校验 |
| 默认值 | 未提供时默认为 1 | 命令实现中 `getIntegerOrDefault(ctx, "count", 1)` |

#### 权限验证

| 检查 | 规则 |
|------|------|
| 权限等级 | `requires(source -> source.hasPermission(2))` |
| 执行者类型 | 必须是玩家 (`ServerPlayerEntity`) — `add` 子命令特有要求 |
| 可用范围 | 服务端命令，单人模式开启作弊，多人模式需要 OP |

### 5.2 输出格式标准

#### 成功消息格式

```
[CSBox] <具体信息>
```

- 前缀 `[CSBox]` 使用 `ChatFormatting.DARK_GREEN` + `ChatFormatting.BOLD`
- 详情使用 `ChatFormatting.GREEN`
- 物品名、箱子名、等级名使用 `ChatFormatting.WHITE` 高亮

#### 错误消息格式

```
[CSBox] Error: <描述>
```

- 前缀 `[CSBox]` 使用 `ChatFormatting.RED` + `ChatFormatting.BOLD`
- 错误描述使用 `ChatFormatting.RED`
- 可用选项列表使用 `ChatFormatting.YELLOW`

### 5.3 自动补全建议

#### 箱子 ID 补全

```java
private static final SuggestionProvider<CommandSourceStack> BOX_ID_SUGGESTIONS =
    (context, builder) -> {
        for (ResourceLocation id : BoxRegistry.getIds()) {
            builder.suggest(id.toString());
        }
        return builder.buildFuture();
    };
```

#### 等级 ID 补全（动态，依赖箱子参数）

```java
private static SuggestionProvider<CommandSourceStack> gradeSuggestions() {
    return (context, builder) -> {
        ResourceLocation boxId = context.getArgument("box", ResourceLocation.class);
        BoxDefinition def = BoxRegistry.get(boxId);
        if (def != null) {
            for (GradeGroup grade : def.grades()) {
                builder.suggest(grade.id());
            }
        }
        return builder.buildFuture();
    };
}
```

### 5.4 异常传播与日志

| 层级 | 处理方式 |
|------|----------|
| Brigadier 语法异常 | 自动捕获并显示给玩家 |
| BoxRegistry 查询异常 | `catch (NullPointerException)` + 显示不存在消息 |
| 业务逻辑异常 | `catch (Exception e)` + 显示 `Component.literal(e.getMessage())` |
| 意外异常 | 记录到 `CsgoBox.LOGGER.error()`，玩家显示 `"An unexpected error occurred"` |
| 箱子重建失败 | 回滚，不修改 BoxRegistry，记录 ERROR 日志 |

---

## 附录 A: 与旧版 (v1.0.0) 的差异

| 方面 | v1.0.0 (旧) | v2.0.0 (新) |
|------|------------|------------|
| 定位 | 通用背包物品管理 | 箱子配置管理 |
| `add` 命令 | 向背包添加物品 | 将手持物品添加到箱子等级池 |
| `set`/`remove`/`query`/`clear`/`replace` | 背包槽位操作 | **已移除**，由原版 `/give` `/clear` `/item` 替代 |
| 新增命令 | — | `list`, `info`, `give`, `reload` |
| 核心参数 | `slot`, `item`, `nbt` | `box`, `grade`, `hand`, `count` |

## 附录 B: GradeGroup 不可变重建算法

```java
// 伪代码：向箱子等级添加物品的核心算法
public static void addItemToGrade(ResourceLocation boxId, String gradeId, 
                                   ItemStack handItem, int count) {
    BoxDefinition oldDef = BoxRegistry.get(boxId);
    
    // 查找目标等级组
    int gradeIndex = -1;
    GradeGroup oldGrade = null;
    List<GradeGroup> grades = oldDef.grades();
    for (int i = 0; i < grades.size(); i++) {
        if (grades.get(i).id().equals(gradeId)) {
            gradeIndex = i;
            oldGrade = grades.get(i);
            break;
        }
    }
    
    // 创建物品副本并设置数量
    ItemStack newItem = handItem.copy();
    newItem.setCount(Math.min(count, handItem.getCount()));
    
    // 重建 GradeGroup
    List<ItemStack> newItems = new ArrayList<>(oldGrade.items());
    newItems.add(newItem);
    GradeGroup newGrade = new GradeGroup(
        oldGrade.id(), oldGrade.displayName(), 
        oldGrade.color(), oldGrade.weight(), newItems
    );
    
    // 重建 BoxDefinition
    List<GradeGroup> newGrades = new ArrayList<>(grades);
    newGrades.set(gradeIndex, newGrade);
    BoxDefinition newDef = new BoxDefinition(
        oldDef.id(), oldDef.name(), oldDef.keyItem(), oldDef.dropRate(),
        oldDef.dropEntities(), newGrades, oldDef.texture(), oldDef.sound(),
        oldDef.entityDropRates()
    );
    
    // 覆盖注册
    BoxRegistry.register(newDef);
}
```