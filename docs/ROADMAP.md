# CS2-Box 路线图

> 阶段性总体规划（非历史快照）。当前版本基线：**2.0.2**（2026-09-17 发布，六平台同步，CI 全绿）。
> 每个方向给出目标、复用点、影响面与前置条件；**不承诺排期**，由维护者按收益/成本取舍。
> 落地进度的权威记录仍是 `CHANGELOG.md`，本文件只做方向目录与决策依据。

---

## 0. 现状快照

### 0.1 平台矩阵（六平台，`docs/ARCHITECTURE.md`）

| 平台 | 加载器 | MC | Java | 备注 |
|---|---|---|---|---|
| `v1_21_1` | NeoForge | 1.21.1 | 21 | legacy 渲染；TACZ / JEI / REI / EMI / Jade / WTHIT / TOP / Create |
| `v26_1_2` | NeoForge | 26.1.2 | 25+preview | decoupled 渲染（跨平台基准）；JEI/REI + Jade/WTHIT/TOP |
| `v26_2` | NeoForge | 26.2 | 25+preview | decoupled 渲染；JEI/REI + Jade/WTHIT/TOP |
| `forge_1_20_1` | Forge | 1.20.1 | 17 | legacy；TACZ / JEI / REI / EMI / Jade / WTHIT / TOP / Create |
| `forge_26_1_2` | Forge | 26.1.2 | 25+preview | decoupled；WTHIT（唯一信息显示） |
| `forge_26_2` | Forge | 26.2 | 25+preview | decoupled；WTHIT |

> 已知缺口（详见方向 RP-06 / RP-07）：`forge_26_1_2` / `forge_26_2` 无 JEI/REI（Modrinth 无 26.x Forge 构建）；26.x 无 Create 适配（上游无构建）。

### 0.2 已交付能力（截至 2.0.x）

- **开箱线**：5 档稀有度、4 档钥匙、自定义 JSON 箱子、批量开箱、滚动动画、3D 检视、磨损、保底（pity）、档内权重、count 区间、tag / loot_table 条目、随机附魔、`enabled/requires` 门控、`/csbox` 命令族（info / reload / validate / editor / nbt hand）
- **经济线**：终端机（5 轮谈判、批量购买、3 小时倒计时）、军火商村民、武库拆解台、`_prices.json` 全局价格表（固定价 / 区间价 / 变体键）、钥匙合成与锻造
- **配置与工具**：`config/csbox/*.json` 热重载、`box.schema.json` / `prices.schema.json`、`box-editor/` 网页工具、`scripts/boxgen.py` / `check-ids.py`、内嵌双语教程
- **联动**：JEI/REI/EMI 概率面板、Jade/WTHIT/TOP、TACZ 枪械（变体 / NBT / 检视）、KubeJS 事件（开箱 / 终端购买 / 实体掉落 / 拆解）、**Create**（机械手开箱 + 拆解台自动化，反射零编译依赖）
- **安全与工程**：CSPRNG 种子（48 位 LCG 不外泄约定）、`OpenBlockGuard` 服务端权威冷却、common 纯业务约束（`checkCommonArchitecture`）、AnimRenderOps 渲染门面（漂移守护）、GitHub Actions 门禁（见 `docs/CI-PROTECTION.md`）
- **2.0.2 体检批次**：终端 `restock_minutes` 整体移除、批量开箱按每玩家剩余额度裁剪、拆解台大额产出流转、同步包容量护栏、保底进度 UI 可见化、配置未知字段警告 + `format_version`、tooltip 缓存与行数上限；CI 补齐 Create compileOnly 依赖供给与平台漂移 baseline 修复

### 0.3 硬边界（历史决策，勿翻案）

- **不新增方块**（2026-08 决议）：机械手开箱等自动化一律落在现有方块/实体/平台交互上
- **军火商经济大改**（`docs/archive/superpowers/archived/2026-08-09-arms-dealer-economy.md`）已归档：不做整体经济重设计；`_prices.json` 价格体系是当前权威
- **玩家间交易**（player-to-player trading）：早期规划的 out-of-scope，除非单独提决议
- **Cloth Config 回归**：out-of-scope
- **运行时零外联**：教程内嵌、无联网下载（v2.0.1 起已是硬约束）

---

## 1. 方向总览

| 编号 | 方向 | 类别 | 成本 | 前置/依赖 | 收益 |
|---|---|---|---|---|---|
| RP-01 | Trade Up 升级合成 | 玩法 | S–M | 无（复用 grade 印记 + 价格表） | 高：垃圾回收第二通道 + 追梦玩法 |
| RP-02 | 开箱图鉴 / 收集册 | 玩法 | M | 无（复用成就统计） | 高：收集粘性 |
| RP-03 | 限时活动箱时间窗 | 玩法/运营 | S | 无（扩展 JSON schema） | 高：运营抓手 |
| RP-04 | 掉落源扩展（钓鱼/结构 loot） | 玩法 | S–M | 无（事件监听即可） | 中 |
| RP-05 | KubeJS 事件扩充 | 联动 | S | 无（仿既有事件） | 中高：脚本服主 |
| RP-06 | Create 26.x 适配 | 联动 | M | **上游 Create 26.x 构建** | 中（平台一致性） |
| RP-07 | JEI/REI 26.x Forge 补齐 | 联动 | S | **上游 Modrinth 构建** | 中（平台一致性） |
| RP-08 | 权限/付费接范例（LuckPerms 等） | 运营 | S | 无（文档 + 可选示例语句） | 中 |
| RP-09 | 统计与排行榜、稀有掉落广播 | 运营 | M | 无（复用 BoxConstraintTracker / 成就统计） | 中高：服主口碑 |
| RP-10 | 六平台 CI + 发布自动化 | 工程 | M | 无（已有 CI 基础） | 高：省人力 |
| RP-11 | 军械库整合包 v2 落地 | 长期主线 | L | FTB Quests 等外挂模组；`docs/superpowers/specs/2026-09-12-...` 草案 | 高（面向整合包发布） |

---

## 2. 方向明细

### RP-01 · Trade Up 升级合成（CS:GO "10 换 1"）

**目标**：N 件**同档盖章物品**（带 `csgobox:grade`）合成 1 件**高一档**物品——保留磨损/附魔随机，产出从该档物品池按权重抽取。

- **复用点**：`common/box/GradeMapCache`（物品池）、`BoxGrades`（档位序）、grade 印记校验（拆解台资格逻辑）、`PriceTable`（可选：合成产物定价提示）
- **形态**：优先 Crafting（shaped 9 格 × N）或专属「升级台」独立交互界面——需遵守「不新增方块」约束，若选界面则挂在现有方块/手持物品上（类似终端机入口），或纯合成配方
- **配置**：`config/csbox/*.json` 或单独 `_tradeups.json`：档位、所需数量 N（默认 10，可配 5/10）、目标档、是否允许跨箱（默认同箱池内）
- **影响面**：common（规则 + 单测）+ 六平台（注册/配方/界面各一处）；可用事件 hook（`TradeUpAttemptEvent`）留给 KubeJS
- **验证**：`common` 单测（N 守恒、同档校验、权重抽取、保底不参与）、六平台 compile、JEI 配方页展示

### RP-02 · 开箱图鉴 / 收集册

**目标**：服务端记录每位玩家「开出过什么」：物品 id、（可选变体）、档位、磨损区间、首次/最近开出时间、计数；客户端提供图鉴界面（按箱子分组），完成度 → 可配置奖励（如保底权重加成或成就）。

- **复用点**：`BoxOpenedEvent`（已存在）、成就/统计计数、`CsLookItemScreen` 3D 展示（点击图鉴条目直接检视）
- **配置**：`enabled` 开关 + 完成度奖励表（JSON）
- **影响面**：common（存储 + 查询逻辑）+ 六平台（界面）。存储内存态（与 `BoxConstraintTracker` 同生命周期）或写入玩家 NBT/能力（每平台能力已有先例）
- **风险**：跨服/重启持久化口径需先定（能力 vs 内存）；大图鉴同步包体需分页

### RP-03 · 限时活动箱时间窗

**目标**：箱子 JSON 增加可选 `"availability": { "start": "<ISO-8601>", "end": "<ISO-8601>" }`（省略 = 长期有效）；窗口外箱子**不可开**（提示文案）、`drop` 掉落暂停、`/csbox info` 与 JEI 面板显示「活动进行中 / 已结束」。

- **配套**：可选 `serverEvents` 全服加成——全局掉落率/权重倍率，按日期窗口生效（如周末双倍），服务端权威
- **复用点**：`enabled/requires` 门控机制、`BoxJsonLoader` 校验、box-editor（新增两个字段）
- **影响面**：common（schema 校验 + 时间判定，纯 JDK `Instant`）+ 六平台（门控接线 + 界面文案）

### RP-04 · 掉落源扩展

**目标**：箱子掉落从「实体击杀」扩展到可配置来源：`source: entity | fishing | chest|structure_loot`（钓鱼钓到箱 / 地牢宝箱开出箱 / 结构战利品表注入）。

- **复用点**：`BoxEntityDropEvent` → 新增 `BoxFishedEvent` / `BoxLootTableEvent`（或统一 `BoxDropSourceEvent`），RNG 纪律与现有实体掉落一致（CSPRNG 派生、可取消、可调概率）
- **影响面**：common（事件定义 + drop 概率表扩展）+ 六平台（钓鱼事件监听 / loot 注入各一处）

### RP-05 · KubeJS 事件扩充

**目标**：补齐运营向事件：`PityTriggerEvent`（保底触发，可广播/通知）、`GradeRolledEvent`（掷骰结果只读，供统计/广播）、`TradeUpAttemptEvent`（配合 RP-01）、`DropRateEvent`（全局掉落率查询/覆盖）。

- **复用点**：现有事件总线模式与 `docs/KUBEJS-EVENTS.md`
- **影响面**：common（事件类）+ 六平台（发布点接线，注意 1.20.1 Forge 与 NeoForge 总线差异，参照 `BoxOpeningEvent.BUS` 既有处理）

### RP-06 · Create 26.x 适配（前置：上游）

**目标**：把 `v1_21_1` / `forge_1_20_1` 的 `CreatePlatformItems` + `DeployerBoxOpen` 反射层平移到 26.x（NeoForge + Forge）。

- **前置**：Create 发布 26.x 构建并公开 `TransportedItemStackHandlerBehaviour`（API 契约与 0.5.x/0.6.x 同构）
- **风险**：Create 26.x 若改包名/API 需重核对；反射失败静默降级（与现状一致）
- **落地时**：更新 `docs/CREATE-COMPAT.md` 平台矩阵

### RP-07 · JEI/REI 26.x Forge 补齐（前置：上游）

**目标**：`forge_26_1_2` / `forge_26_2` 恢复 JEI/REI 概率面板（当前仅 WTHIT）。

- **前置**：Modrinth/官方渠道出现可用构建（`docs/ARCHITECTURE.md` §2.2 已有待办记录）
- **落地时**：以 `v26_1_2`（NeoForge JEI/REI）为基准迁移，逐平台 compile + 冒烟

### RP-08 · 权限/付费接入范例

**目标**：`CsgoBox.PERMISSION_GATE`（`BiPredicate<ServerPlayer,String>`）的对接文档 + 示例：LuckPerms 权限节点（`csgobox.open.<id>`）与 FTB Ranks；可选提供 `permission` 字段与 `_prices.json` 的联动（无权限时不允许购买）。

- **影响面**：文档为主（`docs/CONFIGURATION.md` / 新 `docs/PERMISSIONS.md`）+ 可选示例类
- **无硬依赖**：接入方自行实现 `PERMISSION_GATE.set(...)`

### RP-09 · 统计与排行榜、稀有掉落广播

**目标**：
- `/csbox stats [player]`：总开箱、各档位次数、保底进程（只读透传 `PityTracker`）、磨损累计
- `/csbox top [days]`：开箱数排行（可配置是否启用）
- 稀有掉落广播：开出 restricted / classified 时全服消息（可配置开关/频道/文案，走 `BoxOpenedEvent` 不加塞 RNG）

- **复用点**：`PityTracker`、成就统计、`OpenedBoxTrigger`、既有命令框架
- **影响面**：common（统计聚合）+ 六平台（命令/广播接线）
- **注意**：广播内容不得含 `serverSeed` / 可复现随机流信息（遵守种子纪律）

### RP-10 · 六平台 CI + 发布自动化

**目标**：现有 `build.yml` 矩阵之外：`forge_1_20_1` 平台加入 matrix（当前以本地构建为主）；发布脚本（Modrinth/CurseForge 上传 + 自动生成 playtest jar/`-srg.jar` 命名）。

- **复用点**：`docs/CI-PROTECTION.md`、`docs/RELEASE.md`
- **影响面**：`.github/workflows/` + `scripts/`，无运行时改动

### RP-11 · 军械库整合包 v2 落地（长期主线）

**目标**：按 `docs/superpowers/specs/2026-09-12-军械库整合包-forge-1.20.1-pve-main-spec.md`（及上级 1.21.1 草案）落地：FTB Quests 主线、三阶段难度门、BOSS 链、图鉴毕业判定；csbox 作为**唯一经济/掉落出口**。

- **前置**：RP-02（图鉴毕业判定依赖收集册）与 RP-09（统计）可先行；FTB Quests / Bountiful 等外挂模组由玩家侧安装
- **风险**：P4 防刷钳制（单件回收点 < 铁钥匙成本 9）与 `_prices.json` 现状需核对（部分 TACZ 高价值枪不进点数经济）
- **产出形态**：数据包/配置包仓库（quests、loot、trade、prices），csgobox 本身仅加少量运营事件（RP-05）

---

## 3. 建议里程碑

| 里程碑 | 范围 | 理由 |
|---|---|---|
| **v2.2（玩法批次）** | RP-01 Trade Up + RP-03 活动时间窗 + RP-05 事件扩充（Pity/Grade/TradeUp） | 均为 S–M 成本、common 为主、无上游卡点；三个方向互相咬合（活动箱 + 升级 + 事件广播） |
| **v2.3（运营批次）** | RP-02 图鉴 + RP-09 统计/广播 + RP-08 权限范例 | 承接 v2.2 的统计基础；面向服主与整合包 |
| **v2.4（生态批次）** | RP-06 / RP-07（视上游进度）+ RP-04 掉落源 + RP-10 CI 发布 | 平台一致性收尾；上游就绪即插入 |
| **长期** | RP-11 整合包 v2 主线 | 独立节奏，随外部模组生态推进 |

> 里程碑为建议而非承诺；任何上游发布（Create 26.x / JEI-REI 26.x）可打断插入对应项。

---

## 4. 已否决 / 不采纳（供回溯）

| 提案 | 来源 | 否决理由 |
|---|---|---|
| 军火商经济大改（武库点数循环数值重设） | `docs/archive/superpowers/archived/2026-08-09-arms-dealer-economy.md` | 已归档（2026-08-13）：不整体重设计；沿用 `_prices.json` |
| 机械手开箱专用新方块 | 2026-08 讨论 | 不新增方块；落在 Create 传送带/置物台 |
| 玩家间物品交易 | 早期规划 | out-of-scope |
| Cloth Config 回归 | 早期规划 | out-of-scope |

---

## 5. 演进规则

1. **落地信号**：任何方向进入实现前，先更新本文件状态（`[ ]` → `[~]` → `[x]`）并在 `CHANGELOG.md` 增加对应条目。
2. **架构纪律**：common 不得 import MC/加载器类（`checkCommonArchitecture`）；平台改动先落基准模块（`v26_1_2` 或 `v1_21_1`）再定点合入，禁止整文件覆盖。
3. **随机纪律**：任何新掷骰路径一律 CSPRNG 派生；`serverSeed` 永不写日志/发客户端/进事件。
4. **配置驱动优先**：玩法数值先做 JSON 可配，再考虑硬编码。
5. **上游卡点**：RP-06 / RP-07 以外链构建为准；未就绪时保持静默降级（反射 + 可选集成）。