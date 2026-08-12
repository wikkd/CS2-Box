# CS2-Box 代码审查标准与流程

> 本文档定义 CS2-Box 的代码审查（Code Review）标准与流程，目标是把"质量参差不齐"变成"可预期、可审查、可自动化门禁"的常态。
> 审阅者请遵循 **Code Review Expert** 角色：像导师一样评论——每条意见都要**具体、解释为什么、给出可操作建议**，并按优先级标注。

---

## 1. 目的与适用范围

- 统一三个活跃平台（`v1_21_1` / `v26_1_2` / `v26_2`）+ `common` 共享层的改动验收口径。
- 把项目固有风险（跨版本镜像、架构约束、并发权威、渲染状态）变成**显式、可勾选**的审查项，而不是靠个人经验兜底。
- 适用于所有进入 `main` / `multiloader-refactor` 的 Pull Request，以及发布前的质量门复核。
- 不适用于：`forge_26_1_2` 实验模块的本地 WIP（见 §4.9）。

---

## 2. 角色与职责

| 角色 | 职责 |
|---|---|
| **Author（作者）** | 提交前完成 §6.1 自查；保证 CI 门禁全绿；在 PR 描述中说明改动范围、影响平台、测试方式；积极回应评论。 |
| **Reviewer（审查者）** | 按本文档清单逐条核对；运行必要的本地验证；给出带优先级的评论；在门禁全绿 + 无未解决 Blocker 时批准。至少 1 名 Reviewer 批准（核心/跨平台改动建议 2 名）。 |
| **Maintainer（维护者）** | 判断是否需要第二审查者；负责合并；对 `CONSTRAINT-001` 与镜像纪律的最终责任；发布前跑 §6.4 质量门。 |

> 审查不是"挑错比赛"。好代码要**当面表扬**（clever solution / clean pattern），待改进点要**教学式**解释，让作者下次做得更好。

---

## 3. 审查优先级体系

每条评论必须标注其一：

| 标记 | 含义 | 合并前要求 |
|---|---|---|
| 🔴 **Blocker（必须改）** | 会破坏编译/运行时/架构约束/数据安全，或绕过平台适配 | **不修不能合并** |
| 🟡 **Suggestion（应当改）** | 正确但存在可靠性/可维护性/性能隐患，或违反项目纪律 | 需讨论并达成共识，建议本轮修 |
| 💭 **Nit（锦上添花）** | 风格、命名、注释等不影响行为的小改进 | 可后续跟进，不强求 |

评论格式见 §8。

---

## 4. CS2-Box 专属审查清单（核心）

> 这部分是 CS2-Box 区别于普通 Java 项目的**关键审查面**。任何一条命中即按规则升级优先级。

### 4.1 架构约束 CONSTRAINT-001 🔴
`common/` **不得** `import net.minecraft.*` 或 `net.neoforged.*`（编译环境无 MC classpath，违反即编译失败）。

- 审查点：
  - 改动是否新增了 `common/` 对 MC/NeoForge 类的引用？若有 → **Blocker**，必须下沉到平台模块或 `common/platform/` 接口。
  - `common/platform/` 接口是否仅声明、由平台模块实现？平台专属逻辑是否真的留在了平台侧？
  - 新共享资源（纹理/音效/lang/配方/advancement）是否放在 `common/src/main/resources/`？
- 自动化：`scripts/checkCommonArchitecture` 已挂载在 `compileJava`。若 CI 的 `common-test` 过了，该项基本可信，但 Review 时仍需**肉眼确认**有没有"为了编译通过把逻辑硬塞进平台、common 却留下隐式依赖"的取巧。

### 4.2 多平台镜像纪律 🔴/🟡
三平台**不是纯拷贝**：`v26_2` 有 decoupled API 适配（`BuiltInRegistries.ITEM.get()` 返回 `Optional`、`spawnAtLocation(ServerLevel,...)`、`lookup()`、`MouseButtonEvent`、`setScreenAndShow`、PIP 渲染器等）。

- 审查点：
  - 是否用 `v26_1_2` 整文件覆盖了 `v26_2`？→ **Blocker**（`v26_2` 适配会被破坏，历史教训）。
  - 纯新增无适配差异的文件，是否走 `scripts/mirror.sh new <rel-path>`（`--dry-run` 预演、`--force` 覆盖）？
  - 有适配差异的文件，是否做了**定点合入**（`v26_1_2` → `v26_2` 手工适配），而非整文件覆盖？
  - 改动是否应在三个活跃平台同步？是否漏改某平台（尤其 `v26_2` 的 API 适配点）？
  - 涉及平台代码的改动，CI 是否用 **clean 编译**验证（增量缓存会造假象）？
- 参考：`AGENTS.md`「平台模块镜像纪律」节与 `scripts/mirror.sh`。

### 4.3 版本号四同步 🔴
升级版本时 `gradle.properties` 的 `mod_version=` + 各平台 `META-INF/neoforge.mods.toml`（`${mod_version}` 模板变量）+ `CHANGELOG.md` + `README.md` 必须一致。

- 审查点：
  - 是否手动改了 `mods.toml` 的版本字符串？→ **Blocker**，必须保留 `${mod_version}` 模板变量（由 Gradle 注入，手改会过期）。
  - `CHANGELOG.md` / `README.md` 是否补了对应版本条目/提及？
- 自动化：`scripts/check-version.sh`（CI `common-test` 已接线）。PR 若动版本号，确认该检查通过。

### 4.4 AnimRenderOps 跨平台签名漂移 🔴
`utils/AnimRenderOps.java` 每平台一份，是**唯一渲染原语适配点**。13 个公开 op 跨平台签名必须一致：`blitTextured`×3 / `fill` / `fillGradient` / `scissor` / `scissorDisable` / `setBlendNormal` / `flush` / `renderBlurredBackground` / `renderItem2D` / `renderItem3D` / `supports3D`。

- 审查点：
  - 是否改了 `AnimRenderOps`？若改，**三平台必须同步补同一组 op**，否则漂移检查失败。
  - 是否新增渲染原语？→ 须三平台同步补（见 `scripts/check-animops-drift.sh` 守护的 13 op 清单）。
  - 文件头 `// era: legacy|decoupled` 标注是否正确（1.21.1=legacy，26.x=decoupled）？
  - 屏与助手是否**只经门面**调用渲染原语（无残留原始 `draw`/`RenderSystem` 调用）？可用 grep 审计。
- 自动化：`scripts/check-animops-drift.sh`（CI `common-test` 已接线）。

### 4.5 配置同步 🟡
`CsboxConfig`（NeoForge `ModConfigSpec`）字段需在所有活跃平台（`v1_21_1` / `v26_1_2` / `v26_2`）+ `common` 同步定义；`builder.define*().get()` 扁平内联求值；`CONFIG` 是 `public static final`，**不得写 `CONFIG != null` 守卫**。服务端权威项（如 `bulkOpenCount`，0=无上限）勿在客户端自行解释。

- 审查点：是否新增/修改配置项但漏改某平台？是否误加了 null 守卫？是否同步更新了 `docs/CONFIGURATION.md`？

### 4.6 TACZ 软依赖守卫 🟡
`v1_21_1` 的 TACZ（永恒枪械工坊：零）为 `compileOnly` 软依赖，运行时经 `ModList.isLoaded("tacz")` 检测，无 TACZ 环境须**静默降级**。

- 审查点：TACZ 相关代码是否都包在 `isLoaded("tacz")` 判断内？未装 TACZ 时功能是否干净降级（不报错、不紫黑块）？

### 4.7 并发与权威 🔴/🟡
开箱链路是**服务端权威 RNG** + 异步线程池 + 主线程 finalize；`PacketCsgoProgress` 用 `ConcurrentHashMap` 与每 100 tick 清理的 `OPEN_BLOCKED_UNTIL_TICK`；批量开箱 `BULK_COMPUTE_POOL` 异步计算、`finalizeBulkOpen` 主线程复核。

- 审查点：
  - 是否信任了客户端发来的数值（如批量开箱 `actualK`、钥匙数量）？→ **Blocker**，必须服务端权威 + 复核。
  - 异步计算窗口内是否有库存/结果数量变化导致 `subList` 越界（历史缺陷，已用 `Math.min(actualK, results.size())` 截断）？
  - `ConcurrentHashMap` 的清理/迭代是否有竞态？
  - 注册时机：动态 item 是否用 `RegisterEvent` deferred supplier（**不要**用 `FMLCommonSetupEvent.enqueueWork`，registry 已 freeze）？
- 安全延伸：服务端绝不信任客户端包；权限命令（如 `/csbox`）是否做 2 级权限检查。

### 4.8 GUI / 动画渲染状态 🟡
legacy 门面内部强制 `SRC_ALPHA` blend；decoupled 走 `RenderPipelines`（自带 blend 状态，`setBlendNormal`/`flush` 空操作）。26.2 无 `Options.hideGui`，HUD 用 `HudVisibility` 包装。

- 审查点：
  - 是否有 `RenderSystem.setShaderColor` / `enableBlend` 等状态未复位导致**渲染泄漏**（关闭屏幕后异常着色）？
  - 26.2 的 HUD 显隐是否走 `HudVisibility.show()/toggle()/isHidden()`，而非直接调用已移除的 `Options.hideGui`？
  - 帧首三连（`setShaderColor(1,1,1,1)`+`enableBlend`+`defaultBlendFunc`）是否已统一收口到门面（不应再散落）？

### 4.9 forge_26_1_2 实验模块边界 🟡
`forge_26_1_2` 是同步开发模块（MinecraftForge 26.1.2，随 1.0.6 发行纳入 git、自 1.0.7 线起与 `v26_1_2` 特性同步；不在 CI、不入三平台正式发行矩阵），由 `scripts/port-forge-2612.py` 机械转换 + 手工适配；编译/门禁状态由 `scripts/test-forge-2612.sh` 守护，审查时不再忽略其编译状态。

- 审查点：是否误把 `forge_26_1_2` 当正式平台发布、或要求它进入 CI 矩阵 / NeoForge 镜像纪律？该模块随 1.0.6 发行纳入 git、自 1.0.7 线起与 `v26_1_2` 特性同步（经 `scripts/port-forge-2612.py` + 手工适配，见 AGENTS.md「forge_26_1_2 同步」），不入三平台正式发行矩阵；编译状态由 `scripts/test-forge-2612.sh` 门禁守护。

---

## 5. 通用代码质量审查清单

在 §4 专属项之外，按五维审视（不限于风格）：

### 正确性
- 边界条件（空集合 / 越界 / null / 除零 / 负数）是否处理？
- 逻辑分支是否覆盖所有 `Optional`、枚举、状态机路径？
- JSON schema（`BoxDefinition` / 动态 box item）变更是否同步更新 `CODEC` + `StreamCodec` + GUI，并有 schema 校验测试（`BoxJsonSchemaValidatorTest`）覆盖？

### 安全
- 客户端包是否做了输入校验与权限检查（勿信任客户端）？
- 教程下载（`writeTutorialIfMissing` / `refreshTutorials`）是否受 `^_tutorial_v.*\.md$` 白名单约束、不会误删用户数据？注意：旧版教程**无回收站直接删除**，审查相关改动须格外谨慎数据丢失。
- 命令参数、NBT/DataComponent 解析是否有注入/崩溃风险（畸形 JSON 不应导致服务端崩溃）？
- RNG 是否服务端权威，客户端无法操纵开箱结果？

### 可维护性
- 命名是否清晰、无歧义？方法是否过长（>60 行考虑拆分）？
- 是否消除了重复（抽公共助手 / 走 `AnimRenderOps` 门面）？
- 公共 API / 平台接口是否补 Javadoc？
- `Record` 用于不可变数据结构、`Codec`/`StreamCodec` 用于序列化（见 `CONTRIBUTING.md` 代码规范）？

### 性能
- 是否存在 N+1（如逐物品重复解析 JSON）？是否复用 `BoxJsonLoader` 的 SHA-256 内容指纹缓存？
- 启动/加载是否阻塞主线程（教程下载已改后台线程，勿回退）？
- 渲染热路径是否避免不必要分配 / 状态切换？

### 测试
- 改动核心逻辑是否补/改了 `common` 的 JUnit 5 测试（`BoxJsonSchemaValidatorTest` 等）？
- 平台层变更是否有 `v26_1_2` 的 `PlatformSmokeTest` 级别覆盖？
- 跨版本/运行时行为是否在 PR 描述中说明手动测试（参考 `docs/RUNTIME-UI-TESTING.md`、`TESTING.md`）？

---

## 6. 审查流程

### 6.1 作者提交前自查（Author Checklist）
- [ ] 改动聚焦单一功能/修复，PR 描述清晰（影响平台、测试方式）。
- [ ] `common/` 无 `net.minecraft.*` / `net.neoforged.*` 引用（§4.1）。
- [ ] 跨平台改动已在 **所有活跃平台**同步（纯新增走 `mirror.sh`，适配差异走定点合入，未整文件覆盖 `v26_2`）（§4.2）。
- [ ] 若动版本号：四处同步 + `mods.toml` 保留 `${mod_version}`（§4.3）。
- [ ] 若动 `AnimRenderOps`：三平台签名一致、era 头正确（§4.4）。
- [ ] 配置项三平台 + common 同步，`CONFIG` 无 null 守卫（§4.5）。
- [ ] TACZ 代码包在 `isLoaded("tacz")` 内（§4.6）。
- [ ] 不信任客户端数值，服务端权威 + 复核（§4.7）。
- [ ] 渲染状态无泄漏，26.2 HUD 走 `HudVisibility`（§4.8）。
- [ ] 相关文档（`CHANGELOG` / `CONFIGURATION` / `README` / `docs/*`）已更新。
- [ ] 提交信息遵循 Conventional Commits（`feat:`/`fix:`/`docs:`/`refactor:`）。

### 6.2 提交 PR
使用 `.github/PULL_REQUEST_TEMPLATE.md`，在描述中勾选上述自查项、填写「改动说明 / 影响平台 / 测试说明 / 截图或日志」。PR 指向 `main` 或活跃开发分支。

### 6.3 审查进行中（Reviewer Steps）
1. 先读 PR 描述，明确改动意图与范围。
2. 对照 §4 专属清单逐条核对（这是 CS2-Box 的「必查项」）。
3. 读变更 diff，关注 §5 五维。
4. **本地验证（必要时）**：
   - `./gradlew :common:test` —— common 单元 + 架构约束 + 版本/漂移检查。
   - `./gradlew :<module>:clean compileJava -Pactive_versions=<v>` —— 跨平台改动**务必 clean 编译**确认（防止增量缓存假象）。
   - `./gradlew gameTestServer` —— 涉及方块/物品交互的行为。
5. 意图不清时**先问**而非直接判错（见 §8 示例）。
6. 用 §3 优先级标注每条意见，给出 why + 建议。一轮给完整反馈，不拖轮次 drip-feed。

### 6.4 合并门禁（必须全绿）
**CI（`.github/workflows/build.yml`）**：
- `build` job：3 平台 `compileJava` + `jar`（v26_1_2 额外跑 `PlatformSmokeTest`）+ TACZ 下载（仅 v1_21_1）。
- `common-test` job：`check-version.sh` + `check-animops-drift.sh` + `checkCommonArchitecture` + `:common:test`。

**人工发布门**（发布前，见 `docs/RELEASE.md` §3）：3 平台 clean 编译 + common 单测 + 运行时回归（开箱动画/批量开箱/磨损耐久/成就/命令/动态 item/GUI 像素断言/终端机四区与交互）。

> 合并硬条件：**CI 全绿 + 无未解决 🔴 Blocker + 至少 1 名 Reviewer 批准**。

### 6.5 合并与归档
-  squash 或 rebase 合并到目标分支（保持线性历史）。
- 若涉及版本号变更，确认 `CHANGELOG.md` 已落「未发布」或对应版本段。
- 跨平台/架构类 PR 合并后，建议跑一次 §6.4 人工门确认无回归。

---

## 7. 自动化门禁一览（可直接引用）

| 检查 | 命令 / 文件 | 守护的约束 | 接入 CI |
|---|---|---|---|
| 架构约束 | `./gradlew :common:checkCommonArchitecture` | §4.1 CONSTRAINT-001 | `common-test` |
| 版本同步 | `scripts/check-version.sh` | §4.3 | `common-test` |
| AnimRenderOps 漂移 | `scripts/check-animops-drift.sh` | §4.4 | `common-test` |
| common 单测 | `./gradlew :common:test` | §5 测试/正确性 | `common-test` + `build`(v26_1_2 smoke) |
| 3 平台编译+打包 | `./gradlew :<m>:clean compileJava jar -Pactive_versions=<v>` | §4.2 | `build` |
| 集成测试 | `./gradlew gameTestServer` | §5 正确性 | 暂未接入 CI（建议补 `gametest.yml`） |

> 建议（非强制）：把以上检查中的关键项设为 GitHub **Required status checks**，并新增 `gametest.yml` 把 GameTest 纳入 CI，让 §6.4 门禁更少依赖人工。

---

## 8. 审查评论格式（含 CS2-Box 示例）

```
🔴 **架构约束：common 引用了 MC 类**
文件 common/box/BoxRegistry.java:42
import net.minecraft.world.item.Item;  ← 违反 CONSTRAINT-001。

**Why:** common 编译环境无 MC classpath，CI 的 checkCommonArchitecture 会失败，且破坏「平台→common」依赖方向。

**Suggestion:** 把 Item 类型下沉到平台模块的注册代码；common 侧仅保留平台接口（参考 common/platform/）。
```

```
🟡 **镜像纪律：v26_2 漏改适配点**
文件 v26_2/.../gui/CsboxScreen.java
此处直接复用了 v26_1_2 的 spawnAtLocation 旧签名，但 26.2 的 API 返回 Optional。

**Why:** 26.2 是 decoupled 适配平台，整文件复制会触发编译错误/运行时 NPE。
**Suggestion:** 用定点合入把 v26_1_2 的改动手工适配到 v26_2（lookup()/Optional 处理），不要整文件覆盖。
```

```
💭 **Nit：方法名可更表意**
PacketCsgoProgress.java:88 `tickOpenBlockMap` —— 考虑 `cleanupExpiredOpenBlocks` 更贴合「每 100 tick 清理」语义。
```

```
❓ **澄清（先问再判）**
BoxDefaults.java 的 refreshTutorials 按白名单直接删除旧版教程且无回收站——这次改动是否覆盖用户已自定义教程的场景？确认不会误删，再决定优先级。
```

---

## 9. 常见反模式速查（Review 时一票告警）

| 反模式 | 为何危险 | 对应章节 |
|---|---|---|
| `common/` import `net.minecraft.*` | 编译失败 + 破坏分层 | §4.1 |
| 用 `v26_1_2` 整文件覆盖 `v26_2` | 破坏 26.2 适配 | §4.2 |
| 手改 `mods.toml` 版本字符串 | 版本过期不一致 | §4.3 |
| 改 `AnimRenderOps` 只改一个平台 | 漂移检查失败、运行时缺 op | §4.4 |
| `CONFIG != null` 守卫 | 违背 `public static final` 设计 | §4.5 |
| 信任客户端发来的开箱数量 | 作弊 / 越界崩溃 | §4.7 |
| 渲染后不复位 `RenderSystem` 状态 | 屏幕关闭后异常着色泄漏 | §4.8 |
| 教程刷新无白名单/无回收站 | 误删用户数据 | §4.6/§5 安全 |
| 阻塞主线程做下载/解析 | 启动卡顿 | §5 性能 |

---

## 10. 参考文件
- `AGENTS.md` —— 架构约束、镜像纪律、关键文件、测试矩阵（权威依据）。
- `CONTRIBUTING.md` —— 代码规范、分支/PR 约定、配置系统、宝箱 JSON。
- `docs/RELEASE.md` —— 版本四同步、质量门。
- `docs/TESTING.md` / `docs/RUNTIME-UI-TESTING.md` —— 测试与运行时回归清单。
- `scripts/check-version.sh` / `scripts/check-animops-drift.sh` / `scripts/mirror.sh` —— 自动化守护脚本。
- `.github/workflows/build.yml` —— CI 矩阵与 `common-test` 门禁。
- `.github/PULL_REQUEST_TEMPLATE.md` —— PR 提交时落地的自查模板。
