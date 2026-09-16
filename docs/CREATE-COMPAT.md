# 机械动力（Create）联动

> 版本：v2.0.1 · 平台：`v1_21_1`（NeoForge + Create 0.6.x / MC 1.21.1）、
> `forge_1_20_1`（Forge + Create 0.5.x / MC 1.20.1）
> 其余平台（26.x 等）无 Create 构建，不涉及。

## 一句话

**机械手（Deployer）拿着箱子的对应钥匙，从侧面指向传送带/置物台上的箱子物品，
即可按与手动开箱完全相同的规则直接开箱**：产物替换台上箱子（或发给旁边玩家），
钥匙从机械手消耗，保底/限购/冷却/权限/事件全链路生效。

## 玩法

```
        [机械手]  ← 手持钥匙（或用漏斗/管道补钥匙）
           │  侧面指向（facing 不是朝下）
           ▼
   [传送带 belt] 或 [置物台 depot] 上放着一个箱子物品（csgobox 箱货）
           │
           ▼
   机械手每周期「使用」一次：
      钥匙匹配箱子定义 key → 开箱（服务端完整管线）
      产物（带 csgobox:grade 印记）替换台上的箱子 → 传送带/机械臂运走
      无钥匙要求的箱子 → 机械手空手即可开
```

- **机械手朝向**：必须**侧面/斜向**指向台上物品（`facing != DOWN`）。
  Create 把「朝下对准传送带/置物台」保留给自带的传送带加工管线，机械手朝下时
  不会执行任何右键动作，这是我们无法绕过的上游行为。
- **对准位置**：机械手面前 2 格方块即被点击方块；传送带请对准**物品所在的 segment**，
  置物台对准其本体。
- **钥匙匹配**：按箱子 JSON 的 `key` 字段（与手动开箱同一来源 `BoxDefinition.keyItem`）；
  无 `key`/`key: minecraft:air` 的箱子**空手**即可开。
- **真玩家**也可以手持钥匙右键台上箱子开箱（产物进背包，台上箱子消失）；
  无钥匙箱子真玩家右键**不介入**，保留 Create 原生「拾取台上物品」交互。

## 规则一致性（与手动开箱完全相同）

所有台上开箱走 `BoxOpenExecutor`（与 `PacketCsgoProgress` 共用的无头开箱核心）：

| 环节 | 行为 |
|---|---|
| 玩家/箱子守卫 | `OpenBlockGuard` 10 tick 冷却、箱子/终端类型校验 |
| 事件 | `BoxOpeningEvent`（可取消，取消则台上箱子与钥匙分文不动）、`BoxOpenedEvent` |
| 约束 | `max_per_player` / `cooldown_seconds` / `permission` 全量生效 |
| 保底 pity | 按交互者 UUID 计数推进（机械手按自己的 UUID 独立计数） |
| 钥匙消耗 | 与开箱一致：全背包查找匹配钥匙并消耗；创造模式免费 |
| 权重/全零 | 档内权重、全零权重拒开语义一致 |
| 随机源 | 同一 CSPRNG 派生 `serverSeed`（48 位 LCG 不回传、不暴露） |
| 产物 | 机械手 → 替换台上物品；真玩家 → 物品栏（满则掉落） |

机械手（fake player）场景的产物会带上 `csgobox:grade` 印记，之后可直接投入
武库拆解台回收（拆解台自动化兼容同样面向 Create，见 CHANGELOG v2.0.1）。

## 实现说明

- 不依赖 Create 编译产物：通过**反射**调用 Create 稳定公开 API
  `BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE)` +
  `handleCenteredProcessingOnAllItems(...)`（`TransportedResult.removeItem()/convertTo()`）。
  无 Create 时装载零副作用（Create 类名只在反射探测时解析，失败即静默降级）。
- Create 机械手用假玩家（`DeployerFakePlayer`）模拟右键，会照常派发
  `PlayerInteractEvent.RightClickBlock`——我们在该事件中接管：读台上物品 → 匹配钥匙 →
  执行开箱 → 消费（替换/移除）→ `setCanceled` + `useBlock/useItem` 放行开关阻止 Create
  后续处理。Create 在激活结束后把假玩家主手物品同步回机械手，故钥匙消耗自然生效。
- 代码位置（两个平台相同结构）：
  - `packet/BoxOpenExecutor.java` —— 无头开箱核心（单开包与机械手共用）
  - `packet/PacketCsgoProgress.java` —— 重构为薄壳（校验 → executor → 动画数据包）
  - `create/CreatePlatformItems.java` —— Create 台上物品读写反射层
  - `create/DeployerBoxOpen.java` —— `RightClickBlock` 监听器

## 已知边界

- 传送带上的物品在移动：机械手激活瞬间物品需处于被点击 segment 的中心 ±0.75 格内
  （Create 加工回调的距离上限），自动化时建议让物品在机械手处停留/减速（如挡住传送带）。
- 机械手不产生动画回放（无 GUI），开箱直接完成；产物替换台位。
- 批量的「一次开 N 箱」不适用于本联动（每次机械手动作开 1 箱）。
- 26.x 平台待 Create 发布对应构建后，可用同一套反射层平移接入。