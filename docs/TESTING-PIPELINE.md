# 测试流程分层（Testing Pipeline）

2026-11-07 起效的发布前测试分层。原则：越便宜、越自动的层跑得越频繁；
越贵、越人工的层放在发布前收口，长尾交给发布渠道 soak。

```
┌─ L1 静态/单测 ──── 每次 push ─── CI 自动，分钟级
├─ L2 平台编译 ──── 每次 push ─── CI 自动
├─ L3 GameTest ──── 每次 push ─── CI 自动，真实服务端内跑
├─ L4 运行时回归 ── 发布前 ──── 人工清单（docs/RELEASE.md）
└─ L5 渠道 soak ─── 每次发布 ─── beta 渠道 + 社区
```

## L1 — 静态检查与单元测试（`build.yml` → `common-test` job）

全部自动，命中即红：

| 检查 | 命令 | 防什么 |
|---|---|---|
| common 单测（JUnit 5） | `./gradlew :common:test` | 纯逻辑：概率/档位/保底/约束/解析 |
| 版本四同步 | `scripts/check-version.sh` | gradle.properties/CHANGELOG/README/mods.toml 漂移 |
| AnimRenderOps 漂移 | `scripts/check-animops-drift.sh` | 渲染门面三平台签名不一致 |
| 镜像漂移门禁 | `scripts/check-platform-drift.py --baseline …` | 26 系镜像家族越界改动 |
| 架构约束 | `:common:checkCommonArchitecture` | CONSTRAINT-001（common 禁 import MC） |
| compat-pack id 核查 | `scripts/check-ids.py` | 联动包 JSON 语法/结构/id 合法性 |

本地等价入口：`./gradlew :common:test` + 上述脚本逐个跑。

## L2 — 平台编译矩阵（`build.yml` → `build` job）

八平台 `compileJava` + `PlatformSmokeTest` + `jar`（矩阵见 workflow）。
本地全量验证用 `local-tmp/grade-build.sh` 的串行链（构建互斥探测规则见
AGENTS.md，单次 Gradle 调用只能构建一个 MC 版本）。

## L3 — GameTest 集成测试（`gametest.yml`）

在真实专用服务器内（flat world、无 GPU、headless）跑服务端权威链路，
跑完 `System.exit(失败用例数)`，CI 拿退出码当门禁。

**5 平台矩阵**（2026-11-07 扩展并实跑全绿）：同一套 6 用例（config→registry 加载、
单开 E2E、缺钥匙拒开、first_open 保底、max_per_player、terminal 拒开）在
五个代表平台上跑——每个平台的开箱执行器、GRADE 印记、mock 玩家 API、模板
时代都不一样，平台移植本身就是回归面（Inventory.add 消耗语义 bug 只在
packet 族四平台修、26.x OpenService 家族本来就不经同一代码）。26.x 平台上
vanilla 数据包自带 1 个 `always_pass` 示例用例，所以总数显示 7——本模组
贡献 6。

| 平台 | 任务 | 注册机制 | 模板目录 / DataVersion | mock 玩家 | 实跑 |
|---|---|---|---|---|---|
| v1_21_1 | `gameTestServer`（别名） | `@GameTestHolder` 注解扫描 | `structure/` 3955 | `makeMockPlayer(GameType)` | ✅ 6/6 |
| v26_1_2 | `gameTestServer`（别名） | `RegisterGameTestsEvent` 事件 | `structure/` 4790 | `makeMockPlayer(GameType)` | ✅ 6/6+vanilla |
| v26_2 | `gameTestServer`（别名） | `RegisterGameTestsEvent` 事件 | `structure/` 4903 | `makeMockPlayer(GameType)` | ✅ 6/6+vanilla |
| forge_1_18_2 | `gameTestServer`（别名） | `@GameTestHolder` 注解扫描 | `structures/` 2975 | `makeMockPlayer()`（无参） | ✅ 6/6 |
| forge_1_20_1 | `gameTestServer`（别名） | `@GameTestHolder` 注解扫描 | `structures/` 3465 | `makeMockSurvivalPlayer()`（无参） | ✅ 6/6 |
| fabric_1_21_1 | `runGametest`（别名 `gameTestServer`） | `fabric-gametest` entrypoint | 复用 fabric-api `EMPTY_STRUCTURE` | `makeMockPlayer(GameType)` | ⛔ 见下 |

**已知平台限制**：
- `forge_26_1_2` / `forge_26_2` 暂无 GameTest 覆盖——MinecraftForge 26.x 保留了
  `@GameTest`/`@GameTestNamespace` 注解与 `ForgeGameTestHooks.gatherTests()` API，
  但**没有任何东西调用 gatherTests**（FML 不扫描、无注册事件；Forge 26.1.2 的
  `RegistryDataLoader` 无 NeoForge 式 patch，TEST_INSTANCE 是纯 datapack 注册表），
  代码注册的用例进不了运行时。等上游补齐后再接入（`gametest.yml` 注释里有同样记录）。
- `fabric_1_21_1` 套件已就位（`fabric-gametest` entrypoint + `FabricGameTest`
  实现，编译通过），但 dev 专用服务器在 Loom `officialMojangMappings` 半重映射下
  崩于 fabric-resource-loader-v0 的 `server.LanguageMixin`（与 `runServer` 同一
  上游问题，见 `docs/PORTING-fabric.md`「已知 dev 环境限制」）；gametest run 同为
  dev 服务器，世界生成阶段同点崩溃。上游（loom/fabric-api）修复后再加回 CI 矩阵。

- **用例位置**：各平台 `<module>/src/main/java/…/gametest/CsgoBoxGameTests.java`
  （26.x 走 `RegisterGameTestsEvent` + TEST_FUNCTION 注册表 + 每用例独立
  environment；其余走 `@GameTestHolder` 注解；fabric 实现 `FabricGameTest`）。
  用例覆盖：config→registry 加载（含 `_prices.json` 定价联动）、单开 E2E
  （发放/消耗/GRADE 印记/10-tick 防连点）、缺钥匙拒开、first_open 保底强制、
  max_per_player 上限、terminal 被 box executor 拒绝。
- **开箱执行器接缝**：packet 族四平台直接调
  `BoxOpenExecutor.execute(player, box, true, true)`；NeoForge 26.x 族调
  `OpenService.openSingle(player, box)`（2026-11-07 从 packet handler 里
  行为保持地提取的接缝——`handleSingleOpen` 只剩 transport 包装）。
- **CI 守卫**：job 内 grep `@GameTest`/`@GameTestHolder`/
  `RegisterGameTestsEvent`，扫不到用例自动跳过。
- **结构性盲区**：GameTest 只覆盖服务端权威路径；客户端渲染/GUI、
  多人同步、生产 remap（SRG）不在其内，归 L4。

### GameTest 编写须知（2026-11-07 实战踩坑，写用例前必读）

1. **结构模板必须 gzip 压缩**：`.nbt` 模板经
   `NbtIo.readCompressed` 读取，裸 NBT 会在 `tryLoad` 的静默 catch 里变成
   `Missing test structure`（唯一线索是日志一行
   `Couldn't load structure …`）。生成脚本：`local-tmp/gen-empty-template.py`
   （已参数化输出各时代 DataVersion）。
2. **模板目录分时代**：1.21.1/26.x 是单数 `data/csgobox/structure/*.nbt`；
   1.18.2/1.20.1 是复数 `structures/`。经 datapack 资源加载，CI fresh
   checkout 自动可用。文件系统兜底目录 `<run 目录>/gameteststructures/*.snbt`
   （忽略命名空间）仅 IDE 运行存在，不要依赖。
3. **DataVersion 必须与平台匹配**：模板 NBT 里的 DataVersion 只能被
   DataFixer **升**不能**降**——26.1.2 的模板放进 1.21.1 会炸。每平台各带
   自己的模板（2975/3465/3955/4790/4903，见生成脚本）。
4. **系统属性分 loader**：NeoForge 21.1 用 `neoforge.enabledGameTestNamespaces`
   + `neoforge.gameTestServer`（`forge.*` 旧名不认）；NeoForge 26.x 改由
   userdev run 模板自带 `neoforge.enableGameTest=true`（这是
   `RegisterGameTestsEvent` 在 `RegistryDataLoader` 里触发的开关）；Forge
   legacy（1.18.2/1.20.1）用 `forge.enabledGameTestNamespaces`；fabric 用
   `-Dfabric-api.gametest=true`（Loom `runs.gametest` 已配）。
5. **mock 玩家各代不同，都别用 `makeMockServerPlayerInLevel`**：后者走
   `placeNewPlayer`，玩家登录时的 `PacketSyncBoxDefinitions` 广播会被 mock
   的 embedded channel 拒发（"may not be sent to the client"）并中断 mock
   创建。1.21+ 是 `makeMockPlayer(GameType)`；1.20.1 是无参
   `makeMockSurvivalPlayer()`（GameType 参数 1.20.5 才加）；1.18.2 是无参
   `makeMockPlayer()`（creative，但执行器分支只看 `abilities.instabuild`，
   强制 false 即可）。未放置的 mock 足以覆盖开箱链路；只有统计/权限/loot
   解析分支需要真 ServerPlayer（26.x `openSingle` 对这几处加了
   `instanceof ServerPlayer` 守卫，与 v1_21_1 执行器同约定）。
6. **fixture 物品必须定价——且要合并进已有价格表**：v2.0.1 起装载器拒载含
   未定价 id 的箱子。fixture 价格用 `ensureFixturePrices(dir)` **合并**写入
   `_prices.json`（既有键优先、幂等），不要 `writeIfAbsent` 覆盖——forge_1_20_1
   的 dev run 目录里已有 85 条价格但没有 `minecraft:stone`，writeIfAbsent
   直接漏写 → 5 个 fixture 箱全部被静默跳过（装载器的 skip 不落日志，唯一
   线索是「Scanned N; loaded 5, skipped 8」的差值）。
7. **状态隔离靠 fresh uuid + 独立 box id**：OpenBlockGuard /
   BoxConstraintTracker / PityTracker 都按 (player, box) 键控；每用例新 mock
   玩家（随机 uuid）+ 独立箱子 id 即免 reset。10-tick 防连点这类「同玩家
   连续两次」的断言放同一用例内做（需要跨 tick 用 `helper.runAfterDelay`）。
8. **共享 registry/loader 状态的用例必须每用例独立 batch**：vanilla 语义是
   「batch 之间顺序、同 batch 内测试在 server 线程上交错推进」。注解形态用
   `@GameTest(batch = "gt1".."gt6")`；26.x 事件形态给每用例注册独立
   environment（`registerEnvironment` 返回的 Holder 就是 batch 键）。全放
   默认 batch 时 6 个用例并行交错，`loadAll`（内含 LegacyPriceMigration 的
   文件重写）彼此竞态，registry 会出现瞬时缺定义。fixture 部署做双层幂等
   （registry 已有 def 则整个跳过），只有第一个 batch 真正跑 loader。
9. **1.18.2 没有 `helper.assertTrue`**：直接 `throw new
   GameTestAssertException(msg)`（套件里包了个 `check()` 辅助）。
   26.x 的测试函数是 `Consumer<GameTestHelper>`，**不能声明 throws
   IOException**——fixture 写盘要在内部包掉。
10. **本地复跑注意**：fixture 文件落在各平台 run 目录的
    `config/csbox/`（gitignore），`writeIfAbsent` 语义下改 fixture JSON 时
    要手动删旧文件。fabric 的 `runGametest` 用 `-Dfabric-api.gametest=true`
    触发，entrypoint `fabric-gametest` 在 fabric.mod.json（生产 jar 带着它
    是无害的：非 gametest 模式下注册的用例不运行）。

## L4 — 运行时回归（发布前人工）

清单与口径见 `docs/RELEASE.md` 质量门。与自动化互补的两项重点：

- **生产 jar boot 冒烟**：`renameJar`（1.20.1 为 SRG 重映射产物）后装进
  正式 NeoForge/Forge 服务端 boot——历史上 2.0.0-beta
  `CriteriaTriggers.register` 事故即「dev 全绿、生产 remap 炸」，dev 链路
  永远测不到这一层。
- **双客户端连接同步**：专用服 + 2 客户端连入，检查
  `Failed to synchronize registry data from server`（2.0.1 联机事故形态）。
  无法自动化的原因：客户端 GL 表面抓取不在现有自动化工具射程内
  （dev runClient 自动点连接同样受阻）；保留为人工清单项。

## L5 — 发布渠道 soak

成熟模组的共同做法是「发布得更分层」而不是「测得更仔细」：

1. **alpha**（可选）：小圈子（Discord 测试组）拿最新构建，报崩溃/兼容。
2. **beta**：Modrinth/CurseForge 以 **beta 渠道**发布——release-only 用户
   不受影响，敢开 beta 的用户就是免费 soak 测试群。
3. **release**：soak 稳定后转 release，双平台渠道标记保持一致。

流程要求：2.0.x 线每次功能性更新先走 beta 渠道（同一 jar 换渠道标记），
至少过一轮社区反馈再转正；hotfix 可直发 release。
