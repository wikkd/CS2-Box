---
type: log
title: 操作日志
---

# 操作日志

## [2026-08-03] init | 初始化知识库
- 创建 vault 结构与 schema（CLAUDE.md、index.md、overview.md）
- 主题：CS2-Box 项目本身；位置：项目根 `wiki/` 显式目录

## [2026-08-03] ingest | 首批资料（8 源）
- 读取：8 个核心文档
- 创建 source 页（8）+ concept 页（9）+ entity 页（10）

## [2026-08-03] ingest | 第二批（6 源）
- 读取：update-1.0.4/5、port-26.1.2、TESTING、mcmod-intro、CONTRIBUTING
- 创建 source 页（6）+ entity 页（3）：platform-26、button-palette、render-font-tool

## [2026-08-03] ingest | 第三批（5 源）
- 读取：RUNTIME-UI-TESTING、TEST-HELPER-MOD-SPEC、MANUAL-TESTING、tutorial_zh、superpowers specs
- 创建 source 页（5）

## [2026-08-03] ingest | 第四批（2 源）
- 读取：tutorial_en、visual-timeline 计划
- 创建 source 页（2）+ comparison 页（2）：1.21.1 vs 26.1.2 GUI、v26_1_2 vs v26_2
- 创建 concept 页（3）：gui-region、overlay-color、entity-chinese-map-migration
- 更新：index.md、overview.md

## [2026-08-03] lint | 健康检查 + 对比页
- 创建 comparison 页（2）：[[1-21-1-vs-26-1-2-gui]]、[[v26-1-2-vs-v26-2]]
- 创建 lint 报告：`output/reports/lint-2026-08-03.md`
- 结果：0 孤儿页，所有页面 ≥ 1 入链，无矛盾

## [2026-08-09] build | 本地 wiki 站点（静态生成）
- 新增生成器 `output/generate-site.py`：frontmatter 解析 + markdown 渲染 + wikilink 图
- 新增 SPA 模板 `output/site-template.html`：搜索/分类树/路由/反向链接/暗色主题
- 产物 `output/site/index.html`（数据内嵌，file:// 双击即用）+ `output/site/data.json`
- 验收：`python3 -m unittest wiki/output/test_site.py -v` 9 项全过（52 页收录 / 230 wikilink 0 孤儿 / JSON 有效 / HTML 结构 0 错 / 渲染非空 / 反向链接镜像一致 / scheme 白名单 / script 转义 / 无残留 wikilink）
- 安全加固（security_review 闭环）：SPA 插值统一 esc() 转义、href encodeURIComponent、非白名单链接 scheme 降级纯文本、内嵌 JSON 三重转义（`</` `<!--` `-->`）
- 另：`output/tech-stack.html` 技术栈一页通（8-09 早前产物）