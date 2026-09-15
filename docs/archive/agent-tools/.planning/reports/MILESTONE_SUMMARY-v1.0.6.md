# Milestone Summary — v1.0.6

> 用法:让新成员通过阅读本文档,5 分钟内理解本里程碑做了什么、为什么、怎么用。

## 1. 一句话总结

**v1.0.6 是 CS2-Box 的「多平台扩展 + 教程系统」里程碑**。MC 26.2 beta 版首次进入支持矩阵(`csgobox-26.2-1.0.6.jar`);玩家读到的箱子配置教程不再随 JAR 发布,而是从 Gitee 公开仓库按 mod 版本号下载;JSON 写错时,玩家登录即看到红色行/列错误。

## 2. 架构概览

### 模块结构

```
CS2-Box/
├── common/                     # 跨平台业务代码(MC/NeoForge 无关的纯 A 类)
│   └── utils/
│       ├── ColorTools.java
│       └── OverlayColor.java
├── v1_21_1/                    # Minecraft 1.21.1 + NeoForge 21.1.115
│   └── src/main/java/com/reclizer/csgobox/v1_21_1/
│       ├── box/{BoxDefinition,BoxJsonLoader,BoxRegistry,GradeGroup,
│       │          BoxItemCodec,LoadError,TutorialSources,TutorialFetcher,
│       │          BoxDefaults}.java
│       ├── command/CsboxCommand.java
│       ├── event/{LoadErrorAnnouncer,...}.java
│       └── CsgoBox.java
├── v26_1_2/                    # Minecraft 26.1.2 + NeoForge 26.1.2.76
├── v26_2/                      # Minecraft 26.2 + NeoForge 26.2.0.7-beta  ← 新增
├── docs/tutorials/             # 教程 markdown,推到 Gitee 公开仓库
└── .planning/                  # 规划制品(ROADMAP / STATE / REQUIREMENTS 等)
```

### 关键数据流

```
玩家启动游戏
    ↓
[CsgoBox] 构造器
    ├── 读 mod_version = "1.0.6"(从 ModContainer)
    ├── 注册配置 / 物品 / 事件总线
    └── onServerStarting → BoxJsonLoader.loadAll()
                                ↓
                          创建 config/csbox/
                                ↓
                          BoxDefaults.writeTutorialIfMissing()
                                ├── needsRefresh():扫 .md 文件 → 找 _tutorial_v1.0.6*.md
                                │   └─ 找不到 → 触发 moveStaleTutorials()
                                │       ├── Desktop.moveToTrash()  (Windows/macOS/Linux 桌面)
                                │       └─ 降级 .trash/ 文件夹      (headless 服务器)
                                ├── TutorialSources.loadOrDefault() → 内置 Gitee 源
                                ├── TutorialFetcher.fetch() → HTTP 5s+8s 超时,Redirect.ALWAYS
                                └── 写入 _tutorial_v1.0.6.md / _tutorial_v1.0.6_zh_cn.md
                                ↓
                          加载 *.json 箱子配置
                                ├── 成功 → BoxRegistry.register()
                                └── 失败 → LoadError 收集到 LAST_LOAD_ERRORS
                                            ↓
                          LoadErrorAnnouncer → 玩家登录时根据 ErrorChatAudience 推送红字
```

## 3. 本里程碑做了什么(Phases)

### 阶段 1:Multiloader 基础(stage 1-3)
- 新增 `v26_2/` 平台模块,从 `v26_1_2/` 复制 + 包名重命名 + 资源迁移
- 抽 2 个纯 A 类(`ColorTools / OverlayColor`)到 `common/utils/`
- 新增 8 个 `*_26_2` Gradle 变量,pin 真实 NeoForge 版本号 `26.2.0.7-beta`

### 阶段 2:26.2 decoupled API 适配(stage 4)
- 38 → 0 compile error
- `PictureInPictureRenderer` / `Minecraft.setScreen` / `CriterionTrigger` 等 API 全部对齐新版签名
- `v26_2/gui/pip/Icon3DRenderer.java` 完全重写,3D 旋转保留

### 阶段 3:三模块 build 验证 + 运行时回归(stage 5-6)
- v1_21_1 / v26_1_2 / v26_2 三平台 compileJava + jar 全部通过
- 26.2 客户端开箱、PIP 3D 拖拽、成就、`/csbox reload` 全部验证

### 阶段 4:JSON 加载错误玩家可见纠错
- `LoadError.java` + `BoxJsonLoader` 错误收集(行/列解析自 Gson 异常消息正则)
- `LoadErrorAnnouncer` 玩家登录时推送红字
- `CsboxConfig.ErrorChatAudience` 枚举配置(`OP_ONLY` / `EVERYONE`)
- `/csbox errors` 命令 OP 重查

### 阶段 5:教程系统
- `BoxDefaults` 改造:网络下载 + 版本号文件名 + 跨平台回收站 + 离线安全
- `TutorialSources` / `TutorialFetcher` 新增
- `CsgoBox.MODVERSION` 字段 + 构造器初始化
- 教程 markdown 中英双语推到 Gitee 公开仓库 `gitee.com/hou-xiangling/CS2-Box/docs/tutorials/`
- 用户 SSH 密钥 `~/.ssh/id_ed25519_gitee` 维护教程仓库用

## 4. 关键设计决策

| 决策 | 原因 |
|---|---|
| 教程分发选 Gitee(用户私有仓库)而非 GitHub | 国内访问速度;GitHub raw 内容偶发挂;可解耦 mod 源码与文档 |
| 教程文件名带 mod 版本号(`_tutorial_v1.0.6.md`) | 保证玩家读到的文档与 mod 行为一致;升级时自动迁移旧版 |
| 回收站优先 OS 原生(`Desktop.moveToTrash()`) | 玩家可在熟悉 OS UI 恢复;Java 9+ 跨平台稳定 |
| B 类 6 文件不迁 `common/` | 它们依赖 `net.minecraft.*` / `net.neoforged.*`,违反 CONSTRAINT-001 |
| 教程源配置 JSON 不自动写入磁盘 | 99% 玩家不需要,自动写盘是冗余 clutter |

## 5. 需求覆盖度

| 需求 | 状态 |
|---|---|
| P0-1 多平台构建矩阵(1.21.1 / 26.1.2 / 26.2) | ✅ 已完成 |
| P0-2 26.2 decoupled API 适配 | ✅ 已完成 |
| P0-3 PIP 3D 旋转 26.2 保留 | ✅ 已完成 |
| P0-4 公共业务代码迁移(A 类) | ✅ 部分(ColorTools / OverlayColor) |
| P1-? 教程系统 | ✅ 新增 |
| P1-? JSON 错误玩家可见 | ✅ 新增 |
| 完整 B 类迁移(6 个文件) | ⏳ 显式延期 |
| HUD-overlay 隐 GUI 26.2 | ⏳ 26.2 缺等价 API,延期 |
| 容器化布局 / per-item 视觉基线 / 设计 token | ⏳ 显式延期 |

## 6. 技术债 / 已知限制

1. **B 类文件 3 份重复**:`BoxDefinition / BoxRegistry / GradeGroup / CsboxConfig / CsboxPlayerData / EntityChineseMap` 在 3 平台各一份,改动需同步 3 处。
2. **HUD-overlay 26.2 降级**:`Options.hideGui` 字段在 26.2 被移除,开箱时 hotbar/血条可见。
3. **NeoForge 26.2 仍 beta**:`26.2.0.7-beta`,生产环境慎用。stable 发布后需刷新 `neo_version_26_2`。
4. **Gitee raw 偶发 302**:依赖 ADAS 网关,Java HttpClient 必须显式 `followRedirects(ALWAYS)`。
5. **26.2 教程分发未自动化**:教程更新需要维护者手动 git push 到 Gitee。

## 7. 上手指南(新成员)

### 7.1 开发环境

```bash
# Java 21(JDK 21 toolchain 在 gradle.properties 固定)
java -version

# 切换 active 版本
sed -i.bak 's/^active_versions=.*/active_versions=26.2/' gradle.properties
./gradlew :v26_2:compileJava
mv gradle.properties.bak gradle.properties

# 或一次编译全部(切换版本每次)
for ver in 1.21.1 26.1.2 26.2; do
    sed -i.bak "s/^active_versions=.*/active_versions=$ver/" gradle.properties
    ./gradlew ":v${ver//./_}:compileJava"
    mv gradle.properties.bak gradle.properties
done
```

### 7.2 调试教程下载

```bash
# 启动客户端,日志关注:
# [INFO] Tutorial version mismatch (current mod is 1.0.6); moving stale tutorials
# [INFO] Moved N stale tutorial(s) to the system recycle bin (recoverable from there): [...]
# [INFO] Wrote box configuration reference: ...\_tutorial_v1.0.6.md
# 或离线时:
# [WARN] No tutorial available for _tutorial_v1.0.6.md (offline or all sources failed); skipping
```

### 7.3 改教程后推送

```bash
# 教程仓库(Gitee)
git worktree add /tmp/cs2-tutorials main  # 用 main 不用 multiloader-refactor
cd /tmp/cs2-tutorials
# 编辑 docs/tutorials/_tutorial_v<新版本>.md
git -c user.name='霜月星询' -c user.email='12214766+hou-xiangling@user.noreply.gitee.com' \
    commit -m "docs: update tutorial for v<新版本>"
git push gitee main
cd - && git worktree remove /tmp/cs2-tutorials
```

### 7.4 添加 JSON 错误推送字段

```java
// 1. 错误收集(BoxJsonLoader)
LAST_LOAD_ERRORS.add(new LoadError(file, boxId, reason, line, column, cause));

// 2. 玩家可见(LoadErrorAnnouncer)
sp.sendSystemMessage(err.toChatMessage());  // 红色 Component

// 3. 配置(CsboxConfig)
this.jsonErrorAudienceValue = builder.defineEnum("jsonErrorAudience", ErrorChatAudience.OP_ONLY);
```

## 8. 关键文件路径速查

| 文件 | 作用 |
|---|---|
| `gradle.properties` | `mod_version=1.0.6`、`*_26_2` 8 个变量 |
| `settings.gradle` | `versionModules = ['1.21.1': 'v1_21_1', '26.1.2': 'v26_1_2', '26.2': 'v26_2']` |
| `common/src/main/java/.../utils/` | A 类业务代码 |
| `v*/box/BoxJsonLoader.java` | JSON 加载 + LoadError 收集 |
| `v*/box/{TutorialSources,TutorialFetcher,BoxDefaults}.java` | 教程下载子系统 |
| `v*/box/LoadError.java` | 错误记录 + `toChatMessage()` |
| `v*/event/LoadErrorAnnouncer.java` | 玩家登录推送 |
| `v*/command/CsboxCommand.java` | `/csbox errors` 子命令 |
| `v*/config/CsboxConfig.java` | `ErrorChatAudience` 枚举 |
| `v*/CsgoBox.java` | `MODVERSION` 字段 + 构造器初始化 |
| `~/.ssh/id_ed25519_gitee` | 教程仓库 SSH key |
| `~/.ssh/config` | gitee.com host 别名 |
| `CHANGELOG.md` | 完整中文更新日志 |
| `.planning/STATE.md` | 项目当前阶段状态 |

## 9. 上次发布

- **v1.0.5** (2026-06-29):成就系统、Cloth Config 移除、csgo_key3 锻造路径
- **v1.0.6** (2026-07-02):**本里程碑**,26.2 beta 支持 + 教程系统 + JSON 错误纠错

## 10. 下次发布预期

- 26.2 stable 后重新 pin `neo_version_26_2`
- B 类 6 文件迁 common(若决定推进)
- HUD-overlay 26.2 替代方案
- 教程增量命令 `/csbox tutorial refresh`