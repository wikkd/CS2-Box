# Task 7 Report: 中央展示 = 终端箱池实际物品 3D 模型

**Status: DONE**
**Commit: `9624b01` — feat(terminal): center display shows real box-pool item 3D per rarity tier**
**Branch: `task/terminal-t7` (worktree /Users/shuangyuexingxun/Desktop/.cs2box-wt/t7, base 0e5eba7)**

## 改动（9 文件，三平台 × 3）

### TerminalScreen.java（×3）
- 构造器 `TerminalScreen()` → `TerminalScreen(ItemStack terminalStack)`，`model.start` 后调 `offerRegion.setGradePools(buildGradePools(terminalStack))`
- 新增 `buildGradePools`：`new List[6]`（index = gradeLevel 1..5），`ItemCsgoBox.getDefinition(stack).ifPresent(...)` 按 `BoxDefinition.gradeLevel(g.id())` 填池（越界/0 跳过），返回原样 List 引用（不 copy——决策记录要求每轮取样才 copy）
- imports：`BoxDefinition`、`ItemCsgoBox`、`ItemStack`（v26_1_2/v26_2 各自平台包名；v1_21_1 亦 `com.reclizer.csgobox.v1_21_1.{box,item}`）

### ClickEvent.java（×3）
- `new TerminalScreen()` → `new TerminalScreen(heldItem.copy())`（copy 防止后续 heldItem 槽位变化污染池；v26_2 保留 `setScreenAndShow`，v1_21_1/v26_1_2 保留 `setScreen`）

### TerminalOfferRegion.java（×3）
- 字段：`List<ItemStack>[] gradePools`、`Map<Integer,ItemStack> roundItemCache`（HashMap）、`Random itemRnd`
- `setGradePools`：赋值 + `roundItemCache.clear()`（新会话/新箱子不串轮）
- `gradeForOffer`：`SKIN_RARITY[skinIdx]` = "purple" → 4，否则 3（HTML rarity → box grade）
- `offerItem`：`roundItemCache.computeIfAbsent(offer.round(), ...)` 每轮一次取样；从 gradeForOffer 向下退级到 1，池 null/空跳过；全空回退 `new ItemStack(Items.IRON_SWORD)`；取样 `pool.get(rnd.nextInt(size)).copy()`，不抛异常
- render() 中央物品：`new ItemStack(Items.IRON_SWORD)` → `offerItem(offer)`

## 编译验证（全部 clean compileJava 通过）
- `:v26_1_2:clean compileJava -Pactive_versions=26.1.2` ✅（基准先改先编译）
- `:v26_2:clean compileJava -Pactive_versions=26.2` ✅
- `:v1_21_1:clean compileJava -Pactive_versions=1.21.1` ✅（仅既存 ItemCsgoBox deprecation 提示，与本任务无关）

## 环境备注
- worktree 无 local-repo：从主工作区复制了 `com/tacz`（55MB）与 `simplebedrockmodel`（jarjar 提取物），未跑 download-tacz.sh
- v26_2 首次 clean 编译遇到 "The run type 'client' was not found" 配置错误——为 NeoGradle userdev 跨版本 daemon 冲突（AGENTS.md 已知限制），重试即过，非常规问题
- 镜像纪律遵守：v26_2 定点合入（仅 package/import 路径差异），无整文件覆盖；未用 mirror.sh（非纯新增）

## 自审
- computeIfAbsent 键 = round ✅；池 null/空逐级下退 + 全空 IRON_SWORD 回退（无异常）✅；setGradePools 清缓存 ✅；三平台 ClickEvent 平台包名各自正确（v26_2 `setScreenAndShow` 保留）✅
- 提交文件级限定 9 个路径，无吞并
