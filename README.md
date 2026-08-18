# CS2-Box

> CS:GO 风格开箱体验的 Minecraft NeoForge 模组 —— 同时支持 MC 1.21.1 和 MC 26.x 共 3 个平台。

CS2-Box 把 CS:GO 的开箱逻辑搬到 Minecraft：玩家手持箱子右键打开预览，放入钥匙点开启按钮，服务端授权 RNG 决定结果，客户端播放滚动动画，揭晓稀有度分级物品。当前版本 `1.0.6`，仓库 License MIT（`LICENSE`）。

## 核心特性

- **5 档稀有度分级**：consumer / industrial / mil_spec / restricted / classified，每档独立权重
- **服务端授权 RNG**：服务端在 `PacketCsgoProgress` 内计算中奖索引与物品，客户端只渲染动画
- **JSON 配置箱数据**：`config/csbox/*.json` 文件即可新增箱子类型，无需重新编译
- **Minecraft 1.21+ components 支持**：用 `components` 字段（同时兼容旧版 `tag` 字符串）
- **成就系统**：`全新的开始`（首次主动开箱）+ 隐藏紫色挑战 `导购`（累计主动开 200 个箱）
- **4 把钥匙梯度**：铁 / 金 / 钻石 / 下界合金（下界合金**仅**通过锻造台升级 `csgo_key2` 获得）
- **`/csbox` 命令**：`/csbox info`、`/csbox reload`、`/csbox nbt hand` 等子命令

## 多平台支持

仓库是 multiloader 结构，通过 `gradle.properties` 中的 `active_versions` 切换当前构建版本：

| 模块 | Minecraft | NeoForge / Forge | Java | 角色 |
|---|---|---|---|---|
| `common/` | — | — | 21 | 跨版本业务逻辑 + 共享资源（无 MC/NeoForge 依赖） |
| `v1_21_1/` | 1.21.1 | 21.1.248 | 21 | 旧 API 平台实现 |
| `v26_1_2/` | 26.1.2 | 26.1.2.95 | 25 `--enable-preview` | decoupled rendering API + PIP 3D |
| `v26_2/` | 26.2 | 26.2.0.59 | 25 `--enable-preview` | 最新版；decoupled API + PIP 3D 重写 |
| `forge_26_1_2/` | 26.1.2 | MinecraftForge 26.1.2-64.1.0 | 25 `--enable-preview` | 实验模块：随 v26_1_2 同步开发，不入正式发行矩阵 |
| `forge_26_2/` | 26.2 | MinecraftForge 26.2-65.1.1 | 25 `--enable-preview` | 实验模块：1.0.7 线已追平，不入正式发行矩阵 |

> **已归档（EOL）平台**：v1_21_0 / v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10 / v1_21_11 于 2026-08-09 移出仓库，最后状态保留在 tag `eol-legacy-21x-1.0.6`；旧版本玩家仍可下载既有发布产物。

`common/src/main/resources/` 由所有平台通过 `srcDir project(':common').file('src/main/resources')` 共享（v26_1_2 / v26_2 额外设置 `duplicatesStrategy = EXCLUDE`）。

## 安装

### 玩家安装（使用发布版）

1. 确认已安装对应 Minecraft 版本的 **NeoForge**（或 Forge）加载器：
   - MC 1.21.1 → NeoForge **21.1.248+**
   - MC 26.1.2 → NeoForge **26.1.2.95**（loader 11+）
   - MC 26.2 → NeoForge **26.2.0.59**
   - Forge 版（实验模块）→ MinecraftForge **26.1.2-64.1.0**，jar 名为 `csgobox-forge-26.1.2-<mod_version>.jar`
2. 从 [Releases](https://github.com/wikkd/CS2-Box/releases) 下载对应版本的 jar：`csgobox-<mc>-1.0.6.jar`（如 `csgobox-26.1.2-1.0.6.jar`）。
3. 将 jar 放入 Minecraft 客户端的 `mods/` 文件夹。
4. 启动游戏，世界内用 `/give @p csgobox:csgo_box` 获取箱子即可开箱。

> 三个平台共享同一 `mod_version`，jar 命名规则为 `csgobox-<mc>-<mod_version>.jar`。已归档（EOL）的 1.21.0/3/4/5/8/10/11 旧版玩家可继续使用既有发布产物。

### 开发者构建（从源码）

**前置要求**

| 要求 | v1_21_1 | v26_1_2 / v26_2 | forge_26_1_2 / forge_26_2 |
|---|---|---|---|
| Java JDK | 21 | 25（`--enable-preview`） | 25（`--enable-preview`） |
| Minecraft | 1.21.1 | 26.1.2 / 26.2 | 26.1.2 / 26.2 |
| NeoForge / Forge | 21.1.248+ | 26.1.2.95 / 26.2.0.59（loader 11+） | MinecraftForge 26.1.2-64.1.0 / 26.2-65.1.1 |
| Gradle | 9.5.1（wrapper 自动下载，无需手动安装） | 同左 | 同左 |
| NeoGradle / ForgeGradle | 7.1.38 | 同左 | ForgeGradle 7.0.31/7.0.34 |

> 互联网连接：首次构建需下载 NeoForged userdev 与依赖。

**构建步骤**

```bash
# 1. 克隆仓库
git clone https://github.com/wikkd/CS2-Box.git
cd CS2-Box

# 2. 选择活动版本（默认 26.1.2）：编辑 gradle.properties
#    active_versions=26.1.2   # 可选值：1.21.1 / 26.1.2 / 26.2 / forge-26.1.2

# 3. 构建当前平台的 jar
./gradlew :v26_1_2:jar        # 产物：v26_1_2/build/libs/csgobox-26.1.2-1.0.6.jar

# 4. 启动开发客户端（自动下载并注入 NeoForge 到 run/ 目录）
./gradlew :v26_1_2:runClient
```

验证 Java 版本：

```bash
java -version   # v1_21_1 应显示 21.x；v26_1_2 / v26_2 应显示 25.x
```

> 由于 NeoGradle userdev 限制，**每次 Gradle 调用只能构建一个 MC 版本**（历史限制，见 `settings.gradle`）。需要各平台产物时逐个切换 `active_versions`（或用 `-Pactive_versions=<v>` 覆盖）串行构建，详见 [docs/RELEASE.md](./docs/RELEASE.md)。

**v1_21_1 的 TACZ 依赖**（永恒枪械工坊检视视口集成）：jar 不入库（仓库惯例 `*.jar` 全局忽略），首次构建前运行 `scripts/download-tacz.sh` 填充 `local-repo/com/tacz/`（CI 自动执行），并从 jarjar 提取编译所需的 `simplebedrockmodel`。无 TACZ 环境时相关功能**自动降级**，不影响编译与运行。

## 使用示例

### 获取物品

本模组使用**原版 `/give`** 发放物品（没有 `/csbox give` 子命令）：

```bash
/give @p csgobox:csgo_box          # 武器供应箱
/give @p csgobox:csgo_key0 3       # 铁钥匙 ×3
/give @p csgobox:csgo_key1         # 金钥匙
/give @p csgobox:csgo_key2         # 钻石钥匙
# 下界合金钥匙 csgo_key3 只能通过锻造台升级 csgo_key2 获得（smithing_transform）
```

钥匙梯度：铁（key0）→ 金（key1）→ 钻石（key2）→ 下界合金（key3，锻造台 `smithing_transform`）。

### 开箱流程

1. 手持箱子 **右键** 打开预览界面（2 行 × 10 列物品网格）。
2. 将对应钥匙放入钥匙槽，点击 **开启** 按钮。
3. 服务端权威 RNG 决定结果 → 客户端播放滚动动画 → 揭晓稀有度分级物品。
4. **批量开箱**：手持箱子 **Shift+右键** 进入批量开箱总览屏，设定数量并二次确认后流水揭晓（1.0.7 恢复）。

### `/csbox` 命令参考

| 命令 | 权限 | 说明 |
|---|---|---|
| `/csbox` | OP（权限等级 2） | 显示帮助 |
| `/csbox info [<箱子ID>]` | OP | 列出所有箱子与加载错误；加 `<箱子ID>` 查看该箱权重、掉落实体、各档物品等详情 |
| `/csbox info error` | OP | 仅显示当前箱子加载错误（无错误时绿色提示） |
| `/csbox reload` | OP | 重新加载 `config/csbox/*.json` 箱子定义 |
| `/csbox reload tutorial` | OP | 重载箱子定义，并强制刷新教程文档 |
| `/csbox nbt hand` | 任意玩家 | 打印主手物品序列化后的 JSON（可直接粘贴进箱子 `items`） |

示例：

```bash
/csbox info csgobox:weapon_supply_box
/csbox reload
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

- 箱子类型由 JSON 的 `type` 字段判定（v1.0.8 起唯一机制）：`"type": "terminal"` 为终端机（注册为 `ItemTerminal`，打开终端谈判屏），`"type": "csbox"`（或省略，默认）为普通宝箱。**终端机与普通箱字段严格分离**：终端机不使用 `key` 字段（出现即报 schema 错误，v1.0.8 已从默认配置删除），普通箱用 `key` 指定所需钥匙（`minecraft:air` 免钥匙）。
- `random` 为 5 档权重（grade1→grade5，越高越稀有）；`grade1`~`grade5` 各为一个物品数组，每档按 `random` 对应权重抽取。
- `components` 使用 MC 1.21+ DataComponent 语法（同时兼容旧版 `tag` 字符串）。
- `entity` 为「实体 ID + 掉落率」成对列表，全局 `drop` 为默认掉落率。

> 修改 JSON 后执行 `/csbox reload` 即时生效；`enableHotReload`（默认开启）开启时 `config/csbox/*.json` 文件变化会自动热重载（300ms 防抖）。可用 `/csbox nbt hand` 把手中物品导成 JSON 片段直接复用。

## 配置

- `config/csgobox.toml`：TOML 配置（动画速度、稀有度权重、音量、调试开关等）—— 见 [docs/CONFIGURATION.md](./docs/CONFIGURATION.md)
- `config/csbox/*.json`：箱子数据文件，文件名即箱子 ID —— schema 见 [docs/CONFIGURATION.md](./docs/CONFIGURATION.md)
- **不使用 Cloth Config**（v1.0.5 已完全移除），通过 NeoForge 原生 `ModConfigSpec` 持久化
- **资源路径必须单数** `data/csgobox/recipe/`（Minecraft `RecipeManager` 的 `Registries.elementsDirPath(Registries.RECIPE)` 要求）

## 文档导航

- [docs/PLAYER-INTRO.md](./docs/PLAYER-INTRO.md) — 面向玩家的模组介绍与玩法说明
- [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) — 模块拓扑、核心抽象、数据流、多平台 GUI 渲染管线对比
- [docs/PLATFORM-APIS.md](./docs/PLATFORM-APIS.md) — 3 平台 API 差异速查矩阵 + 主题展开开发指南（含 AnimRenderOps 渲染门面章节）
- [docs/GETTING-STARTED.md](./docs/GETTING-STARTED.md) — 完整安装与首次运行步骤
- [docs/DEVELOPMENT.md](./docs/DEVELOPMENT.md) — 本地开发配置、构建命令、数据生成
- [docs/CONFIGURATION.md](./docs/CONFIGURATION.md) — TOML / JSON 配置参考
- [docs/TESTING.md](./docs/TESTING.md) — NeoForge GameTest 测试指南
- [CHANGELOG.md](./CHANGELOG.md) — 各版本发布记录

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

1. 从目标分支（通常是 `main`）切出功能分支。
2. 改动涉及的模块运行 `./gradlew :<module>:build` 确保编译通过。
3. 用 `./gradlew :<module>:runClient` 在游戏内手动验证。
4. 若改动 `common/`，**必须三平台都验证**（默认 `active_versions` 只构建一个，增量缓存可能造假象，必要时 `clean` 编译确认）。
5. 同步更新文档（`docs/*.md`、`README.md`）与 `CHANGELOG.md`。
6. 提交 PR，附改动说明、测试方式、影响的 MC 版本。

### 关键约束（务必遵守）

- **`common/` 不得 `import net.minecraft.*` 或 `import net.neoforged.*`** —— 版本敏感代码留在平台模块。该约束由 `:common:checkCommonArchitecture` Gradle task 自动挂载在编译上。
- 跨平台改动先改基准模块：新功能以 `v26_1_2` 为基准，legacy 唯一模块 `v1_21_1` 直接改；纯新增文件用 `scripts/mirror.sh new`，有适配差异的文件定点合入；**禁止用 `v26_1_2` 整文件覆盖 `v26_2` / `forge_26_1_2`**（会破坏平台适配）。
- 新增 `AnimRenderOps` 渲染原语须**三平台同步补**，否则 `scripts/check-animops-drift.sh` 漂移检查失败（CI 已接线）。
- `CONFIG` 是 `public static final`，不要写 `null` 守卫。
- **不使用 Cloth Config**，仅用 `ModConfigSpec`。
- 升级版本号时四处同步：`gradle.properties` 的 `mod_version` + 各平台 `neoforge.mods.toml`（模板变量自动注入）+ `CHANGELOG.md` + `README.md`；一致性由 `scripts/check-version.sh` 守护。

### 报告问题

在 [GitHub Issues](https://github.com/wikkd/CS2-Box/issues) 提交。报告 bug 请包含：MC 版本、NeoForge 版本、模组版本、重现步骤、预期/实际行为、相关日志（`runs/client/logs/latest.log` 或 `.minecraft/logs/latest.log`）。功能请求请描述使用场景与收益。

## 许可证

MIT License —— Copyright 2024 Reclizer。详见 [LICENSE](./LICENSE)。

## 项目状态

**当前发布版本**： `1.0.6`（维护中 3 平台：v1_21_1 / v26_1_2 / v26_2，共享同一 `mod_version`，jar 名 `csgobox-<mc>-<mod_version>.jar`；另有 `forge_26_1_2` / `forge_26_2` 实验模块分别随 v26_1_2 / v26_2 同步开发、不入正式发行矩阵；v1_21_0/3/4/5/8/10/11 七个 EOL 平台已归档，最后状态在 tag `eol-legacy-21x-1.0.6`）

**近期进度**（详见 [CHANGELOG.md](./CHANGELOG.md)）：

- ✅ 1.0.6 落地：容器化布局（P1-1）、per-item 视觉基线（P1-3）、三档设计 token（P2-2）、动态 box item、教程系统、开箱排行榜、TACZ 检视视口、v26_2 平台扩展（详见 CHANGELOG.md）
- ✅ AnimRenderOps 渲染门面：6 屏 + 3 助手渲染调用全部收口到每平台唯一的 `utils/AnimRenderOps.java`（13 个公开 op，`// era:` 头标注），零原始 draw 调用残留，签名一致性由 `scripts/check-animops-drift.sh` 守护（CI 已接线）；三平台 clean 编译 + common 测试通过，运行时回归清单见 docs/RUNTIME-UI-TESTING.md
- ✅ v26_2 已落地：`neo_version=26.2.0.59`，`neogradle=7.1.38`，`pack_format=81`。PIP 3D 旋转已重写适配；运行时回归（开箱/进度/查看动画 + 成就触发）用户在 26.2 客户端验证通过。**HUD 提示**：MC 26.2 移除了 `Options.hideGui` 字段，已修复：通过 `Minecraft.gui.hud.toggle()/isHidden()` 包装为 `HudVisibility` 工具类，开箱动画屏自动隐藏 HUD。
- ✅ forge_26_2 追平 1.0.7 线：以 `forge_26_1_2` 为基准迁移，5 平台 `clean compileJava` 全通过，门禁 7/7 PASS，资源一致性已补齐。
- ⏳ 1.0.7/1.0.8 开发线（[未发布]）：批量开箱恢复、终端机谈判会话（随机磨损 + 无耐久物品磨损点数惩罚）、武库商小屋世界生成结构、JEI 开箱概率分类、`blurRadius` 背景模糊等

**已禁用范围**（显式延期）：Cloth Config 回归、Forge 1.20.1 backport、玩家间交易（loot bind-on-open）。
