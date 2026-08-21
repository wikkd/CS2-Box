# TACZ 检视视口集成设计（v1_21_1 / forge_1_20_1）

日期：2026-08-07（2026-08-20 更新）
状态：已实现
范围：`v1_21_1` 与 `forge_1_20_1` 两个 TACZ 平台模块，其余平台零改动

## 背景

1.0.7 引入"检视"功能：开箱抽出物品界面（`CsLookItemScreen`）的底部工具栏"手套"按钮
（index 1）作为 TACZ 第一人称检视入口。兼容
[TaCZ]永恒枪械工坊：零（v1_21_1 为非官方移植 https://github.com/MUKSC/TACZ-1.21.1，
forge_1_20_1 为官方 1.20.1 构建，mod id 均为 `tacz`）自带的检视动画。

TACZ 枪抽中后的**默认展示**不再自动进入 TACZ 固定视角视口：TACZ 自身的 GUI 物品渲染
（`GunItemRendererWrapper.renderByItem` 的 `transformType == GUI` 分支）只画平面槽位
贴图，走原版 item renderer 只会得到 2D 图标；因此默认改用自家
`AnimRenderOps.renderItem3D → renderGunModel3D` 可拖拽 3D 渲染路径。
手套按钮语义为"进入/重播第一人称检视"：点击后按需进入 TACZ 视口并触发动画与音效，
视口已激活时再次点击重播检视；关闭屏幕时清理音效与状态机。

已确认的产品决策：

- **TACZ 平台**：`v1_21_1` 与 `forge_1_20_1`，不做其它平台镜像。
- **默认 3D 展示**：TACZ 枪默认走自家 `renderItem3D → renderGunModel3D`，支持鼠标拖拽旋转；
  不自动进入 TACZ 固定视角视口，也不做异步加载重试。
- **检视武器（index 0）＝ 默认选中项**：打开界面即处于自家 3D 拖拽预览，工具栏默认高亮该按钮；
  点击可退出 TACZ 视口并回到默认预览（`TaczInspectViewport.exit()`）。
- **手套按钮（index 1）＝ 可选 TACZ 第一人称检视视口**：点击进入（未激活）或重播（已激活）；
  手套按钮点击不会退出视口，退出只发生在切回"检视武器"、关闭屏幕/`removed()`。
- **compileOnly + 运行时检测**：`ModList.isLoaded("tacz")` 门槛 + JVM 类懒加载 +
  `catch (Throwable)` 静默降级。无 TACZ 环境功能完全隐形。
- **现屏内嵌切换**：不新开屏幕，复用现有展示区坐标体系
  （`width*37/100`、`height*30/100`、`scale = frameWidth/16`）。
- **交互仅检视音效**：TACZ 视口固定展示角度、无拖拽旋转（拖拽仍走默认自家 3D 路径）；
  手套按钮可重复触发检视。
- **降级为无响应/默认 3D**：未装 TACZ / 物品不是 TACZ 枪 / 枪无动画状态机时，展示区保持
  默认 2D 或自家 3D 渲染，点击手套按钮无任何反应（按钮保持显示，tooltip 照常）。
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

- `scripts/download-tacz.sh`（v1_21_1）：幂等下载 TACZ release
  （tag `neoforge-1.1.8-hotfix-r6`，~57MB）至
  `local-repo/com/tacz/tacz/1.1.8-hotfix-r6/`，并生成最小 pom。
- `scripts/download-tacz-1201.sh`（forge_1_20_1）：幂等下载官方 1.20.1 TACZ 构建
  （产物机制同上，jar 入 `local-repo/`，不提交）。
- jar **不入库**（超出 50MB 提交阈值，用户决策）：`.gitignore` 忽略
  `/local-repo/com/tacz/`；CI（`.github/workflows/build.yml`）在对应平台矩阵编译前
  自动运行下载脚本。
- `v1_21_1/build.gradle`：`compileOnly 'com.tacz:tacz:1.1.8-hotfix-r6'`
  （`LocalPatchedLibraries` 仓库已注册，metadataSources 含 mavenPom()+artifact()）。
  另需 `compileOnly 'com.github.mcmodderanchor:simplebedrockmodel:2.2.1-neoforge+mc1.21.1'`：
  `AnimateGeoItemRenderer` 的父接口 `IFPGeoItemRenderer` 位于 TACZ jarjar 内嵌库中，
  javac 解析类层次需要它；该库由 `scripts/download-tacz.sh` 从 TACZ jar 提取生成
  （仓库惯例 `*.jar` 全局忽略、仅 pom 入库）。
- `forge_1_20_1` 同样声明 `compileOnly` TACZ 依赖，compat 类在
  `forge_1_20_1/.../compat/TaczInspectViewport.java`（API 细节按 Forge 1.20.1 适配）。

### compat 类：`v1_21_1/.../compat/TaczInspectViewport.java` / `forge_1_20_1/.../compat/TaczInspectViewport.java`

所有 TACZ 类引用封闭在该类内，对外仅暴露不含 TACZ 类型的静态方法：

- `isAvailable(ItemStack)`：ModList 门槛 → `stack.getItem() instanceof IGun` →
  `TimelessAPI.getGunDisplay` 非空且带动画状态机。
- `enter(ItemStack, LocalPlayer)`：取 `IClientItemExtensions.of(stack)
  .getCustomRenderer()` 中的 `AnimateGeoItemRenderer`；状态机未初始化则
  `setContext(renderer.initContext(...)) + initialize()`（**不触发 draw**，避免检视
  信号被抽出动画状态吞掉）；`triggerAnimation(stack, INPUT_INSPECT)`；按空仓判定播放
  检视音效。
- `enterDisplay(ItemStack, LocalPlayer)`：仅初始化状态机、不触发 inspect，可用于需要
  TACZ 固定静止展示的场景；当前屏幕默认不再自动调用（默认走自家可拖拽
  `renderItem3D → renderGunModel3D`）。
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

- 字段 `taczViewportActive`。`renderBg` **不自动进入** TACZ 视口：默认展示走
  `AnimRenderOps.renderItem3D`（TACZ 枪内部落到 `renderGunModel3D`，支持拖拽旋转）。
- 手套按钮（index 1）点击：视口未激活但可用 → `TaczInspectViewport.enter()` 进入并播放
  第一人称检视动画；视口已激活 → `TaczInspectViewport.triggerInspect()` 重播；
  不可用 → 不响应。点击**不会**退出视口。
- 检视按钮（index 0）点击：视口激活时 `TaczInspectViewport.exit()` 并回到默认自家 3D 预览
  （拖拽旋转模式）。该按钮是默认选中项。
- `renderBg`：视口激活且 `renderViewport` 返回 true 时替代默认 3D 渲染；
  渲染失败当帧回退默认渲染并永久关闭视口。
- 工具栏 active 高亮：
  `(i == 0 && !taczViewportActive) || (i == 1 && taczViewportActive) || (i == 3 && showInfoPanel)`。
- `removed()` 清理：视口激活时 `exit()`，防止音效/状态机泄漏到屏幕外。

## 已知限制（接受，不做）

1. **枪包未定义 inspect 转换**：极端情况下状态机收到 inspect 输入后停留在静态模型，
   视口显示静止枪械。不做 Lua 状态机内省来预判（YAGNI，且枪包数据结构非稳定 API）。
2. **玩家手持同一把枪时状态机共享冲突**：动画状态机按 gunId 挂载于渲染器，若玩家
   主手恰好持有同 id 枪械，第一人称与视口会共用同一状态机。低概率场景，接受。
3. **TACZ 版本漂移**：本项目面向特定 release（v1_21_1 为非官方移植
   `1.1.8-hotfix-r6`，forge_1_20_1 为官方 1.20.1 构建）；
   TACZ 更新后 API 若变化，compat 类 try-catch 保证静默降级而非崩溃，但需跟进适配。

## 验证

- `./gradlew :v1_21_1:clean compileJava -Pactive_versions=1.21.1` 与
  `./gradlew :forge_1_20_1:clean compileJava -Pactive_versions=forge-1.20.1`
  （clean 防增量假象）
- 无 TACZ 运行时：开箱后展示区与手套按钮行为与旧版一致（默认 2D/普通 3D、点击无响应、无报错日志）
- 有 TACZ 运行时：TACZ 枪配入测试箱 → 抽中 → 展示区默认显示可拖拽 3D 模型
  （自家 `renderItem3D → renderGunModel3D`）→ 点击手套按钮进入/重播第一人称检视动画
  与音效（可重复）→ 关屏无残留音效；手持同枪第一人称检视仍正常
- `./gradlew :common:test` 回归
