<!-- 刷新日期：2026-06-28 -->
<!-- 更新日期：2026-06-29（增量更新——配方目录重命名、CsboxConfig 搬迁、Cloth Config 移除、csgo_key3 工作台配方移除、JDK 21 运行配置锁定） -->
# 代码库关注项

**分析日期：** 2026-06-28
**上次映射提交：** `a8bea6a`（HEAD, 2026-06-29）

## 技术债务

### BoxJsonLoader 是一个上帝类（489 行）
- 问题：`BoxJsonLoader.java` 在一个类中处理了 JSON 默认编写、Schema 教程、文件 IO、权重解析、实体解析、物品解析（DataComponent + 旧版 NBT）以及序列化到磁盘——全在一个类中。
- 文件：`src/main/java/com/reclizer/csgobox/box/BoxJsonLoader.java`
- 影响：高。任何 Schema/格式演变的单一变更点。改动一个关注点就有导致其他功能回归的风险。
- 修复方案：拆分为 `BoxDefaultsWriter`、`BoxJsonParser`、`BoxJsonSerializer`、`BoxFileIO`。每个类可以独立测试。

### 硬编码的 GRADE_COLORS 数组包含重复值
- 问题：`BoxJsonLoader.java:45` 定义了 `GRADE_COLORS = {0xFFD32CE6, 0xFF8847FF, 0xFF4B69FF, 0xFF4B69FF, 0xFF4B69FF}`——第 3-5 项都使用 `0xFF4B69FF`。
- 影响：中。用户在 UI 中看不到 5 个稀有度中的 3 个的视觉递进。
- 修复方案：为每个等级定义不同的颜色，或者提取到 `CsboxConfig` 中。

### `OPEN_BLOCKED_UNTIL_TICK` 使用非线程安全的 `HashMap`
- 问题：`PacketCsgoProgress.java:46` 使用 `new HashMap<>()`。数据包处理器在 Netty 事件循环上运行，多个入站数据包可能并发访问。
- 影响：中到高。竞态条件、更新丢失或高负载下的 `ConcurrentModificationException`。
- 修复方案：使用 `ConcurrentHashMap`，或移到每个玩家的 Capability 中。

### CsboxConfig 字段默认值硬编码在 Builder 中
- 问题：所有默认值都硬编码在 Builder 调用中，版本间无迁移路径。
- 影响：低。玩家保留覆盖值；新默认值仅适用于新安装。

## 已知 Bug

### 两个数据包中的空 `return null` 在加固后仍然存在
- `PacketSyncBoxItems.java:145` 和 `PacketBoxOpenResult.java:125` 在某些路径中返回 `null`。
- 可能导致上游 `NullPointerException`，使数据包处理器静默崩溃。

### 配置文件重命名未自动迁移（csgobox.toml ↔ csgobox-common.toml 来回变更）
- 配置文件名称在 v1.0.5 内已重命名两次，两次都没有自动迁移现有值。
- 玩家必须手动重命名或删除旧的配置文件。

### 空箱警告文字被 3D 模型遮挡
- 1.0.4 之前存在，已通过前景覆盖层修复。

## 安全考虑

### 服务器权威契约合理但执行不均匀
- 验证分散在 `PacketValidation`、`handleServer` 和 `ItemCsgoBox` 中。
- 建议：提取单一的 `OpenBoxValidator.validate()`。

### `requestId` 可伪造
- 客户端 `requestId` 仅用于匹配动画，不用于安全决策。优先级较低。

### KubeJS 集成加载用户提供的 JSON
- 建议：接受前检查物品标识符是否存在，明确拒绝空的等级列表。

## 性能瓶颈

### 每 Tick 的静态 HashMap 清理
- `OPEN_BLOCKED_UNTIL_TICK` 无限制增长。添加 `ServerTickEvent` 处理器清理过期条目。

### `CsboxProgressScreen` 插值每帧计算
- 在服务端发送滚动索引后，预计算缓动曲线。

### `BoxJsonLoader` 每次重载都读取所有 JSON
- 缓存 `Path -> BoxDefinition`，仅重新解析修改过的文件。

## 脆弱区域

### 服务端 `/csbox` 命令（472 行）
- 5 个子命令，圈复杂度高，零测试覆盖。

### 箱子动画选择
- 动画必须落在服务端滚动的索引上。改动缓动数学可能引入视觉不匹配。

### JSON 定义的箱子等级/物品模型
- 同时支持 `components` 和旧版 `tag`。Schema 演变需谨慎。

### 实体掉落率逻辑
- 抢夺附魔缩放（每级 ×0.5，上限 100%）硬编码。

## 扩展限制
- 静态随机状态：全局共享 `SecureRandom` 和一个冷却映射。高玩家数量时冷却映射清理可能 O(n)。可接受。

## 有风险的依赖

### NeoForge 26.1.2.76（刚移植）
- 移植文档在 `docs/port-26.1.2.md` 中。下次升级时注意弃用警告。

### Java 21 toolchain
- `gradle.properties` 硬编码 macOS 路径。Linux/Windows 贡献者需覆盖。自 `a8bea6a` 起已锁定 runConfig。

## 缺失的关键功能

### 无自动化测试套件
- `src/test/java/` 不存在。无 JUnit、无 GameTest。

### 无 JSON Schema 验证
- `BoxJsonLoader` 静默接受格式错误的 JSON。

### 无英文 README/CHANGELOG
- 所有面向用户的文档只有中文，限制了国际贡献。

## 测试覆盖缺口

| 区域 | 风险 | 优先级 |
|------|------|--------|
| packet/ 中每个 handleServer | 高 | 高 |
| RandomItem 加权随机抽样 | 中 | 中 |
| /csbox 命令参数解析 | 中 | 中 |
| BoxJsonLoader 往返解析 | 低到中 | 中 |

## 2026-06-29 更新

### 配方目录重命名：`data/csgobox/recipes/` → `data/csgobox/recipe/`
**状态：已解决。** 目录已重命名为单数 `recipe` 以匹配 Minecraft 数据包规范。

### `csgo_key3` 工作台配方已移除
**状态：破坏性变更，已记录。** `csgo_key3` 现在只能通过锻造台获得。

### `Cloth Config` 依赖已完全移除
**状态：已解决。** 使用 `ModConfigSpec`。`grep -ri "cloth|shedaniel" src/` 无匹配。

### `CsboxConfig` 搬迁到 `com.reclizer.csgobox.config` 包
**状态：已解决。** `.gitignore` 的 `config/` 条目收紧为 `/config/`。

### JDK 21 运行配置锁定
**状态：已针对 runClient 缓解。** 残留风险：`org.gradle.java.home` 仍指向 macOS 特定路径。

---

*关注项审计：2026-06-28*
*增量更新：2026-06-29*
