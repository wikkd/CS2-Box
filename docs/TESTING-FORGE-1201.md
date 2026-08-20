# forge_1_20_1 (MinecraftForge 1.20.1) 测试流程

> 适用范围：`forge_1_20_1` 平台模块——1.0.7 线功能向 MC 1.20.1 / MinecraftForge
> 47.x 的回移实验模块（2026-08-18 首建，计划见
> `.opencode/plans/2026-08-18-forge-1-20-1-port.md`）。
> **不在 CI 矩阵**，不参与 3 平台镜像纪律与 AnimRenderOps 漂移门禁
> （与 `forge_26_1_2` / `forge_26_2` 同策略），不入正式发行矩阵。

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
| JEI | 未实现（`build.gradle` 已声明 compileOnly 依赖，与其他 forge 模块现状一致） |
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
| L1 jar 产物 | `./gradlew :forge_1_20_1:jar -Pactive_versions=forge-1.20.1` | 产出 `csgobox-forge-1.20.1-<mod_version>.jar` 非空，`META-INF/mods.toml` 版本与 `mod_version` 一致 |
| L2 版本一致性 | `./scripts/check-version.sh` | 四同步通过（mods.toml 走模板变量注入） |
| L3 冒烟测试 | `./gradlew :forge_1_20_1:test -Pactive_versions=forge-1.20.1` | `PlatformSmokeTest`（入口类可加载，不初始化 MC 运行时）通过 |

## 4. 运行时 E2E 清单（L4，人工）

```bash
./gradlew :forge_1_20_1:runClient -Pactive_versions=forge-1.20.1
```

首启自动生成 `forge_1_20_1/run/`。创造模式世界内逐项验证：

1. **物品注册**：`/give @p csgobox:csgo_box`、`csgo_key0`~`csgo_key3`、
   `terminal`、`armory_point`、`armory_recycler` 全部可给予；
2. **开箱**：单开（右键）滚动条动画 → 出货屏；Shift+右键批量总览 → 确认屏 → 批量结果；
3. **数据持久化**：开箱后物品 NBT 正确读写（1.20.1 走 tag，非 DataComponent）；
4. **终端机**：打开 → 启动屏 → 谈判（5 轮报价 / 接受长按 / 拒绝 / 上限下拉 /
   倒计时 / 超时自毁 / 会话锁续谈）——**物品预览为 2D**（无 PIP 属预期）；
5. **武库拆解台**：方块放置、菜单、回收产出、`ArmoryRecycleEvent` 取消语义；
6. **村民**：`arms_dealer` 职业与交易（结构生成数据在 common 资源内共享）；
7. **命令**：`/csbox info` / `/csbox nbt hand` / `/csbox reload`；
8. **配置**：`csbox-common.toml` 生成且默认值与 `CsboxConfigDefaults` 一致。

## 5. 已知待办

- [ ] JEI 集成（JEI 15.x，`build.gradle` 依赖已声明）
- [ ] `scripts/test-forge-1201.sh` 门禁脚本（L0-L3 自动化）
- [ ] 运行时 L4 回归报告（`docs/TEST-REPORT-FORGE-1201-*.md`）

## 6. 迁移记录

- 计划：`.opencode/plans/2026-08-18-forge-1-20-1-port.md`（7 Phase，约 89 文件）
- Phase 1 基建（properties / settings / mods.toml / pack.mcmeta / items JSON）
- Phase 2 registry / config / entry / box（NBT 存储）
- Phase 3 capability / events / command
- Phase 4 networking（SimpleChannel 14 packet）+ terminal 存储
- Phase 5 GUI / 渲染（GuiGraphics 直调，无 PIP）
