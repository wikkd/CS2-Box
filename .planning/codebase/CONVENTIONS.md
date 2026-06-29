<!-- refreshed: 2026-06-29 -->
# 编码规范

**分析日期:** 2026-06-28
**最近增量更新:** 2026-06-29

## 命名模式

**包（Package）：**
- 所有源码位于 `src/main/java/com/reclizer/csgobox/` 下
- 子包按职责组织：`box/`、`capability/`、`command/`、`config/`、`event/`、`gui/`、`item/`、`packet/`、`sounds/`、`utils/`
- 小写、单单词、无下划线

**文件：**
- 每个文件一个顶层 public 类型，文件名与类型名一致
- Package-private 辅助类放在与最相关类型同名的文件中
- 任何 Java 文件名中不使用下划线

**类 / Record / 枚举：**
- PascalCase；首字母缩写作为较长标识符的一部分时保持小写（`ItemCsgoBox`、`CsboxConfig` 等）
- Item 类以 `Item` 为前缀，Screen 类以 `Screen` 为后缀，Packet 类以 `Packet` 为前缀，Mod 容器/注册类以 `Mod` 为前缀

**静态工具类：** `public final class X { private X() {} ... }`，所有成员 `public static`

**Record：** `public record Name(...)` 配合 compact constructor 进行规范化。可变操作通过 `with*` 方法返回新实例。

**函数：** camelCase，首选动词。布尔值通常读作谓词（`isOpenBlocked` 等）。

**变量 / 字段：** static final 常量使用 `UPPER_SNAKE_CASE`，实例字段使用 `lowerCamelCase`。

**类型：** 使用 `Identifier`（而非 `ResourceLocation`）。Minecraft 类型使用 `ItemStack`、`Component`、`ServerPlayer` 等。可为 null 的查找使用 `Optional<T>`。

## 代码风格

**格式：** 4 空格缩进，K&R 大括号风格，UTF-8 编码。无自动化格式化工具。

**使用的现代 Java 特性：** Record、模式匹配 switch、`instanceof` 模式匹配、`Math.clamp`（Java 21+）、`var`、紧凑 record 构造方法、工具类 `private` 构造方法。

## 导入组织

1. `java.*` 和 `javax.*`
2. 第三方（`com.google.gson.*`、`com.mojang.*` 等）
3. 项目导入（`com.reclizer.csgobox.*`）
4. Minecraft / NeoForge（`net.minecraft.*`、`net.neoforged.*`）

## 配置

**框架：** NeoForge 原生 `ModConfigSpec`。Cloth Config 已在 1.0.5 中移除。

**规范构建（`CsgoBox.java:39-44`）：**
```java
static {
    var pair = new ModConfigSpec.Builder().configure(CsboxConfig::new);
    CONFIG = pair.getLeft();
    CONFIG_SPEC = pair.getRight();
}
```

**注册（`CsgoBox.java:47`）：**
```java
ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC, "csgobox.toml");
```

**Config 类规则：**
- 类为 `public`，package-private 构造方法接受 `ModConfigSpec.Builder`
- 公共字段镜像解析后的值（`CONFIG.fieldName`），私有 `*Value` 字段持有 raw handle
- `push("groupName")` / `pop()` 括起每个 TOML 区段
- `comment("...")` 在每次 define 调用前
- 在构造函数中直接调用 `.get()`，无 `init()` 辅助方法

## 错误处理

**策略：** 信任边界防御性规范化，格式错误的网络包快速失败，用户配置 JSON 记录日志后继续。

**模式：**
1. Record compact constructor 中规范化（null -> EMPTY、`List.copyOf` 防御性复制、`Mth.clamp` 钳制）
2. 信任边界验证抛出 `DecoderException` / `IllegalArgumentException`
3. JSON 容错：每个步骤 `try/catch`，WARN 级别日志，默认值替代
4. 命令异常使用 `DynamicCommandExceptionType`
5. 错误日志按严重度分级（error/warn/info/debug）

## 日志

**框架：** SLF4J via `com.mojang.logging.LogUtils`

**设置：** 单例 `CsgoBox.LOGGER`，无每个类各自的 Logger 字段。

**模式：** 参数化消息，异常 throwable 放最后，使用标准 info/warn/error/debug。

## 注释

- 每个公共 record 和 packet 类使用 Javadoc
- 行内 `//` 注释用于标记安全相关行为或线程安全不变量
- 句子首字母大写，句号结尾；多段落使用 `<p>` 标签

## 函数设计

- 大多方法 10-30 行。GUI 渲染和命令树有意较大。
- 避免超过 5 个参数，使用小型 record 载体。
- 返回 `Optional<T>`（可为 null 查找）、`ItemStack.EMPTY`（无物品）、空集合而非 `null`。
- 纯函数 -> `public static` 在 `final` 工具类上；有状态 -> `public final class`；Record -> 不可变。

## 模块设计

- DeferredRegister 模式：`Supplier` 字段 + 静态 `register(IEventBus)` 方法
- 无 `module-info.java`，无 `package-info.java`
- Mod 初始化集中在 `CsgoBox.java:46-59`，按顺序注册

## 数据包路径

- 配方目录：`data/csgobox/recipe/`（单数）。所有四个钥匙配方在此。
- 资源命名空间使用小写 modid `csgobox`。不使用旧的 `csbox` 拼写。

## 线程模型

- 仅游戏线程修改。UI 通过 `Minecraft.getInstance().execute(...)` 分发。
- 网络 handler 通过 `context.enqueueWork(...)` 分发。
- 静态可变状态仅在主线程上访问。

---

*规范分析：2026-06-28（增量更新：2026-06-29）*
