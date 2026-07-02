<!-- generated-by: gsd-doc-writer -->
# CS2-Box

> CS:GO 风格开箱体验的 Minecraft NeoForge 模组 —— 同时支持 MC 1.21.1 和 MC 26.1.2。

CS2-Box 把 CS:GO 的开箱逻辑搬到 Minecraft:玩家手持箱子右键打开预览,放入钥匙点开启按钮,服务端授权 RNG 决定结果,客户端播放滚动动画,揭晓稀有度分级物品。当前版本 `1.0.5`,仓库 License MIT(`LICENSE`)。

## 核心特性

- **5 档稀有度分级**:consumer / industrial / mil_spec / restricted / classified,每档独立权重
- **服务端授权 RNG**:服务端在 `PacketCsgoProgress` 内计算中奖索引与物品,客户端只渲染动画
- **JSON 配置箱数据**:`config/csbox/*.json` 文件即可新增箱子类型,无需重新编译
- **Minecraft 1.21+ components 支持**:用 `components` 字段(同时兼容旧版 `tag` 字符串)
- **成就系统**:`全新的开始`(首次主动开箱)+ 隐藏紫色挑战 `导购`(累计主动开 200 个箱)
- **4 把钥匙梯度**:铁 / 金 / 钻石 / 下界合金(下界合金**仅**通过锻造台升级 `csgo_key2` 获得)
- **`/csbox` 命令**:`/csbox list`、`/csbox give`、`/csbox reload` 等子命令

## 双平台支持

仓库是 multiloader 结构,通过 `gradle.properties` 中的 `active_versions` 切换当前构建版本:

| 模块 | Minecraft | NeoForge | NeoGradle | Java | Gradle | 角色 |
|---|---|---|---|---|---|---|
| `common/` | — | — | — | 21 | — | 跨版本业务逻辑 + 共享资源 + platform 接口抽象 |
| `v1_21_1/` | 1.21.1 | 21.1.115 | 7.0.171 | 21 | 8.11 | 1.21.1 平台实现(`@Mod` 入口、DeferredRegister、Screen、Attachment、网络接线) |
| `v26_1_2/` | 26.1.2 | 26.1.2.76 | 7.1.38 | 25 `--enable-preview` | 8.14 | 26.1.2 平台实现(迁移到 decoupled rendering API) |
| `v26_2/` | 26.2 | 26.2.0.7-beta | 7.1.38 | 25 `--enable-preview` | 8.14 | 26.2 平台实现（最新 beta；已完成 decoupled API 适配 + PIP 3D 重写） |

`common/src/main/resources/` 由所有平台通过 `srcDir project(':common').file('src/main/resources')` 共享(v26_1_2 / v26_2 额外设置 `duplicatesStrategy = EXCLUDE`)。

## 构建要求

- **Java 21**(v1_21_1)+ **Java 25**(v26_1_2,需 `--enable-preview`)
- **Gradle** 通过 wrapper 自动管理(8.11 / 8.14)
- **NeoForge** 21.1.115 或 26.1.2.76(根据 `active_versions` 决定)
- 互联网连接(首次构建需下载 NeoForged userdev 与依赖)

验证 Java:

```bash
java -version  # v1_21_1 需 ≥ 21,v26_1_2 需 ≥ 25
```

## 快速开始

```bash
# 1. 克隆并配置活动版本(默认 26.1.2)
git clone https://github.com/wikkd/CS2-Box.git
cd CS2-Box

# 2. 完整构建
./gradlew build

# 3. 启动开发客户端
./gradlew :v26_1_2:runClient   # 或 :v1_21_1:runClient / :v26_2:runClient
```

启动后:

```bash
/csbox give @p csgobox:csgo_box 1
/csbox give @p csgobox:csgo_key0 3
```

手持 `csgo_box` 右键打开预览界面,放入对应钥匙,点开启按钮开始动画。

完整步骤见 [docs/GETTING-STARTED.md](./docs/GETTING-STARTED.md)。

## 配置

- `config/csgobox.toml`:TOML 配置(动画速度、稀有度权重、音量、调试开关等)—— 见 [docs/CONFIGURATION.md](./docs/CONFIGURATION.md)
- `config/csbox/*.json`:箱子数据文件,文件名即箱子 ID —— schema 见 [docs/CONFIGURATION.md](./docs/CONFIGURATION.md)
- **不使用 Cloth Config**(v1.0.5 已完全移除),通过 NeoForge 原生 `ModConfigSpec` 持久化
- **资源路径必须单数** `data/csgobox/recipe/`(Minecraft `RecipeManager` 的 `Registries.elementsDirPath(Registries.RECIPE)` 要求)

## 文档导航

- [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) — 模块拓扑、核心抽象、数据流、双平台 GUI 渲染管线对比
- [docs/GETTING-STARTED.md](./docs/GETTING-STARTED.md) — 完整安装与首次运行步骤
- [docs/DEVELOPMENT.md](./docs/DEVELOPMENT.md) — 本地开发配置、构建命令、数据生成
- [docs/CONFIGURATION.md](./docs/CONFIGURATION.md) — TOML / JSON 配置参考
- [docs/TESTING.md](./docs/TESTING.md) — NeoForge GameTest 测试指南
- [CHANGELOG.md](./CHANGELOG.md) — 各版本发布记录

## 贡献

Bug 报告与功能请求通过 GitHub Issues。代码贡献流程见 [CONTRIBUTING.md](./CONTRIBUTING.md)。

## 许可证

MIT License —— Copyright 2024 Reclizer。详见 [LICENSE](./LICENSE)。

## 项目状态

**当前发布版本**: `1.0.5`(v26_1_2 变体:`1.0.5-26.1.2`,通过 `mod_version` 后缀区分)

**Multiloader 重构进度**(详见 `.planning/ROADMAP.md`):

- ✅ Phase 0-6 done:基线冻结、构建系统、common 边界、v1_21_1 稳定、26.1.2 迁移、26.1.2 日志与 GUI 修复批、26.1.2 审计
- ✅ 阶段 A done:common/utils/ 首批 2 个真正 A 类(ColorTools / OverlayColor)迁移,v1_21_1 + v26_1_2 + v26_2 三模块共存骨架已搭建
- ⏳ Phase 7+ 未开始:common 完整业务代码迁移(目前 B 类文件保留平台层重复,见 `.planning/PROJECT.md`)、容器化布局(P1-1)、per-item 视觉基线(P1-3)、三档设计 token(P2-2)
- ✅ v26_2 已落地:`neo_version=26.2.0.7-beta`, `neogradle=7.1.38`, `neoform=26.2-1`, `pack_format=81`。`./gradlew :v26_2:compileJava` + `:jar` BUILD SUCCESSFUL(`csgobox-26.2-1.0.5.jar` 428 KB);PIP 3D 旋转已重写适配;运行时回归(开箱/进度/查看动画 + 成就触发)用户在 26.2 客户端验证通过。**HUD 提示**:MC 26.2 移除了 `Options.hideGui` 字段,开箱时 hotbar/血条仍可见 —— 用户确认可接受,后续若 26.2 出现等价 API 再补。

**已禁用范围**(显式延期):Cloth Config 回归、Forge 1.20.1 backport、玩家间交易(loot bind-on-open)。

---

<!-- VERIFY: Gradle 8.14 for v26_1_2 — value not directly read in repo, sourced from gradle/wrapper/gradle-wrapper.properties -->