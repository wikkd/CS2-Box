# 更新日志

## [未发布]
### 新增
- **KubeJS/Java 扩展事件三件套（v1.0.8）**：新增 `BoxOpeningEvent`（开箱前、RNG/消耗之前，**可取消**——权限门槛 / 任务解锁 / 每日限量 / 活动箱直接脚本化，单开 count=1、批量按服务端授权批次触发一次，取消整批中止且不设冷却）、`TerminalBuyEvent`（终端机成交后通知：grade / price（含磨损惩罚）/ wearVal / item / round，覆盖「终端购买不触发 BoxOpenedEvent」的独立管线，支撑首次获得登记与军械点经济记账）、`ArmoryRecycleEvent`（武库拆解台消耗物品前，**可取消**——物品黑名单刷点屏蔽，取消后输入保留、零产出）。三事件四平台同步（v1_21_1 / v26_1_2 / v26_2 走 NeoForge 事件总线 + `ICancellableEvent`，KubeJS 脚本 `event.cancel()` 原生可用；forge_26_1_2 走 Forge `MutableEvent` + 实例取消标志 + 自有 `BUS`），`docs/KUBEJS-EVENTS.md` 新增事件一览表、脚本示例与边界契约（RNG 后/发包前仍为禁区），并补充 `Java.loadClass` 只读访问配方（`BoxRegistry` / `BoxDefinition` / `CsgoBox.CONFIG` / `BoxJsonLoader.getLastLoadErrors`）。各平台 `compileJava` 通过、`:common:test` 通过、`check-animops-drift.sh` 无漂移。
- **开箱屏背景模糊增强（`blurRadius` 配置）**：模组半透明屏（主屏 / 出货屏 / 批量总览 / 批量结果 / 确认屏 / 进度屏）打开期间，临时把原版 `menuBackgroundBlurriness` 提升到新配置 `blurRadius`（默认 `8`，原版默认 5；`0` = 不干预跟随原版），屏幕关闭后恢复原值——未装 Blur 模组时背景模糊感更强；安装 Blur 模组时仍由其自身半径接管（该路径用户在 Blur 配置里调）。实现为 `ScreenEvent.Opening` 客户端事件单点挂钩（四平台新增 `event/ScreenBlurBoost`，v26_2 经 `mirror.sh new` 镜像），`common/CsboxConfigDefaults` 新增 `BLUR_RADIUS` / MIN / MAX 常量，四平台 `clean compileJava` 通过、`:common:test` 152 用例全绿。

 - **武库商小屋接入原版村庄道路**：新增 `arms_dealer_hut_{plains,desert,savanna,snowy,taiga}.nbt` 五群系副本——在用户手建版小屋东墙开口 `(7,1,6)` 处补 jigsaw 块（`name=target=minecraft:building_entrance`，`orientation=east_up` 对接街道常见 `west_up` 房屋接口，`pool=<群系>streets`，`joint=aligned`），并注册进 5 个原版村庄房屋池（`data/minecraft/worldgen/template_pool/village/<biome>/houses.json`，weight 6）——村庄自然生成时小屋从街道接口正常连接生成（含军火商村民与商店宝箱）；`scripts/add-village-jigsaw.py` 为唯一事实来源（基础模板 `arms_dealer_hut.nbt` 保持无 jigsaw 不变，独立 structure_set 野外生成不受影响）。

- **武库商小屋结构改由玩家手建版（替换脚本版）**：`csgobox:arms_dealer_hut` 模板换为游戏内手工建造 + 结构方块导出的布局（8×7×9，深板岩砖地基 + 红砖墙 + 深板岩瓦屋顶 + 左右侧铁窗，前后墙不再嵌铁窗）；新增 `scripts/import-arms-dealer-hut.py` 作为导入唯一事实来源——从结构方块导出 NBT 生成模板时保留方块布局原样、去掉 `DataVersion`（1.21.1 与 26.x 均按原样加载）、村民实体规范化（删除写死的世界坐标 Brain 记忆 / Offers / 固定 UUID 与动态字段，补 `Xp:1` 免疫 26.x 解雇 + 名字牌 + `PersistenceRequired`）；`gen-arms-dealer-hut.py` 弃用保留（防呆注释防误跑覆盖）。
 - **JEI 开箱概率分类 + 盒定义多人同步**：新增 JEI 集成（`v1_21_1` / `v26_1_2` / `v26_2` 三平台）——自定义「开箱概率」分类（recipeType `csgobox:box_open`），每个箱子一条配方：钥匙输入槽、箱子输出槽、物品池输出网格 20 格（4×5，超出画「…共 N 件」省略），左侧文本列显示掉落率、各级权重百分比（按 `grade.color()` 着色，最多 4 行 + 「…共 N 级」省略）与实体掉率行（实体名经 `EntityChineseMap`）；任意掉落物可反查「哪个箱子掉、单件概率多少」（tooltip「[等级] 权重 x/y · 单件 z%」）；分类图标用箱子物品栈（回退 `premium_supply_box`）。钥匙 `smithing_transform` 为原版数据驱动配方，JEI 已自动显示、无需代码适配。概率口径 = 服务端实际算法（common 新增纯函数 `BoxOdds`：`totalWeight` / `gradeChance` / `itemChance`，与 `OddsCalculator.pickGrade` / `GradeMap.pickRandom` 语义一致，`:common:test` 新增 7 用例全绿）。**盒定义多人同步**：新增 `PacketSyncBoxDefinitions`（playToClient 全量 `List<BoxDefinition>`，复用 `BoxDefinition.STREAM_CODEC`），玩家加入 / `/csbox reload` / 文件热重载后广播，客户端 `clear()`→`register()` 重灌注册表并触发 JEI 配方刷新（`BoxJeiSync` 静态桥，无 JEI 时零依赖）——纯多人客户端 JEI 数据完整可用，顺带修复此前 MP 客户端缺盒定义只能回退默认权重的问题。JEI 版本固定（19.44.0.401 / 29.22.0.73 / 30.20.0.154），`neoforge.mods.toml` 可选依赖（`mandatory=false`，side CLIENT）。`forge_26_1_2` 跳过：Modrinth 无 JEI 26.x Forge 构建。三平台 `clean compileJava` 通过。
 - **终端机报价磨损改为随机 + 无耐久条物品磨损惩罚（v1.0.8）**：终端机 5 轮报价的磨损值不再按皮肤固定（MW 0.1138 / WW 0.4022 / FT 0.3074），改为**与开箱同款的服务端随机**——`TerminalSession.create` 会话创建时每轮 `rnd.nextFloat()`（0~1 均匀，服务端采样权威，`NegotiationModel` 无 offerSource 的兜底同规则）；聊天卡片磨损行改显该报价**实际**档位（`WearBands.tierIndex(offer.wearVal())`，删除废弃常量 `SKIN_WEAR_VAL` / `SKIN_WEAR_KEYS`）。配套：购买时若该轮实际给予的物品**没有耐久条**（`isDamageableItem()` 为假），磨损不再无意义——按 `WearPenalty`（common 纯函数，`ceil(磨损值 × 20)`，每 5% 磨损 = 1 武库点数，0~+20）在等级基础价上追加惩罚价：磨损越多，购买点数越多（FN 边缘 +0 ~ 战痕累累 +20）；有耐久条的物品维持原行为（磨损 → 扣耐久，不加价）。定价服务端权威（`PacketTerminalBuy`），客户端 `TerminalOfferItems` 同公式显示最终价，报价卡片 / 计数 / 接受按钮价格自动含惩罚，确认对话框新增「含磨损惩罚 +X」提示（新 lang 键 `csgobox.terminal.confirm.price.penalty` 中英）。四平台同步（common `WearPenalty` + 单测，各平台 `clean compileJava` 通过）。
- **武库商小屋（世界生成结构）**：新增 `csgobox:arms_dealer_hut` 结构——军火商的野外据点：深板岩砖地基 + 红砖墙 + 深板岩石砖房顶小屋内置武库拆解台（深板岩柜台展示）+ 军火商村民（`arms_dealer` 职业 Lv.1，名字牌「军火商」，`PersistenceRequired` 防消失 + `Xp:1` 免疫 26.x `ResetProfession` 解雇，保证认领工作台前职业稳定）+ 商店宝箱（战利品表 `csgobox:chests/arms_dealer_hut`：武库点数/基础箱/铁钥匙/绿宝石/铁锭）+ 工作台/床/灯笼/铁栏杆窗。纯数据驱动（`common/src/main/resources/data/csgobox/{structure,worldgen,loot_table,tags}`，四平台共享、零 Java 改动）；structure_set（spacing 30 / separation 10）+ jigsaw 单模板生成，覆盖陆地群系（自定义 biome tag `#csgobox:has_structure/arms_dealer_hut`）；模板 NBT 由 `scripts/gen-arms-dealer-hut.py` 生成（NBT 不手改，脚本为唯一事实来源），villager/chest NBT 与 structure JSON 格式已对照 1.21.1 与 26.2 原版 jar 核验。小屋村民与交易升级机制不变（Lv.1 起，交易升级）。
- **军火商台词池扩充 + 每局随机台词**：谈判台词由固定 5 条剧本扩展为 12 条池（新增 7 条商人台词，如「略有磨损。有一两道划痕，但不耽误干活。」「眼光这么高？挺好。接下来这件才是黄金标准。」等），每局开局从池中随机抽取 5 条不重复组成当轮剧本——重开终端/新谈判台词有变化，5 轮报价结构不变；`NegotiationModel` 新增 `roundLine` 实例洗牌（Fisher-Yates 部分洗牌），`start()` 与新实例构造各洗一次（restore 恢复的会话沿用实例内台词顺序，历史台词不受影响）；中英 lang 新增 `line.5`~`line.11`。`:common:test` 更新（台词断言改为池成员校验 + 新增「5 轮台词唯一」用例）。
 - **终端机 type 字段唯一判定 + 字段严格分离（v1.0.8）**：JSON `type` 字段成为物品注册的唯一判定机制——`"type": "terminal"` 注册 `ItemTerminal`，`"type": "csbox"`（或省略）注册普通 `ItemCsgoBox`；运行时 `BoxDefinition.isTerminal()` 同步改为读 `type`，不再按「id + key」派生。**终端机不再有 `key` 字段**（默认 `terminal.json` 自动生成且含 `"type": "terminal"`，schema 验证器对终端机残留 `key` 报错，`/csbox info` 不再显示终端机的钥匙行）。旧版 v1.0.7 配置自动迁移：注册前 `BoxDefaults.upgradeLegacyTerminalConfig` 为无 `type` 的 `terminal.json` 补 `"type": "terminal"` 并删除遗留 `key`；迁移未覆盖时 `terminal.json` 缺 `type` 会被拒绝加载并给出明确 LoadError——**杜绝静默退化成免钥匙免费开箱**。四平台同步（`BoxDefinition` 增 `type` 字段含 stream codec，各平台 `clean compileJava` 通过，`:common:test` 新增 5 个 type 分离用例全绿）。
- **终端机超时自毁（物品消失）**：超时未成交不仅释放谈判锁，**终端机物品本身也销毁**——终端机首次打开时服务端在其物品组件上打唯一 `terminal_uid`（`DataComponentType<String>`，四平台注册），会话与该 uid 绑定；倒计时归零时服务端从玩家背包（主栏+护甲+副手）销毁持有该 uid 的终端机并提示「终端机已超时自毁。」，改名/放箱子不影响定位；离线或放在容器里的终端机，下次打开时由「已销毁 uid 集合」兜底当场销毁（开屏返回 FAILED 状态而非悬挂）。`PacketTerminalOpen` 改为以服务端主手物品为权威并处理销毁路径，`onTerminalState` 先重置静态物品表防残留上一台数据。
- **终端机计时器落实（服务端权威，原版 tick 驱动）**：倒计时改由服务端持有——四平台 `ModEvents.serverTick`（原版 `ServerTickEvent`）每秒驱动 `TerminalSessionManager.tickSessions`，按世界运行时间（世界游戏刻 × 50）推进每会话倒计时——世界暂停/服务端停机不计时，重启后精确续算；玩家打开终端机与军火商对话即开始计时，超时未成交的会话与 5 轮拒绝同样自毁（`FAILED` + 新系统消息「交易超时，军火商已离开。」，锁释放、下次重开全新谈判）。common `NegotiationModel` 新增 `tickServer`（只推进倒计时与过期，不碰轮次转换——打字/出价动画仍归客户端）与 `expire`；客户端屏同步自过期（倒计时归零原地翻转 FAILED 横幅），`PacketTerminalClose` 不再上报倒计时（服务端权威，杜绝客户端回填时间复活已过期会话），`PacketTerminalState` 增补 `boxId` 字段用于屏侧校验（顺带修复「快速切换两个终端机时迟到的旧会话快照误入新屏」竞态）。
- **终端机会话锁（重开续谈，未成交不重开）**：服务端按「玩家 UUID + 箱子 ID」持有每个终端机的谈判会话（新增 `TerminalSessionManager` / `TerminalSession`，common `NegotiationModel` 补 `Snapshot`/`restore`/`rejectForced`/`buyForced`/`syncClose`），开屏发 `PacketTerminalOpen` 取锁并下发全量快照 `PacketTerminalState`（轮次 / 状态 / 聊天历史 / 倒计时 / 报价上限 / 5 轮报价与物品 / 区域 10 槽位物品），关屏发 `PacketTerminalClose` 钉住当前轮与 TYPING/PENDING 状态——玩家未成交或未满 5 轮拒绝前重开终端机，对话、报价、物品与上次离开完全一致；5 轮全部拒绝（FAILED）或成交（CLOSED）后锁自动释放，下次开启为全新谈判、重新采样。拒绝改走 `PacketTerminalReject` 服务端强制推进（客户端只播本地动画，不依赖回包），购买 `PacketTerminalBuy` 按服务端当轮实际物品逐字段校验（消除伪造 `offerItem` 提级低买）。
- **终端机会话持久化 + 转手锁死 + 购买销毁**：会话与「已销毁 uid 集合」落盘 `<world>/csgobox/terminal_state.bin`（`TerminalStateStore`，魔数 + VERSION=3，随服务端启停 bind/unbind，任何变更即时写盘；v3 起 SystemEntry 历史带可翻译参数）；超时未能在原主身上确认销毁（离线 / 放容器 / 已转手）的终端机，uid 进已销毁集合——该 uid 无论落到谁手里，下次打开当场销毁并清理过期 uid；已打开但未过期、且正被**他人**活跃会话持有的终端机转为锁死态，军火商发「去问问xx吧。」——物主名字盖在终端机物品组件 `terminal_owner` 上（服务端创建新会话时盖章，转手/重启/物主离线都可用），另优先用在线玩家名处理改名（新 lang key `csgobox.terminal.sys.locked`，FAILED 状态天然禁用接受/拒绝，服务端买/拒按发送者会话键校验，无法越权交易）；购买成功后终端机物品与 uid 一并销毁并释放会话锁（`PacketTerminalBuy` 成功路径 `removeByUid` + `setCount(0)`）。四平台同步，各平台 `clean compileJava` 通过，`:common:test` 与 forge L0-L3 门禁 7/7 PASS。
- **终端机默认超时改为 3 小时 + 删除启动屏钥匙计数残留**：`NegotiationModel.COUNT_INITIAL_MS` 由遗留的调试值「2 天 23:57:45」改为 3 小时（common 单点，四平台共用）；`TerminalBootScreen` 移除 `boxKeyCount` / `keyRl` / `itemKey` 及钥匙图标与「需要钥匙」文案——终端机打开不消耗钥匙，启动屏不再误导玩家（四平台同步，各平台 `clean compileJava` 通过，`:common:test` 104 用例通过，forge L0-L3 门禁 7/7 PASS）。
- **终端机打开悬挂兜底**：服务端 `PacketTerminalOpen` 对「主手已非终端机」的提前 return 改为回 `PacketTerminalState.unreachable(...)` FAILED 快照（新增 `sys.unreachable` lang key「无法联系军火商，请重新打开终端机。」），不再静默不回复；客户端 `TerminalScreen` 新增 5 秒无有效回复超时——仍未收到匹配 `requestId` 的会话快照时发提示并关闭屏幕，杜绝「开屏瞬间切热栏 / 死亡 / 服务端异常」导致的永久空白等待（四平台同步，各平台 `clean compileJava` 通过，`:common:test` 通过，forge L0-L3 门禁 7/7 PASS）。
- **终端机谈崩自毁（5 轮拒绝销毁物品）**：谈判破裂与超时/成交一致，终端机**物品本身**消失——`PacketTerminalReject` 服务端强制推进到第 5 轮变 `FAILED` 时，主手终端机当场 `setCount(0)` + `removeByUid` 立即释放会话锁 + 系统消息「谈判破裂，军火商已离开。」（新增 `sys.broke` 中英 lang key）；未满 5 轮仍只 `markDirty()`。玩家发拒绝时终端机必然在主手（reject 以主手定位会话），无需超时路径的已销毁 uid 兜底；销毁后关屏 `PacketTerminalClose` 查表 null 安全 no-op。终态语义统一：成交 / 谈崩 / 超时三种终态全部销毁物品（四平台同步，各平台 `clean compileJava` 通过，`:common:test` 通过，forge L0-L3 门禁 7/7 PASS）。
- **Blur 模组背景模糊软适配**（`blur` 可选集成，不强制加载、无依赖声明）：主屏 / 出货屏 / 批量总览屏 / 批量结果屏 / 确认屏背景由不透明 `0xFF2a2a33` 改为半透明主题灰 `0x8C2a2a33`（新增 `backgroundStyle` 配置，`TRANSLUCENT` 默认 / `OPAQUE` 恢复旧观感；`common/OverlayColor#getBackgroundTranslucent()`），模糊世界透过背景显示；屏幕回归 vanilla 背景管线（26.x 删除 `CsboxScreen.extractBackground` override、全屏 fill 移入 `renderBg`，三平台共用一个 `gui/UiBackdrop#fill()` 取值助手），安装 Blur 模组后其淡入动画与模糊半径/渐变自动生效；进度屏 26.x `extractBlurredBackground` 在 `ModList.isLoaded("blur")` 时改走 vanilla 实现（Blur 动画生效），未装时维持"无视选项强制模糊"现状；终端机屏保持不透明（设计决策）。未装 Blur 时半透明背景遵循原版 `menuBackgroundBlurriness` 选项。设计文档：`docs/superpowers/specs/2026-08-10-blur-mod-adaptation-design.md`
- **恢复批量开箱**（1.0.6 屏蔽 → 1.0.7 恢复）：移除 1.0.6 的两处屏蔽——客户端入口 `ClickEvent` 的 shift 硬编码 `false`（恢复 `mc.options.keyShift.isDown()`，Shift+右键进总览屏）与 `PacketCsgoBulkProgress` 的 `BULK_OPEN_ENABLED` 服务端开关；恢复服务端权威计数（箱/钥匙/`bulkOpenCount` 上限）→ 异步线程池计算 → 主线程扣减的完整链路；另修复恢复后暴露的边界缺陷：`finalizeBulkOpen` 复核时 `actualK` 未 clamp 到已计算结果数（异步窗口内库存增长会致 `results.subList` 越界、整批中止且重试必失败），现按 `Math.min(actualK, results.size())` 截断，剩余箱子下轮再开；1.21.1 / 26.1.2 / 26.2 三平台同步恢复（forge_26_1_2 实验模块一并同步）。
- **`/csbox nbt hand` 命令**：打印主手物品的序列化 JSON（新增 `BoxItemCodec` 统一物品序列化，`tag` 字符串与 `components` 元数据均解析为扁平 key-value），超过 20000 字符截断提示；`/csbox` 裸命令与 `help` 子命令现执行 2 级权限检查
- **forge_26_1_2 纳入同步开发（1.0.7 线）**：随 `v26_1_2` 基准同步终端机 / 军火商 / 高级箱 / 村民等 19 个文件，手工适配 forge API（`ModItems` 补 `armory_point` / `terminal` / `premium_supply_box` 注册与创造栏条目、`ModBlocks` POI `Set` 包装、`CsboxConfig` 补 `BackgroundStyle` UI 段、`AnimRenderOps.renderItem3D` 适配 forge `Icon3DRenderState` 13 参 Euler 签名、`IconListTools` 移植 alpha 淡入版本、`ButtonPalette` 补 `CLOSE`、`GuiItemMove` 补 `Quat` 重载、`PacketTerminalBuy` 改 forge `CustomPayloadEvent` API、`Networking` 注册 terminal 包）；`build.gradle` 删除 1.0.7 排除清单；`clean compileJava` 通过，`test-forge-2612.sh` L0-L3 门禁 7/7 PASS，`PlatformSmokeTest` 基线守卫更新为 1.0.7 同步断言。
- **forge_26_1_2 同步第二轮（Blur + 批量开箱管线对齐，仍 1.0.6 版本标签）**：
  Blur 软适配合入 forge 全部 6 屏（`CsboxScreen` 删 `extractBackground` override、
  背景 fill 移入 `renderBg` 走 `UiBackdrop.fill()` + `AnimRenderOps.fillGradient`；
  出货 / 确认 / 批量总览 / 批量结果 4 屏同管线；`CsboxProgressScreen`
  `extractBlurredBackground` 在 `ModList.isLoaded("blur")` 时走 vanilla 否则
  `AnimRenderOps.renderBlurredBackground`），顺带并入出货屏进入淡出 + `ItemDrag3D`
  拖拽与进度屏拒绝横幅；批量开箱对齐 v26：`PacketCsgoBulkProgress` 换
  `BoxStripGenerator`（`BulkOpenResult` 补 `fallback` 第 8 参、finalize 补回退分级
  修复循环，forge `PacketCsgoProgress` 新增 boxId 版 `resolveGrade`），
  `PacketBoxBulkResult` 按 `BULK_PER_PACKET=32` 分块、`MAX_PENDING_BULK` 64，
  客户端 `drainBulkChunks()` while 聚合全部 chunk；单开路径同步收口：
  `PacketCsgoProgress` 弃用手写 `itemList`+`AnimationStrip` 循环，改走
  `GradeMapCache.get(boxId)` 缓存池 + `BoxStripGenerator.generate` 统一抽条
  （并删除旧 Map 版 `resolveGrade`，仅留 boxId 版）；`/csbox` 命令按 v26
  收口对齐（`set`/`give`/独立 `errors`/`tutorial` 子命令移除，仅留裸命令帮助、
  `info` 含可选 box 参数并附加载错误列表、`reload` 含 `tutorial` 子命令、
  `nbt hand` 任意玩家可用，`BoxItemCodec` 补 `gson()` 访问器）；
  `clean compileJava` 通过，L0-L3 门禁 7/7 PASS。
- **AnimRenderOps 渲染门面（三平台渲染收口重构）**：每平台一份 `utils/AnimRenderOps.java` 作为**唯一渲染原语适配点**（文件头 `// era: legacy|decoupled` 标注时代，26.x 同代整文件镜像），6 屏（CsboxScreen / CsboxProgressScreen / CsboxBulkOverviewScreen / CsboxBulkResultScreen / CsboxLookItemScreen / CsboxConfirmScreen）+ 3 助手（IconListTools / GuiItemMove / ButtonPalette）的全部原始 draw 调用（`blitTextured` / `fill` / `fillGradient` / `scissor` / `flush` / `renderBlurredBackground` / `renderItem2D` / `renderItem3D`，共 13 个公开 op）收口到门面，全仓零原始 draw 调用残留（grep 审计）。关键点：legacy 门面内部强制 SRC_ALPHA blend、decoupled 走 RenderPipelines（自带 blend 状态，`setBlendNormal`/`flush` 空操作）；26.x `renderItem2D` per-item bounding box 居中、`renderItem3D` PIP 路径的 radians→degrees 转换内聚于门面；新增 UV+tint 雪碧图变体（CsLookItemScreen 工具栏，legacy 经 `RenderSystem.setShaderColor`、decoupled 传 blit 末参 tint）。同步清除 3 屏的帧首三连（`setShaderColor(1,1,1,1)`+`enableBlend`+`defaultBlendFunc`），1.21.1 残留 RenderSystem 仅深度开关与工具栏 tint 循环（有意保留）。跨平台签名一致性由 `scripts/check-animops-drift.sh` 守护（3 平台全 OK，CI `common-test` job 已接线），三平台 clean 编译验证通过。
- **终端机物品 `terminal`**：GLB 造型 3D 体素模型（2.5D 等距观感）+ 原创 4 面 PBR 贴图 + 创造标签注册
- **终端机屏幕 `TerminalScreen`（HTML 原型全量迁移）**：`design/terminal-chat.html`（1086 行）11 区域全部实现，三平台（1.21.1 / 26.1.2 / 26.2）同构；`common/terminal/` 新包承载纯 Java 状态机与时间轴（`NegotiationModel` 5 轮谈判状态机 IDLE/TYPING/PENDING/ACCEPT_BUSY/REJECT_BUSY/CLOSED/FAILED + 1100ms 打字锁 + 倒计时 2天23:57:45 每秒递减；`TerminalAnims` cubic-bezier/缓动/错峰翻牌/扫描/轮换纯函数；`WearBands` 五档磨损；`TerminalPalette` 调色板），JUnit 覆盖（谈判全流程/倒计时长跳/缓动/档位）；平台层拆 `gui/terminal/` 4 个渲染助手（ChatRegion / ActionBar / OfferRegion / BottomRow）只经 AnimRenderOps 原语渲染（门面 13 op 零新增，drift 检查保持 3 平台 OK）；10 个预烘焙 PNG 资产（圆角白膜/打字点/512px 点阵 tile/扫描带/径向光/马赛克头像/灰度水印/3 把武器 14° 斜置双色渐变）由 `scripts/gen-terminal-assets.py` 生成并经 `TerminalAssetsTest` 守护；交互：长按胶囊 700ms 确认、批量上限下拉、3D 检视自转拖拽、ESC/✕ 关闭恢复 HUD（26.2 走 `HudVisibility.show()`）；1.21.1 `AnimRenderOps` tint 变体内部补 `setShaderColor(1,1,1,1)` 复位（根治历史泄漏隐患）、`RenderFontTool` 补 `drawStringClamped` 双重载；lang 增补 26 键（对话 5 条/皮肤名/稀有度/计数/提示/系统消息，中英）；移除 `CsboxProgressScreen 2.java` 残留副本
- **`BoxJsonLoader` 解析缓存**：JSON 按 SHA-256 内容指纹缓存解析结果，未变更文件跳过重复解析（`reload` / 首次启动多平台共享加载场景收益）；教程下载改后台线程执行，不再阻塞启动主线程
- **`/csbox` 命令收口**：`set` 调试子命令退役，`errors` / `tutorial` 并入 `info` / `reload`，帮助文本重写

### 更改
- **终端机报价与物品改为服务端采样**：5 轮 offer（皮肤 / 磨损 / 风格 / 编号 / 图案）+ 每轮实际给予物品 + 区域 10 槽位物品全部在会话创建时由服务端从箱子分级池一次性采样（原客户端 `TerminalOfferItems` 随机逻辑迁至 `TerminalSession.create`）；`TerminalOfferItems` 改静态查表、`TerminalOfferRegion` 删除本地随机池与逐轮缓存，客户端不再本地随机——重开一致性由服务端快照保证，客户端只负责渲染。
- **终端机供给与宝箱统一（空箱子 + 按注册入栏）**：首次启动不再自动写入
  `config/csbox/terminal.json`（删除默认终端配置），终端机与 `csbox` 一样只在
  玩家创建 `terminal.json` 后出现在创造物品栏；高级箱同步取消自动生成
  （`writeDefaultPremiumBoxIfMissing` 及其默认配置删除，`premium_supply_box.json`
  由玩家/服主自行创建后入栏）；配置 JSON 不再需要 `type` 字段，箱子类型由
  `key` 推导（仅 `csgobox:terminal` + `minecraft:air` 钥匙视为终端机，air 单独
  出现不会误判）。
- **终端机与宝箱共享逻辑封装**：`BoxDefinition.isTerminal()` 集中类型判定；
  `ModItems.boxItemFor()` 统一「定义 → 物品类」映射，创造栏单循环覆盖终端机 /
  高级箱 / 动态箱子（顺带修复高级箱在创造栏重复出现两次）；`ItemCsgoBox.openScreen()`
  多态打开入口（终端机覆写为启动屏），右键事件不再按物品类先后分支；怪物掉落
  复用同一映射，终端/高级箱定义配置掉落时掉出对应物品类。
- **代码审查机制落地**：新增 `docs/CODE-REVIEW.md`（审查标准与流程，含 CS2-Box 专属 9 项审查清单）、`.github/PULL_REQUEST_TEMPLATE.md`（PR 提交自查模板）；CI 新增 `gametest.yml`（GameTest 集成测试，1.21.1 + 26.1.2，当前无用例时跳过）与 `pr-checks.yml`（PR 描述模板校验，脚本 `scripts/check-pr-description.sh`）；分支保护设置指南见 `docs/CI-PROTECTION.md`（required checks 需仓库 admin 手动执行，见该文档）
- **armory_point 贴图替换为原创金色硬币造型**（原素材存在版权风险，随 1.0.6 发布后玩家端自动生效）
- **开箱音效重编码**（`cs_open` / `cs_dita` / `cs_finish` 体积大幅缩减，文件更小、加载更快；音质待人工听感回归验证，验证记录见 docs/RUNTIME-UI-TESTING.md）
- **终端机会话锁与计时器四平台同步**：`TerminalSessionManager` / `TerminalSession` / `TerminalRoundData` / 4 个新 packet（Open/State/Reject/Close）+ `TerminalOfferItems` / `TerminalOfferRegion` / `TerminalScreen` / `PacketTerminalBuy` / `ModEvents` tick 挂接全部同步 v26_1_2 / v26_2 / v1_21_1 / forge_26_1_2（forge 走 `Networking` 注册与 `CustomPayloadEvent` 适配）；各平台 `clean compileJava` 通过，`:common:test` 通过（NegotiationModel 新增快照恢复 / 强制推进 / 倒计时过期用例），v26_1_2 `PlatformSmokeTest` 与 forge L0-L3 门禁 7/7 PASS。

### 修复
- **终端机购买步骤不再永久悬挂**：交易确认框进入「交易中」后若服务端回包始终不到（丢包 / 服务端异常），此前 ESC 与点击全被等待态吞掉、界面卡死——现在客户端 6 秒无回包自动关闭确认框并提示「无法联系军火商，请重新打开终端机。」（复用 `sys.unreachable` key，晚到的回包因 `requestId` / 对话框已关被丢弃）；服务端 `PacketTerminalOpen` 5 秒兜底已覆盖开屏，购买补上后开屏 / 购买两处都不再悬挂。
- **终端机购买不再受开箱冷却误伤**：`PacketTerminalBuy` 移除对开箱冷却 `isOpenBlockedStatic` 的校验（刚开完宝箱立刻买终端机不会被 INVALID 挡掉），购买成功也不再调用 `blockFurtherOpensStatic`（终端机成交后开宝箱不再被莫名冷却 10 tick）——终端机交易是经济系统而非开箱，不与开箱冷却共用。
- **终端机会话变更即时持久化**：拒绝推进 / 关屏状态钉住 / 点数不足提示此前只改内存态、未置 `dirty`——服务器重启后会话回滚到上一持久化点（拒绝的轮次复活、对话丢失），违反「重新打开与上次打开一致」；三处处理补 `TerminalSessionManager.markDirty()`（配合 1Hz tick 兜底写盘）。
- **关闭瞬间不再回卷已拒绝轮次**：玩家在拒绝动画（450ms）内关屏时，`PacketTerminalClose` 携带的是旧轮次，会把服务端已推进的会话「回卷」到被拒绝的报价——`syncClose` 前先比较轮次，`round < 服务端当前轮` 直接忽略，保证拒绝不可撤销。
- **持久化文件损坏不再崩服务端 tick**：`TerminalSessionManager.destroyTerminal` 对 `UUID.fromString` 加防御（损坏的 `terminal_state.bin` 中非法玩家 UUID 此前会让异常冒泡到 `ServerTickEvent` 崩服）。
- **终端机强制不可堆叠（四平台统一 1 个一组）**：v26_1_2 / forge_26_1_2 动态注册终端机时曾给 `Item.Properties` 预置 `stacksTo(16)`——26.x 中 `stacksTo` 写的是 `MAX_STACK_SIZE` 组件且初始化器链「先添加的后执行」，该 16 会覆盖 `ItemTerminal` 构造内的 `stacksTo(1)`，终端机可 16 个堆叠（破坏「每台独立 uid/锁」语义，打开一叠只打一个 uid、谈崩/成交整叠销毁）。修复并加固为**强制不可堆叠**：① 动态注册终端机分支不再传 `stacksTo`；② `ItemTerminal` 四平台 override `getDefaultInstance()` 强制盖 `MAX_STACK_SIZE=1`（任何创建途径的默认实例都不可堆叠）；③ 26.x 动态注册的 `DATA_COMPONENT_INITIALIZERS` 在注册表级对终端机 set `MAX_STACK_SIZE=1`（双保险）。普通箱 / 高级箱保持 16 一组（批量开箱依赖堆叠，不受影响）。`ItemTerminal(Properties)` 构造补 javadoc 警告 26.x 组件链陷阱。
- 以上修复四平台同步（v26_1_2 / v26_2 / v1_21_1 / forge_26_1_2），各平台 `clean compileJava` 通过，`:common:test` 通过，forge L0-L3 门禁 7/7 PASS。

- **单开扣钥匙时机**：`PacketCsgoProgress.handleServer` 把 `tryConsumeKeys` 移到整条校验链（box_id / 权重 / 分级池 / 获胜索引 / 保底解析）全部通过之后——配置损坏或热重载竞态下不再出现「丢钥匙但没奖励」（此前钥匙先于最终校验被消耗）。批量同家族防御：`finalizeBulkOpen` 在整批结果全为空时直接中止、不消耗任何箱子与钥匙（坏配置不再整批吞物品）。
- **客户端钥匙判定覆盖全槽位**：`CsboxScreen` 单开点【开启】的钥匙检查与钥匙计数从「仅主物品栏」扩展到「主物品栏 + 护甲 + 副手」，并跳过箱子实例（与服务端 `tryConsumeKeys` 槽位覆盖和防误吞规则一致）——钥匙放在护甲/副手的玩家不再被客户端误判为「没有钥匙」而点不开箱子。
- **创造模式开箱不再消耗箱子**：单开 `box.shrink(1)` 与批量 `tryConsumeBoxes` 增加 `instabuild` 豁免，与钥匙/军械库点数在创造模式下的免费行为对齐。

- **终端机购买服务端数量校验**：`PacketTerminalBuy` 服务端授予物品前强制 `toGive.setCount(1)`——`isSameItemSameComponents` 不比较数量，伪造 `offerItem` 数量可「1 件价格买 64 件」；同时确认交易对话框进入「交易中」后 ESC 不再可关闭（此前 ESC 会关掉等待框，服务端已扣点给物而客户端静默丢弃结果、谈判停留在 PENDING）。
- **终端机屏幕关闭逻辑修正**：移除两个终端屏 `onClose` 中无条件的 `hideGui = false`（v26_2 为 `HudVisibility.show()`，屏幕从未隐藏 HUD，此前会破坏玩家 F1 隐藏 HUD 状态）；新增 `removed()` 清理静态 `OPEN_INSTANCE` 单例（被 `setScreen` 顶替/死亡时 MC 只调 `removed()`，此前残留脏引用会命中迟到的购买回包）；长按「接受/拒绝」胶囊后拖出胶囊再松手不再触发（`TerminalActionBar.mouseUp` 校验释放点仍在胶囊内）。
- 以上四处修复四平台同步（v26_1_2 / v26_2 / v1_21_1 / forge_26_1_2），各平台 `clean compileJava` 通过，`:common:test` 与 v26_1_2 `PlatformSmokeTest` 通过，forge L0-L3 门禁 7/7 PASS。


### 移除
- **终端机聊天区 ♞ 水印**（按玩家反馈，三平台 + forge 同步）：删除 `TerminalChatRegion.render()` 中象棋棋子字符（`U+265E`）水印绘制——该水印源自 `design/terminal-chat.html` 原型 `.watermark`（1.0.7 演示对齐 Task 5 落地），移除后聊天区保留点阵网格与标题条；`design/terminal-chat.html` 原型同步删除水印元素与样式，保持设计源与实现一致

## [归档] - 2026-08-09
### 平台归档（EOL）
- **v1_21_0 / v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10 / v1_21_11 移出仓库**：仅玩家基数大的 1.21.1 / 26.1.2 / 26.2 三平台继续维护。7 个 EOL 模块的代码整体删除（最后状态保留在 tag `eol-legacy-21x-1.0.6`，需要时从该 tag 检出复活）；`settings.gradle` 的 `versionModules`、`gradle.properties` 的版本变量组、CI `build.yml` 矩阵（10 → 3 行）同步裁剪，`scripts/mirror.sh` 移除 legacy variant，legacy 专用迁移脚本（`merge-*.py` / `port-12111.py` / `port-focus.py` / `migrate-randomitem.py`）一并删除。

## [1.0.6] - 2026-08-08
### 概述
本版本完成 26.2 平台扩展、教程系统、动态 box item、开箱排行榜、10 平台矩阵（新增 v1_21_0）、GUI 设计系统（token + 容器化 + per-item 基线）、并发安全、磨损扣耐久、TACZ 检视视口与 CI 矩阵等全部开发批次。**批量开箱推迟至 1.0.7 发布**（代码已开发完成、本版本入口与服务端处理均屏蔽，详见下文 `[1.0.7]` 节）。下文按批次记录。

### 补记：forge_26_1_2 模块纳入 git 并发行（2026-08-12）
- **forge_26_1_2（MinecraftForge 26.1.2-64.1.0，Java 25）随 1.0.6 发行纳入 git 管理**。发行时先保持 1.0.6 特性基线（`forge_26_1_2/build.gradle` 对 common 源集/资源排除 1.0.7 线增量），**同日转入 1.0.7 同步开发线**：`build.gradle` 排除清单已删除，随 `v26_1_2` 基准同步终端机 / 军火商 / 高级箱 / 村民 / Blur / 批量开箱恢复等增量（见上文 [未发布] 节），`clean compileJava` 与 `test-forge-2612.sh` L0-L3 门禁 7/7 PASS；仍为实验模块，不入三平台正式发行矩阵与 CI。
- **资源修复**：补 5 个 1.0.6 基线物品模型定义（`assets/csgobox/items/{csgo_box,csgo_key0-3}.json`），修复模组物品（箱/钥匙/动态箱）紫黑棋盘格缺失模型；开箱屏箱子渲染改 PIP `Icon3DRenderState` 高清 3D 路径（`utils/GuiItemMove.java` 与 v26_1_2 `AnimRenderOps.renderItem3D` 对齐），消除放大像素化。
- **测试设施**：`docs/TESTING-FORGE-2612.md`（测试流程 + 发布门禁）与 `scripts/test-forge-2612.sh`（自动化门禁 L0-L3：clean 编译 / jar 产物校验 / 版本四同步 / 渲染门面漂移 / PlatformSmokeTest）；L4 经 mc_tools TestHelper 新增 forge-26.1.2 构建目标自动化 E2E（`test_csbox.sh` / `test_csbox_ext.py`）。发布门禁 L0-L3 7/7 PASS、L4 11P/0F/0W，详见 `docs/TEST-REPORT-FORGE-2612-2026-08-11.md`。
- **工程**：`settings.gradle` foojay-resolver-convention 0.9.0 → 1.0.0（修复 ForgeGradle 7 run 任务在 Gradle 9 的挂起）、`gradle.properties` 补 `net.minecraftforge.gradle.merge-source-sets=true`（dev 运行 mod 定位）、IDEA run 配置新增 `MC_Forge_26_1_2_*`（清理过时的 `MC_26_1_2/26_2_*Data` 配置）。

### 玩家更新摘要（forge 版 1.0.6，2026-08-12）
- 模组现可运行于 **MinecraftForge 26.1.2**（Java 25），1.0.6 全部功能与 NeoForge 多平台版本一致。
- **修复物品显示为紫黑棋盘格**：箱子 / 钥匙等模组物品补齐模型定义，不再出现"没有模型"的紫黑贴图。
- **修复开箱屏箱子"像素贴图化"**：箱子渲染改为高清 3D（PIP）路径，放大查看不再模糊、有立体感。
- 本版本随带的玩法功能：教程文档自动下载与更新、动态箱子 JSON（`config/csbox/*.json`）、开箱排行榜、按磨损值扣耐久、JSON 加载错误红色提示等。
- **安装**：将 `csgobox-forge-26.1.2-1.0.6.jar` 放入 `mods/` 文件夹，需要 MinecraftForge 26.1.2（Java 25）。
- **本版分发 jar 已含同步并入的新增玩法**（批量开箱恢复、终端机、武库拆解台 / 武库点数、军火商高级箱、Blur 软兼容、`/csbox nbt hand`），玩家向更新日志见 `docs/PLAYER-CHANGELOG-FORGE-1.0.6.md`。

### 上线前补充：TACZ 检视视口 / v1_21_0 平台 / 审美测试脚本
### 新增
- **TACZ 检视视口（仅 1.21.1 平台，可选集成）**：开箱检视屏（CsLookItemScreen）抽中 TACZ（永恒枪械工坊：零）枪械时，中央展示区默认以 3D 视口展示枪械模型（TACZ 自身 GUI 物品渲染只画 2D 槽位贴图，此处经 TACZ 公共渲染器/状态机 API 自驱渲染绕过），点击底部工具栏"手套"按钮播放检视动画与官方检视音效（可重复触发）。不改变玩家手持物品。TACZ 为 compileOnly 软依赖：未安装 TACZ、或抽出物品不是 TACZ 枪械时保持原 2D 图标展示、点击无响应；构建前需运行 `scripts/download-tacz.sh` 下载 TACZ jar（~57MB，不入库，CI 自动下载）。
- **审美测试脚本 `clean` 子命令**（`scripts/test_animation_aesthetics.py`）。一键清理测试产物：默认只删 `shots/*.png` 保留 `report.md`，`--report` 连同报告一起删；`--dry-run` 预览不删、`--yes` 免确认；目录不存在或无匹配文件幂等返回 0，单文件删除失败汇总返回 1。删除前打印清单 + 确认提示防误删。
- **v1_21_0 平台模块（第 10 个平台，MC 1.21.0 / NeoForge 21.0.167 稳定版）。** NeoForge 稳定线矩阵补全：1.21.0 是 1.21.1 的直接前身（补丁级 API 差异），从 `v1_21_1/` 完整镜像并包名重命名（42 java 文件零适配），`settings.gradle` 的 `versionModules` 与 `gradle.properties` 新增 10 个 `*_21_0` 变量，CI 矩阵新增 1.21.0 (Java 21) 行。`compileJava -Pactive_versions=1.21.0` 验证 BUILD SUCCESSFUL。

### 批次一：26.2 平台扩展 + 教程系统
### 新增

#### 多平台 / 26.2 beta 支持
- **v26_2 平台模块(第三个版本模块)。** 通过 `settings.gradle` 动态 include(`active_versions=26.2` 时启用),从 `v26_1_2/` 完整复制并包名重命名(`v26_1_2 → v26_2`,`Platform26 → Platform26V2`,独立 `IPlatform` 实现 `mcVersion()` 返回 `"26.2"`)。资源文件 `META-INF/neoforge.mods.toml`、`pack.mcmeta`、`assets/csgobox/items/*.json`、`data/csgobox/recipe/*.json`、`data/csgobox/advancement/*.json` 一并迁移。Gradle `settings.gradle` 与 `gradle.properties` 新增 8 个 `*_26_2` 变量。
- **26.2 decoupled API 破坏性变更适配** (`commit 4c9a004`,38 → 0 compile error)。`PictureInPictureRenderer` 构造器不再接收 `MultiBufferSource`(新签名为 `renderToTexture(state, poseStack, SubmitNodeCollector)`,`featureRenderDispatcher.renderAllFeatures()` 由父类负责触发);`Minecraft.setScreen(Screen)` → `Minecraft.setScreenAndShow(Screen)`;advancement `CriterionTrigger` 从抽象类改为 interface,`SimpleCriterionTrigger.trigger` 改为 protected(`Predicate<TriggerInstance>` 强制类型转换);`GameRenderer.getLighting()` → `lighting()`;`Options.hideGui` 字段整体移除(运行时回归阶段用户确认 HUD-overlay 降级可接受)。
- **`v26_2/gui/pip/Icon3DRenderer.java` 完全重写** (106 → 99 LOC)。3D 旋转保留:`scale(1,-1,-1)` + `Axis.{XP,YP,ZP}.rotationDegrees` 驱动 `rotXDeg/rotYDeg/rotZDeg`。运行时回归验证 `CsboxScreen` 与 `CsLookItemScreen` 的鼠标拖拽 3D 旋转均工作正常。
- **`common/` 业务代码共享层**。首批 A 类文件(无 MC 依赖)迁移:`common/utils/ColorTools.java` + `common/utils/OverlayColor.java`,git 识别为 rename(`v1_21_1 → common`),8 个 caller 的 import 同步更新。后续阶段 1 完整 B 类迁移(见 `multiloader-execution-spec.md`)将 `BoxDefinition / BoxRegistry / GradeGroup / CsboxConfig` 等业务核心统一到 `common/`,本版本暂保留 3 平台各一份。
- **真实 NeoForge 26.2 版本号 pin** 到 `gradle.properties`:`neo_version_26_2=26.2.0.7-beta`(26.2 当前最新,跳过 0.4/0.5)、`neogradle_version_26_2=7.1.38`(与 v26_1_2 同)、`neoform_release=26.2-1`、`loader_version_range_26_2=[11,)`、`pack_format_26_2=81`(按 Mojang 惯例 +1 每小版本)。
- **三模块 build 矩阵**。`./gradlew :v1_21_1:compileJava` + `:v26_1_2:compileJava` + `:v26_2:compileJava` 全部 BUILD SUCCESSFUL;`:v26_2:jar` 产出 `csgobox-26.2-1.0.6.jar`(pack_format=81)。
- **`.planning/` 规划制品**。`multiloader-refactor-plan.md`、`multiloader-execution-spec.md`、`phase0-audit.md`、`csbox-gui-26.1.2-fix-guide.md`、`runtime-verification-checklist.md` 等 5 篇主题文档 + `PROJECT.md / REQUIREMENTS.md / ROADMAP.md / STATE.md` 状态卡 + `intel/` 子目录 19 个 classifications + 3 张 synthesis(`SYNTHESIS/context/constraints`),由 `/gsd-ingest-docs` 自动 ingest 后保留。

#### 教程系统(教程 JSON 自动下载 + 版本管理 + 跨平台回收站)
- **网络下载教程文档**。教程 markdown 改为从网络拉取,不再硬编码在 JAR 里。新增 `box/TutorialSources.java`(读取 `config/csbox/_tutorial_sources.json` 源列表,默认指向 `https://gitee.com/hou-xiangling/CS2-Box/raw/main/docs/tutorials/`,玩家可手动创建该 JSON 加镜像 / 调超时)+ `box/TutorialFetcher.java`(Java 11+ `HttpClient`,`HttpClient.Redirect.ALWAYS` 自动跟随 302,5s 连接 / 8s 单请求超时,异常全部 catch 不冒泡)。`BoxJsonLoader.loadAll()` 在创建 `config/csbox/` 后调用 `BoxDefaults.writeTutorialIfMissing()`。
- **教程文档自带版本号**。文件名嵌入 mod 版本,例如 `_tutorial_v1.0.6.md` / `_tutorial_v1.0.6_zh_cn.md`,源仓库路径 `gitee.com/hou-xiangling/CS2-Box/docs/tutorials/` 已上传对应文件。下次 mod 升级时,玩家机器上的 `_tutorial_v1.0.6*.md` 会被自动清理并下载新版。
- **跨平台回收站 + 安全过滤**。`BoxDefaults.moveStaleTutorials()` 三重安全:(1) 优先调用 `java.awt.Desktop.moveToTrash()` 走系统原生回收站(Windows Recycle Bin / macOS Finder Trash / Linux XDG Trash);(2) 无 GUI(headless server)或 `java.desktop` 缺失时降级到 `config/csbox/.trash/` 子文件夹,文件名冲突自动加毫秒时间戳前缀;(3) 严格正则白名单 `^_tutorial_v.*\.md$`,绝不触碰 `notes.md` 等用户文件、`_tutorial_sources.json` 配置、旧版无版本号 `_tutorial.md` 等。跨分区移动自动 `Files.move` → `Files.copy + Files.delete` 降级。
- **离线安全**。整个 `writeTutorialIfMissing` 体被 `try { ... } catch (Exception e)` 包裹,任何意外(网络异常、HttpClient 构造失败、磁盘写入失败)只记 WARN 日志,游戏正常启动运行。
- **教程文档内容**(中英双版本,源仓库 `docs/tutorials/`)。涵盖字段说明(顶层字段 / entity / 物品对象 / 等级 / 钥匙 / 校验规则 / 故障排查)、JSON 示例(自定义名 / 附魔 / 玩家头颅)、锻造台 `csgo_key3` 唯一获取路径说明。

#### JSON 加载错误玩家可见纠错
- **`box/LoadError.java`** 记录单条 JSON 加载失败:`Path file / String boxId / String reason / int line / int column / Throwable cause`,自带 `toChatMessage()` 返回红色 `Component`(行/列已知时显示 `"第 N 行第 M 列"`)。
- **`box/BoxJsonLoader.java` 错误收集**:`LAST_LOAD_ERRORS` 静态列表,`loadAll()` 开头清空,`loadFromFile()` 把所有异常分支(`JsonSyntaxException` / `Identifier.parse` 失败 / 空箱子跳过)收集为 `LoadError` 而非只写日志。Gson 2.13+ 不再提供 `JsonSyntaxException.getLocation()`,用正则 `at line (\d+) column (\d+)` 从错误消息里抓取行/列。新增公开 API `getLastLoadErrors() / hasLoadErrors()`。
- **`event/LoadErrorAnnouncer.java`** 在 `PlayerEvent.PlayerLoggedInEvent` 触发,根据 `ErrorChatAudience` 配置(`OP_ONLY` 默认 / `EVERYONE`)向玩家推送加载错误。v26.x 用 `sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`,v1_21_1 用 `sp.hasPermissions(2)`。
- **`config/CsboxConfig.java` 新增** `ErrorChatAudience` 枚举 + `jsonErrorAudienceValue`(`defineEnum` 落到 `CONFIG_SPEC`,玩家可在 `config/csgobox.toml` 改)。
- **`command/CsboxCommand.java` 新增** `/csbox errors` 子命令,OP 权限,显示当前所有加载错误(无错误时显示绿色 "当前无箱子加载错误")。

### 决定
- **B 类 6 文件不迁 `common/`(显式选择)。** `BoxDefinition / BoxRegistry / GradeGroup / CsboxConfig / CsboxPlayerData / EntityChineseMap` 全部 `import net.minecraft.*` 或 `import net.neoforged.*`,违反 `.planning/intel/constraints.md` CONSTRAINT-001。它们保留在每个平台模块的副本中(共 3 份)。若未来要做 B 类重构,需要新增平台抽象接口(`IIdentifier / IItemStack / IComponent / IModConfig`)或在 common 中允许数据包装类携带 MC 引用,工作量为 6-10 小时。
- **教程分发仓库选择 Gitee(用户私有仓库)而非 GitHub**。原因:国内访问速度 + 用户主仓库路径 `gitee.com/hou-xiangling/CS2-Box`。教程 markdown 文件单独存放于 `docs/tutorials/` 子目录,与 mod 源码解耦。玩家可在 `config/csbox/_tutorial_sources.json` 里加 GitHub / 自建 CDN 作为镜像。
- **回收站优先 OS 原生**(`Desktop.moveToTrash()`)。原因:玩家可在熟悉的 OS UI(Finder Trash / Windows Recycle Bin / Linux 文件管理器)恢复,跨平台 API 自 Java 9 起稳定;headless server 与极简 JVM 自然降级到 `.trash/` 文件夹。
- **教程文件名带版本号**(例 `_tutorial_v1.0.6.md`)。原因:教程内容可能随版本更新而调整(mod 字段、命令、配方可能变更),带版本号保证玩家读到的文档与 mod 行为匹配;升级时旧版本自动移到回收站。

### 修复
- **`TutorialFetcher` HttpClient 默认不跟随 302 重定向。** Gitee raw URL 返回 HTTP 302(走 ADAS 网关),Java HttpClient 默认行为是失败。修复:构造时显式 `.followRedirects(HttpClient.Redirect.ALWAYS)`。
- **`BoxJsonLoader` 把教程源文件当箱子加载。** `tutorial_sources.json` 文件名不以 `_` 开头时会被 loader 当箱子解析并报 "All items failed to parse"。修复:文件名前加 `_` 前缀(`_tutorial_sources.json`),自动被 loader 跳过(`Files.newDirectoryStream` 跳过 `_` 开头文件)。
- **教程源配置 JSON 自动写入 config/csbox/ 产生冗余 clutter。** 第一版会在首次启动自动写 `_tutorial_sources.json`,玩家认为是无用文件。修复:改为"只在玩家手动创建时读取,默认情况下不写盘",99% 玩家看到干净 `config/csbox/`。
- **删除老 `_tutorial.md` 等旧版无版本号文件。** Gitee 仓库旧版文件已删除;`/tmp/cs2-tutorials-v2` worktree 用 `git mv` 重命名为带版本号文件名,提交 `e29200f` 推送到 gitee。

### 移除
- **教程硬编码常量。** `BoxDefaults.TUTORIAL_CONTENT_EN` / `TUTORIAL_CONTENT_ZH`(原约 200 行 Java 嵌入 markdown)整段删除,改为运行时从 Gitee 拉取。
- **HUD-overlay 隐 GUI 功能在 26.2 不可用**(NeoForge 26.2 移除 `Options.hideGui` 字段)。开箱时 hotbar/血条仍可见,用户确认接受此降级,延后到 26.2 stable + 出现等价 API 时再补。
- **测试用 JSON 文件清理。** `runs/client/config/csbox/test_*.json`(LoadError 功能验证时临时写入的)从 3 平台 `runs/` 全部删除。

### 更改
- **配置可见性默认值**:`jsonErrorAudience` 默认 `OP_ONLY`(非 OP 玩家登录时收不到加载错误推送)。OP 自身执行 `/csbox errors` 不受此配置影响(命令本身已 OP-only)。
- **配置文件命名**:`config/csgobox.toml`(1.0.5 后续调整后保持此名)。所有玩家配置文件路径不变。
- **PIP 3D 旋转在 26.2 的实现**:`v26_2/gui/pip/Icon3DRenderer.java` 因 `PictureInPictureRenderer` API 变动完全重写,3D 鼠标拖拽行为与 v26_1_2 视觉一致。

### 备注
- **构建产物**:
  - `csgobox-1.21.1-1.0.6.jar`(MC 1.21.1 + NeoForge 21.1.115)
  - `csgobox-26.1.2-1.0.6.jar`(MC 26.1.2 + NeoForge 26.1.2.76)
  - `csgobox-26.2-1.0.6.jar`(MC 26.2 + NeoForge 26.2.0.7-beta,**注意 26.2 仍 beta,生产环境慎用**)
- **教程文档源**:`gitee.com/hou-xiangling/CS2-Box/docs/tutorials/`,公开仓库,无需认证。玩家想自托管可 fork 该仓库后改 `_tutorial_sources.json`。
- **SSH 密钥**(`~/.ssh/id_ed25519_gitee`,用户机器本地)用于教程维护者推送更新到 Gitee,与 mod 运行时下载无关(下载走 HTTPS 公开访问)。
- **运行时回归**:v1_21_1 / v26_1_2 / v26_2 三平台客户端均已验证箱子加载、开箱动画、PIP 3D 旋转、成就触发、`/csbox reload`、`/csbox errors`、教程下载/版本升级迁移/回收站恢复。

### 未完成
- 一旦 26.2 发布 stable release(去掉 `-beta` 后缀),需要重新刷 `neo_version_26_2` 并验证 `mc_version_range_26_2` 是否需要向前兼容 beta。
- 阶段 1 完整 B 类迁移:common 业务代码(B 类文件保留平台层重复)、P1-1 容器化布局、P1-3 per-item 视觉基线、P2-2 三档设计 token(均显式延期)。
- 教程系统可考虑增量:`/csbox tutorial refresh` 命令手动刷新;`.trash/` 自动清理(保留最近 N 份或 N 天内);启动时打印回收站内容提示。

### 批次二：动态 box item
### 新增
- **动态 box item 注册**（方案 A 实施）。`FMLCommonSetupEvent.enqueueWork` 阶段扫描 `config/csbox/*.json`，对每个 `<filename>.json` 自动注册一个 item ID `csgobox:<filename>`（`ItemCsgoBox` 子类，`getDefaultInstance` 预置 `box_id` = 自身 id）。效果：原版 vanilla `/give @p csgobox:weapon_supply_box 5` 直接生效，**无需 components 语法**。3 平台镜像。
  - 已存在的 item id 跳过（避免与基础 `csgobox:csgo_box` 冲突）
  - `_tutorial*.json` / `_tutorial_sources.json` 等 `_` 前缀文件跳过（loader 惯例）
  - 文件名不是合法 ResourceLocation path 时 log warn 后跳过
  - 服务端日志：`[csgo-dynamic-items] registered N dynamic box item(s) from config/csbox/ (M skipped as already registered)`
- **Known limitation：模型文件缺失**。动态 item 没有对应的 `assets/csgobox/models/item/<name>.json`，**功能完全正常**（开箱、RNG、`/give` 全部走通），仅在背包栏的图标显示紫黑缺纹理。临时解法：玩家手动在 assets 里添加同名模型文件；根治解法留待后续支持 IClientItemExtensions 覆盖（已在 JavaDoc 中标注 TODO）。
- v26.x 适配：26.x 的 `EventBusSubscriber` 注解已移除 `bus` 参数（默认走 MOD 总线），`@EventBusSubscriber(modid = MODID, bus = ..., value = Dist.CLIENT)` 改为 `@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)`。

### 更改
- **批量开箱推迟至 1.0.7**：入口（shift+右键）与服务端 `PacketCsgoBulkProgress.handleServer` 整体屏蔽，shift+右键恢复原单开行为；功能代码完整保留（10 平台镜像），1.0.7 解除屏蔽即用。详见下文 `[1.0.7]` 节。

### 新增（开箱排行榜 / scoreboard）
- **`/csbox scoreboard` 子命令（OP 权限）**。完全复用原版 `/scoreboard objectives` 系统，模仿死亡榜的玩法机制。3 平台镜像（`v1_21_1` / `v26_1_2` / `v26_2`）。
  - `/csbox scoreboard` — 显示当前 objective 状态（已开启 / 未开启 + 显示位置）
  - `/csbox scoreboard on` — 添加 objective `csbox_opened`（criteria `DUMMY`，显示名 `CS2 Boxes Opened` / `CS2 开箱数`），默认 `setdisplay list`，并把当前所有在线玩家的开箱数（`csgobox:opened_boxes` 自定义统计）同步进 objective
  - `/csbox scoreboard off` — 移除 objective
  - `/csbox scoreboard list|sidebar|belowName` — 切换显示位置（与原版死亡榜的 setdisplay 槽位一致）
- **数据源**：`1.0.5` 已注册的 `Stats.CUSTOM.csgobox.opened_boxes` 统计。开箱时 `sp.awardStat(OPENED_BOXES_STAT, K)` 累加，玩家每次开箱后 `CsboxCommand.syncOpenedBoxesToScoreboard` 把当前 stat 值写入 scoreboard 的对应 Score（DUMMY 准则手动同步）。
- **范围外（明确未做）**：不自动重置排行榜（兼容原版 `/scoreboard players reset * csbox_opened`）；不自动 `setdisplay`（避免与其他 mod 撞 sidebar，玩家/OP 用 `/csbox scoreboard on` 后手动调 setdisplay 子命令切换）；不重写原版 scoreboard API（直接调用 `MinecraftServer.getScoreboard()`）。
- **新增 11 个 i18n key**（中英双版本）：`commands.csgobox.help.line.scoreboard` + `commands.csgobox.scoreboard.{objective_display_name,status_off,status_on,already_on,on_success,off_not_found,off_success,display_not_set,display_changed}`。

### 范围外（明确未做）
- 26.2 的 `Options.hideGui` 已知缺失（沿用 v1.0.6 现状，HUD 降级可接受）。
- `OPEN_BLOCKED_UNTIL_TICK` map 清理（`.planning/codebase/CONCERNS.md:58` deferred 项，本 PR 不修）。
- 通用 v1_21_1 / v26_1_2 / v26_2 的代码生成/模板抽取（保持镜像风格）。

### 备注
- 3 平台独立编译通过（`./gradlew :v1_21_1:compileJava` / `:v26_1_2:compileJava` / `:v26_2:compileJava` 各自 BUILD SUCCESSFUL）。无法在同一次 Gradle 启动中多版本并行（NeoGradle userdev IDEA 扩展冲突，是项目历史限制）。

### 修复（动态 box item + 启动崩溃）
- **`v1_21_1` / `v26_1_2` 启动崩溃（`Registry is already frozen`）**。原 `registerDynamicBoxItems(FMLCommonSetupEvent event)` 走 `enqueueWork`，而 1.21.1 / 26.1.2 的 enqueueWork 时机晚于 item registry freeze，导致 `new ItemCsgoBox()` 构造时 `MappedRegistry.createIntrusiveHolder` 抛 `IllegalStateException`，integrated server 进不去 world。**修复**：把 `registerDynamicBoxItems` 改签名接收 `RegisterEvent`，内部用 `event.register(Registries.ITEM, itemId, () -> new ItemCsgoBox(...))` deferred supplier —— Item 实例在 registry finalize 阶段构造，时机早于 freeze。`v26_2` 早先已采用此写法（`RegisterEvent` 路线），本次把 v1.21.1 / v26.1.2 镜像对齐。3 平台都加了 `if (!event.getRegistryKey().equals(Registries.ITEM))` 守卫（因为 listener 注册时未做 key 过滤）。
- **`v26_2` `Components not bound yet` warning**。原 `BoxJsonLoader.loadAll()` 在 `FMLCommonSetupEvent.enqueueWork` 中调 `BoxItemCodec.parseItem` → `new ItemStack(item, count)`，但此时 Item 的 `bindComponents` 还没跑（datapack reload 之后才跑），`Item.builtInRegistryHolder().components()` 抛 NPE 被 swallow，导致 `weapon_supply_box` 整箱解析失败。**修复**：把 `loadAll()` 从 `FMLCommonSetupEvent` 移到 `ServerStartingEvent`，此时 registry 已 freeze 且所有 `bindComponents` 都已运行。3 平台同步改。
- **SLF4J 日志补完**：`v1_21_1` 的 `BoxItemCodec.parseItem` 之前用 `LOGGER.warn("...{}", elem, e.getMessage())`（format 只有一个 `{}`），SLF4J 实际丢弃第二个 `e.getMessage()` 参数，operator 看不到真因。改为 `LOGGER.warn("...{}", elem, e)` Throwable variant。`v26_1_2` / `v26_2` 早先已修。
- **运行时回归**：3 平台 `runClient` 全部 BUILD SUCCESSFUL + integrated server 正常进 world + 玩家加入/退出 + 成就触发 + box JSON 加载（`weapon_supply_box.json` → `Loaded box from JSON ... Scanned 1 JSON file(s); loaded 1, skipped 0 ... CS2 Box server started with 1 box definitions`）+ 0 个 `Registry is already frozen` / `Components not bound yet` 错误。

### 批次三：9 平台矩阵 + GUI 设计系统
### 新增
- **9 平台版本矩阵**：`settings.gradle` + `gradle.properties` 扩展到 `v1_21_3/4/5/8/10/11`，全部 9 模块 clean compileJava 验证通过。
- **`/csbox tutorial refresh`** 子命令：强制重下当前版本教程（覆盖已存在），`BoxDefaults.refreshTutorials` 与启动路径共用 TutorialSources/TutorialFetcher。
- **教程系统收敛到 common**：9 平台 `BoxDefaults.java` 副本整体删除，`refreshTutorials` 上移 `common/box/BoxDefaults.java`（B 类迁移 #1/6 收尾）。
- **旧版本教程直接删除**：取代 OS 回收站 + `.trash/` 两级回收机制（含 `canUseOsTrash` / `moveToOsTrash` / `moveToFallbackTrash` / `pruneFallbackTrash` / `tryMoveOrCopy` / `uniqueFallbackPath` 全部移除）。`deleteStaleTutorials` 按 `^_tutorial_v.*\.md$` 白名单直接 `Files.delete`，单文件失败仅 warn 不中断。
- **26.2 HUD 隐藏恢复**：`HudVisibility` 工具类（`Minecraft.gui.hud.toggle()/isHidden()` set 语义包装），开箱动画屏自动隐藏 hotbar/血条，消除 1.0.6 遗留降级。
- **三档设计 token**（P2-2）：`common/utils/OverlayColor` 扩展 surface/panel/divider/panelHover/panelPressed/panelDisabled；`ButtonPalette.DISABLED`。
- **容器化布局**（P1-1）：`common/utils/GuiRegion` 命名区域（title/preview/list/actions/actionPair），CsboxScreen 落地（批量开箱屏随 1.0.7 一起落地）。
- **per-item 视觉基线**（P1-3）：`IconListTools.renderGuiItem` 用 `ItemModelResolver` + `getModelBoundingBox()` 测量模型范围并居中（26.x + 1.21.8/10/11）。
- **GitHub Actions matrix**（`.github/workflows/build.yml`）：9 版本顺序构建 + common 测试 + jar 产物上传。
- **`docs/RELEASE.md`**：发布流程、质量门（含增量缓存假象警告）。
- **`EntityChineseMap` 迁 common**：String key 纯数据版，9 平台副本删除（B 类迁移 #1/6）。

### 修复
- **`OPEN_BLOCKED_UNTIL_TICK` 并发安全**：HashMap → ConcurrentHashMap + `tickOpenBlockMap` 每 100 tick 清理（9 平台）。
- **v1_21_11 从未真正编译通过**（clean 编译 80 错误）：1.21.11 的 GuiGraphics 已是 decoupled API（Matrix3x2fStack/RenderPipeline/无 RenderSystem），以 v26_1_2 为蓝本完整适配。
- **动态 box item 紫黑纹理**：`DataComponents.ITEM_MODEL` 复用 `csgobox:csgo_box` 模型（8 平台）；1.21.5+ 补 `items/*.json` 定义（这些版本此前静态 item 模型也损坏）。
- **GRADE_COLORS 第 3-5 档同色**：按 CS:GO 官方 quality 色系修正（industrial 0xFF5E98D9 / consumer 0xFFB0C3D9）。
- **记分板彻底原版化**：移除 `/csbox scoreboard` 子命令（on/off/list/sidebar/belowName/status）与全部配套代码（`scoreboardStatus/On/Off/SetDisplay/currentDisplaySlotName/syncOpenedBoxesToScoreboard` + 11 个 i18n key）。记分板改由玩家用原版指令管理：`/scoreboard objectives add <名> minecraft:custom:csgobox:opened_box`（自定义统计 `csgobox:opened_box` 已注册，原版自动实时读取 stat 值，不再需要模组手动写分）；旧存档的 `csbox_opened`（DUMMY）objective 需先 `remove` 再按新方式重建。

### 备注
- 26.2 仍为 beta（最新 26.2.0.40-beta），保持 26.2.0.7-beta 不升级。
- B 类迁移剩余 5 文件（BoxDefinition/GradeGroup/CsboxConfig/CsboxPlayerData/BoxRegistry）依赖 ItemStack/ModConfigSpec/Component/StreamCodec，common 编译环境无 MC classpath，需平台抽象接口（IItemStack/IComponent/IModConfig），ROADMAP 1.1B 判定 6-10h 工程，保留平台层。
- 1.21.1~1.21.5 无 ItemModelResolver API，IconListTools 保持原锚定绘制。

### 批次四：磨损扣耐久

### 新增
- **按磨损值损耗耐久**（`damageItemByWear` 配置，`[advanced]`，默认开启）：抽出的物品若有耐久属性，按 CS:GO 磨损值百分比扣耐久（`round(磨损值 × 最大耐久)`，钳制 `[0, max-1]`，永不碎裂）。磨损值由服务端权威生成（开箱动画路径）；查看界面有耐久物品显示实际扣损率，无耐久物品维持随机磨损率展示。10 平台全部 clean compileJava 通过。

## [1.0.7] - 计划中

> 以下计划已全部实现，实现记录见上方 [未发布]；本节保留为历史计划快照。

> 批量开箱原计划随 1.0.6 发布，现推迟至本版本。功能代码已在 1.0.6 开发完成并经 10 平台镜像，1.0.6 发布时入口（shift+右键）与服务端处理（`PacketCsgoBulkProgress.handleServer`）已整体屏蔽，shift+右键恢复原单开行为；本版本解除屏蔽后直接可用。

### 新增（武库点数）
- **武库点数物品 `csgobox:armory_point`**：绿色十字徽章图标（16×16，无色区域透明）；作为可配置物品出现在动态箱子 JSON 掉落体系中（管理员在 `config/csbox/*.json` 的 grades 权重表添加即掉落）；creative tab 展示。
- **兑换配方**：3×3 全填 64 武库点数 → 1 铁钥匙（`csgo_key0`，`armory_point_exchange.json`），武库点数成为钥匙经济的中转货币。
- **x10 平台注册**：`v1_21_0..v26_2` 全部注册（legacy `ITEMS.register` / 1.21.3+ `setId` / new `registerItem` 三形态定点合入，`scripts/port-armory-point.py` 幂等脚本）；`v26_1_2 PlatformSmokeTest` 新增 `ITEM_ARMORY_POINT` 反射断言。

### 新增（批量开箱）
- **批量开箱触发**：`ClickEvent.onRightClick` 检测 `mc.options.keyShift.isDown()`，shift 状态开 `CsboxBulkOverviewScreen` 总览屏，否则走原 `CsboxScreen` 单开流程（无 regression）。10 平台镜像。
- **总览屏 `CsboxBulkOverviewScreen`**：实时计算 `min(背包内同名箱数, 钥匙数)`，按稀有度配色（蓝/绿/红）显示「箱子数 / 钥匙数 / 本次可开」+ 「开启」+ 「返回」按钮。开启按钮在 K=0 时禁用。
- **2D 底部上升流水式面板 `CsboxBulkResultScreen`**：底部上升的 CS:GO 风格 ticker feed，每 4 tick 推入一条新中奖物品（图标 + 稀有度颜色 + 名称 + 数量 + 序号），淡入 10% / 稳定 70% / 淡出 20%；最多同时显示 8 条；全部出完且最后一条过 100 tick lifetime 后显示「收集」按钮；「全部显示」视图按物品合并展示 2D 网格。
- **新增数据包**（10 平台各 1 份）：
  - `PacketCsgoBulkProgress(requestId)`（C→S）：批量开箱请求。StreamCodec 仅 `long`。
  - `PacketBoxBulkResult(requestId, items, grades)`（S→C）：boxes 2..K 的简洁结果（无动画数据）。max 1024 条/包。
- **服务端异步预计算管线**：
  - `CsgoBox.BULK_COMPUTE_POOL`：2 个 daemon 线程的 `ExecutorService`（`csgobox-bulk-compute-N`）。
  - `BulkBoxContext`（record）快照 `weights` + `gradeMap` 供后台线程只读消费。
  - `BulkOpenResult`（record）单次结果。Box 1 含完整 50 项动画 + serverSeed；其余仅 `(item, grade)`。
  - `PacketCsgoBulkProgress.handleServer` 主线程：校验 / 快照 / 预消耗 K 箱 + K 钥匙（沿用 `tryConsumeKeys(player, box, count)` 新签名）/ `CompletableFuture.supplyAsync` 提交后台 → 完成后 `sp.level().getServer().execute(...)` 切回主线程收尾。
  - 主线程收尾：发 `PacketBoxOpenResult`（box1 全量 50 项动画）+ 发 `PacketBoxBulkResult`（boxes 2..K）+ `inventory.add` 循环（vanilla 自动 merge 同类 stack，满则 `sp.drop` 兜底）+ `awardStat(OPENED_BOXES_STAT, K)` + `OpenedBoxTrigger.trigger() × K`。
- **服务端辅助 API 抽取**（10 平台镜像）：
  - `PacketCsgoProgress.tryConsumeKeys(player, box, count)`：原 `tryConsumeKeys(player, box)` 抽出为支持 `count` 参数（单开调用 `count=1`）。
  - `PacketCsgoProgress.tryConsumeBoxes(player, box, count)`：新增，遍历玩家全背包消耗 N 个同名箱（含主手）。
  - `PacketCsgoProgress.isOpenBlockedStatic / blockFurtherOpensStatic`：原 `private` 提升为 package-private 静态方法供 bulk handler 复用。
- **客户端 `CsboxProgressScreen` 路由**：主动画播完分支检测 `PacketBoxBulkResult.consumeMatching(requestId)`；命中 → 开 `CsboxBulkResultScreen(全部 items + grades)`；未命中（单开路径）→ 沿用原 `CsLookItemScreen` 行为。完全向后兼容。
- **`bulkOpenCount` 配置**（`[advanced]`，0=无上限，默认 0）：服务端权威截断，客户端总览屏镜像 clamp。
- **ConfirmationScreen 二次确认**：总览屏「全部开启」先跳确认屏（展示消耗量），确认后才发 `PacketCsgoBulkProgress`。
- **新增 9 个 i18n key**（中英双版本）：`gui.csgobox.bulk.{title,box_name,box_count,key_count,key_count_no_key,openable_count,cannot_open,confirm,collect,waterfall_empty}`。

### 备注
- `BULK_COMPUTE_POOL` 硬编码 2 daemon 线程（hot path 上 99% 玩家 1 个 bulk 请求，2 线程足够 2 个并发操作员）。
- 性能：576 个箱子（36 主背包 × 16 stack 上限）单次异步预计 ~300ms 后台（不卡主线程），主线程收尾发 2 个包 + `inventory.add` × K（vanilla `SimpleContainer.add` 自动合并同类 stack 至 `Math.min(maxStack, count+addCount)`，满后 `sp.drop` 走 vanilla `ItemEntity` 自然 merge）。
- 风险：玩家在 async 计算中退出 / 死亡 → 主线程收尾时检查 `sp.isRemoved() || !sp.isAlive()` 直接丢弃结果；cooldown `OPEN_BLOCKED_UNTIL_TICK`（10 tick）防双发。

## [1.0.5] - 2026-06-29

### 新增
- **成就系统（`全新的开始`）。** 在原版进度面板中加入 `CS2 Box` 标签页，第一个成就「全新的开始」(`A Fresh Start`) 在玩家首次主动开启任意 CS:GO 箱子时解锁，与原版成就一致：弹出 toast，聊天栏显示 `wikkd has completed the advancement [CS2 Box] 全新的开始`，不发放任何奖励。Mob 掉落的箱子不算"开箱"，需玩家右键主动开启。数据通过 Minecraft 原生 `CriteriaTriggers` 持久化，无需新增 Capability，存档迁移无影响。后续若扩展更多成就，沿用 `csgobox:advancement/root.json` 节点下追加 JSON 即可。
- **隐藏紫色挑战「导购」(`Shopkeeper`)。** 玩家累计主动开启 200 个 CS:GO 箱子时解锁；图标为绿宝石，框色为紫色（`frame: "challenge"`），满足条件前在进度面板中不显示该节点（`hidden: true`）。数据走 Minecraft 原生统计系统 `csgobox:opened_boxes`（`Stats.CUSTOM`），无需新增 Capability，`TriggerInstance` 新增 `count` 字段实现"任意 vs 阈值"二合一（`csgobox:opened_box` 同一个 trigger 类同时驱动两个成就）。奖励与原版成就一致 —— 无。
- **配置开关 `enableAchievements`（默认 `true`）。** 在 `config/csgobox-common.toml` 的 `[advanced]` 段新增 `enableAchievements: boolean = true`，玩家可手动关闭整个成就系统。关闭期间 `csgobox:opened_boxes` 统计仍累加（保留进度），`OpenedBoxTrigger.trigger` 跳过调用；重新开启后，后续开箱即恢复触发，统计进度不丢。

### 修复
- **`CsboxConfig` 字段初始化修复。** 早先 v1.0.5 (commit 862ab1f) 中的 `CsboxConfig` 类采用 `init()` 延迟填充模式，但 `init()` 整个代码库中从未被调用，导致所有配置驱动的字段在运行时读取为 0/false/null：生物 CS:GO box 掉落、调试日志、默认 box 自动加载、物品名称预览、音效（打开/tick/揭晓）全部失效；`switch (CONFIG.animationSpeed)` 在首次动画 tick 抛出 `NullPointerException`，任何玩家开箱即崩溃。按 `AGENTS.md` 第 21 行约定，将 `.get()` 内联到构造器中，删除死代码 `init()`。该问题在 `.planning/v1.0.5-REVIEW.md` 中被记录为 CR-001/CR-002/CR-003。

### 移除
- **完全移除 Cloth Config 依赖。** 模组不再依赖 `me.shedaniel.cloth:cloth-config-neoforge`。配置现通过 NeoForge 原生 `ModConfigSpec` API 持久化，存储为 `config/csgobox-common.toml`。

### 新增
- **`csgobox:csgo_key3` 的锻造台升级路径。** 玩家在锻造台中使用 `minecraft:netherite_upgrade_smithing_template` 和一个下界合金锭，将钻石钥匙 (`csgobox:csgo_key2`) 升级为下界合金钥匙。
  - 配方文件：`data/csgobox/recipe/csgo_key3_smithing.json`
  - **v1.0.5 修正**：此配方现为下界合金钥匙的唯一获取方式。原工作台 3x 下界合金锭合成配方（`data/csgobox/recipe/csgo_key3.json`）已移除。

### 更改
- 配置文件路径从 `config/csgobox.toml` 迁移至 `config/csgobox-common.toml`。现有玩家需手动删除旧文件以避免混淆，数值不会自动迁移。
- **v1.0.5 后续**：配置文件路径从 `config/csgobox-common.toml` 改回 `config/csgobox.toml`，与 1.0.4 之前的命名一致。现有玩家需手动将旧文件重命名（或删除以重置为默认值），数值不会自动迁移。
- 扁平化 `CONFIG` 字段访问。Java 调用方现使用 `CONFIG.fieldName` 而非 `CONFIG.section.fieldName`。TOML 端仍按 `[general]`、`[advanced]`、`[sound]`、`[animation]` 分组。

### 备注
- 构建产物为 `csgobox-1.0.5.jar`。
- 字段语义和默认值较 v1.0.4 无变化。
- 实际 tag `v1.0.5` 指向当前 commit。原 `release: v1.0.5` 提交 (862ab1f) 由于缺少 `CsboxConfig.java` 无法从 tag 干净编译，未被打 tag。

## [1.0.4] - 2026-06-19

### 新增
- 默认生成的箱子 JSON 现包含英文 `_tutorial` 对象。文档涵盖文件名映射、钥匙、掉落率、随机权重、实体格式、等级列表、物品对象、`components`、旧版 `tag` 及推荐工作流程。
- 在 `PacketBoxOpenResult` 中添加服务端授权的动画物品数据，使客户端动画条与最终奖励使用同一服务端选中的结果。
- 为预览和开箱结果数据包添加请求 ID 匹配，防止过期客户端响应被错误屏幕消费。

### 修复
- 修复了集成服务器游戏中客户端 GUI 从错误线程打开的问题。箱子界面现仅为本地客户端玩家打开，并调度到客户端线程。
- 修复了 `RenderFontTool` 在屏幕字体临时为 null 时崩溃的问题，改用 `Minecraft.getInstance().font` 回退。
- 修复了服务端拒绝开箱请求（如短冷却、钥匙缺失、空箱或物品无效）时动画永远等待的问题。服务端现发送匹配的空白结果，客户端可正常退出。
- 修复了中奖物品位于动画条开头附近时动画速度行为异常的问题。现从动画窗口后期选取中奖索引，使动画开始快、接近奖励时减速。
- 修复了空箱警告文字被 3D 箱子模型遮挡的问题，改为在模型上方使用前景叠加层绘制警告。

### 更改
- 开箱冷却改为短效防双击保护，而非完整动画时长，因此用 ESC 取消动画不会阻塞下次手动测试。
- 在边界处复制可变 `ItemStack` 和集合数据，防止意外修改原始配置数据。
- `RandomItem` 对 null 和空输入进行了防御性处理，并使用 long 类型总权重以避免溢出。
- `CsboxProgressScreen` 现直接使用帧间渲染插值因子，而非将速度混入插值量。

### 备注
- 现有 JSON 文件不会被覆盖。`_tutorial` 对象仅在模组在空的 `config/csbox` 目录中自动生成新默认 JSON 时出现。
- 当前 Gradle 模组版本为 `1.0.4`，预期发布 jar 为 `csgobox-1.0.4.jar`。

## [1.0.2] - 2026-06-01

### 新增
- **NeoForge 1.21.1 移植** — 从 Forge 1.20.1（ChloePrime/CS2-Box）完整迁移至 NeoForge 21.1.115+
- **`/csbox` 命令系统** — 游戏内箱子管理命令：
  - `/csbox list` — 列出所有已注册箱子及等级概要
  - `/csbox info <box>` — 显示特定箱子的详细配置
  - `/csbox add <box> <grade> hand <count>` — 将手持物品添加到箱子的等级池
  - `/csbox give <box> [count] [player]` — 向玩家给予箱子物品
  - `/csbox reload` — 从 KubeJS 脚本重新加载箱子定义
  - 全面的 TAB 补全支持（箱子 ID 和等级 ID）
- **箱子 JSON 加载器（`BoxJsonLoader`）** — 运行时从 `config/csbox/*.json` 加载箱子配置，同时支持 `components`（DataComponent）和旧版 `tag`（NBT）物品格式
- **实体掉落率系统** — 通过 JSON 配置中的 `entity_drop_rates` 覆盖单个实体掉落率；抢夺附魔加成（每级 ×0.5，上限 100%）
- **KubeJS 集成** — 基于脚本的箱子创建 API：
  - `BoxBuilderJS` / `GradeBuilderJS` — 箱子与等级的流式构建器
  - `CsboxRegistryEventJS` — 注册自定义箱子的 KubeJS 事件
  - `DefaultBoxes.js` — 内置默认箱子定义
  - `KubeJsPlugin` — 兼容 KubeJS 2101.x 的插件入口点
- **箱子注册表 API** — `BoxDefinition`、`GradeGroup`、`BoxRegistry` — 不可变数据模型，采用安全运行时修改的"读-重建-替换"模式
- **`PacketBoxOpenResult`** — 专用服务端→客户端数据包，保证开箱后数据传递，解决 UI 渲染竞态条件
- **中文（zh_cn）翻译** — 完整的本地化，包括命令消息和界面字符串

### 修复
- **等级映射反转** — JSON 配置中的等级（grade5 = 最稀有，grade1 = 普通）在显示时被错误映射：AWP/下界合金装备显示为"grade 1"（蓝色），垃圾物品却显示为"grade 5"（金色）。已在 `ItemCsgoBox.getItemGroup()` 和 `RandomItem.randomItemsGrade()` 中修复。
- **客户端-服务端数据同步** — 解决了服务端数据可能在屏幕创建前到达导致 UI 渲染失败的问题。专用数据包确保显示前 100% 送达。
- **物品栈污染** — 配置中的 `ItemStack` 实例现于存储/修改前调用 `.copy()`，防止原始配置数据被破坏。
- **JSON 实体列表解析** — 纯实体 ID 数组（如 `["minecraft:zombie"]`）时崩溃。现同时支持交替的 `[id, rate]` 格式和纯 `[id]` 格式（回退至全局掉落率）。

### 更改
- **移除废弃类** — `CsgoBoxCraftMenu`、`CsgoBoxCraftScreen`、`RecModMenus`、`RecModScreens`、`ItemOpenBox`、`PacketUpdateMode`、`ItemNBT`
- **移除合成配方/模型** — `csgo_box_craft` 配方、模型和纹理已移除
- **更新依赖** — NeoForge 21.1.115、Cloth Config 15.0.130、KubeJS 2101.7.2-build.368、Rhino 2101.2.7-build.82
- **Gradle toolchain** — 需 JDK 21（通过 `org.gradle.java.home`）
- **StreamCodec** — 字段超过 6 个的类使用手动 `StreamCodec.of()` encode/decode（NeoForge 1.21.1 要求）

### 环境要求
- Minecraft 1.21.1
- NeoForge 21.1.115+
- Java 21
