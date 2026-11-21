# forge_1_20_1 (MinecraftForge 1.20.1) 测试流程

> 适用范围：`forge_1_20_1` 平台模块——2.0.0 线功能向 MC 1.20.1 / MinecraftForge
> 47.x 的回移模块（2026-08-18 首建，计划见
> `.opencode/plans/2026-08-18-forge-1-20-1-port.md`）。
> **不在 CI 矩阵**，不参与 3 平台镜像纪律与 AnimRenderOps 漂移门禁
> （与 `forge_26_1_2` / `forge_26_2` 同策略）；自 **2.0.0 起纳入正式发布**，与其余
> 五平台同步发行。

## 1. 模块定位

forge_1_20_1 = **MinecraftForge 1.20.1-47.4.22**（Java 17，ForgeGradle 7.x），
基准模块为 `forge_26_1_2`，复用 `common/` 全部纯 Java 逻辑。与其他平台的
已知差异（设计决策，非缺陷）：

| 差异点 | 说明 |
|---|---|
| 无 PIP 渲染系统 | 1.20.1 无 `RegisterPictureInPictureRendererEvent`；`AnimRenderOps.renderItem3D` 降级为 2D `renderItem`，`supports3D()` 返回 `false` |
| DataComponent → NBT | 箱子/终端机数据存储回退为 ItemStack NBT（`getOrCreateTag()` / `setTag()`） |
| Networking | `SimpleChannel` + 14 个 class 风格 packet（非 `CustomPacketPayload` record） |
| Capability | `CapabilityManager.get(CapabilityToken)` + `LazyOptional` + `AttachCapabilitiesEvent` |
| JEI | 已实现（`cfd750f`，JEI 15.x 开箱概率查询；`compileOnly` + `-PwithJei` 可选 `runtimeOnly`；`mods.toml` 可选依赖 `[15.20,)`，side=CLIENT） |
| 序列化 | `StreamCodec`/`RegistryFriendlyByteBuf` → `FriendlyByteBuf` 手动读写 + NBT |

## 2. 前置条件

- JDK 17 toolchain（`forge_1_20_1/build.gradle` 已锁定，Gradle 自动下载/检测）；
- 仓库 Gradle wrapper 9.5.1（无需全局 Gradle）；
- **每次 Gradle 调用必须带 `-Pactive_versions=forge-1.20.1`**（每次只构建一个
  MC 版本是仓库历史限制；默认 `active_versions` 是 NeoForge 26.1.2，不带参数会
  构建错模块）；
- 首次构建需联网拉取 ForgeGradle/Forge userdev 与 official mappings。

## 3. 自动化验证（L0-L3）

暂无专属门禁脚本（`test-forge-1201.sh` 未建，模块成熟后按
`test-forge-2612.sh` 模式补齐），手动执行等价命令：

| 阶段 | 命令 | 通过条件 |
|---|---|---|
| L0 clean 编译 | `./gradlew :forge_1_20_1:clean :forge_1_20_1:compileJava -Pactive_versions=forge-1.20.1` | exit 0（含 common 架构检查） |
| L1 jar 产物 | `./gradlew :forge_1_20_1:renameJar -Pactive_versions=forge-1.20.1` | 产出 `csgobox-forge-1.20.1-<mod_version>-srg.jar` 非空，`META-INF/mods.toml` 版本与 `mod_version` 一致，且字节码为 SRG 名（见 §3.1） |
| L2 版本一致性 | `./scripts/check-version.sh` | 四同步通过（mods.toml 走模板变量注入） |
| L3 冒烟测试 | `./gradlew :forge_1_20_1:test -Pactive_versions=forge-1.20.1` | `PlatformSmokeTest`（入口类可加载，不初始化 MC 运行时）通过 |

### 3.1 生产 jar SRG 校验（必做）

Forge 1.20.1 生产运行时是 **SRG 命名域**；`jar` 直出产物（official/MCP 名）会在
第一个改名过的 Minecraft 成员调用处崩溃——2.0.0-beta 的
`NoSuchMethodError: CriteriaTriggers.register` 就是这一根因（dev 环境是 official
名所以复现不了）。发布前用 `renameJar` 产物做一次字节码抽查：

```bash
# CriteriaTriggers.register 在 SRG 域应显示为 m_10595_（Forge AT 转 public）
"$JAVA_HOME/bin/javap" -classpath "forge_1_20_1/build/libs/csgobox-forge-1.20.1-<mod_version>-srg.jar" \
  -c -p com.reclizer.csgobox.forge_1_20_1.CsgoBox | grep -A1 CriteriaTriggers
# 期望：invokestatic ... CriteriaTriggers.m_10595_...
```

普通 `jar` 产物（无 `-srg`）**禁止作为发布物**。

## 4. 运行时 E2E 清单（L4，人工）

```bash
# 先部署全部第三方模组的 official-mapped 副本（见下方"TACZ 运行时"一节）
./scripts/remap-1201.sh
./gradlew :forge_1_20_1:runClient -Pactive_versions=forge-1.20.1
```

首启自动生成 `forge_1_20_1/run/`。创造模式世界内逐项验证：

1. **物品注册**：`/give @p csgobox:csgo_box`、`csgo_key0`~`csgo_key3`、
   `terminal`、`armory_point`、`armory_recycler` 全部可给予；
2. **固定箱子物品 + `/csbox give`（v2.0.1 注册表解耦回归）**：
   - `/give @p csgobox:gun_crate` 等 5 个默认箱子 id 仍可给予，且 tooltip 显示箱子名与内容；
   - `/csbox give @p csgobox:gun_crate 3` 生效（权限 2）；自建箱子
     （`config/csbox/my_box.json`）用 `/csbox give @p csgobox:my_box` 发放后能正常开箱，
     **且 `/give @p csgobox:my_box` 不存在**（预期：注册表已不随配置变化）；
   - `type: "terminal"` 的箱子用 `/csbox give` 发放的是 `terminal` 物品（不可堆叠）并打开终端屏；
   - 联机回归：**故意让客户端与服务端的 `config/csbox/` 不一致**（服务端多一个箱子 JSON），
     客户端仍能正常进服（不再出现 "Failed to synchronize registry data from server"），
     且服务端新增的箱子在客户端可见（`PacketSyncBoxDefinitions` 下发）；
   - 两边都是默认配置时，`/csbox info` 的箱子清单一致；
2. **开箱**：单开（右键）滚动条动画 → 出货屏；Shift+右键批量总览 → 点「开启」直接开箱 → 批量结果（无二次确认屏）；
3. **数据持久化**：开箱后物品 NBT 正确读写（1.20.1 走 tag，非 DataComponent）；
4. **终端机**：打开 → 启动屏 → 谈判（5 轮报价 / 接受长按 / 拒绝 / 上限下拉 /
   倒计时 / 超时自毁 / 会话锁续谈）——**物品预览为 2D**（无 PIP 属预期）；
5. **武库拆解台**：方块放置、菜单、回收产出、`ArmoryRecycleEvent` 取消语义；
6. **村民**：`arms_dealer` 职业与交易（worldgen JSON 为 1.19+ 单数布局在 1.20.1 直读；结构模板 NBT 与战利品表走本平台复数副本 `structures/` / `loot_tables/`，2026-11-06 修复——此前 NBT 不被读取导致小屋模板缺失；NBT 无 DataVersion 按名称解析，L4 需实机确认小屋正常生成）；
7. **命令**：`/csbox info` / `/csbox nbt hand` / `/csbox reload`；
8. **配置**：`csbox-common.toml` 生成且默认值与 `CsboxConfigDefaults` 一致；
9. **JEI（可选，需先装 JEI 15.x 或用 `-PwithJei` 进 dev）**：「开箱概率」分类出现在 JEI 目录；每个箱子一条配方（钥匙输入槽 / 箱子输出槽 / 物品池 4×5 网格）；左侧概率文本（掉率、各稀有度权重百分比、实体掉率）与 `BoxOdds` 口径一致；物品 tooltip 含 `[等级] 权重 x/y · 单件 z%`；`/csbox reload` 后配方即时刷新。

### 4.1 TACZ 运行时（重要）

ForgeGradle 7（mavenizer）的 dev 客户端以 **official-mapped** 名称运行
（如 `LivingEntity.tick()`），而 TACZ 1.20.1 **生产 jar 是 SRG-mapped**（mixin
refmap 目标为 `LivingEntity.m_8119_`），且 FG7 **没有 `fg.deobf`** 对依赖 jar
做重映射——直接把生产 TACZ jar 丢进 `run/mods` 会在启动时崩溃：

```
Critical injection failure ... could not find any targets matching
'Lnet/minecraft/world/entity/LivingEntity;m_8119_()V'
```

因此 dev 环境测试 TACZ（及一切第三方 1.20.1 模组 jar）前，先跑统一管线：

```bash
./scripts/remap-1201.sh
```

该脚本由 `scripts/remap-1201-manifest.txt` 清单驱动（TACZ / Create /
SlashBlade / 森罗家族 / architectury / cloth-config / curios / REI 共 17 项），
幂等可重跑：产物带工具指纹侧车（`*.stamp`，含 `TaczDeobf.java` /
`remap-tacz-1201.py` / tsrg 的 sha256），任一工具或映射表变了下次运行自动
全量重建；产物新于源 jar 也才允许跳过。TACZ 的处理流程（全部 jar 通用）：

1. `scripts/TaczDeobf.java`（ASM + srgutils）把 jar 整包从 SRG 重映射为
   official：**方法/字段引用 + 声明全部**（含 mixin `@Shadow` 成员与覆写方法
   如 `createBlockStateDefinition`，record 访问器的 f_ 型方法 id 亦覆盖）。
   SpecialSource 只能部分重映射这类 jar
   （会漏 `BlockState.m_61124_`、继承字段 `f_49792_`、覆写声明 `m_7926_`），
   故不用它。另外还处理 **lambda / 方法引用**：`invokedynamic` 调用点的
   SAM 方法名（如 `Pack$ResourcesSupplier.open` 的 `m_247679_`）与 bootstrap
   句柄里的方法/字段引用必须同步改，否则启动时抛
   `AbstractMethodError: ... does not define or inherit an implementation of ...`
   （lambda 类没实现已改名后的接口方法）；
2. 清单声明的 jarjar 内嵌包（TACZ 的 `simplebedrockmodel`、Create 的
   Ponder/flywheel/Registrate）同样重映射并回填；
3. `scripts/remap-tacz-1201.py` 把全部 jar 的 mixin refmap 目标从 SRG 改写为
   official 名称；
4. 每个产物过 `scripts/check-official-remap.py` 门（常量池可执行 SRG 引用 /
   refmap 残留 → 拒绝部署）后才 diff-cp 部署进 `run/mods`。

产物：各源 jar 同目录 `<名>-official.jar`，并部署到
`forge_1_20_1/run/mods/<清单部署名>`（run/mods 被 gitignore，不提交）。
之后 `runClient` 即可正常加载 TACZ。单独审计现状（不重建）：

```bash
python scripts/check-official-remap.py          # 全量审计 run/mods
python scripts/check-official-remap.py --deps   # 附带缺前置依赖告警
```

## 5. 已知待办

- [x] JEI 集成（`cfd750f`，JEI 15.x 开箱概率查询——`jei/` 4 文件 + `BoxJeiSync` 桥 + `mods.toml` 可选依赖）
- [ ] `scripts/test-forge-1201.sh` 门禁脚本（L0-L3 自动化）
- [ ] 运行时 L4 回归报告（`docs/TEST-REPORT-FORGE-1201-*.md`，含 JEI 分类 E2E 抽查，见第 4 节）

### 5.1 dev 客户端启动崩溃：Epic Fight 物品扫描 × 第三方背包（2026-11-20 定位）

**症状**：`runClient` 在加载屏阶段崩溃，`crash-reports/crash-*-client.txt` 顶部为
`Description: Rendering overlay` +
`java.lang.IllegalStateException: Cannot get config value before config is loaded.`
（该报错在 Forge 源码里由 `!FMLEnvironment.production` 守卫，**只在 dev 抛出**，生产环境仅打警告，玩家侧无影响）。
首次出现 2026-11-17（Epic Fight 加入 dev 集当天），非本模组问题。

**根因（一类问题，不止一个模组）**：Forge 1.20.1 的配置装载顺序是 CLIENT → COMMON → SERVER。
Epic Fight 的 `ClientConfig.onLoad` 在 CLIENT 配置事件里调用 `ItemsPreferenceScreen.resetItems()`，
遍历全部物品查询 capability；**任何在 item capability 路径里读取非 CLIENT 配置的模组都会踩雷**。
已确认的踩雷者：

- **Traveler's Backpack 9.1.58**：`TravelersBackpack.enableCurios()` 读 SERVER 配置 →
  已由管线补丁 `scripts/patch-tb-capability-guard-1201.py` 修掉（`remap-1201.sh` 在
  TB 重新 remap 时自动回补，产物含 `curiosEnabledGuarded` 标记）。
- **Sophisticated Backpacks 3.26.20**：`BackpackItem.getNumberOfSlots()` →
  `BackpackWrapper.cacheSlotNumbers()` 读配置 → 清空偏好列表复测时仍崩
  （`crash-2026-11-20_16.48.43-client.txt`）。**即单修 TB 不够**。

**修复（保留全部模组，推荐）**：给 `run/config/epicfight-client.toml` 的两个偏好列表任一填上
合法物品 id，`resetItems()` 就不再在装载期被调用（EF 字节码：两表都为空才触发），
从源头绕开整类问题：

```toml
combat_preferred_items = ["minecraft:iron_sword", "minecraft:diamond_sword", "minecraft:iron_axe"]
mining_preferred_items = ["minecraft:iron_pickaxe", "minecraft:diamond_pickaxe", "minecraft:iron_shovel"]
```

注意 `run/` 被 gitignore，换机器/重建运行目录后需重新填；进游戏后在 EF 物品偏好界面点
「重置」可正常生成完整列表（那时配置已全部装载，不会崩）。

TB 补丁只在 TB 重新 remap 时应用（stamp 缓存命中会跳过，2026-11-20 实测首次落地时
`run/mods` 里仍是未打补丁的 jar）。强制重建：

```bash
rm -f local-repo/com/tiviacz/travelersbackpack/9.1.58/travelersbackpack-*-official.jar*
scripts/remap-1201.sh
```

管线输出应出现 `patched ... TravelersBackpackItem$2.class ...`；产物中该 class 含
`curiosEnabledGuarded` 标记。**补丁只解决 TB 一家**，SB 等同类模组仍会崩，
所以偏好列表种子仍是 dev 环境的必需项。

**替代方案**：把 `travelersbackpack-forge-1.20.1-9.1.58-official.jar` 移出 `run/mods`
（会失去 `backpack_preview_test` 箱子的预览联测环境）；或对每个踩雷模组逐一打补丁
（不推荐——踩雷面随 dev 模组集变化）。

## 6. 迁移记录

- 计划：`.opencode/plans/2026-08-18-forge-1-20-1-port.md`（7 Phase，约 89 文件）
- Phase 1 基建（properties / settings / mods.toml / pack.mcmeta / items JSON）
- Phase 2 registry / config / entry / box（NBT 存储）
- Phase 3 capability / events / command
- Phase 4 networking（SimpleChannel 14 packet）+ terminal 存储
- Phase 5 GUI / 渲染（GuiGraphics 直调，无 PIP）
