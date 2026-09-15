# 设计文档：docs/PLATFORM-APIS.md 多平台 API 差异开发指南

日期：2026-08-07

## 背景

仓库 10 个平台模块（7 个 legacy `v1_21_x` + 2 个 new `v26_x` + 1 个实验 `forge_26_1_2`）之间 API 差异大。现有 `docs/port-26.1.2.md` 只覆盖 1.21.1→26.1.2 单次移植，`docs/ARCHITECTURE.md` 只讲架构。缺一份**面向开发者的全量 API 差异参考文档**。

调研已完成（explore agent 双路对比：legacy 内部断点 + 26.x family），数据源为各平台 `src/main/java` 逐文件 diff。

## 目标

- 开发者开发/升级某个平台时，按主题快速查到「哪个版本、哪个 API、怎么写」
- 覆盖全部 10 平台
- flywheel：矩阵总览 + 主题展开

## 交付物

1. `docs/PLATFORM-APIS.md`（中文，~400-600 行）
2. `README.md` 文档导航区加一行入口

## 文档结构

### 第一部分：全量速查矩阵（方案 A）

- 1.1 版本分类速查表：10 平台分组（legacy 7 / new 2 / forge 实验 1），基准模块、Java、pack_format
- 1.2 API 断点 × 版本矩阵：行 = API 主题，列 = 各关键版本，行列值 = baseline / 变化点
- 1.3 文件级差异指引：改动最重的文件 × 受影响的断点版本

### 第二部分：主题展开（方案 B，每章固定结构）

> 每章 = `版本矩阵表` → `代码对照摘要`（旧 vs 新）→ `文件指引`

2. `ResourceLocation → Identifier`（26.x 全库改名）
3. 注册表 API（Optional Holder、registerItem、setId、ITEM_MODEL）
4. Screen / 输入事件（GuiGraphicsExtractor、extract 生命周期、事件对象化、setScreenAndShow、HudVisibility）
5. 渲染管线（RenderPipelines、tint、renderItem、nextStratum、PIP 3D）
6. 网络层（context.reply / ServerboundCustomPayloadPacket）
7. Attachment / 序列化（MapCodec、IAttachmentSerializer、spawnAtLocation、lookup）
8. 背包遍历 / 输入辅助（getNonEquipmentItems、hasShiftDown 删除）
9. 命令 / 权限 / 成就（permissions()、criterion 包迁移）
10. 26.2 专属适配 + forge_26_1_2 实验模块

## 内容约束

- 代码对照基于调研结果，标注版本、文件路径
- 基准：legacy 用 v1_21_1，new 用 v26_1_2
- 不引入无关重构主题（评审时剔除）