---
type: overview
title: 知识库总览
updated: 2026-08-03
---

# CS2-Box 知识库总览

CS2-Box 是 CS:GO 风格开箱玩法的 Minecraft NeoForge 模组，当前 **9 平台**共享 `mod_version=1.0.7`（工作基线含 1.0.8 WIP）。知识库覆盖项目架构、平台适配差异、核心机制、关键组件、发布流程、测试体系。

## 核心结构

- **9 平台矩阵**：7 个 1.21.x（Java 21 旧 API）+ 2 个 26.x（Java 25 `--enable-preview`，decoupled API）
- **common/ 约束**：无 MC/NeoForge 依赖，平台 → common 单向依赖
- **镜像纪律**：非纯拷贝，先改基准模块再定点合入

## 已收录（51 个页面）

| 类别 | 数量 | 覆盖内容 |
|---|---|---|
| sources | 22 | 所有核心文档 + 版本更新 + 移植 + 测试 + 教程 + 工具设计 |
| concepts | 12 | 架构 + 机制 + 渲染 + 设计系统 + 迁移 |
| entities | 13 | 入口 + 配置 + 数据包 + 加载器 + 工具类 + 按钮 + 字体 |
| comparisons | 2 | 1.21.1 vs 26.1.2 GUI、v26_1_2 vs v26_2 差异 |
| schema/nav | 4 | CLAUDE.md + index.md + overview.md + log.md |

## 待办

- [ ] 把 `raw/` 放上资料（剪报、截图、笔记）
- [ ] 深入研究后创建更多概念页
- [ ] 运行 lint 检查矛盾/孤儿页面（已列在下方）