---
type: schema
title: CS2-Box 知识库 Schema
updated: 2026-08-13
---

# CS2-Box 知识库 Schema

## 用途与范围

本项目专属知识库，记录 CS2-Box（Minecraft NeoForge 模组，CS:GO 开箱玩法）的开发知识：

- **架构约束**：common/ 无 MC 依赖、平台 → common 依赖方向、9 平台模块镜像纪律
- **平台矩阵**：v1_21_1 / v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10 / v1_21_11（NeoForge 21.x 旧 API）+ v26_1_2 / v26_2（NeoForge 26.x decoupled API）
- **跨平台适配差异**：各平台的 API 差异（BuiltInRegistries Optional、spawnAtLocation、lookup()、MouseButtonEvent、setScreenAndShow、PIP 渲染器、HudVisibility 等）
- **核心机制**：服务端权威 RNG、批量开箱、动态 box item、成就/事件系统、配方
- **构建与发布**：Gradle 单版本构建约束、版本号四处同步、发布流程

通用模组开发知识不在此库范围内。

## 目录结构

```
wiki/
├── raw/                  # 第 1 层：资料（不可变，LLM 只读）
│   ├── docs/             # 项目文档、设计笔记
│   └── assets/           # 图片等附件
├── wiki/                 # 第 2 层：LLM 全权维护
│   ├── CLAUDE.md         # 本 schema
│   ├── index.md          # 内容目录（LLM 操作的入口）
│   ├── log.md            # 操作日志（append-only）
│   ├── overview.md       # 知识库总览
│   ├── sources/          # 资料摘要，按用途分四子目录：
│   ├── guides/       #   上手与流程（readme/changelog/architecture/development/configuration/release/...）
│   ├── updates/      #   版本与移植说明（update-*/port-*）
│   ├── testing/      #   测试体系与规格（testing/manual-testing/superpowers-specs/...）
│   └── features/     #   功能设计与 GDD（wear-durability/blur-mod-adaptation/...）
│   ├── concepts/         # 架构概念文章
│   ├── entities/         # 代码组件/文件条目
│   ├── comparisons/      # 跨平台对比分析
│   └── archived/         # 已归档（废弃）页面：不参与索引与 lint
└── output/               # 第 3 层：查询产物
    ├── reports/
    ├── slides/           # Marp
    └── charts/           # matplotlib
```

## 页面类型与 frontmatter

### source（sources/）
```yaml
---
type: source
title: ...
source_path: raw/docs/...
date_ingested: YYYY-MM-DD
tags: [...]
key_concepts: [...]
key_entities: [...]
---
```
正文：Summary → Key takeaways → Connections（`[[wikilinks]]`）

### concept（concepts/）
```yaml
---
type: concept
title: ...
updated: YYYY-MM-DD
tags: [...]
---
```
正文：Overview → Details → Platform notes（如有差异）→ Sources → Related

### entity（entities/）
```yaml
---
type: entity
title: ...
kind: class|file|component|config
platform: v1_21_1|all|...
updated: YYYY-MM-DD
---
```
正文：Overview → Responsibilities → Cross-platform differences → Sources → Related

### comparison（comparisons/）
```yaml
---
type: comparison
title: ...
updated: YYYY-MM-DD
---
```
正文：Dimensions → 对比表 → 结论 → Sources

## 命名规则

- 文件名：lowercase-with-hyphens（`packet-csgo-progress.md`）
- source 页面与 raw 文件名对应
- 全部交叉引用用 `[[wikilinks]]`

## 摄取工作流

1. 新资料（文档变更、适配经验、CHANGELOG 等）→ 放入 `wiki/raw/`（用户操作）
2. LLM 读取，讨论要点（3-5 条）→ 写入 sources/
3. 每个显著概念 → 创建或更新 concepts/
4. 每个显著组件 → 创建或更新 entities/
5. 平台差异显著时 → comparisons/
6. 更新 index.md / overview.md，追加 log.md

## 归档规则

- 废弃方向/功能 → 相关页面移入 `archived/`，从 index.md / overview.md 移除并刷新计数；`archived/` 不参与 lint 与索引
- 计划文档归档至 `docs/superpowers/archived/`，文件顶部加 ARCHIVED 横幅（保留原路径）

## 平台矩阵速查

| 版本 | 备注 |
|---|---|
| v1_21_1 | legacy 基准模块 |
| v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10 / v1_21_11 | 旧 API，各自有适配 |
| v26_1_2 | new 基准模块（decoupled API） |
| v26_2 | 26.x，`HudVisibility` 等特有适配 |

## 构建命令速查

```bash
./gradlew :<module>:compileJava -Pactive_versions=<v>
./gradlew :common:test    # JUnit 5 单测
```

约束：每次 Gradle 调用只能构建一个 MC 版本；涉及平台改动需 clean 编译验证。

## 输出偏好

- 查询产物用 markdown 报告 → `output/reports/`
- 演示 → Marp slides；图表 → matplotlib PNG + 源脚本
- 有价值产物必须流回 wiki（flowback）

## 领域规则

- 平台模块**不是纯拷贝**，禁止整文件覆盖其他模块（历史教训：v1_21_10 编译失败）
- 跨平台改动：先改基准模块（v1_21_1 / v26_1_2），再 mirror 或定点合入
- 版本号升级需四处同步：gradle.properties + neoforge.mods.toml + CHANGELOG.md + README.md
