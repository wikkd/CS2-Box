# CS2-Box 多平台 API 差异开发指南

> 面向全部 **3 个在维护平台模块**的 API 差异开发说明。按「版本分类 → 全量速查矩阵 → 主题展开」组织，帮助你在开发任何平台时快速定位「哪个 API、哪个版本、怎么写」。

- **基准模块**：legacy 用 `v1_21_1`，new 用 `v26_1_2`
- **适用范围**：各平台 `src/main/java/com/reclizer/csgobox/<platform>/` 下的版本敏感代码
- **配套文档**：架构总览见 [ARCHITECTURE.md](./ARCHITECTURE.md)；1.21.1→26.1.2 单次移植记录见 [port-26.1.2.md](./port-26.1.2.md)
- **已归档（EOL）平台**：v1_21_0 / v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10 / v1_21_11 于 2026-08-09 移出仓库（tag `eol-legacy-21x-1.0.6`）；下文矩阵中的 legacy 中间断点行仅作历史参考

## 版本分类速查

| 分类 | 模块 | MC | NeoForge | Java | pack_format | 基准 |
|---|---|---|---|---|---|---|
| **legacy (旧 API)** | `v1_21_1` | 1.21.1 | 21.1.248 | 21 | 34 | ★ 基准 |
| **new (decoupled)** | `v26_1_2` | 26.1.2 | 26.1.2.95 | 25 + `--enable-preview` | 80 | ★ 基准 |
| | `v26_2` | 26.2 | 26.2.0.59 | 25 + `--enable-preview` | 81 | |
| **实验** | `forge_26_1_2` | 26.1.2 | Forge 64.1.0 | 25 | 80 | 非正式，勿当平台 |

> 关键结论：**21.x→26.x** 是全面重构的 decoupled API，**26.1.2→26.2** 是小改。legacy 内部 6 个断点（1.21.3/.4/.5/.8/.10/.11）已随 EOL 平台归档。

---

## 全量速查矩阵

### API 断点 × 版本

| API 主题 | 1.21.1 | 1.21.3 | 1.21.4 | 1.21.5 | 1.21.8 | 1.21.10 | 1.21.11 / 26.1.2 | 26.2 |
|---|---|---|---|---|---|---|---|---|
| 注册表 `BuiltInRegistries.ITEM.get()` | 直接返 Item | **→ Optional** | = | = | = | = | = (Holder) | = |
| Item 构造签名 | 无参 | = | = | = | = | = | **注入 Properties + setId** | = (去重复 stacksTo) |
| 物品渲染 `renderItem` | `world` 参数 | **去 world / renderItemEntity** | = | = | = | = | **PIP / 矩阵栈** | = |
| `blit` | 简写 | 需 `RenderType.GUI_TEXTURED`+tint | = | 删 RenderSystem 手动 | **`RenderPipelines.GUI_TEXTURED`** | = | **去 tint 参数 + `Matrix3x2f` 栈** | = |
| `appendHoverText` | `List<Component>` | = | = | **`TooltipDisplay + Consumer`** | = | = | = + `hideTooltip()` | = |
| 背包 `items/armor/offhand` | 直接字段 | = | **getItem()/getItemBySlot** | = | = | = | **`getNonEquipmentItems()`** | = |
| 发包方式 | `PacketDistributor.sendToServer` | = | = | = | `ClientPacketDistributor` | **`conn.send(ServerboundCustomPayloadPacket)`** | 同 | 同 |
| 服务端回拨 | `PacketDistributor.sendToPlayer` | = | = | = | = | = | **`context.reply(...)`** | 同 |
| Screen | `GuiGraphics` | **渲染 API 改** | = | = | `renderBlurredBackground(guiG)` | **事件对象化 (Mouse/KeyEvent)** | **`extractRenderState` + 构造器加参** | `setScreenAndShow()` |
| HUD 显隐 | `options.hideGui` | = | = | = | = | = | `options.hideGui` | **`Minecraft.gui.hud.toggle()`** |
| attachment 序列化 | `Codec` | = | = | = | `MapCodec` | = | `IAttachmentSerializer` + ValueIO | = |
| `ResourceLocation` | (ResourceLocation) | = | = | = | = | = | **`Identifier`** | = |
| 权限 | `hasPermission(2)`.permissions() 相关 | = | = | = | `hasPermission` | = | **`permissions().hasPermission()`** | = |
| 成就包 | `critereon` | = | = | = | = | = | `criterion` | **predicates/triggers** |

> 单元格标注变化起始版本，`=` 表示与左列相同。26.1.2 与 1.21.11 共享大部分变化（PIP、extract、Identifier 由 1.21.11 引入/贯穿）。

### 文件级差异指引（改动最重的文件）

| 文件 | 受影响的断点 |
|---|---|
| `gui/CsboxScreen.java` | 全部（1.21.3 渲染 / 1.21.8 发包 / 1.21.10 事件对象 / 1.21.11 Screen 构造+玩法 / 26 PIP、Hud相关） |
| `gui/CsboxBulkOverviewScreen.java` | 全部 |
| `gui/CsboxBulkResultScreen.java` | 1.21.8 发包、1.21.10、1.21.11、26.x |
| `gui/CsLookItemScreen.java` | 全部 |
| `gui/CsboxProgressScreen.java` | 渲染相关全部 + 26.x Hud |
| `utils/IconListTools.java` | 1.21.3 渲染、1.21.8 per-item 居中、1.21.11 矩阵栈、26.x |
| `utils/GuiItemMove.java` | 1.21.3 渲染、1.21.8 灯光、1.21.11/26 PIP |
| `utils/RenderFontTool.java` | 1.21.11、26.x 字体绘制 |
| `packet/PacketCsgoProgress.java` | 1.21.4 背包、1.21.11/26 attachment、26 context.reply |
| `packet/PacketCsgoBulkProgress.java` | 1.21.4 背包、26 context.reply |
| `item/ItemCsgoBox.java` | 1.21.5 tooltip、1.21.11 构造、26.x |
| `capability/ModCapability.java` | 1.21.8 MapCodec、1.21.11/26 IAttachmentSerializer |
| `box/BoxItemCodec.java` | 1.21.3 Optional、1.21.5 NBT、1.21.11 Identifier |

---

## 主题展开

### 1. 注册表 API

**断点**：1.21.3（Optional）、1.21.11（`DeferredRegister.Items` + `setId`）、26.x

#### 代码对照

```java
// 1.21.1 — 直接取 Item
var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));

// 1.21.3+ / 26.x — Optional<Holder.Reference<Item>>，需 .map(Holder.Reference::value)
Item item = BuiltInRegistries.ITEM.get(Identifier.parse(id))
        .map(Holder.Reference::value).orElse(null);
```

```java
// 1.21.1 — DeferredRegister<Item> 泛型注册
DeferredRegister<Item> ITEMS = DeferredRegister.createItems(MODID);
ITEMS.register("csgo_box", ItemCsgoBox::new);

// 1.21.11+ / 26.x — DeferredRegister.Items + registerItem(name, factory, props)
DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
ITEMS.registerItem("csgo_box", ItemCsgoBox::new, properties -> properties);
```

```java
// 1.21.11+ Item 必须携带 setId(ResourceKey)，否则动态 item 缺 key
ITEMS.registerItem("csgo_box", ItemCsgoBox::new, p -> p.stacksTo(16)
        .setId(ResourceKey.create(Registries.ITEM, itemId)));
```

```java
// 26.x 动态 item：显式设置 ITEM_MODEL data component，避免紫黑棋盘格
stack.set(DataComponents.ITEM_MODEL, Identifier.parse(MODID + ":csgo_box"));
```

**文件指引**：`item/ItemCsgoBox.java`、`item/ItemCsgoKey.java`、`item/ModItems.java`、`box/BoxItemCodec.java`、`gui/CsboxBulkMigrationScreen.java`
### 2. Screen / GUI / 输入事件

**断点**：1.21.3（渲染 API）、1.21.8（`renderBlurredBackground` 签名）、1.21.10（输入事件对象化）、1.21.11（Screen 构造器 + extract 生命周期）、26.x（`GuiGraphicsExtractor`）

#### 代码对照

```java
// Screen 构造器：1.21.11+ / 26.x 需要显式传 Minecraft + Font
// 1.21.1-1.21.10:
public CsboxScreen(Component title) { super(title); }
// 1.21.11+ / 26.x:
public CsboxScreen(Minecraft mc, Font font, Component title) { super(mc, font, title); }
```

```java
// 鼠标/键盘输入：1.21.10+ 事件对象化（去掉 @Override）
// 1.21.1-1.21.9:
@Override public boolean mouseClicked(double x, double y, int button) { ... }
@Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { ... }

// 1.21.10+:
@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    event.x(); event.y(); event.button();
}
@Override public boolean keyPressed(KeyEvent event) { event.key(); }
```

```java
// 渲染生命周期：26.x 从 GuiGraphics 改为 GuiGraphicsExtractor
// 1.21.x:
@Override protected void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) { ... }
// 26.x:
@Override protected void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) { ... }
// renderBackground → extractBackground；renderLabels 参数类型同步变更
```

```java
// 26.2：setScreen → setScreenAndShow（全部跳转点）
// 26.1.2:
Minecraft.getInstance().setScreen(new CsboxProgressScreen(...));
// 26.2:
Minecraft.getInstance().setScreenAndShow(new CsboxProgressScreen(...));
```

```java
// HUD 显隐：26.2 移除 Options.hideGui，改用 Minecraft.gui.hud
// 26.1.2:
this.minecraft.options.hideGui = true;
// 26.2（封装在 utils/HudVisibility.java）:
HudVisibility.hide();   // mc.gui.hud.toggle() / isHidden() 包装
HudVisibility.show();
```

**文件指引**：`gui/CsboxScreen.java`、`gui/CsboxProgressScreen.java`、`gui/CsboxBulkOverviewScreen.java`、`gui/CsLookItemScreen.java`、`event/ClickEvent.java`、`utils/HudVisibility.java`（仅 v26_2）

### 3. 渲染管线 / 物品渲染

**断点**：1.21.3（`renderItem` 签名 + `RenderType`）、1.21.5（删 RenderSystem 手动）、1.21.8（`RenderPipelines`）、1.21.11（PIP + `Matrix3x2f` 栈 + 去 tint）、26.x（PIP 渲染器注册）

#### 代码对照

```java
// blit：1.21.3+ 需显式渲染管线，1.21.11 再去掉尾参 tint
// 1.21.1:
guiGraphics.blit(tex, x, y, 0, 0, w, h, 32, 32);
// 1.21.3-1.21.8:
guiGraphics.blit(RenderType.GUI_TEXTURED, tex, x, y, 0, 0, w, h, 32, 32, 0xFFFFFFFF);
// 1.21.8+:
guiGraphics.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, w, h, 32, 32, 0xFFFFFFFF);
// 1.21.11+ / 26.x:
guiGraphics.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0F, 0F, w, h, 32, 32);
```

```java
// 物品渲染：1.21.3+ 用 GuiGraphics.renderItem/renderItemEntity，1.21.11+ 有 per-item bounding box 居中
// 1.21.1: ItemRenderer.getModel + BakedModel + RenderSystem 手写
// 1.21.3+:
guiGraphics.renderItem(entity, stack, x, y, seed);
// 1.21.11+ (IconListTools)：TrackingItemStackRenderState + ItemModelResolver.updateForLiving
//   + getModelBoundingBox() 计算 offsetX/Y 居中 → guiGraphics.item(entity, stack, 0, 0, seed)
```

```java
// 渲染层：1.21.11+ 用 nextStratum() 提升上层渲染，替代手动 depth test
// 1.21.1: RenderSystem.enable/disableDepthTest 手挡
// 1.21.11+:
guiGraphics.nextStratum();
```

```java
// 3D PIP：1.21.11+ / 26.x 用画中画渲染器实现拖拽 3D 物品
// 26.1.2 注册（CsgoBox.java）:
@SubscribeEvent
public void onRegisterPIP(RegisterPictureInPictureRenderersEvent event) {
    event.register(Icon3DRenderState.class,
        bufferSource -> new Icon3DRenderer(bufferSource));
}
// 26.2：渲染器改无参构造 + renderToTexture(State, PoseStack, SubmitNodeCollector)
event.register(Icon3DRenderState.class, ignored -> new Icon3DRenderer());
```

**文件指引**：`utils/IconListTools.java`、`utils/GuiItemMove.java`、`gui/pip/Icon3DRenderer.java`、`gui/pip/Icon3DRenderState.java`（1.21.11+/26.x 独有）、`CsgoBox.java`（PIP 注册）

### 4. 网络层

**断点**：1.21.8（`ClientPacketDistributor`）、1.21.10（`conn.send`）、26.x（`context.reply`）

#### 代码对照

```java
// 客户端发包：1.21.1 → 1.21.8 → 1.21.10+ / 26.x
// 1.21.1: PacketDistributor.sendToServer(new PacketCsgoProgress(...));
// 1.21.8: ClientPacketDistributor.sendToServer(new PacketCsgoProgress(...));
// 1.21.10+ / 26.x:
ClientPacketListener conn = Minecraft.getInstance().getConnection();
if (conn != null) conn.send(new ServerboundCustomPayloadPacket(new PacketCsgoProgress(...)));
```

```java
// 服务端回拨：26.x 改 context.reply，替代 PacketDistributor.sendToPlayer
// 1.21.x:
PacketDistributor.sendToPlayer(player, new PacketBoxOpenResult(...));
// 26.x:
context.reply(new PacketBoxOpenResult(...));
```

**文件指引**：`packet/PacketCsgoProgress.java`、`packet/PacketCsgoBulkProgress.java`、`packet/PacketRequestBoxItems.java`、`gui/CsboxScreen.java`、`gui/CsboxBulkOverviewScreen.java`

### 5. Attachment / 序列化

**断点**：1.21.8（`MapCodec`）、1.21.11 / 26.x（`IAttachmentSerializer` + `ValueInput/Output`）

#### 代码对照

```java
// 1.21.1-1.21.5: Codec（RecordCodecBuilder.create）
// 1.21.8+:
static final MapCodec<CsboxPlayerData> CODEC = RecordCodecBuilder.mapCodec(...);
// 1.21.11+ / 26.x: attachment 序列化走 IAttachmentSerializer
.serialize(new IAttachmentSerializer<>() {
    public CsboxPlayerData read(Holder<DataComponentType<?>> holder, ValueInput input) {
        return input.read("data", CODEC).orElseGet(CsboxPlayerData::new);
    }
    public void write(CsboxPlayerData data, ValueOutput output) {
        output.store("data", CODEC, data);
    }
});
```

```java
// 1.21.3+：spawnAtLocation 需要 ServerLevel + 事件双端守卫
// 1.21.1:
mob.spawnAtLocation(stack);
// 1.21.3+:
mob.spawnAtLocation((ServerLevel) mob.level(), stack);
if (mob.level().isClientSide()) return;  // 26.x 事件双端触发
```

```java
// 1.21.3+：registry access 从 registryOrThrow 改为 lookup().orElseThrow()
// 1.21.1:
mob.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.LOOTING);
// 1.21.3+ / 26.x:
mob.level().registryAccess().lookup(Registries.ENCHANTMENT).orElseThrow().getOrThrow(Enchantments.LOOTING);
```

**文件指引**：`capability/ModCapability.java`、`capability/CsboxPlayerData.java`、`event/ModEvents.java`、`box/BoxItemCodec.java`

### 6. 背包遍历 / Tooltip / 输入辅助

**断点**：1.21.4（背包字段删除）、1.21.5（tooltip）、1.21.10（`hasShiftDown` 删除）

#### 代码对照

```java
// 背包遍历：1.21.4+ 不能直接访问字段
// 1.21.1:
for (ItemStack stack : player.getInventory().items) { ... }
player.getInventory().armor; player.getInventory().offhand;
// 1.21.4+:
for (int i = 0; i < 36; i++) { ItemStack stack = player.getInventory().getItem(i); }
player.getItemBySlot(EquipmentSlot.HEAD/CHEST/LEGS/FEET/OFFHAND);
// 1.21.11+ / 26.x: 简化遍历非装备槽
for (ItemStack stack : entity.getInventory().getNonEquipmentItems()) { ... }
```

```java
// Tooltip：1.21.5+ 签名改变 + Consumer 回调
// 1.21.1:
appendHoverText(ItemStack, TooltipContext, List<Component>, TooltipFlag);
// 1.21.5+:
appendHoverText(ItemStack, TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag);
// 1.21.11+ / 26.x 额外处理 hideTooltip:
if (display.hideTooltip()) return;
```

```java
// 1.21.10+：Screen.hasShiftDown() 删除，只保留 Options 检测
mc.options.keyShift.isDown();
```

**文件指引**：`packet/PacketCsgoProgress.java`、`packet/PacketCsgoBulkProgress.java`、`item/ItemCsgoBox.java`、`event/ClickEvent.java`、`gui/*.java`

### 7. 命令 / 权限 / 成就

**断点**：1.21.11 / 26.x（权限 API）、1.21.11（criterion 包）、26.2（predicates/triggers）

#### 代码对照

```java
// 权限：1.21.11+ / 26.x
// 1.21.1-1.21.10:
.requires(source -> source.hasPermission(2))
// 1.21.11+ / 26.x:
.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
// 同上：sp.hasPermissions(2) → sp.permissions().hasPermission(...)
```

```java
// 成就：包迁移（24 文件同步）
// 1.21.1-1.21.10: net.minecraft.advancements.critereon
// 1.21.11 / 26.1.2: net.minecraft.advancements.criterion  +  ContextAwarePredicate → ContextAware
// 26.2: net.minecraft.advancements.predicate / predicates / triggers
```

**文件指引**：`command/CsxCommand.java`、`event/LoadErrorAnnouncer.java`、`advancement/OpenedBoxTrigger.java`、`advancement/ModLoadedTrigger.java`

### 8. 命名/结构迁移

**断点**：1.21.11 / 26.x（`ResourceLocation → Identifier`）、1.21.11（NBT 解析）、26.x（`@EventBusSubscriber` 去 `bus=`）

#### 代码对照

```java
// 1.21.11+ / 26.x: ResourceLocation → Identifier（全仓库 24+ 文件）
// 1.21.1: ResourceLocation.parse("minecraft:air"); ResourceLocation.CODEC
// 1.21.11+ / 26.x: Identifier.parse("minecraft:air"); Identifier.CODEC
// 命令参数: ResourceLocationArgument.id() → IdentifierArgument.id()
// 声音: ResourceLocation.fromNamespaceAndPath → Identifier.fromNamespaceAndPath
```

```java
// 1.21.5+ NBT 解析
TagParser.parseTag(tagStr) → TagParser.parseCompoundFully(tagStr);
```

```java
// 26.x: @EventBusSubscriber 去掉 bus 参数（MOD 为默认）
// 1.21.x: @EventBusSubscriber(modid = MODID, bus = Bus.MOD, value = Dist.CLIENT)
// 26.x:   @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
```

**文件指引**：全仓库（`box/*`、`sounds/ModSounds.java`、`command/CsxCommand.java`、`advancement/*`、`packet/*`、`event/BoxOpenedEvent.java`、`gui/*`）、`CsgoBox.java`

### 9. 26.2 专属适配

| 变化 | 26.1.2 | 26.2 |
|---|---|---|
| HUD 显隐 | `options.hideGui = true/false` | `HudVisibility.hide()/show()`（`mc.gui.hud.toggle()`） |
| Screen 跳转 | `setScreen(...)` | `setScreenAndShow(...)` |
| PIP 渲染器 | `Icon3DRenderer(BufferSource)` 构造 + `renderToTexture(State, PoseStack)` | 无参构造 + `renderToTexture(State, PoseStack, SubmitNodeCollector)` |
| PIP 灯光 | `gameRenderer.getLighting()` | `gameRenderer.lighting()` |
| 成就 | `criterion` | `predicates/triggers` + Predicate 显式强转 |
| 动态 item 注册 | `addListener(registerDynamicBoxItems)` 方法内过滤 registry key | RegisterEvent 内 `else if (ITEMS)` 直接注册 |
| 计时 | `mc.level.getGameTime()` tick 计时 | `System.currentTimeMillis()` wall-clock |

**文件指引**：`utils/HudVisibility.java`（仅 v26_2）、`gui/CsboxScreen.java`、`gui/CsboxProgressScreen.java`、`gui/pip/Icon3DRenderer.java`、`gui/pip/Icon3DRenderState.java`、`CsgoBox.java`、`gui/CsboxBulkResultScreen.java`

### 10. forge_26_1_2 实验模块

- **不是**正式平台：源码未提交、不在 CI 矩阵、不参与 mirror/镜像纪律
- 由 `scripts/port-forge-2612.py` 从 v26_1_2 **机械转换** + 手工适配（MinecraftForge 64.1.0，Java 25）
- 与 v26_1_2 差异集中在 **ForgeGradle 7.0.31 + Forge 事件总线**，MC 侧 API 与 v26_1_2 相同（`Identifier`、extract、PIP 等）
- 开发时以 v26_1_2 为基准，跑 `scripts/port-forge-2612.py` 再手工修

---

## 维护说明

- 新增平台断点时：先在 `全量速查矩阵` 更新一行，再在对应主题章节补充代码对照
- 各平台源码永远以基准模块（v1_21_1 / v26_1_2）为 diff 基线，用 `diff -rq` + 剥离 package 行对比
- 改动涉及平台时用 `clean` 编译确认（增量缓存可能造假象）

## 11. AnimRenderOps 渲染门面（2026-08-09 重构）

> 每个平台的 `utils/AnimRenderOps.java` 是**唯一的渲染原语适配点**：屏（CsboxScreen /
> CsboxProgressScreen / CsboxBulkOverviewScreen / CsboxBulkResultScreen / CsboxLookItemScreen /
> CsboxBulkResultScreen）与逻辑助手（IconListTools / GuiItemMove / ButtonPalette）只经它调用渲染 API。

| 平台 | era | 说明 |
|---|---|---|
| v1_21_1 | `legacy` | `GuiGraphics` + 立即模式；`blitTextured` 内部强制 SRC_ALPHA blend；`renderBlurredBackground` 反射桥接 |
| v26_1_2 | `decoupled` | `GuiGraphicsExtractor` + RenderPipelines（自带 blend 状态）；`setBlendNormal`/`flush` 空操作 |
| v26_2 | `decoupled` | 与 26.1.2 同代，整文件镜像；HUD 差异由 `HudVisibility` 承载，不入本门面 |

**公开 op（13 个，跨平台签名一致）**：`blitTextured`×3（6 参 / 7 参 texW,texH / 13 参 UV+tint）、
`fill`、`fillGradient`、`scissor`、`scissorDisable`、`setBlendNormal`、`flush`、
`renderBlurredBackground`、`renderItem2D`、`renderItem3D`、`supports3D()`。

- UV+tint 变体（工具栏雪碧图）：legacy 内部经 `RenderSystem.setShaderColor` 应用 tint，
  decoupled 传 26.x blit 末参 tint —— 签名统一，实现随时代
- `renderItem2D`（26.x）：per-item bounding box 居中；`renderItem3D`（26.x）：PIP 路径
  （`Icon3DRenderState` + `submitPictureInPictureRenderState`），radians→degrees 转换在门面内部
- 屏内 `RenderFontTool` 文本调用不入门面（drawString 各平台签名一致）；v1_21_1 / forge_1_20_1 的 TACZ 视口
  （`TaczInspectViewport`）是独立路径，不并入 `renderItem3D`；TACZ 枪默认 3D 展示走 `renderItem3D → renderGunModel3D`
- **新增原语必须三平台同步补**，签名漂移由 `scripts/check-animops-drift.sh` 守护（CI 已接线）
- 1.21.1 残留 RenderSystem 状态操作（CsboxScreen 深度测试开关、CsLookItemScreen 工具栏 tint
  循环）属有意保留，非 draw 原语
