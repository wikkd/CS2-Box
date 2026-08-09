# /csbox nbt hand — 查看手中物品 NBT JSON 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 或 executing-plans 逐任务实现。步骤用 checkbox 跟踪。

**Goal:** 新增 `/csbox nbt hand` 子命令（所有玩家可用），输出主手物品的完整 JSON（`{id, count, components}`），可直接粘贴进 `config/csbox/*.json` 的 items 条目。

**Architecture:** 复用各平台已有的 `BoxItemCodec.serializeItemStack()`（唯一序列化事实来源，从包私有改为 public），命令层只做「取主手物品 → 序列化 → 聊天输出」。权限结构从「根节点 requires」改为「每子命令 requires」以放行 nbt 分支。

**Tech Stack:** NeoForge 21.x（v1_21_1）/ 26.1.2 / 26.2、Brigadier、Gson、DataComponentPatch codec

## Global Constraints

- 基准模块 `v26_1_2`，`v26_2` 定点合入（禁止整文件覆盖）；`v1_21_1` 手工适配
- 每平台改动后用 **clean** `compileJava` 验证（增量缓存可能造假象）
- forge_26_1_2 由 `scripts/port-forge-2612.py` 从 v26_1_2 机械转换，源码不入库不提交
- 每个 Gradle 调用只构建一个版本，用 `-Pactive_versions=<v>` 指定
- 命令权限：现有子命令保持管理员级（v1_21_1: `hasPermission(2)`；26.x: `permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`），`nbt` 分支不设权限
- 裸 `/csbox` 帮助保持管理员可见（运行时校验），玩家敲 `/csbox` 仍提示未知命令

---

### Task 1: 公共 lang 键（zh_cn + en_us）

**Files:**
- Modify: `common/src/main/resources/assets/csgobox/lang/zh_cn.json`
- Modify: `common/src/main/resources/assets/csgobox/lang/en_us.json`

**Interfaces:**
- Produces: 6 个新键，供 Task 2-4 引用——`commands.csgobox.help.line.nbt`、`commands.csgobox.nbt.hand.header`、`commands.csgobox.nbt.hand.empty`、`commands.csgobox.nbt.hand.truncated`、`commands.csgobox.nbt.hand.error`、`commands.csgobox.help.need_op`

1. zh_cn.json：`"commands.csgobox.help.line.tutorial"` 行后插入 `help.line.nbt`；`"commands.csgobox.error.grade_not_found"` 行后插入 5 键
2. en_us.json：同锚点插入
3. 验证 `python3 -m json.tool` 两文件合法
4. Commit

### Task 2: v26_1_2 基准实现（命令重构 + nbt 分支）

**Files:**
- Modify: `v26_1_2/.../box/BoxItemCodec.java`（类 `final`→`public final`、方法 `static`→`public static`）
- Modify: `v26_1_2/.../command/CsboxCommand.java`

新增 imports：`Gson`、`JsonObject`、`SimpleCommandExceptionType`、`BoxItemCodec`。
新增字段：`NBT_EMPTY`（SimpleCommandExceptionType）、`GSON`、`MAX_NBT_CHARS = 20000`。
新增方法：`isGameMaster`（`permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`）、`showHandNbt`。
注册树：根节点删除 `.requires(...)`；每个现有子命令分支头插 `.requires(CsboxCommand::isGameMaster)`；裸 `.executes(showHelp)` 挂根；新增 `nbt → hand` 分支。
`showHelp`：开头运行时校验 GM，throw `SimpleCommandExceptionType(need_op).create()`；帮助行中 give_vanilla 与 reload 之间插 `help.line.nbt`。
验证：`./gradlew :v26_1_2:clean :v26_1_2:compileJava -Pactive_versions=26.1.2` → BUILD SUCCESSFUL。Commit。

### Task 3: v26_2 定点合入

同 Task 2 全部改动手工合入（分无 FMLPaths import——保持全限定名写法；`BoxItemCodec` 包名 `v26_2`）。验证 `clean compileJava -Pactive_versions=26.2`。Commit。

### Task 4: v1_21_1 适配

同 Task 2 结构。差异：`isGameMaster` = `source.hasPermission(2)`（不 import Permissions）；`IdentifierArgument`/`Identifier` 保持 `ResourceLocationArgument`/`ResourceLocation`。验证 `clean compileJava -Pactive_versions=1.21.1`。Commit。

### Task 5: forge_26_1_2 再生（实验模块，不提交）

运行 `python3 scripts/port-forge-2612.py`；`./gradlew :forge_26_1_2:compileJava -Pactive_versions=forge-26.1.2` 尽力验证；失败只记录不阻塞。

### Task 6: 文档同步

- README.md:16 功能清单行加 `/csbox nbt hand`
- CHANGELOG.md 顶部新增 `## [Unreleased]` 节
- Commit

### Task 7: 运行时回归清单（人工验证）

玩家/管理员两种身份、空手、带 components 物品、粘贴回 config 闭环 reload+info+开箱、超长书截断、三平台各一次。