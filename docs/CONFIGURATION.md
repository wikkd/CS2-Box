<!-- generated-by: gsd-doc-writer -->
# 配置指南

CS2 Box 使用 NeoForge 的 `ModConfigSpec` 系统通过 TOML 文件提供配置选项。配置在首次运行时自动创建，可以在游戏运行时修改（重载后生效）。

## 配置文件位置

| 环境 | 路径 |
|-------------|------|
| 客户端 | `config/csgobox.toml` |
| 服务端 | `config/csgobox.toml` |

模组注册一个通用配置文件（`csgobox.toml`），同时应用于客户端和服务端。

## 配置项

### 通用设置

| 配置项 | 类型 | 默认值 | 说明 |
|----------|------|---------|-------------|
| `animationSpeed` | 枚举 | `NORMAL` | 动画播放速度：`SLOW` = 2倍基速，`NORMAL` = 1倍基速，`FAST` = 0.5倍基速 |
| `globalDropRatePercent` | 整数 | `100` | 全局掉落概率百分比。范围：0-1000 |

### 高级设置

| 配置项 | 类型 | 默认值 | 说明 |
|----------|------|---------|-------------|
| `loadDefaultBoxes` | 布尔 | `true` | 启动时自动从 `config/csbox/*.json` 加载默认宝箱 |
| `enableDebugLogging` | 布尔 | `false` | 启用控制台详细调试日志 |
| `enableAchievements` | 布尔 | `true` | 启用成就系统（关闭时仍会累积统计） |

### 音效设置

| 配置项 | 类型 | 默认值 | 说明 |
|----------|------|---------|-------------|
| `openSoundVolume` | 整数 | `100` | 开箱音效音量百分比。范围：0-100 |
| `tickSoundVolume` | 整数 | `50` | 滴答音效音量百分比。范围：0-100 |
| `finishSoundVolume` | 整数 | `100` | 完成音效音量百分比。范围：0-100 |

### 动画设置

| 配置项 | 类型 | 默认值 | 说明 |
|----------|------|---------|-------------|
| `totalAnimationTicks` | 整数 | `145` | 基础动画持续时间（tick）。范围：20-500 |
| `animationSpeedMultiplier` | 整数 | `1` | 动画速度倍数（值越大越快）。范围：1-10 |
| `showItemNames` | 布尔 | `true` | 在宝箱预览界面显示物品名称 |

## 宝箱数据配置

可以通过 `config/csbox/` 目录下的 JSON 文件定义自定义宝箱。文件名（不含 `.json`）将成为宝箱 ID。

### 宝箱 JSON 字段说明

| 字段 | 类型 | 必填 | 说明 |
|-------|------|----------|-------------|
| `name` | 字符串 | 是 | 宝箱物品和界面显示的名称 |
| `key` | 字符串 | 是 | 所需钥匙物品 ID，使用 `minecraft:air` 表示不需要钥匙 |
| `drop` | 浮点数 | 否 | 默认实体掉落概率，范围 0.0 到 1.0 |
| `random` | 数组[5] | 否 | 五个等级权重，从 grade1 到 grade5 顺序排列 |
| `entity` | 数组 | 否 | 掉落该宝箱的实体 ID 列表（或 ID/概率 交替列表） |
| `grade1` - `grade5` | 数组 | 是 | 每个稀有度等级的物品池（grade1 最低，grade5 最高） |

### 物品格式

等级池中的物品使用以下格式：

```json
{
  "id": "minecraft:diamond_sword",
  "count": 1,
  "components": {
    "minecraft:custom_name": "{\"text\":\"示例剑\",\"italic\":false}"
  }
}
```

### 宝箱配置示例

```json
{
  "name": "武器供应箱",
  "key": "csgobox:csgo_key0",
  "drop": 1.0,
  "random": [625, 125, 25, 5, 2],
  "entity": ["minecraft:zombie", 1, "minecraft:skeleton", 1],
  "grade5": [
    {"id": "minecraft:netherite_sword", "count": 1}
  ],
  "grade4": [
    {"id": "minecraft:diamond_sword", "count": 1}
  ],
  "grade3": [
    {"id": "minecraft:golden_sword", "count": 1}
  ],
  "grade2": [
    {"id": "minecraft:iron_sword", "count": 1}
  ],
  "grade1": [
    {"id": "minecraft:wooden_sword", "count": 1}
  ]
}
```

## 配置重载

模组监听 `ModConfigEvent.Reloading` 事件，配置重载时记录日志。重新加载配置的方法：

1. 修改 `config/csgobox.toml` 文件
2. 在游戏中使用 `/reload` 命令（Minecraft 1.14+）或重启服务端

对于 `config/csbox/` 中的宝箱数据文件，需要重启游戏或服务端使更改生效（由 `loadDefaultBoxes` 控制）。

## 默认宝箱

当 `loadDefaultBoxes` 启用时，模组从 `config/csbox/*.json` 加载宝箱定义。示例配置位于 `runs/client/config/csbox/weapon_supply_box.json`。
