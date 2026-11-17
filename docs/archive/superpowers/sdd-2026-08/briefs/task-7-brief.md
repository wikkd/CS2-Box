### Task 7: 中央展示 = 终端箱池实际物品 3D 模型

**Files:**
- Modify: `{3 平台}/.../gui/TerminalScreen.java`、`{3 平台}/.../event/ClickEvent.java`、`{3 平台}/.../gui/terminal/TerminalOfferRegion.java`

**决策记录：** 终端机绑定 `csgobox:terminal` 箱定义（BoxDefaults 生成，池 = 真实 MC 物品）。中央物品 = 按报价稀有度映射箱等级（purple→grade4 钻石系、blue→grade3 黄金系），每轮随机取样一次并缓存；池为空回退铁剑。卡片仍显示虚构皮肤文案。

- [ ] **Step 1: v26_1_2 `TerminalScreen`**：构造器接栈并构建等级池：
```java
public TerminalScreen(ItemStack terminalStack) {
    super(Component.translatable("gui.csgobox.terminal.title"));
    this.model.start(System.currentTimeMillis());
    offerRegion.setGradePools(buildGradePools(terminalStack));
}

@SuppressWarnings("unchecked")
private static java.util.List<ItemStack>[] buildGradePools(ItemStack terminalStack) {
    java.util.List<ItemStack>[] pools = new java.util.List[6]; // index = gradeLevel 1..5
    ItemCsgoBox.getDefinition(terminalStack).ifPresent(def ->
            def.grades().forEach(g -> {
                int lvl = BoxDefinition.gradeLevel(g.id());
                if (lvl > 0 && lvl < pools.length) {
                    pools[lvl] = g.items();
                }
            }));
    return pools;
}
```
  新增 imports：`com.reclizer.csgobox.v26_1_2.box.BoxDefinition`、`com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox`、`net.minecraft.world.item.ItemStack`。
- [ ] **Step 2: v26_1_2 `ClickEvent`**：`new TerminalScreen()` → `new TerminalScreen(heldItem.copy())`（v26_2 同位置但 `setScreenAndShow`；v1_21_1 同 44 行 `setScreen`）
- [ ] **Step 3: v26_1_2 `TerminalOfferRegion`**（按内容定位）：
  - 字段：`private java.util.List<ItemStack>[] gradePools;`、`private final java.util.Map<Integer, ItemStack> roundItemCache = new java.util.HashMap<>();`、`private final java.util.Random itemRnd = new java.util.Random();`
  - 方法：
```java
public void setGradePools(java.util.List<ItemStack>[] pools) {
    this.gradePools = pools;
    this.roundItemCache.clear();
}

/** HTML rarity → box grade: purple=restricted(4), blue=mil_spec(3)。 */
private static int gradeForOffer(NegotiationModel.Offer offer) {
    return "purple".equals(NegotiationModel.SKIN_RARITY[offer.skinIdx()]) ? 4 : 3;
}

/** 每轮一次取样（缓存），等级为空时向下退级，全空回退铁剑。 */
private ItemStack offerItem(NegotiationModel.Offer offer) {
    return roundItemCache.computeIfAbsent(offer.round(), r -> {
        for (int g = gradeForOffer(offer); g >= 1; g--) {
            java.util.List<ItemStack> pool = gradePools != null && g < gradePools.length
                    ? gradePools[g] : null;
            if (pool != null && !pool.isEmpty()) {
                return pool.get(itemRnd.nextInt(pool.size())).copy();
            }
        }
        return new ItemStack(Items.IRON_SWORD);
    });
}
```
  - `render()` 物品块：`ItemStack stack = new ItemStack(Items.IRON_SWORD);` → `ItemStack stack = offerItem(offer);`
- [ ] **Step 4/5/6: v26_2、v1_21_1 合入**（v1_21_1 注意 `ResourceLocation`/`GuiGraphics` 与本任务无关，`ItemCsgoBox.getDefinition`/`BoxDefinition.gradeLevel` 同包存在）**+ 三平台 clean 编译**
- [ ] **Step 7: 提交**：`feat(terminal): center display shows real box-pool item 3D per rarity tier`

