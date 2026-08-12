---
type: overview
title: 知识库总览
updated: 2026-08-10
---

# CS2-Box 知识库总览

CS2-Box 是 CS:GO 风格开箱玩法的 Minecraft NeoForge 模组，当前 **3 个在维护平台**（`v1_21_1` legacy、`v26_1_2`、`v26_2` decoupled）+ `forge_26_1_2` 实验模块（mod_version 工作基线 1.0.8 WIP）。legacy 早期 6 个断点平台（1.21.0/1.21.3~1.21.11）已于 2026-08-09 归档为 EOL（tag `eol-legacy-21x-1.0.6`）。知识库覆盖项目架构、平台适配差异、核心机制、关键组件、发布流程、测试体系、Superpowers UI 自动化测试。

## 核心结构

- **3 平台矩阵（在维护）**：`v1_21_1`（1.21.1 Java 21 旧 API）+ `v26_1_2` / `v26_2`（26.x Java 25 `--enable-preview`，decoupled API）；另有 `forge_26_1_2` 实验模块（源码未提交、不在 CI、不参与镜像纪律）
- **common/ 约束**：无 MC/NeoForge 依赖，平台 → common 单向依赖
- **镜像纪律**：非纯拷贝，先改基准模块再定点合入

## 已收录（79 个页面）

| 类别 | 数量 | 覆盖内容 |
|---|---|---|
| sources | 42 | 分四子目录：guides（上手/流程 15）+ updates（版本移植 3）+ testing（测试体系 10）+ features（功能设计/GDD 14） |
| concepts | 16 | 架构 + 机制 + 渲染 + 设计系统 + 迁移 + 代码审查 + 测试体系 + 经济 + Easing |
| entities | 15 | 入口 + 配置 + 数据包 + 加载器 + 工具类 + 按钮 + 字体 + 3D 预览 + 动画渲染门面 |
| comparisons | 2 | 1.21.1 vs 26.1.2 GUI、v26_1_2 vs v26_2 差异 |
| schema/nav | 4 | CLAUDE.md + index.md + overview.md + log.md |

## 待办

- [x] 运行 lint 检查矛盾/孤儿页面（2026-08-03 通过，0 孤儿）
- [x] 摄取 Superpowers 设计/计划 + 平台/流程参考文档（2026-08-10，+26 页）
- [x] 重构 wiki 文件树：sources 拆为 guides/updates/testing/features 四子目录；修复 arms-dealer-economy slug 冲突（源文档摘要→arms-dealer-economy-design，概念版保留）；刷新 index/overview 计数（2026-08-10）
- [ ] 重新生成静态站点并校验（运行 `wiki/wiki-site/scripts/build-wiki-data.mjs` + `wiki/output/scripts/wiki_lint.py`）
- [ ] 把新内容同步至云端知识库（资料库 skill）
