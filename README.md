<div align="center">

# CS2-Box

**把 CS:GO 的开箱体验搬进 Minecraft**

[![Build](https://github.com/wikkd/CS2-Box/actions/workflows/build.yml/badge.svg)](https://github.com/wikkd/CS2-Box/actions/workflows/build.yml)
[![PR checks](https://github.com/wikkd/CS2-Box/actions/workflows/pr-checks.yml/badge.svg)](https://github.com/wikkd/CS2-Box/actions/workflows/pr-checks.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](./LICENSE)
[![Release](https://img.shields.io/github/v/release/wikkd/CS2-Box?sort=semver&label=release)](https://github.com/wikkd/CS2-Box/releases)
![Minecraft](https://img.shields.io/badge/MC-1.20.1%20%7C%201.21.1%20%7C%2026.1.2%20%7C%2026.2-blueviolet)
![Loader](https://img.shields.io/badge/loader-NeoForge%20%7C%20Forge-8A2BE2)
![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2025-critical)

**简体中文** | [English](./README.en.md)

</div>

---

CS2-Box 把 CS:GO 的开箱逻辑搬到 Minecraft：手持箱子右键打开预览，放入钥匙点「开启」，**服务端权威 RNG** 决定结果，客户端只播放滚动动画，最后揭晓稀有度分级物品。不花真钱，一切都在游戏里。

- **五档稀有度** + 服务端真随机 + 批量开箱 + 开箱保底
- **JSON 配置驱动**：改文件就上新箱子，无需重新编译
- **一套代码，六端同步**：同时覆盖 NeoForge 与 Forge 共 6 个平台
- **在线配置编辑器**：[wikkd.github.io/CS2-Box](https://wikkd.github.io/CS2-Box/) —— 浏览器里可视化编辑箱子，也可游戏内输入 `/csbox editor` 获取链接

当前版本 **`2.0.2`**，License MIT。这是 Reclizer 原版 CsgoBox 的重制维护版：重做了界面、修复了已知漏洞、重写了 NBT 处理，不再依赖 CraftTweaker。

## 快速开始

1. 安装对应 Minecraft 版本的 NeoForge（或 Forge），从 [Releases](https://github.com/wikkd/CS2-Box/releases) 下载 jar 放入 `mods/`
2. 进入世界后获取物品：

   ```bash
   /give @p csgobox:csgo_box        # 武器供应箱
   /give @p csgobox:csgo_key0       # 铁钥匙
   ```

3. 手持箱子 **右键** 打开预览 → 放入钥匙 → 点 **「开启」**
4. 手持箱子 **Shift+右键** 一次性批量开箱

安装细节与各平台版本要求见下方 [安装](#安装)。

## 核心特性

### 开箱体验

- **5 档稀有度分级**：consumer / industrial / mil_spec / restricted / classified，每档独立权重
- **服务端授权 RNG**：服务端在 `PacketCsgoProgress` 内计算中奖索引与物品，客户端只渲染动画，杜绝作弊
- **批量开箱**：Shift+右键进入批量总览屏，一键开整组箱子，结果流水屏可滚动查看
- **开箱概率可查**：箱子 tooltip 显示 5 档概率（v2.0.1 起档内加权时逐物品显示；按 <kbd>F3</kbd>+<kbd>H</kbd> 开启高级提示框可见概率行），另有 JEI / REI / EMI 分类查询与 Jade / WTHIT / The One Probe 准星信息
- **成就系统**：「全新的开始」（首次主动开箱）+ 隐藏紫色挑战「导购」（累计主动开 200 箱）
- **5 把钥匙**：铜 / 铁 / 金 / 钻石 / 下界合金（铜钥匙 = 3 铜锭合成；下界合金**仅**通过锻造台升级 `csgo_key2` 获得）

### 配置表达力（v2.0.1）

- **JSON 配置箱数据**：`config/csbox/*.json` 即可新增箱子类型，`/csbox reload` 或自动热重载生效，无需重编译
- **档内物品加权**：每件物品独立 `weight`
- **`count:[min,max]` 区间**：掉落量随机区间
- **`#tag` 物品标签引用 / `loot_table` 战利品表引用**：直接引用 MC 内容
- **随机附魔 `enchant`** 快捷写法
- **Minecraft 1.21+ Data Components**：`components` 字段（同时兼容旧版 `tag` 字符串）
- **联动门控与身份**：`requires`（缺模组整箱跳过）、`enabled`、每箱 `icon`；未知物品 id 区分「模组未装 vs 拼写错」
- **JSON Schema**：[docs/box-schema/box.schema.json](./docs/box-schema/box.schema.json)，IDE 补全与校验
- **工具脚本**：`scripts/boxgen.py` 生成箱子配置、`scripts/check-ids.py` 核查物品 id

### 终端经济（v2.0.1）

- `discount` 折扣、`stock` 终端全局库存（售罄即止）
- 开箱约束：`max_per_player` 每人上限、`cooldown_seconds` 冷却、`permission` 权限节点
- 武库商村民、武库拆解台与终端机围绕 `_prices.json` 价格表联动定价

### 生态联动

- **配方查看**：JEI / REI / EMI（可选客户端 mod，自动检测）
- **准星信息**：Jade / WTHIT / The One Probe（可选，自动检测）
- **Create 机械动力**：武库拆解台开放物品处理（机械臂/传送带/管道可自动化拆解），机械手可自动开箱（详见 [docs/CREATE-COMPAT.md](./docs/CREATE-COMPAT.md)）
- **TACZ 永恒枪械工坊**：1.21.1 与 1.20.1 平台检视视口集成，未安装时自动降级
- **KubeJS**：开箱/终端事件钩子（详见 [docs/KUBEJS-EVENTS.md](./docs/KUBEJS-EVENTS.md)）
- **Cloth Config（可选）**：安装后可获得配置 GUI；TOML 仍是唯一持久化来源
- **联动数据包示例**：[compat-packs/](./compat-packs/README.md) 提供 Apotheosis / Iron's Spells / TACZ 示例箱配置

## 多平台支持

仓库是 multiloader 结构，通过 `gradle.properties` 中的 `active_versions` 切换当前构建版本：

| 模块 | Minecraft | NeoForge / Forge | Java | 角色 |
|---|---|---|---|---|
| `common/` | — | — | 21 | 跨版本业务逻辑 + 共享资源（无 MC/NeoForge 依赖） |
| `v1_21_1/` | 1.21.1 | 21.1.248 | 21 | 旧 API 平台实现 |
| `v26_1_2/` | 26.1.2 | 26.1.2.95 | 25 `--enable-preview` | decoupled rendering API + PIP 3D |
| `v26_2/` | 26.2 | 26.2.0.59 | 25 `--enable-preview` | 最新版；decoupled API + PIP 3D 重写 |
| `forge_26_1_2/` | 26.1.2 | MinecraftForge 26.1.2-64.1.0 | 25 `--enable-preview` | 正式发布：随 v26_1_2 同步开发 |
| `forge_26_2/` | 26.2 | MinecraftForge 26.2-65.1.1 | 25 `--enable-preview` | 正式发布：2.0.0 线已追平 |
| `forge_1_20_1/` | 1.20.1 | MinecraftForge 47.4.22 | 17 | 正式发布：1.20.1 回移（ForgeGradle 7.x） |

<details>
<summary><strong>已归档（EOL）平台</strong></summary>

v1_21_0 / v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10 / v1_21_11 已于 2026-08-09 移出仓库，最后状态保留在 tag [`eol-legacy-21x-1.0.6`](https://github.com/wikkd/CS2-Box/tree/eol-legacy-21x-1.0.6)。旧版本玩家仍可继续使用既有发布产物。

</details>

`common/src/main/resources/` 由所有平台通过 `srcDir project(':common').file('src/main/resources')` 共享（v26_1_2 / v26_2 额外设置 `duplicatesStrategy = EXCLUDE`）。

## 安装

### 玩家安装（使用发布版）

1. 确认已安装对应 Minecraft 版本的 **NeoForge**（或 Forge）加载器：

   | Minecraft | 加载器 |
   |---|---|
   | 1.21.1 | NeoForge **21.1.248+** |
   | 26.1.2 | NeoForge **26.1.2.95**（loader 11+） |
   | 26.2 | NeoForge **26.2.0.59** |
   | 1.20.1（Forge） | MinecraftForge **47.4.22** |
   | 26.1.2（Forge） | MinecraftForge **26.1.2-64.1.0** |
   | 26.2（Forge） | MinecraftForge **26.2-65.1.1** |

2. 从 [Releases](https://github.com/wikkd/CS2-Box/releases) 下载对应版本的 jar
3. 将 jar 放入 `mods/` 文件夹
4. 启动游戏，世界内用 `/give @p csgobox:csgo_box` 获取箱子即可开箱

<details>
<summary><strong>jar 命名规则</strong></summary>

全部平台共享同一 `mod_version`：

- NeoForge：`csgobox-<mc>-<mod_version>.jar`（如 `csgobox-26.1.2-2.0.2.jar`）
- Forge：`csgobox-forge-<mc>-<mod_version>.jar`
- **唯一例外**：Forge 1.20.1 发布物为 `csgobox-forge-1.20.1-<mod_version>-srg.jar`（SRG 重映射版，1.20.1 生产必需）

已归档（EOL）的 1.21.0/3/4/5/8/10/11 旧版玩家可继续使用既有发布产物。

</details>

### 开发者构建（从源码）

**前置要求**

| 要求 | v1_21_1 | v26_1_2 / v26_2 | forge_26_1_2 / forge_26_2 | forge_1_20_1 |
|---|---|---|---|---|
| Java JDK | 21 | 25（`--enable-preview`） | 25（`--enable-preview`） | 17 |
| Minecraft | 1.21.1 | 26.1.2 / 26.2 | 26.1.2 / 26.2 | 1.20.1 |
| NeoForge / Forge | 21.1.248+ | 26.1.2.95 / 26.2.0.59（loader 11+） | MinecraftForge 26.1.2-64.1.0 / 26.2-65.1.1 | MinecraftForge 47.4.22 |
| Gradle | 9.5.1（wrapper 自动下载，无需手动安装） | 同左 | 同左 | 同左 |
| NeoGradle / ForgeGradle | 7.1.38 | 同左 | ForgeGradle 7.0.31/7.0.34 | ForgeGradle [7.0.17,8) |

> 互联网连接：首次构建需下载 NeoForged userdev 与依赖。

**构建步骤**

```bash
# 1. 克隆仓库
git clone https://github.com/wikkd/CS2-Box.git
cd CS2-Box

# 2. 选择活动版本（默认 26.1.2）：编辑 gradle.properties
#    active_versions=26.1.2   # 可选值：1.21.1 / 26.1.2 / 26.2 / forge-26.1.2

# 3. 构建当前平台的 jar
./gradlew :v26_1_2:jar        # 产物：v26_1_2/build/libs/csgobox-26.1.2-2.0.1.jar

# 4. 启动开发客户端（自动下载并注入 NeoForge 到 run/ 目录）
./gradlew :v26_1_2:runClient
```

验证 Java 版本：

```bash
java -version   # v1_21_1 应显示 21.x；v26_1_2 / v26_2 应显示 25.x
```

> 由于 NeoGradle userdev 限制，**每次 Gradle 调用只能构建一个 MC 版本**（历史限制，见 `settings.gradle`）。需要各平台产物时逐个切换 `active_versions`（或用 `-Pactive_versions=<v>` 覆盖）串行构建，详见 [docs/RELEASE.md](./docs/RELEASE.md)。

<details>
<summary><strong>v1_21_1 / forge_1_20_1 的 TACZ 依赖</strong></summary>

永恒枪械工坊检视视口集成：jar 不入库（仓库惯例 `*.jar` 全局忽略），首次构建前分别运行 `scripts/download-tacz.sh`（v1_21_1）或 `scripts/download-tacz-1201.sh`（forge_1_20_1）填充 `local-repo/com/tacz/`（CI 自动执行），并从 jarjar 提取编译所需的 `simplebedrockmodel`。无 TACZ 环境时相关功能**自动降级**，不影响编译与运行。

</details>

## 使用示例

### 获取物品

发放箱子有两种方式：

- **原版 `/give`**（获取箱子与钥匙本体）：

```bash
/give @p csgobox:csgo_box          # 武器供应箱
/give @p csgobox:csgo_key0 3       # 铁钥匙 ×3
/give @p csgobox:csgo_key1         # 金钥匙
/give @p csgobox:csgo_key2         # 钻石钥匙
# 下界合金钥匙 csgo_key3 只能通过锻造台升级 csgo_key2 获得（smithing_transform）
```

- **`/csbox give <玩家> <箱子ID> [数量]`**（v2.0.1 起，OP）：按箱子 ID 直接发放配置箱，例如：

```bash
/csbox give @p csgobox:weapon_supply_box 1
```

钥匙梯度：铁（key0）→ 金（key1）→ 钻石（key2）→ 下界合金（key3，锻造台 `smithing_transform`）。

### 开箱流程

1. 手持箱子 **右键** 打开预览界面（2 行 × 10 列物品网格）
2. 将对应钥匙放入钥匙槽，点击 **开启** 按钮
3. 服务端权威 RNG 决定结果 → 客户端播放滚动动画 → 揭晓稀有度分级物品
4. **批量开箱**：手持箱子 **Shift+右键** 进入批量开箱总览屏，点「开启」直接开箱（无二次确认屏），结果以流水屏展示并可滚动查看全部

### `/csbox` 命令参考

| 命令 | 权限 | 说明 |
|---|---|---|
| `/csbox help` | 全员 | 显示帮助，含可点击链接：打开教程文件夹、在线教程、网页配置工具 |
| `/csbox info [<箱子ID>]` | OP | 列出所有箱子与加载错误（含物品来源模组统计）；加 `<箱子ID>` 查看该箱权重、掉落实体、各档物品等详情 |
| `/csbox info error` | OP | 仅显示当前箱子加载错误（无错误时绿色提示） |
| `/csbox reload` | OP | 重新加载 `config/csbox/*.json` 箱子定义 |
| `/csbox reload tutorial` | OP | 重载箱子定义，并强制刷新教程文档 |
| `/csbox validate [<箱子ID>]` | OP | v2.0.1 干跑校验，不发布到运行时 |
| `/csbox give <玩家> <箱子ID> [数量]` | OP | v2.0.1 发放配置箱 |
| `/csbox nbt hand` | 任意玩家 | 打印主手物品序列化后的 JSON（可直接粘贴进箱子 `items`） |
| `/csbox editor` | 任意玩家 | 输出可点击的网页配置工具链接 |

示例：

```bash
/csbox info csgobox:weapon_supply_box
/csbox validate
/csbox give @p csgobox:weapon_supply_box 1
/csbox nbt hand
```

### 配置一个自定义箱子

箱子数据放在 `config/csbox/<箱子ID>.json`，**文件名即箱子 ID**。下面是最简示例（完整字段与 `_tutorial` 注释见 `common/src/main/resources/data/csgobox/` 下的示例，以及 [docs/CONFIGURATION.md](./docs/CONFIGURATION.md)）：

```json
{
  "name": "我的箱子",
  "key": "csgobox:csgo_key0",
  "drop": 1.0,
  "random": [625, 125, 25, 6, 4],
  "entity": ["minecraft:zombie", 1, "minecraft:skeleton", 1],
  "grade1": [
    { "id": "minecraft:iron_ingot", "count": 1 }
  ],
  "grade5": [
    { "id": "minecraft:netherite_ingot", "count": 1,
      "components": { "minecraft:custom_name": "{\"text\":\"欧皇专属\",\"italic\":false}" } }
  ]
}
```

- 箱子类型由 JSON 的 `type` 字段判定（v2.0.0 起唯一机制）：`"type": "terminal"` 为终端机（注册为 `ItemTerminal`，打开终端谈判屏），`"type": "csbox"`（或省略，默认）为普通宝箱。**终端机与普通箱字段严格分离**：终端机不使用 `key` 字段（出现即报 schema 错误），普通箱用 `key` 指定所需钥匙（`minecraft:air` 免钥匙）
- `random` 为 5 档权重（grade1→grade5，越高越稀有）；`grade1`~`grade5` 各为一个物品数组，每档按 `random` 对应权重抽取
- `components` 使用 MC 1.21+ DataComponent 语法（同时兼容旧版 `tag` 字符串）
- `entity` 为「实体 ID + 掉落率」成对列表，全局 `drop` 为默认掉落率
- **不会写 JSON？** 用在线编辑器 [wikkd.github.io/CS2-Box](https://wikkd.github.io/CS2-Box/) 可视化编辑，导出后放进 `config/csbox/` 即可

> 修改 JSON 后执行 `/csbox reload` 即时生效；`enableHotReload`（默认开启）开启时 `config/csbox/*.json` 文件变化会自动热重载（300ms 防抖）。可用 `/csbox nbt hand` 把手中物品导成 JSON 片段直接复用。

## 配置

- `config/csgobox.toml`：TOML 配置（动画速度、稀有度权重、音量、调试开关等）—— 见 [docs/CONFIGURATION.md](./docs/CONFIGURATION.md)
- `config/csbox/*.json`：箱子数据文件，文件名即箱子 ID —— schema 见 [docs/CONFIGURATION.md](./docs/CONFIGURATION.md)
- `config/csbox/_prices.json`：全局价格表（终端机售价与拆解产出依据，未定价物品不可拆解）
- 配置持久化统一走 NeoForge/Forge 原生 `ModConfigSpec`（TOML 是唯一持久化来源）；安装可选 mod Cloth Config 可获得配置 GUI
- **资源路径必须单数** `data/csgobox/recipe/`（Minecraft `RecipeManager` 的 `Registries.elementsDirPath(Registries.RECIPE)` 要求）

## 文档导航

| 文档 | 内容 |
|---|---|
| [docs/PLAYER-INTRO.md](./docs/PLAYER-INTRO.md) | 面向玩家的模组介绍与玩法说明 |
| [docs/PLAYER-CHANGELOG-2.0.2.md](./docs/PLAYER-CHANGELOG-2.0.2.md) | 玩家向 2.0.2 更新说明 |
| [docs/GETTING-STARTED.md](./docs/GETTING-STARTED.md) | 完整安装与首次运行步骤 |
| [docs/CONFIGURATION.md](./docs/CONFIGURATION.md) | TOML / JSON 配置参考（v2.0.1 全字段 + [JSON Schema](./docs/box-schema/box.schema.json)） |
| [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) | 六平台模块拓扑、核心抽象、数据流、GUI 渲染管线（legacy/decoupled 双 era）、终端机子系统与工程门禁 |
| [docs/PLATFORM-APIS.md](./docs/PLATFORM-APIS.md) | 多平台 API 差异速查矩阵 + 主题展开开发指南（含 AnimRenderOps 渲染门面章节） |
| [docs/DEVELOPMENT.md](./docs/DEVELOPMENT.md) | 本地开发配置、构建命令、数据生成 |
| [docs/ROADMAP.md](./docs/ROADMAP.md) | 总体规划：后续可做方向、优先级、前置依赖与已否决项 |
| [docs/CREATE-COMPAT.md](./docs/CREATE-COMPAT.md) | Create（机械动力）联动说明 |
| [docs/KUBEJS-EVENTS.md](./docs/KUBEJS-EVENTS.md) | KubeJS 事件参考 |
| [docs/BIOME-INTEGRATION.md](./docs/BIOME-INTEGRATION.md) | 让武库商小屋在模组群系/村庄生成（纯数据 + `add-biome.py` 脚本） |
| [docs/SHADER-COMPAT.md](./docs/SHADER-COMPAT.md) | Iris/Oculus shader 兼容策略与回归清单（3D 预览降级 2D） |
| [docs/TESTING.md](./docs/TESTING.md) | NeoForge GameTest 测试指南 |
| [CHANGELOG.md](./CHANGELOG.md) | 各版本发布记录 |

<details>
<summary><strong>设计文档（RFC / 草案）</strong></summary>

- [docs/DESIGN-jade-wthit-integration.md](./docs/DESIGN-jade-wthit-integration.md) — Jade/WTHIT 信息显示集成设计（待联网接线）
- [docs/DESIGN-rarity-mapping.md](./docs/DESIGN-rarity-mapping.md) — 稀有度映射协议 RFC（草案）
- [docs/DESIGN-terminal-protocol.md](./docs/DESIGN-terminal-protocol.md) — 终端机谈判通用化设计（草案）

</details>

## 贡献指南

欢迎通过 Issue 与 PR 参与贡献，完整流程见 [CONTRIBUTING.md](./CONTRIBUTING.md)。

### 开发环境

- JDK 21（v1_21_1）/ JDK 25（v26_1_2、v26_2，需 `--enable-preview`）
- Gradle（wrapper 自带 9.5.1，无需手动安装）
- 编辑 `gradle.properties` 的 `active_versions` 切换构建目标

### 分支与提交约定

- 长期分支：`main`（稳定）
- 功能分支命名：`feat/描述`、`fix/描述`、`docs/描述`、`refactor/描述`
- 提交信息推荐 [Conventional Commits](https://www.conventionalcommits.org/)：`feat:` / `fix:` / `docs:` / `refactor:`

### PR 流程

1. 从目标分支（通常是 `main`）切出功能分支
2. 改动涉及的模块运行 `./gradlew :<module>:build` 确保编译通过
3. 用 `./gradlew :<module>:runClient` 在游戏内手动验证
4. 若改动 `common/`，**必须各平台模块都验证**（CI 仅覆盖 3 个 NeoForge 平台，Forge 模块另有各自门禁，详见 [docs/CODE-REVIEW.md](./docs/CODE-REVIEW.md)；默认 `active_versions` 只构建一个，增量缓存可能造假象，必要时 `clean` 编译确认）
5. 同步更新文档（`docs/*.md`、`README.md`）与 `CHANGELOG.md`
6. 提交 PR，附改动说明、测试方式、影响的 MC 版本

<details>
<summary><strong>关键约束（务必遵守）</strong></summary>

- **`common/` 不得 `import net.minecraft.*` 或 `import net.neoforged.*`** —— 版本敏感代码留在平台模块。该约束由 `:common:checkCommonArchitecture` Gradle task 自动挂载在编译上
- 跨平台改动先改基准模块：新功能以 `v26_1_2` 为基准，legacy 唯一模块 `v1_21_1` 直接改；纯新增文件用 `scripts/mirror.sh new`，有适配差异的文件定点合入；**禁止用 `v26_1_2` 整文件覆盖 `v26_2` / `forge_26_1_2`**（会破坏平台适配）
- 新增 `AnimRenderOps` 渲染原语须**三平台同步补**，否则 `scripts/check-animops-drift.sh` 漂移检查失败（CI 已接线）
- `CONFIG` 是 `public static final`，不要写 `null` 守卫
- 升级版本号时四处同步：`gradle.properties` 的 `mod_version` + 各平台 `neoforge.mods.toml`（模板变量自动注入）+ `CHANGELOG.md` + `README.md`；一致性由 `scripts/check-version.sh` 守护
- **`premium_supply_box` / `ItemPremiumBox` 已永久移除（2026-08-19）**：该「军火商高级箱」物品及其全部痕迹（代码 / 资源 / 配置 / 文档）已被彻底删除，**请勿再次引入**——`forge_26_1_2` / `forge_26_2` 的 `PlatformSmokeTest` 反向守卫会直接断言失败

</details>

### 报告问题

在 [GitHub Issues](https://github.com/wikkd/CS2-Box/issues) 提交。报告 bug 请包含：MC 版本、NeoForge 版本、模组版本、重现步骤、预期/实际行为、相关日志（`runs/client/logs/latest.log` 或 `.minecraft/logs/latest.log`）。功能请求请描述使用场景与收益。

## 许可证

[MIT License](./LICENSE) —— Copyright 2024 Reclizer

## 项目状态

- **当前发布版本**：`2.0.1`（正式版，合并「联动批次 A + 箱子配置优化 + 抽奖收尾」三个批次，6 平台同步发行，共享同一 `mod_version`）
- **开发中版本**：无（下一版本线待规划）

<details>
<summary><strong>近期进度</strong>（详见 <a href="./CHANGELOG.md">CHANGELOG.md</a>）</summary>

- **2.0.1**：开箱概率进 tooltip、武库商小屋群系接入、`/csbox info` 来源模组统计、compat-packs 官方联动示例、物品注册表与配置解耦（联机修复）、档内物品权重 / count 区间 / `#tag` / 战利品表 / 随机附魔、终端价格表、每箱 icon、库存/折扣、开箱约束（每人上限/冷却/权限）、`/csbox validate` 干跑、schema 校验、boxgen/check-ids 工具脚本、开箱保底（pity）、帮助与教程入口
- **2.0.0**：批量开箱恢复 + UI 打磨（无二次确认、可滚动「显示全部」网格）、终端机谈判会话（随机磨损 + 无耐久物品磨损点数惩罚）、武库商小屋世界生成结构、JEI 开箱概率分类、`blurRadius` 背景模糊
- **1.0.6**：容器化布局、per-item 视觉基线、三档设计 token、动态 box item、教程系统、开箱排行榜、TACZ 检视视口、v26_2 平台扩展
- **AnimRenderOps 渲染门面**：6 屏 + 3 助手渲染调用全部收口到每平台唯一的 `utils/AnimRenderOps.java`（13 个公开 op），零原始 draw 调用残留，签名一致性由 `scripts/check-animops-drift.sh` 守护
- **forge_26_2 追平 2.0.0 线**：以 `forge_26_1_2` 为基准迁移，5 平台 `clean compileJava` 全通过，门禁 7/7 PASS
- **forge_1_20_1 回移**：MC 1.20.1 正式发布线（SRG 重映射、SimpleChannel 网络、NBT 存储、JEI/REI 接入）

</details>

**已禁用范围**（显式延期）：Cloth Config 强依赖回归（保留可选 GUI）、玩家间交易（loot bind-on-open）。

---

<div align="center">

**CS2-Box** · MIT License · 多加载器覆盖 MC 1.20.1 – 26.2

简体中文 · [English](./README.en.md) · [在线配置编辑器](https://wikkd.github.io/CS2-Box/)

</div>
