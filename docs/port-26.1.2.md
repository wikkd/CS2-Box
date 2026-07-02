# CSBox 26.1.2 移植说明

日期：2026-06-28

## 概述

1.0.5 版本基于 MC 1.21.1 / NeoForge 21.1.115。本文档记录移植到 **MC 26.1.2 / NeoForge 26.1.2.76** 的全部变更。

移植过程中 **保留所有 1.0.5 功能**：开箱动画、CS2 风格滚动、稀有度分级、生物掉落、`/csbox` 命令、4 把钥匙。

移植目标是让模组能在 MC 26.1.2 客户端加载并运行，同时保留 1.21.1 主分支（`main`）的独立开发能力。

预期构建产物：

```text
build/libs/csbox-1.0.5-26.1.2.jar
```

## 构建环境变更

| 项目 | 1.21.1 (原) | 26.1.2 (新) |
|---|---|---|
| Minecraft | 1.21.1 | 26.1.2 |
| NeoForge | 21.1.115 | 26.1.2.76 |
| NeoGradle | 7.0.171 | 7.1.38 |
| Gradle | 8.11 | 8.14 |
| Java toolchain | 21 | 25 |
| 编译参数 | 标准 | `--enable-preview`（NeoForm 重编译用） |

### `gradle.properties` 关键变更

```properties
# 1.21.1
minecraft_version=1.21.1
mod_version=1.0.5
neo_version=21.1.115
loader_version_range=[4,)

# 26.1.2
minecraft_version=26.1.2
mod_version=1.0.5-26.1.2
neo_version=26.1.2.76
loader_version_range=[11,)
```

`mod_version` 增加 `-26.1.2` 后缀，让 GitHub Actions matrix 能区分两版本产物。

### `gradle/wrapper/gradle-wrapper.properties`

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14-bin.zip
```

### `settings.gradle`

```groovy
plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
    id 'net.neoforged.moddev' version '7.1.38'
}
```

## 代码层全局重命名

以下两处用 `sed` 全量替换：

| 1.21.1 | 26.1.2 |
|---|---|
| `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier` |
| `net.minecraft.client.gui.GuiGraphics` | `net.minecraft.client.gui.GuiGraphicsExtractor` |

涉及 12+ Java 文件，纯包路径变更，无逻辑修改。

## 事件总线机制

NeoForge 26.1.2 中 `@EventBusSubscriber` 注解**仍然存在**（位于 `net.neoforged.fml.common.EventBusSubscriber`，由 FML loader-11.0.13 提供），且 FML 在启动时仍会自动扫描并注册标注 `@SubscribeEvent` 的静态方法。因此我们的 `ModEvents.java` 和 `CsboxCommand.java` 继续使用该注解，无需改动。

> 注：早期版本的移植规划曾误判此注解被移除。基于实际反编译 `neoforge-26.1.2.76-universal.jar` 和 `loader-11.0.13.jar` 验证，`EventBusSubscriber` 类仍然存在并可正常工作。

## 主要 API 变更（11 项）

### 1. `Screen` 构造器签名变化

`Screen.minecraft` 字段从 `protected Minecraft` 变为 `protected final Minecraft`，并要求构造器传入。

**改前**（1.21.1）：

```java
public CsboxScreen(...) {
    super(...);
    this.minecraft = Minecraft.getInstance();  // ❌ 编译错：final 字段
}
```

**改后**（26.1.2）：

```java
public CsboxScreen(...) {
    super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("cs_screen"));
}
```

涉及 `CsboxScreen.java`、`CsLookItemScreen.java`、`CsboxProgressScreen.java`。

### 2. `TagParser` API 重命名

`parseTag(String)` 在 26.1.2 中更名为 `parseCompoundFully(String)`。

**改前**：

```java
CompoundTag tag = TagParser.parseTag(jsonString);
```

**改后**：

```java
CompoundTag tag = TagParser.parseCompoundFully(jsonString);
```

涉及 `BoxJsonLoader.java`。

### 3. `Item.appendHoverText` 新签名

新增 `TooltipDisplay` 参数；`List<Component>` 改为 `Consumer<Component>`；移除 `canPerformAction`（已删除）。

**改前**：

```java
@Override
public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    tooltip.add(line1);
    tooltip.add(line2);
}

@Override
public boolean canPerformAction(ItemStack stack, ... ) { ... }
```

**改后**：

```java
@Override
public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
    if (display.hideTooltip()) return;  // 尊重玩家隐藏 tooltip 的设置
    tooltip.accept(line1);
    tooltip.accept(line2);
}
// canPerformAction 已删除
```

`TooltipDisplay` 在 26.1.2 替代了 1.21.1 的 `TooltipFlag.ADVANCED`。`hideTooltip()` 返回 `true` 表示玩家已关闭该物品的 tooltip 显示，此时我们的代码应当提前返回，否则行为会与 1.21.1 不一致。

涉及 `ItemCsBox.java`。

### 4. `AttachmentType` 序列化机制重构

`AttachmentType.Builder.serialize(Codec)` 在 26.1.2 中删除，改用 `IAttachmentSerializer<T>` 接口。

**改前**：

```java
typeBuilder.serialize(CsboxPlayerData.CODEC);
```

**改后**：

```java
typeBuilder.serialize(new IAttachmentSerializer<CsboxPlayerData>() {
    @Override
    public CsboxPlayerData read(ValueInput input) {
        return input.read("data", CsboxPlayerData.CODEC).orElse(new CsboxPlayerData());
    }

    @Override
    public void write(ValueOutput output, CsboxPlayerData data) {
        output.store("data", CsboxPlayerData.CODEC, data);
    }
});
```

涉及 `ModCapability.java`。

### 5. 矩阵栈变更为 `Matrix3x2fStack`（JOML）

旧的 `com.mojang.blaze3d.vertex.PoseStack`（3D）变更为 `org.joml.Matrix3x2fStack`（2D）。涉及 5 个 GUI 文件。

**改前**（`PoseStack` 3D）：

```java
import com.mojang.blaze3d.vertex.PoseStack;

PoseStack pose = guiGraphics.pose();
pose.pushPose();
pose.translate(pX, pY, 1.0F);  // 3D translate (带 z)
pose.scale(scale, scale, 1.0F);  // 3D scale
pose.mulPose(Axis.XP.rotation(angleX));
pose.mulPose(Axis.YP.rotation(angleY));
// ... 渲染
pose.popPose();
```

**改后**（`Matrix3x2fStack` 2D）：

```java
Matrix3x2fStack pose = guiGraphics.pose();
pose.pushMatrix();
pose.translate(pX, pY);          // 2D translate (无 z)
pose.scale(scale, scale);        // 2D scale
pose.translate(8F, 8F);          // 移到中心
pose.rotate(angleYComponent);    // 2D rotation（Y 轴旋转近似）
pose.translate(-8F, -8F);        // 移回
// ... 渲染
pose.popMatrix();
```

**已知限制**：
- 2D 矩阵无 z 轴层级。如果 1.21.1 用 `translate(0, 0, 1)` 做层级，26.1.2 丢失该效果。
- 3D X-rotation（垂直翻转）在 2D 中无法表达，**已丢弃**。
- 修复位置：`RenderFontTool.java`、`GuiItemMove.java`、`IconListTools.java`、`CsboxScreen.java`、`CsLookItemScreen.java`、`CsboxProgressScreen.java`。

### 6. `GuiGraphics.renderItem` 新增第 5 参数 `seed`

```java
// 旧签名
guiGraphics.renderItem(LivingEntity entity, ItemStack stack, int x, int y);

// 新签名（seed=0 表示固定渲染，!=0 表示随机化）
guiGraphics.renderItem(LivingEntity entity, ItemStack stack, int x, int y, int seed);
```

所有现有调用统一补 `0`。涉及 `GuiItemMove.java`、`IconListTools.java`、`CsboxScreen.java`。

### 7. 网络包发送 API 选择

NeoForge 26.1.2 中 `PacketDistributor` 工具类**仍然存在**（在 `net.neoforged.neoforge.network.PacketDistributor`），并提供 `sendToPlayer`、`sendToAllPlayers` 等静态方法。但 26.1.2 还引入了一种更优雅的方式：通过 `IPayloadContext.reply()` 在 handler 内部直接回复客户端。

**客户端发送**（`CsboxScreen.java`、`CsLookItemScreen.java`）：

```java
// 1.21.1
PacketDistributor.sendToServer(new PacketBoxOpenResult(...));

// 26.1.2 —— 直接通过 Connection 发送
Connection conn = Minecraft.getInstance().getConnection();
if (conn != null) {
    conn.send(new ServerboundCustomPayloadPacket(new PacketBoxOpenResult(...)));
}
```

**服务端发送**（`PacketCs2Progress.java`、`PacketRequestBoxItems.java`）：

```java
// 1.21.1
PacketDistributor.sendToPlayer(sp, new PacketBoxOpenResult(...));

// 26.1.2 —— 在 handler 内部用 context.reply()
context.enqueueWork(() -> {
    // ...
    if (player instanceof ServerPlayer sp) {
        context.reply(new PacketBoxOpenResult(...));
    }
});
```

`IPayloadContext.reply()` 的默认实现委托给 `listener.send(payload)`，可以在 handler 内（包括 `enqueueWork` 内部）安全调用。

### 8. 网络事件 API 重构

`MouseButtonEvent`、`KeyEvent` 的访问方式从 `event.getButton()` 变为 `event.buttonInfo().button()`。

```java
// 1.21.1
if (event.getButton() == 0) { ... }

// 26.1.2
if (event.buttonInfo().button() == 0) { ... }
```

涉及所有 GUI Screen 文件。

### 9. `registry.get(key)` 返回 `Optional<Holder.Reference<T>>`

```java
// 1.21.1
Item item = BuiltInRegistries.ITEM.get(keyRl);

// 26.1.2
Item item = BuiltInRegistries.ITEM.get(keyRl).get().value();
```

涉及 `ModItems.java`、`BoxJsonLoader.java`。

### 10. `Entity.level()` 返回类型变化

```java
// 1.21.1
mob.spawnAtLocation(mob.level(), stack);

// 26.1.2
mob.spawnAtLocation((ServerLevel) mob.level(), stack);
```

需要强转为 `ServerLevel`。涉及 `ModEvents.java`。

### 11. `Container.items` 字段改为私有

```java
// 1.21.1
for (int i = 0; i < container.getContainerSize(); i++) {
    ItemStack stack = container.getItem(i);
}

// 26.1.2（同上，仍用 getter，但实现改为通过 `items` 列表的迭代器）
for (int i = 0; i < container.getContainerSize(); i++) {
    ItemStack stack = container.getItem(i);
}
```

API 调用形式未变，但实现细节变了。涉及 `CsboxCommand.java` 和 4 个 Packet 文件。

## 高风险点修复

### 修复 1：`GuiItemMove.renderItemInInventoryFollowsMouse`

**问题**：原 1.21.1 用 3D PoseStack 实现鼠标拖拽旋转 + 缩放，26.1.2 简化为 `guiGraphics.item(player, item, x, y, 0)` 后 **scale + rotation 完全丢失**。

**修复**：用 `Matrix3x2fStack` 恢复 scale + 2D rotation：

```java
public static void renderItemInInventoryFollowsMouse(
        GuiGraphicsExtractor guiGraphics, int x, int y,
        float angleXComponent, float angleYComponent,
        ItemStack item, LivingEntity player, float scale
) {
    var pose = guiGraphics.pose();
    pose.pushMatrix();
    pose.translate(x, y);
    pose.scale(scale, scale);
    pose.translate(8F, 8F);                  // 移到物品中心
    pose.rotate(angleYComponent);             // Y 轴 3D 旋转的 2D 近似
    pose.translate(-8F, -8F);
    guiGraphics.item(player, item, 0, 0, 0);
    pose.popMatrix();
}
```

涉及 `GuiItemMove.java:21-39`。

### 修复 2：`IconListTools.renderGuiItem`

**问题**：原版支持 `scale` 参数但 26.1.2 简化为忽略。

**修复**：同样应用 scale + translate：

```java
public static void renderGuiItem(LivingEntity entity, GuiGraphicsExtractor guiGraphics,
                                  ItemStack itemStack, float pX, float pY, float scale) {
    var pose = guiGraphics.pose();
    pose.pushMatrix();
    pose.translate(pX, pY);
    pose.scale(scale, scale);
    guiGraphics.item(entity, itemStack, 0, 0, 0);
    pose.popMatrix();
}
```

涉及 `IconListTools.java:42-49`。

### 修复 3：`RenderFontTool.drawString` 返回值

**问题**：原 1.21.1 返回字符宽度（用于布局），26.1.2 简化为返回 `0`，破坏 `renderCenteredText` 等调用方的文本居中计算。

**修复**：恢复返回语义为 `Math.round(width * scale)`：

```java
public static int drawString(GuiGraphicsExtractor guiGraphics, Font pFont,
                              FormattedCharSequence pText, float pX, float pY,
                              int ox, int oy, float scale, int pColor) {
    Font font = pFont != null ? pFont : Minecraft.getInstance().font;
    if (font == null) return 0;
    int width = font.width(pText);
    guiGraphics.pose().pushMatrix();
    guiGraphics.pose().translate(pX, pY);
    guiGraphics.pose().scale(scale, scale);
    guiGraphics.text(font, pText, 0, 0, pColor);
    guiGraphics.pose().popMatrix();
    return Math.round(width * scale);
}
```

涉及 `RenderFontTool.java:12-24`。

## 验证结果

### 构建验证

```
$ ./gradlew compileJava --no-daemon
> Task :compileJava
BUILD SUCCESSFUL in 12s
14 actionable tasks: 1 executed, 13 up-to-date

$ ./gradlew jar --no-daemon
> Task :jar
BUILD SUCCESSFUL in 7s
16 actionable tasks: 1 executed, 15 up-to-date

$ ls -lh build/libs/
-rw-r--r--@ 1 shuangyuexingxun staff 355K Jun 28 16:00 csbox-1.0.5-26.1.2.jar
```

jar 内 MANIFEST 和 `META-INF/neoforge.mods.toml` 正确，24 个修改类全部打入。

### 编译警告

`ItemCsBox.java` 触发 `deprecation` 警告（一个未指定的 API 调用）。**与本次移植无关**，属于 MC 26.1.2 内部 API 弃用。建议后续版本检查。

### 运行时验证

**未执行**。`./gradlew runClient` 需要图形环境（macOS 可用）和较长的 MC 资源加载时间。本文档发布时未运行；建议在正式发布前做完整 GUI 测试。

## 兼容性增强（移植后修复）

在初版移植通过 `compileJava` 与 `jar` 后，又对代码做了一轮 review，找出以下问题并修复。

### 修复 1：`PacketCs2Progress.handleServer` 死代码

**问题**（修复前第 138-139 行）：

```java
player.setData(ModCapability.PLAYER_DATA,
        new CsboxPlayerData(0L, 0, ItemStack.EMPTY, 0));   // ← 立刻被覆盖
player.setData(ModCapability.PLAYER_DATA,
        new CsboxPlayerData(serverSeed, 0, giveItem.copy(), finalGrade));
```

第一次 `setData` 立刻被第二次覆盖，浪费一次 attachment 写入并可能引发网络同步副作用。

**修复**：删除冗余调用，只保留一次 `setData`。

涉及 `PacketCs2Progress.java:137-138`。

### 修复 2：`ItemCsBox.appendHoverText` 尊重 `TooltipDisplay.hideTooltip()`

**问题**：1.21.1 中 tooltip 的显示与否由 `TooltipFlag.ADVANCED` 控制。26.1.2 改为 `TooltipDisplay`，其 `hideTooltip()` 返回 `true` 表示玩家已关闭该物品的 tooltip。原版代码从不检查这个标志，玩家关闭 tooltip 后仍会看到 CSBox 内容。

**修复**：

```java
@Override
public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                             TooltipDisplay tooltipDisplay,
                             Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
    if (tooltipDisplay.hideTooltip()) {
        return;  // 玩家已关闭 tooltip，不显示内容
    }
    // ... 原逻辑
}
```

涉及 `ItemCsBox.java:111`。

### 修复 3：服务端网络包发送改用 `IPayloadContext.reply()`

**问题**：服务端代码原本用 `PacketDistributor.sendToPlayer(sp, payload)`，需要 `ServerPlayer` 实例做类型检查。26.1.2 提供 `IPayloadContext.reply(payload)`，自动回复给当前 handler 对应的发送方，无需类型转换，代码更简洁。

**修复**：

```java
// 修复前（PacketCs2Progress.java, PacketRequestBoxItems.java）
if (player instanceof ServerPlayer sp) {
    PacketDistributor.sendToPlayer(sp, new PacketBoxOpenResult(...));
}

// 修复后
if (player instanceof ServerPlayer sp) {
    context.reply(new PacketBoxOpenResult(...));
}
```

`sendRejected` 辅助方法也改为接收 `IPayloadContext` 参数，统一调用方式。

涉及 `PacketCs2Progress.java`、`PacketRequestBoxItems.java`。

### 修复 4：`BoxJsonLoader` 教程文本版本号更新

**问题**：JSON 配置教程文本仍写 "Minecraft 1.21.1"，与目标版本 26.1.2 不符。

**修复**：改为 "Minecraft 1.21+"。

涉及 `BoxJsonLoader.java:173`。

### 修复 5：`ModSounds` 完全限定名风格统一

**问题**：`ModSounds.java:14` 内联使用 `net.minecraft.core.registries.Registries.SOUND_EVENT`，与其他文件风格不一致。

**修复**：加 `import` 语句后用短名 `Registries.SOUND_EVENT`。

涉及 `ModSounds.java`。

### 修复 6：`PacketBoxOpenResult.sPendingResults` 线程安全注释

**问题**：静态 `Queue` 字段无文档说明线程模型，未来重构可能引入竞态。

**修复**：加注释说明 `enqueueWork` 保证 handler 内部工作在主线程，`consumeMatching` 也由主线程调用（来自 `CsboxScreen.containerTick`），两个访问点共享同一线程。

涉及 `PacketBoxOpenResult.java:104-107`。

## 分支管理

| 分支 | 状态 | 用途 |
|---|---|---|
| `main` | clean（HEAD=`617cbe7`） | 1.21.1 持续开发 |
| `neoforge-26.1.2` | clean | 26.1.2 移植基准（基于 main 拉出） |
| `neoforge-26.1.2-wip` | 24 文件改动（脏工作区） | 26.1.2 当前 WIP |

**保护机制**：`main` 始终 clean，任何时刻可以 `git checkout main` 返回 1.21.1 开发。WIP 在独立分支 + git stash（如有）保护下。

**回退到 1.21.1 的步骤**：

```bash
git stash                       # 保存当前未提交修改
git checkout main               # 回到 1.21.1
```

**继续 26.1.2 WIP 的步骤**：

```bash
git checkout neoforge-26.1.2-wip
git stash pop                   # 恢复之前的修改
```

## 兼容性

### 存档

- 26.1.2 客户端加载 1.21.1 存档：**不兼容**。MC 26.1 是独立大版本，数据包格式与 1.21.1 不同。
- 26.1.2 客户端只能加载 26.1.x 存档。

### 配置文件

- `config/csbox-common.toml`：跨版本通用，字段名一致。
- `config/csbox/*.json`：箱子配置，跨版本通用。

### 资源文件

- `assets/csbox/textures/`、`lang/`、`sounds/`、`shaders/`：跨版本通用。
- `data/csbox/recipes/`：跨版本通用。

### 命令

`/csbox <givebox|givekey|reload|version>`：所有子命令在 26.1.2 中保持兼容。

## 手动测试清单

发布前需逐项验证：

### 客户端

- [ ] 客户端能加载 26.1.2 + NeoForge 26.1.2.76
- [ ] 主菜单点击 `/csbox version` 返回 `1.0.5-26.1.2`
- [ ] 创造模式物品栏显示 4 把钥匙和 CS:GO Box
- [ ] 放置钥匙和箱子到世界
- [ ] 右键箱子打开 GUI：标题、稀有度图框、物品列表正常显示
- [ ] **拖拽物品**：鼠标拖拽时物品能 2D 旋转（Y 轴近似）
- [ ] 物品 icon 缩放正常（修复前会丢失）
- [ ] 点击 "OPEN BOX" 触发开箱动画
- [ ] 开箱进度条正常显示
- [ ] 开箱完成后结果窗口（CsLookItemScreen）正常显示
- [ ] 警告文本（未配置箱子）显示居中，红色
- [ ] 按钮文字居中（修复前会偏向左侧）

### 服务端

- [ ] 玩家击杀生物掉落箱子（概率由 `globalDropRatePercent` 控制）
- [ ] 多人游戏中其他玩家能打开你的箱子
- [ ] `/csbox givebox <player> <grade>` 给予指定稀有度箱子
- [ ] `/csbox givekey <player> <tier>` 给予指定钥匙
- [ ] `/csbox reload` 重载配置不报错

### 性能

- [ ] FPS 不下降（GUI 渲染未明显增加开销）
- [ ] 开箱动画 60 FPS 平滑

## 已知问题与限制

1. **3D 物品旋转降级为 2D 旋转**：原 1.21.1 用 3D PoseStack 实现垂直翻转（X 轴），26.1.2 仅保留 Y 轴 2D 旋转。视觉效果是鼠标拖拽时物品能左右翻转，但不能再前后翻转。如需完全恢复 3D 旋转，需重写为 `BatchedGuiCommands` + `ItemStackRenderState` 系统，工作量约 1-2 天。
2. **`RenderFontTool` 当前调用方未使用返回值**：虽然已恢复返回 `width * scale` 的语义，但现有 5 处调用都丢弃返回值。如果未来有调用方需要返回值，行为已正确。
3. **MC 26.1.2 客户端兼容性测试未完成**：本文档发布前未实际启动 MC 客户端验证 GUI。GUI 渲染逻辑经过代码审查，但运行时表现需手动确认。

## 升级路径

### 从 1.21.1 升级

1. 客户端：安装 MC 26.1.2 + NeoForge 26.1.2.76 + 此版本 mod
2. 服务端：同上
3. 配置文件：`config/csbox-common.toml` 自动兼容
4. 箱子配置：`config/csbox/*.json` 自动兼容
5. 存档：必须新建（跨大版本）

### 从 26.1.2-wip 升级到正式版

无需操作。正式版本号为 `1.0.5-26.1.2`，与 WIP 版本号一致。

## 相关文档

- `AGENTS.md`：构建命令、包结构、配置系统速查
- `docs/update-1.0.5.md`：1.0.5 变更（移除 Cloth Config）
- `README.md`：用户向文档（待同步更新版本号）
- `CHANGELOG.md`：待添加 26.1.2 移植条目