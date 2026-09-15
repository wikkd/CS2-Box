# 设计：终端机谈判 → 通用 NPC 交互协议

> 状态：**设计草案，未排期**（架构级改动）。渐进路线：① 村民军火商直接开谈判 UI →
> ② 会话解耦为通用服务 → ③ Citizens/Custom NPCs 桥接。本文档只做设计与边界，实现
> 需单独立项评审。

## 1. 现状（已核实源码）

- `TerminalSessionManager`：静态 `ConcurrentHashMap`，键 `玩家UUID:terminal_uid`
  （**会话绑定物品 uid，不是箱子类型**）；`TerminalSession` = common `NegotiationModel`
  （576 行状态机，平台无关）+ 5 轮 `TerminalRoundData` + 10 槽位物品。
- 生命周期：`PacketTerminalOpen` 取锁 → `Reject/Buy/Close` 服务端强制推进 →
  `isFinished()`（CLOSED/FAILED）释放锁；三终态销毁终端机物品。
- 落盘 `TerminalStateStore`（`world/csgobox/terminal_state.bin`，VERSION=3）；
  转手锁死（`terminal_owner` 盖章）；超时按世界时钟推进。
- 7 个 `PacketTerminal*` 包 + `TerminalScreen`/`TerminalBootScreen` 每平台一份。
- 军火商职业村民是**固定交易表**（`trade_set/` + `villager_trade/`），与谈判是两套系统。

## 2. 目标

把「5 轮讨价还价会话」从「终端机物品」解耦成通用服务，让**任意发起方**
（村民、自定义 NPC、其它模组物品）都能复用：`open → offer → resolve → destroy`。

## 3. 渐进路线

### 阶段 1：村民军火商直接开谈判 UI（最小、可验证）
- 把军火商村民的固定交易表升级为终端式谈判：右键村民 → 打开 `TerminalScreen`
  （复用现成 UI + `PacketTerminalState` 数据流）。
- 会话键仍 `玩家UUID:uid`，但 uid 改为村民会话 uid（村民不销毁，只是会话生命周期）。
- 不变量：只有合法会话能销毁对应物品——村民场景无物品销毁，直接释放锁。
- 六平台同步成本：主要改 `ModEvents` 交互入口 + `TerminalSessionManager.getOrCreate`
  的入参抽象（现在硬编码 `ItemTerminal` 栈）。

### 阶段 2：会话服务解耦（接口化）
- `INegotiationProvider`（发起方适配器）：`sessionKey()` / `buildOffers()` /
  `onResolve()` / `onDestroy()`。
- `NegotiationSession`：`open → offer → resolve → destroy` 生命周期。
- `NegotiationService`：取代静态 Manager 的静态 `ConcurrentHashMap`（包一层接口）。
- 落盘 VERSION=3 兼容：旧记录保留，新记录加 `initiatorType` 判别字段。
- `PacketTerminal* → PacketNegotiation*`：common 层保留旧类作 deprecated 包装，
  兼容旧 packet ID（客户端旧版可握手）。

### 阶段 3：生态桥接（可选）
- Citizens2：`NPCRegistry` + 右键拦截 → 调 `NegotiationService.open`。
- Custom NPCs / 其它对话模组：事件桥（KubeJS 三事件模式复用）。

## 4. 边界与风险

- **与「终端机与普通箱严格分离」不变量冲突**：必须保证只有合法会话能销毁/开出对应物品
  （阶段 1 村民场景无销毁，风险最低）。
- 六平台 ×（7 包 + 2 屏 + 会话管理）同步成本线性翻倍——每个阶段都要六平台同步。
- `common NegotiationModel` 已平台中立可复用，瓶颈在平台层包装而非状态机本身。
- 超时销毁逻辑（`removeByUid`、离线 uid 集合）与「物品 uid」强耦合，解耦时需
  `initiatorType` 分派销毁策略。

## 5. 评审问题

1. 阶段 1 是否值得先做（村民谈判化）？还是直接跳阶段 2？
2. 协议命名：`PacketNegotiation*` 是否引入兼容负担？
3. 会话键：保留 `player:uid` 还是改为 `player:sessionId`（随机 UUID + 发起方标识）？
4. 落盘格式 VERSION 升到 4 还是保持 3 + 判别字段？

## 6. 工作量（每阶段，估算）

- 阶段 1：1–2 人日（入口改动 + 六平台同步）。
- 阶段 2：接口抽取每平台 1–2 人日 + 落盘兼容 + 六平台回归。
- 阶段 3：视桥接目标，另行评估。
