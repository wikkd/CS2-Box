<!-- generated-by: gsd-doc-writer -->
# CS2-Box 配置指南

> CS2-Box 通过 NeoForge 原生 `ModConfigSpec` 持久化 TOML 配置 + `config/csbox/*.json` 文件配置箱子数据。**不使用 Cloth Config**(v1.0.5 已完全移除)。

## 1. 配置文件位置

| 环境 | 路径 |
|---|---|
| 客户端 | `config/csgobox.toml` |
| 服务端 | `config/csgobox.toml` |

模组注册一个通用配置文件(`csgobox.toml`),客户端与服务端共用同一份。

## 2. 配置项总览

`CsboxConfig.java` 在 v1_21_1 与 v26_1_2 两个 loader 中定义一致(11 个字段,4 个 TOML 分组):

### 2.1 `[general]` 通用设置

| 配置项 | 类型 | 默认值 | 范围 | 说明 |
|---|---|---|---|---|
| `animationSpeed` | 枚举 | `NORMAL` | `SLOW` / `NORMAL` / `FAST` | 动画播放速度:`SLOW` = 2× 基速,`NORMAL` = 1×,`FAST` = 0.5× |
| `globalDropRatePercent` | 整数 | `100` | 0-1000 | 全局掉落概率百分比 |

### 2.2 `[advanced]` 高级设置

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `loadDefaultBoxes` | 布尔 | `true` | 启动时自动从 `config/csbox/*.json` 加载默认宝箱 |
| `enableDebugLogging` | 布尔 | `false` | 启用控制台详细调试日志 |
| `enableAchievements` | 布尔 | `true` | 启用成就系统;关闭时仍累积统计(保留进度) |
| `damageItemByWear` | 布尔 | `true` | 抽出的物品若有耐久,按磨损值百分比损耗耐久(不会碎裂) |

### 2.3 `[sound]` 音效设置

| 配置项 | 类型 | 默认值 | 范围 | 说明 |
|---|---|---|---|---|
| `openSoundVolume` | 整数 | `100` | 0-100 | 开箱音效音量百分比 |
| `tickSoundVolume` | 整数 | `50` | 0-100 | 滴答音效音量百分比 |
| `finishSoundVolume` | 整数 | `100` | 0-100 | 完成音效音量百分比 |

### 2.4 `[animation]` 动画设置

| 配置项 | 类型 | 默认值 | 范围 | 说明 |
|---|---|---|---|---|
| `totalAnimationTicks` | 整数 | `145` | 20-500 | 基础动画持续时间(tick) |
| `animationSpeedMultiplier` | 整数 | `1` | 1-10 | 动画速度倍数(值越大越快) |
| `showItemNames` | 布尔 | `true` | — | 在宝箱预览界面显示物品名称 |

### 2.5 `[ui]` UI 设置

| 配置项 | 类型 | 默认值 | 范围 | 说明 |
|---|---|---|---|---|
| `backgroundStyle` | 枚举 | `TRANSLUCENT` | `OPAQUE` / `TRANSLUCENT` | 屏幕背景样式：`TRANSLUCENT`（默认）= 半透明主题灰（alpha 140），模糊的世界透过背景显示（原生模糊或 Blur 模组的动画模糊）；`OPAQUE` = 实心深色面板（旧观感） |

> 说明：该选项为软适配——未安装 Blur 模组（`blur`）时半透明背景遵循原版 `menuBackgroundBlurriness` 模糊选项（设为 0 则无模糊）；安装 Blur 后自动获得其淡入动画与可配置模糊半径/渐变。进度屏（开箱动画）始终为半透明背景，不受此开关影响。

## 3. 宝箱数据配置(JSON schema)

可通过 `config/csbox/` 目录下的 JSON 文件定义自定义宝箱。**文件名(不含 `.json`)即为箱子 ID**。

### 3.1 顶级字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | 字符串 | 是 | 宝箱在界面显示的名称 |
| `type` | 字符串 | 否 | 箱子类型:`csbox`(默认,普通宝箱)/ `terminal`(终端机,独立 loot 池) |
| `key` | 字符串 | 是 | 所需钥匙物品 ID;`minecraft:air` 表示不需要钥匙。**终端机默认 `minecraft:air`(免钥匙)——这是终端机与普通箱子在 `key` 上的唯一区分点** |
| `drop` | 浮点数 | 否 | 默认实体掉落概率(0.0 到 1.0)。**当 `entity` 为纯实体 ID 列表时,本值是每个实体的掉落概率;若 `entity` 用 ID/概率交替数组,本值作为未显式指定概率实体的兜底** |
| `random` | 浮点数组[5] | 否 | 5 个等级权重(grade1 到 grade5 顺序) |
| `entity` | 数组 | 否 | 掉落该宝箱的实体 ID 列表(或 ID/概率 交替数组)。**终端机已启用:危险生物按 `drop` 概率掉落终端机** |
| `grade1` ~ `grade5` | 数组 | 否 | 各等级物品清单(industry / consumer / mil_spec / restricted / classified) |

### 3.2 物品对象

```json
{
  "id": "minecraft:diamond",
  "count": 1,
  "price": 1500,
  "components": {
    "minecraft:custom_name": "\"闪亮钻石\""
  }
}
```

- `id`:物品命名空间 ID
- `count`:数量(默认 1)
- `price`(可选):整数,**该物品在 0 磨损下的基准价格/价值**;必须为非负整数,缺省则无价格。终端机默认配置按档位给出阶梯价(grade1=50 / grade2=200 / grade3=500 / grade4=1500 / grade5=4000,均为 [PLACEHOLDER],待经济系统联调重定)
- `components`(可选):Minecraft 1.21+ data components
- 旧版 `tag` 字符串字段**仍可加载**(向后兼容)

### 3.3 默认文件生成

首次启动时 `BoxJsonLoader.loadAll()` 会保证 `config/csbox/` 目录存在，并：

- 写入 `terminal.json`（类型 `terminal`，独立 loot 池，`key: minecraft:air` 免钥匙，并带 `entity` 危险生物掉落）—— 终端机开箱即有专属掉落，不再借用其他箱子；已有用户配置则跳过。
- 异步下载 `_tutorial_v<版本>.md` 教程文档（联网时）。

**普通箱子没有内置默认配置**：`weapon_supply_box.json` 等文件需要由玩家/服主自行创建，或从教程文档中复制示例。

### 3.4 五个等级命名

| 等级 ID | 显示名 |
|---|---|
| `consumer` | 消费级(灰) |
| `industrial` | 工业级(浅蓝) |
| `mil_spec` | 军规级(蓝) |
| `restricted` | 受限级(紫) |
| `classified` | 保密级(粉/红) |

## 4. 配置重载行为

**TOML 配置(`config/csgobox.toml`)重载**:

- 游戏中 `/reload` 命令可触发 `ModConfigEvent.Reloading`,日志记录
- 重启游戏也可应用新值

**箱子 JSON(`config/csbox/*.json`)重载**:

- `BoxJsonLoader.loadAll()` **只在 `ServerStartingEvent` 触发**
- 必须重启游戏或专用服务端才能生效
- 现有 JSON 文件**不会被覆盖**(只读)

## 5. 运行时一致性

- **`CONFIG` 是 `public static final`**,永不为 null。**删除所有 `CONFIG != null` 守卫检查**——这是 v1.0.5 修复的关键 bug
- 调用方通过 `CsgoBox.CONFIG.<fieldName>()` 访问(扁平化访问,不是 `CONFIG.section.fieldName`)
- Java 端通过 `CsboxConfig` 构造器中的 `builder.define*().get()` 内联求值,不再有 `init()` 延迟填充(那也是 v1.0.5 修复点)
- **不使用 Cloth Config**,不引入 `me.shedaniel.cloth:cloth-config-neoforge` 依赖

## 6. 配置开关与运行时一致性

- `enableDebugLogging=false`(默认)避免控制台被调试日志刷屏
- `enableAchievements=false` 关闭成就弹窗与 toast,但 `Stats.CUSTOM` 仍累积,重新开启后即恢复触发
- `loadDefaultBoxes=false` 可阻止自动加载 `config/csbox/*.json`,适合纯数据包驱动的服务端

修改这些开关并 `/reload` 即可生效;JSON 改动则需重启。