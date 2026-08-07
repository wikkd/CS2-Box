# TACZ 检视视口集成设计（v1_21_1 专属）

日期：2026-08-07
状态：已实现
范围：仅 `v1_21_1` 平台模块，其余 9 个平台零改动

## 背景

1.0.7 引入"检视"功能：开箱抽出物品界面（`CsLookItemScreen`）的底部工具栏"手套"按钮
（index 1）原先无功能，现作为检视入口。第一阶段只兼容
[TaCZ]永恒枪械工坊：零（非官方 1.21.1 移植，https://github.com/MUKSC/TACZ-1.21.1，
mod id `tacz`）自带的检视动画。

TACZ 枪抽中后默认即进入 3D 展示视口（idle 静止模型）：TACZ 自身的 GUI 物品渲染
（`GunItemRendererWrapper.renderByItem` 的 `transformType == GUI` 分支）只画平面槽位
贴图，走原版 item renderer 只会得到 2D 图标，因此必须绕过它、自驱 TACZ 渲染器。
手套按钮语义为"播放/重播检视"：视口已激活时触发动画与音效，自动进入失败时兜底
完整进入视口。关闭屏幕时清理音效与状态机。

已确认的产品决策：

- **仅 1.21.1**：TACZ 只存在于该版本，不做跨平台镜像。
- **compileOnly + 运行时检测**：`ModList.isLoaded("tacz")` 门槛 + JVM 类懒加载 +
  `catch (Throwable)` 静默降级。无 TACZ 环境功能完全隐形。
- **现屏内嵌切换**：不新开屏幕，复用现有展示区坐标体系
  （`width*37/100`、`height*30/100`、`scale = frameWidth/16`）。
- **交互仅检视音效**：无拖拽旋转（视口固定展示角度）、手套按钮可重复触发检视。
- **降级为无响应/2D**：未装 TACZ / 物品不是 TACZ 枪 / 枪无动画状态机时，展示区保持
  原版 2D 物品图标，点击手套按钮无任何反应（按钮保持显示，tooltip 照常）。
- **直接集成**：不抽象 Provider 层；未来接入其他枪械模组时再评估。

## TACZ API 分析结论（源码核实）

对 TACZ 源码（克隆自 MUKSC/TACZ-1.21.1 HEAD）的关键核实：

1. **官方检视入口不可复用**：`IClientPlayerGunOperator.fromLocalPlayer(player).inspect()`
   最终走到 `LocalPlayerInspect.inspect()`，其中写死只作用于
   `player.getMainHandItem()`；且动画状态机只在第一人称持枪渲染
   （`AnimateGeoItemRenderer.renderFirstPersonInner`）时被推进。抽出的奖励物品在背包里
   而非主手，故无法直接调官方 inspect。
2. **所需 API 全部 public**：`AnimateGeoItemRenderer`（实际实现
   `GunItemRendererWrapper`）的 `getStateMachine / initContext / updateContext /
   triggerAnimation / getModel / getTextureLocation`；`LuaAnimationStateMachine` 的
   `isInitialized / setContext / initialize / update / processContextIfExist / exit`；
   `BedrockModel.render(PoseStack, ItemDisplayContext, RenderType, int, int)`（内部自行
   endBatch）与 `cleanAnimationTransform()`；`SoundPlayManager.playInspectSound(
   LivingEntity, GunDisplayInstance, boolean)` / `stopPlayGunSound()`；
   `TimelessAPI.getGunDisplay(ItemStack)` 返回 `Optional<GunDisplayInstance>`。
3. **inspect 输入常量**：`GunAnimationConstant.INPUT_INSPECT = "inspect"`。
4. **检视音效的空仓变体**：复刻 `LocalPlayerInspect` 的判定——
   `GunData.getBolt() == Bolt.OPEN_BOLT` 时看 `IGun.getCurrentAmmoCount(stack) <= 0`，
   否则看 `!IGun.hasBulletInBarrel(stack)`。

结论：可行路线是"自定义视口 + 公共 API 自驱渲染"——在 GUI 渲染帧里手动推进状态机并
直接调用 BedrockModel 渲染，绕开主手与第一人称限制。

## 实现结构

### 构建接线

- `scripts/download-tacz.sh`：幂等下载 TACZ release
  （tag `neoforge-1.1.8-hotfix-r6`，~57MB）至
  `local-repo/com/tacz/tacz/1.1.8-hotfix-r6/`，并生成最小 pom。
- jar **不入库**（超出 50MB 提交阈值，用户决策）：`.gitignore` 忽略
  `/local-repo/com/tacz/`；CI（`.github/workflows/build.yml`）在 v1_21_1 矩阵编译前
  自动运行下载脚本。
- `v1_21_1/build.gradle`：`compileOnly 'com.tacz:tacz:1.1.8-hotfix-r6'`
  （`LocalPatchedLibraries` 仓库已注册，metadataSources 含 mavenPom()+artifact()）。
  另需 `compileOnly 'com.github.mcmodderanchor:simplebedrockmodel:2.2.1-neoforge+mc1.21.1'`：
  `AnimateGeoItemRenderer` 的父接口 `IFPGeoItemRenderer` 位于 TACZ jarjar 内嵌库中，
  javac 解析类层次需要它；该库由 `scripts/download-tacz.sh` 从 TACZ jar 提取生成
  （仓库惯例 `*.jar` 全局忽略、仅 pom 入库）。

### compat 类：`v1_21_1/.../compat/TaczInspectViewport.java`

所有 TACZ 类引用封闭在该类内，对外仅暴露不含 TACZ 类型的静态方法：

- `isAvailable(ItemStack)`：ModList 门槛 → `stack.getItem() instanceof IGun` →
  `TimelessAPI.getGunDisplay` 非空且带动画状态机。
- `enter(ItemStack, LocalPlayer)`：取 `IClientItemExtensions.of(stack)
  .getCustomRenderer()` 中的 `AnimateGeoItemRenderer`；状态机未初始化则
  `setContext(renderer.initContext(...)) + initialize()`（**不触发 draw**，避免检视
  信号被抽出动画状态吞掉）；`triggerAnimation(stack, INPUT_INSPECT)`；按空仓判定播放
  检视音效。
- `enterDisplay(ItemStack, LocalPlayer)`：仅初始化状态机、不触发 inspect，用于默认
  3D 静止展示（TACZ 原生 GUI 渲染只画槽位贴图，需绕过）。
- `triggerInspect(ItemStack, LocalPlayer)`：视口已激活时重播 inspect 动画与音效。
- `renderViewport(GuiGraphics, ..., centerX, centerY, scale)`：每帧
  `processContextIfExist(ctx -> renderer.updateContext(...)) + stateMachine.update()`
  推进状态机，随后以固定展示角度（不随玩家视角）做 PoseStack 变换
  （translate 到展示区中心 → Y 翻转 → 展示角 → 16*scale → Bedrock 对齐平移 →
  ZP 180° 翻转基岩模型），`model.render(pose, ItemDisplayContext.GUI,
  RenderType.entityCutout(texture), 0xF000F0, NO_OVERLAY)` + `cleanAnimationTransform()`
  + `endBatch()`。
- `exit(ItemStack)`：`stopPlayGunSound()` + 已初始化状态机 `exit()`
  （TACZ 侧 `needReInit` 机制会在下次持枪时自动重新初始化，无残留）。
- 所有公共方法先 `ModList` 门槛再 `try-catch (Throwable)`，异常即 warn 日志 + 静默降级。

### Screen 接线：`CsLookItemScreen`

- 字段 `taczViewportActive` + 一次性守卫 `taczDisplayChecked`。`renderBg` 首帧对 TACZ
  枪自动 `enterDisplay()`（只初始化状态机、不触发 inspect），默认展示 3D 静止模型。
- 手套按钮点击：视口已激活 → `triggerInspect()` 播放检视动画与音效；未激活但可用 →
  `enter()` 兜底进入；不可用 → 不响应。
- `renderBg`：视口激活且 `renderViewport` 返回 true 时替代
  `GuiItemMove.renderItemInInventoryFollowsMouse`；渲染失败当帧回退 2D 并永久关闭视口。
- 工具栏 active 高亮扩展为 `(i == 3 && showInfoPanel) || (i == 1 && taczViewportActive)`。
- `removed()` 清理：视口激活时 `exit()`，防止音效/状态机泄漏到屏幕外。

## 已知限制（接受，不做）

1. **枪包未定义 inspect 转换**：极端情况下状态机收到 inspect 输入后停留在静态模型，
   视口显示静止枪械。不做 Lua 状态机内省来预判（YAGNI，且枪包数据结构非稳定 API）。
2. **玩家手持同一把枪时状态机共享冲突**：动画状态机按 gunId 挂载于渲染器，若玩家
   主手恰好持有同 id 枪械，第一人称与视口会共用同一状态机。低概率场景，接受。
3. **TACZ 版本漂移**：本项目面向非官方移植的特定 release（1.1.8-hotfix-r6）；
   TACZ 更新后 API 若变化，compat 类 try-catch 保证静默降级而非崩溃，但需跟进适配。

## 验证

- `./gradlew :v1_21_1:clean compileJava -Pactive_versions=1.21.1`（clean 防增量假象）
- 无 TACZ 运行时：开箱后展示区与手套按钮行为与旧版一致（2D 图标、点击无响应、无报错日志）
- 有 TACZ 运行时：TACZ 枪配入测试箱 → 抽中 → 展示区直接显示 3D 模型 → 手套按钮播放
  inspect 动画与音效（可重复）→ 关屏无残留音效；手持同枪第一人称检视仍正常
- `./gradlew :common:test` 回归
