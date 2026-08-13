---
type: index
title: 内容目录
updated: 2026-08-13
---

# CS2-Box 知识库 — 内容目录

> 任何操作前先读本页。查询时通过 `[[wikilinks]]` 跳转。
> ⚠️ 已归档（废弃）页面见 `archived/` 目录（2026-08-13：军火商经济相关 3 页），不参与索引与 lint。

## Schema
- [[CLAUDE]] — 本知识库的 schema 与规则

## Sources（资料摘要 — 40 个）
按用途分四组：guides（上手/流程）、updates（版本变更）、testing（测试体系）、features（功能设计/GDD）。目录结构见 `sources/{guides,updates,testing,features}/`。

### 上手与流程（guides — 15）
- [[readme]] — 项目概览（功能、平台矩阵、构建）
- [[changelog]] — 版本历史（1.0.2–1.0.8，含踩坑教训）
- [[mcmod-intro]] — MC百科 模组介绍页
- [[architecture]] — 模块拓扑、数据流、渲染管线对比
- [[development]] — 开发配置、构建命令、代码规范
- [[configuration]] — TOML + JSON 配置参考
- [[release]] — 发布流程与质量门
- [[kubejs-events]] — 事件系统与 KubeJS 兼容
- [[getting-started]] — 快速入门
- [[contributing]] — 贡献指南
- [[tutorial-doc]] — 教程文档（中文，JSON 配置参考）
- [[tutorial-doc-en]] — 教程文档（英文版）
- [[platform-apis]] — 多平台 API 差异速查（legacy/decoupled 全量矩阵 + AnimOps 门面）
- [[code-review]] — 代码审查标准与流程（专属审查清单 + 评论格式）
- [[ci-protection]] — CI 分支保护（Required Status Checks）设置指南

### 版本与移植（updates — 3）
- [[update-1-0-4]] — 1.0.4 更新说明（服务端授权 RNG、动画修复）
- [[update-1-0-5]] — 1.0.5 更新说明（Cloth Config 移除、ModConfigSpec）
- [[port-26-1-2]] — 26.1.2 移植说明（11 项 API 变更）

### 测试体系（testing — 10）
- [[testing]] — GameTest 测试指南
- [[runtime-ui-testing]] — 运行时 GUI 自动化测试工作流
- [[test-helper-mod-spec]] — 测试辅助模组需求规格
- [[manual-testing-v1-0-5]] — 1.0.5 手动测试用例（33 TC）
- [[superpowers-specs]] — UI 自动化测试设计文档（Visual Timeline + Tagged Screenshots）
- [[visual-timeline-plan]] — Visual Timeline 实现计划
- [[fullcheck-suite]] — 上线前全量检查测试套件（10 平台）
- [[animation-aesthetics-test]] — 1.21.1 抽卡动画审美测试脚本
- [[animation-aesthetics-test-plan]] — 动画审美测试脚本 实施计划
- [[clean-test-shots]] — 清理测试照片功能（clean 子命令）

### 功能设计与 GDD（features — 13）
- [[wear-durability]] — 开箱物品按磨损值损耗耐久（服务端权威 RNG）
- [[creative-no-key]] — 创造模式免钥匙开箱
- [[toolbar-hover]] — CsLookItemScreen 工具栏悬停反馈（反白 + tooltip）
- [[lookitem-info-panel]] — CsLookItemScreen 信息面板重排（复刻 CS:GO）
- [[tacz-inspect-viewport]] — TACZ 检视视口集成（v1_21_1 专属）
- [[animops-facade]] — AnimRenderOps 动画渲染门面设计
- [[blur-mod-adaptation]] — CS2-Box × Blur 背景模糊适配
- [[csbox-nbt-hand]] — /csbox nbt hand 实现计划
- [[gui-animation-gaps]] — GUI 动效补全 + Easing 提取计划
- [[creative-no-key-plan]] — 创造模式免钥匙开箱 实施计划
- [[lookitem-info-panel-plan]] — 信息面板重排 实施计划
- [[animops-facade-plan]] — 动画渲染门面 实施计划
- [[arms-dealer-hut]] — 武库商小屋（世界生成结构，军火商野外据点）

## Concepts（架构概念 — 15 个）
- [[multiloader-architecture]] — 9 平台矩阵 + common 约束
- [[platform-mirror-discipline]] — 镜像纪律 + 定点合入
- [[server-authoritative-rng]] — 服务端权威 RNG + 防双击
- [[bulk-opening-pipeline]] — 批量开箱（异步 + 确认屏）
- [[dynamic-box-item-registration]] — RegisterEvent deferred supplier 动态注册
- [[tutorial-system]] — 教程下载/版本管理/删除策略
- [[achievement-system]] — 成就（原生 CriteriaTriggers + Stats.CUSTOM）
- [[rendering-pipeline]] — 旧 API vs decoupled 渲染管线对比
- [[version-sync]] — 版本号四处同步
- [[gui-region]] — 容器化布局（GuiRegion 命名区域）
- [[overlay-color]] — 设计 Token 体系（surface/panel/divider）
- [[entity-chinese-map-migration]] — EntityChineseMap B 类迁移
- [[code-review-standards]] — 代码审查标准（CONSTRAINT-001/镜像/版本同步/AnimOps 漂移等必查面）
- [[superpowers-testing]] — Superpowers UI 自动化测试体系（审美/全量/运行时）
- [[easing-library]] — Easing 公共缓动库（common 纯函数，CONSTRAINT-001 合规）

## Entities（代码组件 — 15 个）
- [[csgo-box]] — 平台入口类（CONFIG、动态注册、BULK_POOL）
- [[csbox-config]] — ModConfigSpec 配置（11+ 项，4 分组）
- [[packet-csgo-progress]] — 单开 RNG 数据包 + 冷却
- [[packet-csgo-bulk-progress]] — 批量开箱请求（异步预计算）
- [[box-defaults]] — 教程下载/版本管理（common 层）
- [[box-json-loader]] — JSON 加载器 + LoadError 收集
- [[icon-list-tools]] — 2D 物品网格（per-item 居中）
- [[hud-visibility]] — v26_2 HUD 隐藏工具类
- [[opened-box-trigger]] — 成就触发器（首次/200 箱）
- [[box-opened-event]] — NeoForge 事件（post-event，KubeJS 兼容）
- [[platform-26]] — 26.x 平台接口实现
- [[button-palette]] — 按钮色系 token（OPEN/DANGER/DISABLED）
- [[render-font-tool]] — 文字渲染工具（限宽省略号）
- [[anim-render-ops]] — 动画渲染唯一适配点（13 op 门面，三平台一致）
- [[gui-item-move]] — 3D 拖拽预览（renderRotAngleX/Y，渲染委托 AnimRenderOps）

## Comparisons（跨平台对比 — 2 个）
- [[1-21-1-vs-26-1-2-gui]] — 1.21.1 vs 26.1.2 GUI 渲染管线对比
- [[v26-1-2-vs-v26-2]] — v26_1_2 vs v26_2 破坏性变更对比

## Output（查询产物）
_（空 — 见 `../output/`）_
