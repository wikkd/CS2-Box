# CS2 Box — 箱子配置编辑器（Cloth Config）技术规划 v2.0

> **版本**: 2.0-draft | **核心目标**: 通过 Cloth Config GUI 编辑/管理箱子配置
> **适用范围**: CS2 Box 模组 v1.0.2+ NeoForge 1.21.1

---

## 1. 需求分析

### 1.1 用户核心需求

用户希望通过 **图形界面** 替代手动编辑 JSON 文件和输入 `/csbox` 命令来管理箱子配置。当前管理箱子的方式存在以下痛点：

| 当前方式 | 痛点 |
|----------|------|
| 手动编辑 `config/csbox/*.json` | 需要知道物品 ID、DataComponent 格式，容易写错 |
| `/csbox add ... hand ...` | 需记住命令语法，每次只能添加一个物品 |
| `/csbox set ... count/weight` | 无法直观看到所有物品，操作繁琐 |
| `/csbox info` | 只能文字输出，无法交互式编辑 |

### 1.2 数据模型全景

```
BoxDefinition (箱子定义)
├── id: ResourceLocation          ← csgobox:weapon_supply_box
├── name: Component               ← "武器供应箱"
├── keyItem: ResourceLocation     ← csgobox:csgo_key0
├── dropRate: float               ← 0.08 (8%)
├── dropEntities: List<RL>        ← [zombie, skeleton, ...]
├── entityDropRates: Map<RL,F>    ← {zombie: 1.0, creeper: 0.5}
├── texture: Optional<RL>          ← 自定义箱子纹理
├── sound: Optional<RL>           ← 自定义开箱音效
└── grades: List<GradeGroup>      ← [grade5, grade4, ..., grade1]
    ├── id: String                ← "classified" / "restricted" / ...
    ├── displayName: String       ← "保密" / "受限" / ...
    ├── color: int (ARGB)         ← 0xFFFFDC1D (金色)
    ├── weight: int               ← 2 (抽奖权重)
    └── items: List<ItemStack>    ← [netherite_sword×1, netherite_axe×1, ...]
                                    每个 ItemStack 可带 DataComponent/NBT
```

### 1.3 JSON ↔ 代码映射关系

JSON 文件 (`weapon_supply_box.json`) 的字段到代码的映射：

| JSON 字段 | BoxDefinition 字段 | 可编辑? |
|-----------|-------------------|---------|
| `"name"` | `name` (Component) | ✅ 文本输入 |
| `"key"` | `keyItem` (RL) | ✅ 下拉选择（钥匙物品列表） |
| `"drop"` | `dropRate` (float) | ✅ 滑块 (0~100%) |
| `"random": [w5,w4,w3,w2,w1]` | `grades[i].weight` | ✅ 5 个滑块 |
| `"entity": [id,rate, ...]` | `dropEntities` + `entityDropRates` | ✅ 列表增删 |
| `"texture"` | `texture` (Optional RL) | ✅ 下拉/文本 |
| `"sound"` | `sound` (Optional RL) | ✅ 下拉/文本 |
| `"grade5"` ~ `"grade1"` | `grades[0..4].items` | ⭐ 核心功能 |

---

## 2. 架构方案

### 2.1 技术选型：Cloth Config 自定义入口 + 自定义编辑 Screen

**关键决策**: 箱子配置是**嵌套动态数据结构**（Box → Grades → Items），不适合用 Cloth Config 标准的静态注解驱动配置。采用以下混合架构：

```
Mod Menu / Mod 配置按钮
       │
       ▼
┌─────────────────────────────────┐
│  CsboxConfigGuiProvider          │  ← Cloth Config 入口点
│  (AutoConfig.GuiProvider)        │
│       │                         │
│       ▼                         │
│  ┌───────────────────┐          │
│  │ BoxListScreen      │          │  ← 自定义 Screen（非标准 Cloth Config）
│  │ (箱子列表选择界面)   │          │
│  │                     │          │
│  │  ┌──────────────┐  │          │
│  │  │ 武器供应箱   │  │          │  ← 点击进入详情
│  │  │ 枪械补给箱   │  │          │
│  │  │ [+ 新建箱子] │  │          │
│  │  └──────────────┘  │          │
│  └─────────┬─────────┘          │
│            │                     │
│            ▼                     │
│  ┌───────────────────┐          │
│  │ BoxEditScreen      │          │  ← 自定义 Screen（单箱子完整编辑）
│  │ (箱子详情编辑界面)   │          │
│  │                     │          │
│  │  [基本信息区]        │          │  名称/掉落率/钥匙/纹理/音效
│  │  [实体掉落区]        │          │  实体列表 + 掉落率
│  │  [等级权重区]        │          │  5 个等级权重滑块
│  │  [等级物品池]        │          │  ★ 核心：Tab 切换 5 个等级
│  │   Tab: grade5(保密)  │          │    物品列表 + 添加/删除/数量调整
│  │   Tab: grade4(受限)  │          │
│  │   ...               │          │
│  │  [保存/导出JSON]     │          │
│  └───────────────────┘          │
└─────────────────────────────────┘
```

**为什么不用纯 Cloth Config 注解方案？**

| 限制 | 原因 |
|------|------|
| 动态箱子数量 | Cloth Config 要求编译期确定字段数，但箱子是运行时从 JSON 加载的 |
| 动态物品列表 | 每个等级的物品数量不固定，无法用固定数量的 `@ConfigEntry` 声明 |
| 物品选择器 | 需要从背包/物品注册表中选择物品，这是游戏内交互，不是简单文本输入 |
| DataComponent/NBT | 枪械模组的物品带有复杂自定义数据，需要可视化编辑 |

### 2.2 与现有系统的关系

```
                    ┌─────────────┐
                    │ JSON 文件   │  config/csbox/*.json  (持久化源)
                    └──────┬──────┘
                           │ 加载
                           ▼
                    ┌─────────────┐
                    │ BoxRegistry │  运行时内存 (Map<RL, BoxDefinition>)
                    └──┬──────┬───┘
                       │      │
           ┌───────────┘      └───────────┐
           ▼                              ▼
   ┌───────────────┐              ┌───────────────┐
   │ /csbox 命令    │              │ GUI 编辑器      │  ← 新增
   │ (CLI 操作)     │              │ (本规划目标)    │
   └───────────────┘              └───────┬───────┘
                                           │ 读写
                                           ▼
                                   ┌───────────────┐
                                   │ BoxJsonLoader  │  saveToFile() ← 新增方法
                                   └───────────────┘
                                           │ 写入
                                           ▼
                                   ┌───────────────┐
                                   │ JSON 文件      │  config/csbox/*.json (更新)
                                   └───────────────┘
```

**关键设计原则**：
- GUI 和 `/csbox` 命令操作的是**同一个 `BoxRegistry`**
- GUI 修改后调用 `BoxJsonLoader.saveToFile()` **同步回写 JSON**
- JSON 是唯一持久化源；重启后从 JSON 重新加载

---

## 3. 详细设计

### 3.1 文件结构

```
src/main/java/com/reclizer/csgobox/
├── config/                          ← 新增包
│   ├── CsboxConfig.java             ← AutoConfig 主类（轻量，仅做入口）
│   └── CsboxConfigGuiProvider.java  ← Cloth Config GUI Provider（打开自定义 Screen）
│
├── gui/client/                      ← 新增编辑器 Screen
│   ├── BoxListScreen.java           ← 箱子列表选择界面
│   └── BoxEditScreen.java           ← 单箱子完整编辑界面
│
├── api/box/
│   └── BoxJsonLoader.java           ← 新增 saveToFile() 方法
│
└── ... (现有文件不变)
```

### 3.2 Phase 1: Cloth Config 入口 + 箱子列表

#### 3.2.1 CsboxConfig.java（轻量主类）

```java
@Config(name = "CS2 Box")
public class CsboxConfig {
    // 仅作为 AutoConfig 注册入口
    // 实际配置逻辑在自定义 Screen 中处理
}
```

此类的唯一作用是满足 [CsgoBox.java:37](src/main/java/com/reclizer/csgobox/CsgoBox.java#L37) 已有的 `AutoConfig.register()` 调用。

#### 3.2.2 CsboxConfigGuiProvider.java

```java
@AutoConfig.GuiProvider
public class CsboxConfigGuiProvider implements ConfigScreenProvider<CsboxConfig> {
    @Override
    public Screen getScreen(CsboxConfig config, Screen parent) {
        return new BoxListScreen(parent);  // 打开自定义箱子列表界面
    }
}
```

#### 3.2.3 BoxListScreen.java — 箱子列表

**布局**（参考 CsboxScreen 的像素风风格）：

```
╔════════════════════════════════════════╗
║  ══ CS2 Box 箱子管理器 ══              ║  ← 标题栏
╠════════════════════════════════════════╣
║                                          ║
║  ┌────────────────────────────────┐     ║
║  │ 📦 武器供应箱                   │     ║  ← 可点击条目
║  │    5个等级 · 49个物品 · 掉落100%  │     ║
║  ├────────────────────────────────┤     ║
║  │ 📦 枪械补给箱                   │     ║
║  │    5个等级 · 16个物品 · 掉落8%    │     ║
║  ├────────────────────────────────┤     ║
║  │ ... 更多箱子 ...                 │     ║
║  └────────────────────────────────┘     ║
║                                          ║
║  ┌────────────────────────────────┐     ║
║  │        ［＋ 新建箱子］            │     ║  ← 创建空白箱子
║  └────────────────────────────────┘     ║
║                                          ║
║  提示: 选择一个箱子进行编辑，或创建新箱子  ║
╚════════════════════════════════════════╝
```

**功能**:
- 从 `BoxRegistry.getAll()` 读取所有已注册箱子
- 显示每个箱子的：名称、等级数、物品总数、掉落率
- 点击条目 → 打开 `BoxEditScreen(boxDef)`
- 「+ 新建箱子」→ 弹出输入框获取 ID 和名称 → 创建空 BoxDefinition → 进入编辑
- ESC 返回父界面

**交互**:
- 鼠标悬停显示箱子 tooltip（包含实体列表等详细信息）
- 支持鼠标滚轮滚动（箱子多时）

### 3.3 Phase 2: 箱子编辑器（核心）

#### 3.3.1 BoxEditScreen.java — 完整编辑界面

**布局**（分区域设计）：

```
╔════════════════════════════════════════════════╗
║  ← 返回    编辑: 武器供应箱 (csgobox:weapon_supply_box)  ║
╠════════════════════════════════════════════════╣
║                                                  ║
║  ┌─ 基本信息 ─────────────────────────────────┐ ║
║  │ 名称: [武器供应箱_________]                  │ ║
║  │ 掉落率: [████████░░░░] 100%    钥匙: [▼csgo_key0] │ ║
║  │ 纹理: [默认____▼]  音效: [默认____▼]         │ ║
║  └────────────────────────────────────────────┘ ║
║                                                  ║
║  ┌─ 掉落实体 ─────────────────────────────────┐ ║
║  │ zombie ████████ 100%  [✕]                   │ ║
║  │ skeleton ██████ 100%  [✕]                   │ ║
║  │ creeper ████████ 100%  [✕]                   │ ║
║  │ [+ 添加实体...]                              │ ║
║  └────────────────────────────────────────────┘ ║
║                                                  ║
║  ┌─ 等级权重 ─────────────────────────────────┐ ║
║  │ grade5(保密):  [██░░░░░░] 2    ■ 金色      │ ║
║  │ grade4(受限):  [███░░░░░] 5    ■ 红橙      │ ║
║  │ grade3(军规):  [█████░░░] 25   ■ 粉紫      │ ║
║  │ grade2(工业):  [████████░] 125  ■ 紫蓝      │ ║
║  │ grade1(消费):  [████████████] 625 ■ 蓝色    │ ║
║  └────────────────────────────────────────────┘ ║
║                                                  ║
║  ┌─ 等级物品池 ───────────────────────────────┐ ║
║  │ [grade5] [grade4] [grade3] [grade2] [grade1]│ ║  ← Tab 切换
║  ├────────────────────────────────────────────┤ ║
║  │                                            │ ║
║  │  ⚔ 下界合金剑 ×1        [✕删除] [数量-][+]  │ ║  ← 物品图标+名称
║  │  🪓 下界合金斧 ×1        [✕删除] [数量-][+]  │ ║
║  │  ⛏ 下界合金镐 ×1        [✕删除] [数量-][+]  │ ║
║  │  🔨 下界合金锹 ×1        [✕删除] [数量-][+]  │ ║
║  │  🌾 下界合金锄 ×1        [✕删除] [数量-][+]  │ ║
║  │  💎 钻石头盔 ×1         [✕删除] [数量-][+]  │ ║
║  │  ...                                      │ ║
║  │                                            │ ║
║  │  [＋ 从背包添加物品]  [＋ 输入物品ID添加]    │ ║  ← 两种添加方式
║  └────────────────────────────────────────────┘ ║
║                                                  ║
║           [💾 保存并返回]  [📤 导出为JSON]       ║
╚════════════════════════════════════════════════╝
```

#### 3.3.2 各区域详细设计

##### 区域 A: 基本信息

| 字段 | 控件类型 | 说明 |
|------|----------|------|
| 名称 | 文本输入框 | 直接编辑 Component.getString()，支持 § 颜色代码 |
| 掉落率 | 滑块 + 百分比文本 | 范围 0.001 ~ 1.0，步进 0.001，右侧显示 "X%" |
| 钥匙物品 | 下拉选择框 | 列出所有注册的钥匙物品（`csgo_key0~3`），支持搜索 |
| 纹理 | 下拉 + 文本输入 | "默认" 或自定义 ResourceLocation |
| 音效 | 下拉 + 文本输入 | "默认" 或自定义 ResourceLocation |

##### 区域 B: 掉落实体列表

- **展示**: 实体 ID + 掉落率进度条 + 百分比 + 删除按钮
- **添加**: 点击「+ 添加实体」→ 弹出搜索框（模糊匹配 `BuiltInRegistries.ENTITY_TYPE`）→ 选择后设置掉落率
- **编辑掉落率**: 点击实体行的掉落率数值 → 弹出小滑块调整
- **特殊格式**: 支持两种模式切换 —— 全局统一掉落率 / 逐实体独立掉落率

##### 区域 C: 等级权重

- 5 行，每行对应一个等级
- 左侧显示等级标识 + 名称 + 颜色预览圆点
- 中间滑块：范围 1~2000，步进 1
- 右侧实时显示该等级概率百分比 = `weight / totalWeight * 100`
- 权重归一化提示：当总权重变化时动态更新各等级概率

##### 区域 D: 等级物品池 ⭐（最核心）

**Tab 切换**: 5 个标签页对应 grade5~grade1，使用等级颜色高亮当前选中 tab

**物品列表展示**:
- 每行渲染：物品图标（16x16）+ 物品名称（含附魔/NBT 信息）+ 数量 + 操作按钮
- 使用 `IconListTools.renderItemFrame()` 或类似方法复用现有渲染逻辑
- 悬停显示完整 Tooltip（含 DataComponent 信息）
- 数量按钮 `[−]` `[+]` 范围 1~64，或直接输入
- `[✕删除]` 移除此物品

**添加物品 — 方式一: 从背包添加**
- 点击「+ 从背包添加物品」→ 打开一个简易物品选择界面
- 显示玩家背包所有物品（排除空槽位）
- 点击物品 → 以 `.copy()` 添加到当前等级物品池
- 自动跳过已在池中的物品（防重复）

**添加物品 — 方式二: 输入物品 ID**
- 点击「+ 输入物品 ID」→ 弹出文本输入框
- 输入格式: `minecraft:diamond_sword`
- 支持自动补全（从 `BuiltInRegistries.ITEM` 匹配）
- 高级选项: 可选填 DataComponent JSON（折叠面板）

##### 区域 E: 底部操作栏

| 按钮 | 功能 |
|------|------|
| **保存并返回** | 将当前编辑状态重建为 BoxDefinition → `BoxRegistry.register()` → `BoxJsonLoader.saveToFile(def)` → 返回列表页 |
| **导出为 JSON** | 将当前 BoxDefinition 序列化为格式化 JSON 并复制到剪贴板 / 显示在聊天栏 |
| **删除此箱子** | 二次确认后从 `BoxRegistry` 移除并删除对应 JSON 文件 |

### 3.4 Phase 3: JSON 持久化

#### 3.4.1 BoxJsonLoader.saveToFile() — 新增方法

```java
public static void saveToFile(BoxDefinition def) {
    Path file = BOXES_DIR.resolve(def.id().getPath() + ".json");
    JsonObject json = new JsonObject();

    // 序列化基本属性
    json.addProperty("name", def.name().getString());
    json.addProperty("key", def.keyItem().toString());
    json.addProperty("drop", def.dropRate());

    // 序列化权重
    JsonArray weightsArr = new JsonArray();
    for (GradeGroup g : def.grades()) weightsArr.add(g.weight());
    json.add("random", weightsArr);

    // 序列化实体
    if (!def.entityDropRates().isEmpty()) {
        JsonArray entityArr = new JsonArray();
        for (var entry : def.entityDropRates().entrySet()) {
            entityArr.add(entry.getKey().toString());
            entityArr.add(entry.getValue());
        }
        json.add("entity", entityArr);
    } else if (!def.dropEntities().isEmpty()) {
        JsonArray entityArr = new JsonArray();
        for (RL e : def.dropEntities()) entityArr.add(e.toString());
        json.add("entity", entityArr);
    }

    // 序列化等级物品
    for (int i = 0; i < def.grades().size(); i++) {
        String gradeKey = "grade" + (def.grades().size() - i);
        GradeGroup g = def.grades().get(i);
        JsonArray itemsArr = new JsonArray();
        for (ItemStack item : g.items()) {
            itemsArr.add(serializeItemStack(item));
        }
        json.add(gradeKey, itemsArr);
    }

    try (Writer w = Files.newBufferedWriter(file)) {
        GSON.toJson(json, w);
    }
}

private static String serializeItemStack(ItemStack stack) {
    JsonObject obj = new JsonObject();
    obj.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    obj.addProperty("count", stack.getCount());

    // 序列化 DataComponent（如果有）
    DataComponentPatch patch = stack.getComponents();
    if (!patch.isEmpty()) {
        var result = DataComponentPatch.CODEC.encodeStart(JsonOps.INSTANCE, patch);
        result.result().ifPresent(elem -> obj.add("components", elem));
    }
    return GSON.toJson(obj);
}
```

### 3.5 Phase 4: 国际化与构建验证

#### 语言文件新增键（en_us.json / zh_cn.json）

```jsonc
{
  "gui.csgobox.box_manager.title": "CS2 Box Manager",
  "gui.csgobox.box_manager.subtitle": "Select a box to edit or create a new one",
  "gui.csgobox.box_manager.new_box": "New Box",
  "gui.csgobox.box_manager.no_boxes": "No boxes registered",
  "gui.csgobox.box_manager.entry": "%s · %d grades · %d items · %.0f%% drop",

  "gui.csgobox.edit.title": "Editing: %s (%s)",
  "gui.csgobox.edit.basic_info": "Basic Info",
  "gui.csgobox.edit.name": "Name",
  "gui.csgobox.edit.drop_rate": "Drop Rate",
  "gui.csgobox.edit.key_item": "Key Item",
  "gui.csgobox.edit.texture": "Texture",
  "gui.csgobox.edit.sound": "Sound",
  "gui.csgobox.edit.default": "Default",

  "gui.csgobox.edit.entities": "Drop Entities",
  "gui.csgobox.edit.entity_add": "Add Entity...",
  "gui.csgobox.edit.entity_search": "Search entities...",
  "gui.csgobox.edit.global_rate": "Use global rate for all",
  "gui.csgobox.edit.custom_rates": "Custom rate per entity",

  "gui.csgobox.edit.weights": "Grade Weights",
  "gui.csgobox.edit.probability": "%.1f%%",

  "gui.csgobox.edit.items_pool": "Item Pool",
  "gui.csgobox.edit.add_from_inventory": "Add from Inventory",
  "gui.csgobox.edit.add_by_id": "Add by Item ID",
  "gui.csgobox.edit.item_id_prompt": "Enter item ID (e.g. minecraft:diamond_sword)",
  "gui.csgobox.edit.item_count": "Count",
  "gui.csgobox.edit.item_remove": "Remove",
  "gui.csgobox.edit.item_duplicate": "Item already exists in this grade",
  "gui.csgobox.edit.empty_grade": "No items in this grade",

  "gui.csgobox.edit.save": "Save & Back",
  "gui.csgobox.edit.export_json": "Export JSON",
  "gui.csgobox.edit.delete": "Delete Box",
  "gui.csgobox.edit.delete_confirm": "Are you sure you want to delete \"%s\"?",
  "gui.csgobox.edit.saved": "Box \"%s\" saved successfully!",
  "gui.csgobox.edit.deleted": "Box \"%s\" deleted.",

  "gui.csgobox.create.id_prompt": "Enter box ID (e.g. my_custom_box)",
  "gui.csgobox.create.name_prompt": "Enter display name",
  "gui.csgobox.create.id_exists": "A box with ID \"%s\" already exists!",
  "gui.csgobox.create.created": "Box \"%s\" created! Configure it now."
}
```

---

## 4. 实施计划与任务分解

### Task 1: 基础设施（前置依赖）
- [ ] 1.1 确认 build.gradle 中 cloth-config 依赖正确（已有声明 15.0.130）
- [ ] 1.2 创建 `config/CsboxConfig.java` — AutoConfig 占位类
- [ ] 1.3 创建 `config/CsboxConfigGuiProvider.java` — GUI Provider 入口
- [ ] 1.4 编译验证通过

### Task 2: 箱子列表界面
- [ ] 2.1 创建 `gui/client/BoxListScreen.java` — 基础框架（标题、背景、返回按钮）
- [ ] 2.2 实现箱子列表渲染（从 BoxRegistry 读取，显示名称/等级/物品/掉落率）
- [ ] 2.3 实现点击进入编辑 + 「新建箱子」对话框
- [ ] 2.4 实现滚动支持（箱子数量较多时）

### Task 3: 箱子编辑器 — 基本信息区
- [ ] 3.1 创建 `gui/client/BoxEditScreen.java` — 基础框架
- [ ] 3.2 实现「基本信息」区域（名称/掉落率/钥匙/纹理/音效）
- [ ] 3.3 实现下拉选择框组件（钥匙物品列表枚举）

### Task 4: 箱子编辑器 — 掉落实体区
- [ ] 4.1 实现「掉落实体」列表渲染
- [ ] 4.2 实体搜索/选择弹窗（模糊匹配 ENTITY_TYPE 注册表）
- [ ] 4.3 实现独立掉落率编辑（点击调整）
- [ ] 4.4 实现添加/删除实体操作

### Task 5: 箱子编辑器 — 等级权重区
- [ ] 5.1 实现 5 行等级权重滑块
- [ ] 5.2 实时概率百分比计算和显示
- [ ] 5.3 等级颜色标识渲染

### Task 6: 箱子编辑器 — 物品池区 ⭐
- [ ] 6.1 实现 Tab 切换（5 个等级标签页）
- [ ] 6.2 实现物品列表渲染（图标 + 名称 + 数量 + 操作按钮）
- [ ] 6.3 实现「从背包添加」功能（打开物品选择子界面）
- [ ] 6.4 实现「输入 ID 添加」功能（文本输入 + 自动补全）
- [ ] 6.5 实现物品数量调整和删除
- [ ] 6.6 物品重复检测和提示

### Task 7: 持久化与收尾
- [ ] 7.1 在 `BoxJsonLoader` 中实现 `saveToFile(BoxDefinition)` 方法
- [ ] 7.2 实现「保存」按钮（重建 BoxDefinition → Registry → JSON 写入）
- [ ] 7.3 实现「导出 JSON」（复制到剪贴板）
- [ ] 7.4 实现「删除箱子」（确认弹窗 + Registry 移除 + 文件删除）
- [ ] 7.5 完整中英文语言文件

### Task 8: 测试与打磨
- [ ] 8.1 Gradle 构建验证
- [ ] 8.2 游戏内测试：新建箱子 → 编辑属性 → 添加物品 → 保存 → 重启验证 JSON 持久化
- [ ] 8.3 测试 gun.json（含 tacz 枪械 DataComponent）的加载和编辑
- [ ] 8.4 测试边界情况：空物品池、超长名称、特殊字符、大量物品

---

## 5. 关键技术要点

### 5.1 Cloth Config 集成方式

```java
// CsgoBox.java 第 37 行已有（保持不变）:
CONFIG = AutoConfig.register(CsboxConfig.class, Toml4jConfigSerializer::new).getConfig();

// CsboxConfigGuiProvider 重定向到自定义 Screen:
@AutoConfig.GuiProvider
public class CsboxConfigGuiProvider implements ConfigScreenProvider<CsboxConfig> {
    @Override
    public Screen getScreen(CsboxConfig config, Screen parent) {
        return new BoxListScreen(parent);  // 不使用标准 Cloth Config Builder
    }
}
```

这样 Mod Menu 中的「配置」按钮会直接打开我们的自定义箱子管理器。

### 5.2 不可变 record 的编辑策略

`BoxDefinition` 和 `GradeGroup` 都是 **record（不可变）**。GUI 编辑流程：

```
用户在 GUI 中修改值
       │
       ▼
维护一份「编辑状态副本」（mutable 临时对象或直接用字段）
       │
       ▼
点击「保存」时：
  1. 从编辑状态重建新的 BoxDefinition（read → rebuild → replace）
  2. BoxRegistry.register(newDef)  // 覆盖旧的定义
  3. BoxJsonLoader.saveToFile(newDef)  // 同步回写 JSON
```

这与现有 `/csbox add` / `/csbox set` 命令使用的 **read-rebuild-replace** 模式完全一致。

### 5.3 GUI 组件复用

尽量复用现有的渲染工具类：

| 现有类 | 复用于 |
|--------|--------|
| `IconListTools.renderItemFrame()` | 物品池中的物品图标渲染 |
| `IconListTools.renderGuiItem()` | 钥匙物品预览 |
| `RenderFontTool.drawString()` | 所有文本渲染 |
| `ColorTools.colorItems()` | 等级颜色/标签颜色 |
| `OverlayColor.getBackgroundColor()` | 屏幕背景 |

### 5.4 屏幕尺寸适配

参考现有 Screen 的百分比布局方式：

```java
// 使用 width/height 百分比定位（而非绝对像素），确保不同分辨率下正常
int titleY = this.height * 5 / 100;
int contentTop = this.height * 12 / 100;
int btnBottom = this.height * 94 / 100;
```

---

## 6. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Cloth Config 版本 API 变更 | GuiProvider 接口不兼容 | 锁定 cloth_config_version=15.0.130，查阅其源码确认接口 |
| 大量物品时性能问题（如 50+ 物品的等级） | 渲染卡顿 | 物品列表使用虚拟滚动（只渲染可见区域） |
| DataComponent 序列化丢失精度 | 枪械物品 NBT 数据损坏 | saveToFile 使用与 loadFromFile 相同的 Codec 序化链路 |
| 多人联机时配置冲突 | 服务端/客户端不一致 | GUI 仅在单人模式或服务端 OP 端可用；修改通过 Packet 同步 |
| 中文输入法在自定义文本框中异常 | 无法输入中文 | 使用 Minecraft 原生 TextField 组件（支持 IME） |

---

## 7. 交付物清单

| # | 文件 | 类型 | 预估行数 |
|---|------|------|----------|
| 1 | `config/CsboxConfig.java` | 新建 | ~15 |
| 2 | `config/CsboxConfigGuiProvider.java` | 新建 | ~20 |
| 3 | `gui/client/BoxListScreen.java` | 新建 | ~250 |
| 4 | `gui/client/BoxEditScreen.java` | 新建 | ~800 |
| 5 | `api/box/BoxJsonLoader.java` | 修改（追加 saveToFile） | +60 |
| 6 | `resources/assets/csgobox/lang/en_us.json` | 修改（追加翻译键） | +40 |
| 7 | `resources/assets/csgobox/lang/zh_cn.json` | 修改（追加翻译键） | +40 |

**总计**: 新增约 **1085 行**，修改约 **140 行**

---

## 8. 补充考量（新增）

> 以下为规划审查阶段补充的关键技术点，按优先级排列。

### 8.1 🔴 [P0-紧急] 当前代码无法编译

[CsgoBox.java:8](src/main/java/com/reclizer/csgobox/CsgoBox.java#L8) 和 [CsgoBox.java:34](src/main/java/com/reclizer/csgobox/CsgoBox.java#L34) 引用了不存在的 `CsboxConfig` 类：

```java
import com.reclizer.csgobox.config.CsboxConfig;   // 第 8 行 — 类不存在！
public static CsboxConfig CONFIG;                   // 第 34 行 — 编译必报错
CONFIG = AutoConfig.register(CsboxConfig.class, ...);  // 第 37 行 — 运行时 NPE
```

**影响**: `./gradlew compileJava` 必定失败。创建 `CsboxConfig.java` 必须是 **Task 0**，不能排在 Task 1.2。

**修复方案**: 将原计划 Task 1 拆分为：

```
Task 0 (紧急): 创建 CsboxConfig.java 空壳类，恢复编译
  - 0.1 创建 config/ 包目录和 CsboxConfig.java（空 @Config 注解类）
  - 0.2 执行 gradlew compileJava 验证编译通过
  - 0.3 后续 Task 1~7 按原计划推进
```

### 8.2 🔴 [P0] 客户端-服务端架构与多人模式

当前规划隐含假设所有操作在客户端完成，但忽略了核心数据的位置：

```
┌─────────────────────────────────────────────────┐
│  BoxRegistry 存储位置分析                      │
├─────────────────────────────────────────────────┤
│                                                 │
│  BoxJsonLoader.loadAll() 在 FMLCommonSetupEvent │
│  → 此事件在 DedicatedServer 端和服务端线程执行    │
│  → BoxRegistry 是服务端数据结构                  │
│                                                 │
│  GUI Screen 运行在客户端线程                     │
│  → 客户端无法直接访问 BoxRegistry               │
│  → 客户端无法直接写入 JSON 文件                 │
│                                                 │
└─────────────────────────────────────────────────┘
```

#### 单人模式（IntegratedServer）

单人游戏中 client = server，`Minecraft.getInstance().isSingleplayer() == true`：
- GUI 可以直接操作 `BoxRegistry`（因为逻辑上在同一进程）
- 但需确保操作在**服务端线程**执行：通过 `PacketDistributor.sendToServer()` 发送自定义 Packet，或直接调用（因为单人模式下 sendToServer 会回环到本地）
- **推荐方案**: 单人模式下 GUI 直接调用修改方法，无需额外 Packet

#### 多人模式（DedicatedServer / LAN Host）

| 角色 | 能力 | 限制 |
|------|------|------|
| **OP（权限等级 2+）** | 打开 GUI、编辑箱子、保存到 JSON | 仅能编辑，修改即时生效 |
| **普通玩家** | 打开 GUI（只读预览） | 无法保存、无法修改 |
| **非 OP 客户端** | 不显示配置入口 | Mod Menu 中隐藏 CS2 Box 配置按钮 |

#### 多人模式实现方案

需要新增一个服务端 Packet：

```java
// 新增: PacketBoxConfigUpdate.java
public record PacketBoxConfigUpdate(BoxDefinition def) implements CustomPacketPayload {
    public static final CustomPacket.PayloadType<PacketBoxConfigUpdate> TYPE = ...;
    
    static final StreamCodec<PacketBoxConfigUpdate> CODEC = StreamCodec.of(
        (buf, p) -> BoxDefinition.STREAM_CODEC.encode(buf, p.def()),
        buf -> new PacketBoxConfigUpdate(BoxDefinition.STREAM_CODEC.decode(buf))
    );
    
    static void handle(PacketBoxConfigUpdate pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 服务端：接收客户端发来的箱子定义 → 更新 Registry → 写入 JSON
            BoxRegistry.register(pkt.def());
            BoxJsonLoader.saveToFile(pkt.def());
        });
    }
}
```

GUI 保存流程变为：

```
客户端 GUI 点击"保存"
    ↓
构建 PacketBoxConfigUpdate(updatedDef)
    ↓
PacketDistributor.sendToServer(packet)  ← 发送到服务端
    ↓
服务端 handle(): BoxRegistry.register() + saveToFile()
    ↓
服务端广播同步给所有其他客户端（可选）
```

**决策点**: Phase 1 可先仅支持**单人模式完整功能 + 多人模式只读预览**，Phase 2 再补全多人编辑。

### 8.3 🔴 [P0] Cloth Config 缺失时的优雅降级

`neoforge.mods.toml` 声明 cloth_config 为 `mandatory=false`。用户可能不安装 Cloth Config。

#### 问题链

```
用户未安装 cloth-config
    ↓
AutoConfig.register() 抛出 NoClassFoundError 或类似异常
    ↓
CsgoBox 构造函数崩溃
    ↓
整个模组加载失败
```

#### 降级方案

```java
// CsgoBox.java 构造函数中改为:
public CsgoBox(IEventBus modEventBus) {
    try {
        CONFIG = AutoConfig.register(CsboxConfig.class, Toml4jConfigSerializer::new).getConfig();
        CsgoBox.LOGGER.info("Cloth Config loaded, GUI editor available");
    } catch (Throwable t) {
        CONFIG = null;
        CsgoBox.LOGGER.warn("Cloth Config not found, box editor GUI disabled. Cause: {}", t.getMessage());
    }
    // ... 其余初始化不变
}
```

同时 `CsboxConfigGuiProvider.getScreen()` 需要检查 `CONFIG == null`：

```java
public Screen getScreen(CsboxConfig config, Screen parent) {
    if (CsgoBox.CONFIG == null) {
        return null;  // 或返回一个提示 "请安装 Cloth Config" 的简单 Screen
    }
    return new BoxListScreen(parent);
}
```

**影响范围**: 无 Cloth Config 时模组正常工作（开箱、掉落等），只是没有 GUI 编辑器，仍可用 `/csbox` 命令管理。

### 8.4 🟡 [P1] 未保存更改检测

用户可能花费 10+ 分钟编辑一个复杂箱子，然后误按 ESC 关闭。

#### 实现：脏标记（Dirty Flag）

```java
public class BoxEditScreen extends Screen {
    private boolean dirty = false;  // 是否有未保存的修改
    
    private void markDirty() { this.dirty = true; }
    
    @Override
    public void onClose() {
        if (dirty && minecraft != null && minecraft.player != null) {
            minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) super.onClose();  // 确认丢弃 → 关闭
                    else { /* 取消 → 保持当前界面 */ }
                },
                Component.translatable("gui.csgobox.edit.discard_title"),
                Component.translatable("gui.csgobox.edit.discard_message"),
                Component.translatable("gui.csgobox.edit.discard_confirm"),
                Component.translatable("common.cancel")
            ));
            return;
        }
        super.onClose();
    }
}
```

触发 `markDirty()` 的时机：
- 基本信息区任何字段值变化
- 实体列表增删
- 权重滑块拖动
- 物品池增删/数量变更

**注意**: 初始化加载时不标记 dirty（从 BoxDefinition 加载初始值 = 干净状态）。

### 8.5 🟡 [P1] saveToFile() 原子性写入

当前 saveToFile 直接写目标文件：

```java
// 危险: 写入中途崩溃 → JSON 文件损坏 → 重启后加载失败
try (Writer w = Files.newBufferedWriter(file)) {
    GSON.toJson(json, w);  // 如果这里崩溃...
}
```

#### 安全方案：先写临时文件再 rename

```java
public static void saveToFile(BoxDefinition def) throws IOException {
    Path file = BOXES_DIR.resolve(def.id().getPath() + ".json");
    Path tempFile = BOXES_DIR.resolve(def.id().getPath() + ".json.tmp");
    
    try (Writer w = Files.newBufferedWriter(tempFile)) {
        GSON.toJson(serializeToJson(def), w);
    }
    
    Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
}
```

`StandardCopyOption.ATOMIC_MOVE` 保证：
- 要么完全写入成功
- 要么文件保持原内容不变（即使崩溃）
- 不会出现半写状态的损坏文件

### 8.6 🟡 [P1] KubeJS 共存策略

KubeJS 通过 `CsboxRegistryEventJS` 注册箱子，GUI 编辑器通过 JSON 文件持久化。两者存在数据竞争：

```
时间线:
T1: 游戏启动 → KubeJsPlugin.registerEvents() → DefaultBoxes 注册箱子 A
T2: BoxJsonLoader.loadAll() → 从 JSON 加载箱子 A（覆盖 KubeJS 版本）
T3: 用户打开 GUI → 编辑箱子 A → 保存到 JSON
T4: 用户执行 /csbox reload → KubeJS 重新注册 → 覆盖 GUI 的修改
```

#### 策略声明

| 操作 | 数据来源 | 说明 |
|------|----------|------|
| 首次启动 | KubeJS (DefaultBoxes.js) | 生成默认箱子 |
| 后续启动 | JSON 文件 (BoxJsonLoader) | JSON 优先于 KubeJS 默认 |
| `/csbox reload` | KubeJS 脚本重新执行 | reload 明确告知会丢失手动修改 |
| GUI 编辑 | JSON 文件 (saveToFile) | 与 `/csbox add` 效果一致 |

**GUI 内提示**: 当检测到 KubeJS 已加载时，在 BoxEditScreen 底部显示提示文字：

```
⚠ KubeJS is loaded. Changes made here will be overwritten by /csbox reload.
  To make permanent changes via script, edit kubejs/ scripts.
```

### 8.7 🟡 [P1] 权限模型

#### GUI 入口权限检查

```java
// CsboxConfigGuiProvider.java
public Screen getScreen(CsboxConfig config, Screen parent) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null) {
        boolean isOp = mc.player.hasPermission(2);  // 与 /csbox 命令一致
        if (!isOp && !mc.isSingleplayer()) {
            mc.player.displayClientMessage(
                Component.translatable("gui.csgobox.edit.no_permission")
                    .withStyle(ChatFormatting.RED)
            );
            return null;
        }
    }
    return new BoxListScreen(parent);
}
```

#### 多人模式下非 OP 的体验

| 操作 | OP | 非 OP |
|------|-----|------|
| 打开箱子管理器 | ✅ | ❌ 提示无权限 |
| 浏览箱子列表（只读） | ✅ | ✅ 可考虑开放 |
| 编辑任何字段 | ✅ | ❌ |
| 导出 JSON | ✅ | ✅（只读导出） |

### 8.8 🟢 [P2] 动态等级数量适配

当前设计硬编码 5 个 Tab（grade1~grade5），但 BoxDefinition.grades() 的 size 可能不是 5：

- KubeJS 创建的箱子可能有 3 个或 7 个等级
- 未来版本可能支持自定义等级数

#### 适配方案

```java
// BoxEditScreen 中动态生成 Tab:
List<GradeGroup> grades = editingDef.grades();  // 运行时获取实际数量
int gradeCount = grades.size();

for (int i = 0; i < gradeCount; i++) {
    GradeGroup g = grades.get(i);
    int color = g.color();  // 使用 GradeGroup 自带的颜色而非 ColorTools 硬编码
    // 绘制 Tab 按钮，使用 g.displayName() 作为标签文本
}

// 权重区也动态渲染 gradeCount 行
// 物品池 Tab 数量 = gradeCount
```

**最低保证**: 至少支持 1~10 个等级的动态适配。Tab 区域使用滚动容器处理超过屏幕宽度的情况。

### 8.9 🟢 [P2] BoxEditScreen 内部状态管理

BoxEditScreen 需要管理的可变状态较多，建议引入内部状态类：

```java
public class BoxEditScreen extends Screen {
    
    /** 编辑中的箱子副本（mutable 视图） */
    private EditState state;
    
    /** 当前选中的等级 Tab 索引 */
    private int selectedGradeIndex = 0;
    
    /** 各区域滚动偏移 */
    private int entityScrollOffset = 0;
    private int itemScrollOffset = 0;
    
    /** 子界面状态 */
    private Screen subScreen = null;  // 实体搜索/背包选择/ID输入
    
    /** 记录用于脏标记检测的初始快照 */
    private String initialSnapshotHash;
    
    // --- 内部状态类 ---
    static class EditState {
        String name;
        float dropRate;
        ResourceLocation keyItem;
        Optional<ResourceLocation> texture, sound;
        List<EntityDropEntry> entities;      // 可变长度
        List<GradeEditState> grades;          // 可变长度
        
        static EditState from(BoxDefinition def) { /* 深拷贝 */ }
        BoxDefinition toBoxDefinition() { /* 重建 */ }
    }
    
    static class GradeEditState {
        int weight;
        List<ItemStack> items;  // 可变长度
        
        static GradeEditState from(GradeGroup g) { /* 深拷贝 */ }
        GradeGroup toGradeGroup(String id, String displayName, int color) { /* 重建 */ }
    }
    
    static class EntityDropEntry {
        ResourceLocation entityId;
        float rate;
    }
}
```

**好处**:
- `EditState` 与 `BoxDefinition` 解耦，GUI 修改不影响原始数据
- 快照哈希用于精确的脏标记判断
- 序列化/反序列化可用于撤销栈（未来扩展）

### 8.10 🟢 [P2] 带 DataComponent 物品的编辑限制

以 gun.json 中的 tacz 枪械物品为例：

```json
{"id":"tacz:modern_kinetic_gun","count":1,
 "components":{"minecraft:custom_data":{
   "GunId":"cs2_wt:awp",
   "GunFireMode":"SEMI",
   "HasBulletInBarrel":1,
   "GunCurrentAmmoCount":4
 }}}
```

#### GUI 中的表现

| 操作 | 支持度 | 说明 |
|------|--------|------|
| 显示物品名称 | ⚠️ 部分 | 显示 "tacz Gun" 而非 "AWP"，custom_data 信息在 Tooltip 中展示为原始 JSON |
| 修改数量 | ✅ 完整 | `[+]` `[-]` 正常工作 |
| 删除物品 | ✅ 完整 | `[✕删除]` 正常工作 |
| 复制物品到其他等级 | ✅ 完整 | 带 DataComponent 一起复制 |
| **新建带 DataComponent 的物品** | ❌ 不支持 | "输入 ID" 方式只能创建纯净物品 |
| **编辑已有 DataComponent** | ❌ 不支持 | GUI 不提供 DataComponent 编辑器 |

#### 用户指引

在物品池区域添加提示条：

```
💡 Tip: Items with custom data (like guns) show basic info only.
   Use /csbox add <box> <grade> hand <count> to add items with their current data.
   For advanced item configuration, edit the JSON file directly.
```

### 8.11 🟢 [P2] 其他补充要点

##### 8.11.1 箱子 ID 格式校验

```java
private static boolean isValidBoxId(String idStr) {
    if (!idStr.contains(":")) return false;
    try {
        RL rl = ResourceLocation.parse(idStr);
        return rl.getPath().equals(rl.getPath().toLowerCase())  // 路径小写
            && !rl.getPath().contains("..")              // 无路径遍历
            && rl.getNamespace().matches("[a-z][a-z0-9._-]*")  // 合法命名空间
            && rl.getPath().matches("[a-z0-9/_.-]+");       // 合法路径
    } catch (Exception e) {
        return false;
    }
}
```

##### 8.11.2 物品搜索性能优化

多模组环境（100+ mods）下 `BuiltInRegistries.ITEM` 可能有 5000+ 条目：

```java
// 方案: 预构建搜索索引（首次使用时构建，之后缓存）
private static List<Item> searchableItems = null;
private static Map<String, List<Item>> searchCache = null;

private static void ensureSearchIndex() {
    if (searchableItems == null) {
        searchableItems = BuiltInRegistries.ITEM.stream().toList();
        // 按名称首字母分组，缩小搜索范围
        searchCache = new HashMap<>();
        for (Item item : searchableItems) {
            String key = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase();
            // 按 2-gram 分片建立倒排索引
        }
    }
}
```

对于 ID 输入框，仅在用户停止输入 200ms 后触发搜索（防抖），避免每次按键都遍历全表。

##### 8.11.3 掉落率 = 0 的交互引导

当用户将 dropRate 滑块拖到 0 时：

```
UI 自动切换提示:
  "Drop rate is 0%. This box will ONLY drop from entities with custom rates.
   Set entity-specific rates below, or increase drop rate to enable global drops."
```

实体区的「全局统一/逐独立」切换开关自动切到「逐独立」模式。

##### 8.11.4 NeoForge 原生配置 vs Cloth Config 对比

| 维度 | NeoForge ModConfig | Cloth Config |
|------|-------------------|-------------|
| 依赖 | 无（内置） | 需要 cloth-config mod |
| GUI 美观度 | 原生文本/数字 | 专业滑块/颜色选择器 |
| 动态字段 | 差（需要 ForgeConfigSpec.Builder） | 好（注解驱动） |
| TOML/JSON 支持 | 内置 | 通过 AutoConfig 扩展 |
| 社区接受度 | 高（官方方案） | 高（事实标准） |

**最终选择理由**: 本项目的 GUI 需要颜色选择器、物品列表等高级组件，NeoForge 原生 ModConfig 无法满足。Cloth Config 是最佳选择，但必须做好缺失降级（见 8.3 节）。

##### 8.11.5 代码风格约束

新 GUI 代码必须遵循现有约定：

- **包位置**: `com.reclizer.csgobox.gui.client`（与 CsboxScreen/CsLookItemScreen 同包）
- **布局方式**: 百分比定位 (`this.width * X / 100`)，不用绝对像素
- **渲染调用**: `GuiGraphics.fill()` / `.blit()` / `.renderItem()` 手写渲染
- **字体渲染**: 统一使用 `RenderFontTool.drawString()`
- **颜色工具**: 统一使用 `ColorTools` / `OverlayColor`
- **禁止引入**: 任何第三方 UI 库（如 Minecraft GUI Liberty 等）
- **注释语言**: 英文注释（代码中），中文注释（规划文档中）
