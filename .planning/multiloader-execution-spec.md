> ⚠️ 已归档（历史规划快照）：本文档为过去的重构/审计规划产物，不再维护；当前进度与状态以 CHANGELOG.md 与 README.md 为准。

# CS2-Box MultiLoader 重构执行规范

日期：2026-06-29

适用范围：

- `common/`
- `v1_21_1/`
- `v26_1_2/`
- 根 Gradle Multi-Project 构建

本文档基于 [multiloader-refactor-plan.md](/Users/shuangyuexingxun/Desktop/CS2-Box/.planning/multiloader-refactor-plan.md) 细化为可执行任务清单与实施规范。目标不是描述理想架构，而是提供一个可以按顺序执行、每一步可验收、失败可回退的落地方案。

## 1. 实施原则

### 1.1 总体策略

重构遵循以下顺序：

1. 先稳定构建系统
2. 再建立 `common` 的最小可行边界
3. 再让 `v1_21_1` 成为稳定平台基线
4. 最后建立 `v26_1_2` 并吸收 26.1.2 API 差异

禁止并行推进以下两类工作：

- 在 `v1_21_1` 尚不可稳定编译时，同时开展大规模 `common` 迁移
- 在 `common` 的边界尚未稳定时，同时复制完整 `v1_21_1` 到 `v26_1_2`

### 1.2 任务执行方式

每一步必须显式记录：

- 输入：依赖哪些文件、模块、现状
- 输出：新增或变更什么文件、配置或能力
- 依赖：必须先完成的前置任务
- 验收：用什么命令或审查清单确认完成

任何一步如出现以下信号，应停止继续下钻并回到上一层边界设计：

- 为了让 `common` 编译通过而新增大量一次性平台接口
- 同一个类在 `common` 和平台模块之间来回移动两次以上
- 构建系统问题与业务迁移问题交叉，无法定位失败原因

## 2. 分阶段实施步骤

## 阶段 0：基线冻结与迁移审计

### 0.1 目标

建立真实基线，避免按过期计划执行。

### 0.2 输入

- 当前工作树未提交改动
- 现有多模块结构
- `docs/port-26.1.2.md`
- 根 `build.gradle`、`settings.gradle`、`gradle.properties`

### 0.3 输出

- 当前迁移现状清单
- 共享代码候选清单
- 平台代码保留清单
- 构建阻塞点清单

### 0.4 依赖

- 无

### 0.5 执行任务

1. 导出当前变更状态。
2. 盘点 `common/src/main/java` 当前已存在的抽象接口。
3. 盘点 `v1_21_1` 中所有 Java 类，按“纯业务 / 平台绑定 / 不确定”三类标记。
4. 检查根 Gradle 配置与真实目录状态是否一致。
5. 检查当前是否存在 Mixin、Access Transformer、DataGen、运行配置等额外构建维度。

### 0.6 推荐命令

```bash
git status --short
rg --files common/src/main/java v1_21_1/src/main/java
rg -n "import net\\.(minecraft|neoforged)" common/src/main/java
rg -n "mixin|Mixin|mixins" -S .
./gradlew projects
```

### 0.7 验收标准

- 能回答“当前哪些代码已经迁出，哪些还在 `v1_21_1`”
- 能回答“当前构建失败是仓库配置问题、模块缺失问题，还是源码问题”
- 产出一份明确的类级归属表

### 0.8 失败处理

如果 `./gradlew projects` 因 `v26_1_2` 缺失而失败，先记录为阶段 1 的首要修复项，不继续推进源码迁移。

## 阶段 1：构建系统收口

### 1.1 目标

让根 Multi-Project、`common`、`v1_21_1` 至少处于可配置、可解析依赖状态。

### 1.2 输入

- 阶段 0 的阻塞点清单
- 当前根 `build.gradle`
- 当前 `settings.gradle`
- 当前 `v1_21_1/build.gradle`

### 1.3 输出

- 稳定的仓库配置
- 稳定的模块 include 策略
- 可复用的 Gradle 子模块模板约束

### 1.4 依赖

- 阶段 0 完成

### 1.5 执行任务

#### 任务 1.5.1：统一依赖仓库

输入：

- 根 `build.gradle`
- `v1_21_1/build.gradle`

输出：

- `allprojects.repositories` 包含 NeoForged 官方发布仓库

要求：

- `https://maven.neoforged.net/releases` 必须在根仓库级声明
- 避免每个子模块重复定义同一仓库，除非该仓库只对单模块生效

验收：

```bash
./gradlew :v1_21_1:dependencies --configuration runtimeClasspath
```

通过条件：

- 不再出现 `lwjgl-freetype-3.3.3-natives-macos-patch.jar` 缺失

#### 任务 1.5.2：修正 settings 与模块目录一致性

输入：

- `settings.gradle`
- 文件系统中的实际模块目录

输出：

- 一致的 include 策略

执行策略二选一：

1. 动态 include
2. 立即补齐 `v26_1_2/` 空骨架后静态 include

推荐：

- 在 `v26_1_2` 尚未创建前，先采用动态 include

验收：

```bash
./gradlew projects
```

通过条件：

- Gradle 配置阶段不因模块目录缺失失败

#### 任务 1.5.3：统一子模块构建约束

输入：

- 根 `build.gradle`
- `common/build.gradle`
- `v1_21_1/build.gradle`

输出：

- 明确的根公共约束
- 明确的平台模块私有约束

要求：

- 根项目只保留公共 `group`、`version`、`repositories`、Java toolchain、编码配置
- NeoGradle、runs、processResources、mod metadata 展开放在平台模块中
- `common` 仅使用 `java-library`

验收：

```bash
./gradlew help
./gradlew :common:tasks
./gradlew :v1_21_1:tasks
```

通过条件：

- `common` 不携带任何 NeoForge 运行任务
- `v1_21_1` 具备平台运行与打包任务

### 1.6 阶段验收

必须同时满足：

- `./gradlew :common:compileJava` 可执行
- `./gradlew :v1_21_1:compileJava` 至少能进入源码编译阶段
- 根项目 `./gradlew projects` 成功

### 1.7 回退策略

- 如新增仓库导致解析顺序异常，回退到“只保留 mavenCentral + NeoForged releases”的最小集合
- 如动态 include 影响 IDE，可暂时改为只 include 已存在模块

## 阶段 2：建立 common 的最小可行边界

### 2.1 目标

先把真正稳定的纯业务代码迁入 `common`，避免一开始抽象过度。

### 2.2 输入

- 阶段 0 生成的类级归属表
- 当前 `common/src/main/java/com/reclizer/csgobox/platform/*`
- 当前 `v1_21_1` 业务源码

### 2.3 输出

- `common` 中可独立编译的共享业务核心
- 清晰的“已迁移 / 暂缓迁移”边界

### 2.4 依赖

- 阶段 1 完成

### 2.5 迁移优先级

优先迁移第一批：

- `box/BoxDefinition.java`
- `box/BoxRegistry.java`
- `box/GradeGroup.java`
- `config/CsboxConfig.java`
- `capability/CsboxPlayerData.java`
- `utils/ColorTools.java`
- `utils/EntityChineseMap.java`
- `utils/OverlayColor.java`

第二批视实际依赖决定：

- `box/BoxJsonLoader.java`
- `advancement/ModLoadedTrigger.java`
- `advancement/OpenedBoxTrigger.java`
- `packet/*`
- `command/CsboxCommand.java`
- `sounds/ModSounds.java`

默认暂缓迁移：

- `gui/*`
- `item/*`
- `event/*`
- 渲染相关 `utils/*`

### 2.6 执行任务

#### 任务 2.6.1：为每个候选类做依赖判定

输入：

- 单个候选类源码

输出：

- 归类结果：
  - A：可直接迁入 `common`
  - B：拆分后可迁入 `common`
  - C：必须保留平台层

判定规则：

- 直接引用 `net.minecraft.*` 或 `net.neoforged.*` 不等于一定不能迁
- 但若类的核心语义依赖这些类型，则判定为 C
- 若只在边界处依赖少量平台类型，则优先拆出纯业务部分

验收：

- 每个候选类都有归类结论和原因

#### 任务 2.6.2：迁移第一批纯业务类

输入：

- A 类清单

输出：

- `common/src/main/java/com/reclizer/csgobox/**`

执行要求：

- 使用统一包前缀 `com.reclizer.csgobox`
- 同步修正 imports、包声明和引用方依赖
- 不在 `common` 中引入临时适配注释或 TODO 占位接口

验收：

```bash
rg -n "import net\\.(minecraft|neoforged)" common/src/main/java
./gradlew :common:compileJava
```

通过条件：

- `common` 不直接依赖 MC/NeoForge API
- `common` 可独立编译

#### 任务 2.6.3：补齐最小平台接口

输入：

- B 类清单
- 当前 `platform/*` 接口

输出：

- 最小必要的接口扩展

要求：

- 一个接口必须服务至少两个共享调用点，才有资格新增
- 不允许为了迁移单个类而引入过宽泛的“万能平台服务”
- 不允许在接口中暴露 `Object` 泛滥的无类型 API，除非别无选择且边界极窄

验收：

- 新增接口有明确使用方
- 平台接口数量增长受控

### 2.7 阶段验收

必须同时满足：

- `common` 至少承载 1 批真实业务代码，而不只是接口空壳
- `v1_21_1` 仍能编译，且对 `common` 的依赖方向正确
- 没有把 GUI、注册、事件桥接提前抽象进 `common`

### 2.8 回退策略

若某个类迁入后导致平台接口膨胀：

1. 撤销该类迁移
2. 将其重新归类为 C
3. 只保留已经证明稳定的共享类

## 阶段 3：收敛 v1_21_1 为稳定平台实现

### 3.1 目标

将 `v1_21_1` 从“当前主代码仓库”收敛为“1.21.1 平台实现层”，作为后续 `v26_1_2` 的对照基线。

### 3.2 输入

- 阶段 2 完成后的 `common`
- 当前 `v1_21_1` 全量源码
- 1.21.1 当前可工作的行为基线

### 3.3 输出

- 精简后的 `v1_21_1`
- 清晰的平台入口和平台实现类
- 可打包的 1.21.1 平台产物

### 3.4 依赖

- 阶段 2 完成

### 3.5 执行任务

#### 任务 3.5.1：确定 1.21.1 平台入口

输入：

- 当前 `v1_21_1/.../CsgoBox.java`

输出：

- 最终入口类命名方案

要求：

- 允许保留 `CsgoBox` 名称，只要其职责已收敛到平台入口
- 若重命名为 `CsgoBoxMod`，则必须同步更新 `@Mod` 标注、资源模板和引用
- `CONFIG` / `CONFIG_SPEC` 初始化顺序必须与现有约束保持一致

验收：

- 入口类中不再直接承载可迁入 `common` 的业务逻辑
- 配置注册仍输出到 `config/csgobox.toml`

#### 任务 3.5.2：建立 1.21.1 平台实现包

输入：

- 当前 `platform/*` 接口

输出：

- `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/platform/*Impl.java`

最低实现范围：

- Identifier/ResourceLocation 包装
- TagParser 包装
- Registry 查询包装
- PayloadContext 回复包装
- MouseButtonEvent 包装
- Attachment 注册包装
- 版本特定 platform service 包装

验收：

- `v1_21_1` 对 `common` 的平台能力调用均经由实现类接入

#### 任务 3.5.3：保留平台特有代码

输入：

- 当前 `v1_21_1` 源码

输出：

- 仅保留平台特有类

必须保留在 `v1_21_1`：

- `gui/*`
- `item/ItemCsgoBox.java`
- `item/ItemCsgoKey.java`
- `item/ModItems.java`
- `event/ClickEvent.java`
- `event/ModEvents.java`
- `capability/ModCapability.java`
- 渲染工具 `utils/GuiItemMove.java`
- 渲染工具 `utils/IconListTools.java`
- 渲染工具 `utils/RenderFontTool.java`
- 平台相关随机与动画辅助，如源码证明存在 MC API 依赖

验收：

- 平台层不再保留已经迁入 `common` 的主实现副本

#### 任务 3.5.4：修正资源合并与产物命名

输入：

- `v1_21_1/build.gradle`
- `common/src/main/resources`
- `v1_21_1/src/main/resources`

输出：

- 正确合并资源的 `processResources`
- 目标产物名 `csgobox-1.21.1-1.0.5.jar`

验收：

```bash
./gradlew :v1_21_1:processResources
./gradlew :v1_21_1:jar
jar tf v1_21_1/build/libs/csgobox-1.21.1-1.0.5.jar | head -n 200
```

通过条件：

- JAR 内包含 `assets/csgobox/**`
- JAR 内包含 `data/csgobox/recipe/**`
- `META-INF/neoforge.mods.toml` 变量已展开

### 3.6 阶段验收

必须同时满足：

- `./gradlew :v1_21_1:compileJava`
- `./gradlew :v1_21_1:jar`
- 1.21.1 平台可以作为唯一启用模块独立构建

## 阶段 4：创建 v26_1_2 模块骨架

### 4.1 目标

以 `common` + `v1_21_1` 为基线，建立 26.1.2 平台模块的最小骨架，而不是复制整个旧代码树。

### 4.2 输入

- 已稳定的 `common`
- 已收敛的 `v1_21_1`
- `docs/port-26.1.2.md`

### 4.3 输出

- `v26_1_2/` 目录骨架
- 可被 Gradle 识别的 26.1.2 平台模块

### 4.4 依赖

- 阶段 3 完成

### 4.5 执行任务

#### 任务 4.5.1：创建目录与基础资源

输出目录：

- `v26_1_2/build.gradle`
- `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/`
- `v26_1_2/src/main/resources/META-INF/neoforge.mods.toml`
- `v26_1_2/src/main/resources/pack.mcmeta`

验收：

```bash
find v26_1_2 -maxdepth 4 -type f | sort
./gradlew :v26_1_2:tasks
```

#### 任务 4.5.2：复制平台层结构，不复制共享逻辑

复制范围：

- 平台入口
- `platform/*Impl`
- `gui/*`
- `item/*`
- `event/*`
- `capability/ModCapability.java`
- 平台渲染工具

禁止复制：

- 已经落入 `common` 的业务核心

验收：

- `v26_1_2` 代码树不再出现共享业务的第二份主副本

### 4.6 阶段验收

- `./gradlew :v26_1_2:tasks`
- `./gradlew projects`

## 阶段 5：吸收 26.1.2 API 差异

### 5.1 目标

完成 MC 26.1.2 / NeoForge 26.1.2.76 的平台适配，原则是“差异留在平台模块，契约稳定留在 common”。

### 5.2 输入

- `docs/port-26.1.2.md`
- `v26_1_2` 平台骨架
- `common` 契约接口

### 5.3 输出

- 可编译的 `v26_1_2`
- 经过验证的 26.1.2 平台接口实现

### 5.4 依赖

- 阶段 4 完成

### 5.5 执行任务

#### 任务 5.5.1：逐项应用 API 差异

必须覆盖：

1. `Screen` 构造器变化
2. `TagParser.parseTag` 到 `parseCompoundFully`
3. `appendHoverText` 新签名
4. `AttachmentType` 序列化机制变化
5. `PoseStack` 到 `Matrix3x2fStack`
6. `GuiGraphics.renderItem` 的 `seed`
7. 网络回复方式差异
8. 鼠标事件按钮访问差异
9. 注册表查询返回值差异
10. `Entity.level()` / `ServerLevel` 语义差异
11. `Container.items` 私有化影响复查

验收：

- 每一项差异都有对应源码落点
- 每一项差异都有“留在平台层 or 上提 common 接口”的决定记录

#### 任务 5.5.2：完成 GUI 适配

重点文件：

- `CsboxScreen.java`
- `CsboxProgressScreen.java`
- `CsLookItemScreen.java`
- `RenderFontTool.java`
- `GuiItemMove.java`
- `IconListTools.java`

要求：

- 明确哪些 3D 效果在 2D 矩阵下无法等价保留
- 记录视觉降级项，而不是无声替换

验收：

- `./gradlew :v26_1_2:compileJava`
- GUI 相关类全部完成编译

### 5.6 阶段验收

必须同时满足：

- `./gradlew :v26_1_2:compileJava`
- 关键平台实现类已落地

## 3. 严格规范要求

## 3.1 编码规范

### 3.1.1 包命名

- `common` 使用 `com.reclizer.csgobox.*`
- `v1_21_1` 使用 `com.reclizer.csgobox.v1_21_1.*`
- `v26_1_2` 使用 `com.reclizer.csgobox.v26_1_2.*`

检查方式：

```bash
rg -n "^package " common/src/main/java v1_21_1/src/main/java v26_1_2/src/main/java
```

### 3.1.2 类职责

- 一个类只能属于以下之一：
  - 共享业务
  - 平台桥接
  - 平台实现
  - 客户端 GUI/渲染
- 禁止在同一类中混合“共享业务 + 平台入口注册 + 渲染细节”

检查方式：

- 代码审查清单
- 类头部 import 审查

### 3.1.3 注释与临时代码

- 不允许保留无截止条件的 `TODO`
- 临时兼容注释必须写明针对的版本与删除条件

检查方式：

```bash
rg -n "TODO|FIXME|TEMP|HACK" common v1_21_1 v26_1_2
```

## 3.2 目录命名约束

- 模块目录必须与 `settings.gradle` 的 include 名一致
- 平台目录使用下划线版本名：`v1_21_1`、`v26_1_2`
- 不允许混用 `v1211`、`1_21_1` 等别名目录

检查方式：

- `settings.gradle` 人工审查
- `find . -maxdepth 2 -type d | sort`

## 3.3 跨版本接口契约设计原则

### 3.3.1 契约只表达稳定语义

接口只抽象以下内容：

- 标识符解析
- NBT/Tag 解析
- 注册表查询
- 网络回复上下文
- 鼠标按钮事件
- 附件注册
- 平台提供的最小运行能力

禁止抽象：

- 具体 Screen 生命周期
- 具体渲染矩阵类型
- 大而全的“MinecraftFacade”

检查方式：

- 审查 `common/src/main/java/com/reclizer/csgobox/platform/*`
- 若一个接口方法超过 7 个且调用点集中于单一类，视为设计失败信号

### 3.3.2 契约优先窄接口

- 接口先为当前明确共享需求服务
- 不为未来可能差异提前抽象

检查方式：

- 比对每个接口的调用点数量

## 3.4 依赖方向禁止规则

必须满足：

- `common` 不依赖 `v1_21_1`
- `common` 不依赖 `v26_1_2`
- `common` 不直接依赖 `net.minecraft.*`
- `common` 不直接依赖 `net.neoforged.*`
- 平台模块可以依赖 `common`
- 平台模块之间绝对禁止互相依赖

检查方式：

```bash
rg -n "import com\\.reclizer\\.csgobox\\.v1_21_1|import com\\.reclizer\\.csgobox\\.v26_1_2" common/src/main/java
rg -n "import net\\.(minecraft|neoforged)" common/src/main/java
```

## 3.5 资源分层策略

### 3.5.1 common 资源

放入 `common/src/main/resources/`：

- `assets/csgobox/**`
- `data/csgobox/**`
- 通用语言文件
- 通用模型、音效、贴图、配方、进度 JSON

### 3.5.2 平台资源

放入平台模块：

- `META-INF/neoforge.mods.toml`
- `pack.mcmeta`
- 平台专属运行元数据
- 若未来存在平台特有 access transformer 或额外配置，也只放平台模块

检查方式：

```bash
find common/src/main/resources -maxdepth 4 -type f | sort
find v1_21_1/src/main/resources -maxdepth 4 -type f | sort
find v26_1_2/src/main/resources -maxdepth 4 -type f | sort
```

## 3.6 Mixin 分层策略

当前状态：

- 仓库当前未发现 Mixin 配置

规范：

- 默认禁止为本次重构新增 Mixin
- 若未来必须新增：
  - Mixin 配置文件必须放平台模块
  - 不得放入 `common`
  - 每个平台模块维护自己的 mixin 配置与目标类版本
  - `common` 只能感知被 Mixin 暴露后的稳定契约，不得感知 Mixin 实现细节

检查方式：

```bash
rg -n "mixin|Mixin|mixins" -S common v1_21_1 v26_1_2
```

## 4. 环境问题处理与兼容性方案

## 4.1 版本对齐表

| 维度 | common | v1_21_1 | v26_1_2 |
|---|---|---|---|
| Java toolchain | 21 | 21 | 默认 21，必要时升 25 |
| Gradle wrapper | 8.14 | 8.14 | 8.14 |
| NeoGradle | 无 | 7.0.171 | 7.1.38 |
| NeoForge | 无 | 21.1.115 | 26.1.2.76 |
| Minecraft | 无 | 1.21.1 | 26.1.2 |
| 资源 pack_format | 无 | 34 | 80 待实测确认 |

说明：

- `common` 始终不声明 Minecraft/NeoForge 依赖
- `v26_1_2` 是否需要 Java 25，必须以实际编译和运行验证为准

## 4.2 JDK 版本策略

### 4.2.1 默认策略

- 根项目 toolchain 维持 Java 21
- `common` 与 `v1_21_1` 固定 Java 21
- `v26_1_2` 首先尝试 Java 21

### 4.2.2 升级触发条件

若出现以下任一情况，允许仅对 `v26_1_2` 提升 toolchain：

- NeoGradle 7.1.38 明确要求更高 JDK
- NeoForm / 编译任务要求 preview 或 JDK 25 特性
- 26.1.2 依赖树在 Java 21 下不可编译或不可运行

### 4.2.3 子模块单独 toolchain 方案

推荐模板：

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

若 `v26_1_2` 必须 25：

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

检查方式：

```bash
./gradlew -q javaToolchains
```

回退策略：

- 若 Java 25 破坏 IDE 或 CI，一律回退到“仅保留 1.21.1 可构建”的中间态，不继续推进 26.1.2 代码迁移

## 4.3 Gradle / NeoGradle 兼容方案

### 4.3.1 根项目模板定位

文件：

- `build.gradle`
- `settings.gradle`
- `gradle.properties`

根项目职责：

- 统一 `group` / `version`
- 统一仓库
- 统一 Java 编码与 toolchain 默认值
- include 子模块

禁止在根项目中配置：

- NeoForge runs
- 平台专属 dependencies
- `processResources` 的 mod metadata 展开

### 4.3.2 common 模板定位

文件：

- `common/build.gradle`

职责：

- `java-library`
- `withSourcesJar()`
- 仅编译共享 Java 代码

模板约束：

```groovy
plugins {
    id 'java-library'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}
```

### 4.3.3 平台模块模板定位

文件：

- `v1_21_1/build.gradle`
- `v26_1_2/build.gradle`

职责：

- 应用 NeoGradle
- 声明 NeoForge 依赖
- 配置 runs
- 合并平台资源与 `common` 资源
- 生成版本化 JAR

最小模板要素：

```groovy
plugins {
    id 'java-library'
    id 'net.neoforged.gradle.userdev' version "${neogradle_version_xxx}"
}

dependencies {
    implementation project(':common')
    implementation "net.neoforged:neoforge:${neo_version_xxx}"
}
```

### 4.3.4 仓库兼容

必须包含：

- `mavenCentral()`
- `https://maven.neoforged.net/releases`

可选：

- `https://maven.blamejared.com`
- `https://modmaven.dev`
- `https://www.cursemaven.com`
- `https://maven.latvian.dev/releases`

检查方式：

```bash
./gradlew :v1_21_1:dependencies
./gradlew :v26_1_2:dependencies
```

回退策略：

- 若第三方仓库导致解析冲突，先移除非必要仓库
- 保留最小集合后重试

## 4.4 NeoForge 21.x 与 26.x 差异处理原则

### 4.4.1 入口与注册机制

原则：

- 平台入口类留在各自模块
- `@Mod`、事件总线注册、payload 注册、Attachment 注册均在平台层处理
- `common` 不直接声明任何 FML/NeoForge 生命周期监听器

检查方式：

```bash
rg -n "@Mod|EventBusSubscriber|RegisterPayloadHandlersEvent|AttachmentType" common/src/main/java
```

通过条件：

- 上述平台入口机制不出现在 `common`

### 4.4.2 API 破坏变更吸收层

处理顺序：

1. 先判断差异是否只是平台调用点变化
2. 若是，留在平台层修复
3. 只有当两边都需要共享同一语义时，才上提到 `common` 接口

禁止做法：

- 为了统一所有版本而在 `common` 引入复杂图形或注册抽象

## 4.5 依赖冲突处理

常见来源：

- lwjgl patch 工件解析
- 两个平台模块使用不同 NeoGradle / NeoForge 版本
- 非官方仓库中的同名工件

处理流程：

1. 用 `dependencies` 和 `dependencyInsight` 确认来源
2. 优先删减仓库，不优先写复杂 exclude
3. 若必须写约束，放平台模块，不放根项目

推荐命令：

```bash
./gradlew :v1_21_1:dependencyInsight --dependency lwjgl-freetype
./gradlew :v26_1_2:dependencyInsight --dependency neoforge
```

## 4.6 IntelliJ / DevEnv 多模块配置

### 4.6.1 导入策略

- 通过根 Gradle 项目导入
- 以子模块为单位同步
- 不直接把 `common` 或平台模块单独当根项目打开

### 4.6.2 运行配置策略

- `v1_21_1` 与 `v26_1_2` 各自使用 Gradle run tasks
- IntelliJ 中不手工复制 JVM 参数，优先调用 Gradle tasks

检查方式：

- `Gradle` 面板中可见各平台的 `runClient` / `runServer` / `data` 等任务

### 4.6.3 常见问题处理

- 若 IDE 因动态 include 漏识别模块：
  - 暂时改为只 include 已存在模块
  - 重新导入 Gradle
- 若 toolchain 不识别：
  - 检查 `gradle.properties`
  - 检查 IntelliJ SDK 与 Gradle JVM

## 4.7 构建脚本模板片段定位

### 根 `build.gradle`

应包含：

- `plugins { id 'base' }`
- `allprojects { repositories { ... } }`
- `subprojects { java.toolchain ... }`

不应包含：

- `runs { ... }`
- `dependencies { implementation "net.neoforged:neoforge:..." }`

### `settings.gradle`

应包含：

- `pluginManagement.repositories`
- 根项目名
- 子模块 include 策略

### `gradle.properties`

应包含：

- 跨版本共享 mod 元数据
- `mc_version_1_21_1`
- `neo_version_1_21_1`
- `neogradle_version_1_21_1`
- `mc_version_26_1_2`
- `neo_version_26_1_2`
- `neogradle_version_26_1_2`
- `active_versions`
- `java_toolchain_version`

### 平台 `build.gradle`

应包含：

- `archivesName = "${mod_id}-${mc_version_xxx}"`
- `implementation project(':common')`
- 平台 `processResources`
- 平台 `jar` manifest

## 4.8 回退与降级策略

### 4.8.1 构建系统失败时

回退到最近一个满足以下条件的状态：

- `common` 可编译
- `v1_21_1` 可编译
- 根项目可导入

### 4.8.2 common 迁移失控时

降级策略：

- 停止迁移第二批候选类
- 只保留已验证稳定的共享类
- 将 `command`、`packet`、`advancement` 延后到平台层

### 4.8.3 26.1.2 风险过高时

降级策略：

- 保留 `v26_1_2` 骨架与构建文件
- 暂不承诺 GUI 动画完全一致
- 先实现“可编译、可加载、核心功能可用”

### 4.8.4 IDE 体验恶化时

降级策略：

- 简化 `settings.gradle`
- 暂停动态 include
- 保留单平台开发路径，待 26.1.2 稳定后再恢复多平台并行

## 5. 统一检查清单

## 5.1 编译检查

```bash
./gradlew :common:compileJava
./gradlew :v1_21_1:compileJava
./gradlew :v1_21_1:jar
./gradlew :v26_1_2:compileJava
./gradlew :v26_1_2:jar
./gradlew build
```

## 5.2 依赖方向检查

```bash
rg -n "import net\\.(minecraft|neoforged)" common/src/main/java
rg -n "import com\\.reclizer\\.csgobox\\.v1_21_1|import com\\.reclizer\\.csgobox\\.v26_1_2" common/src/main/java
```

## 5.3 资源检查

```bash
find common/src/main/resources -type f | sort
find v1_21_1/src/main/resources -type f | sort
find v26_1_2/src/main/resources -type f | sort
```

## 5.4 审查清单

- `common` 是否仍保持无 MC/NeoForge 直接依赖
- 平台模块是否没有互相依赖
- `CONFIG` 初始化顺序是否未被破坏
- `csgobox.toml` 路径是否保持不变
- `data/csgobox/recipe/` 是否保持单数目录
- 1.21.1 和 26.1.2 产物命名是否正确
- JAR 中是否合并了 `common` 资源
- 是否误引入了 Mixin
- 是否出现重复业务主副本

## 6. 交付定义

满足以下条件，视为本次重构完成：

1. `common` 成为真实共享业务模块，而非空壳
2. `v1_21_1` 可独立打包并保持 1.0.5 功能基线
3. `v26_1_2` 可编译并具备核心功能运行基础
4. 根 Multi-Project 可稳定导入和构建
5. 版本、资源、配置与产物命名规则全部收口
