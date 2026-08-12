---
type: source
title: 上线前全量检查测试套件（FULLCHECK）
source_path: docs/superpowers/specs/2026-08-08-fullcheck-suite-design.md
date_ingested: 2026-08-10
tags: [testing, fullcheck, e2e, quality]
key_concepts: [superpowers-testing, server-authoritative-rng, bulk-opening-pipeline]
key_entities: [box-json-loader, csbox-config, packet-csgo-progress, packet-csgo-bulk-progress, opened-box-trigger]
---

# 上线前全量检查测试套件（FULLCHECK）— 设计

> source: `docs/superpowers/specs/2026-08-08-fullcheck-suite-design.md`

## Summary

2026-08-08 批准（方案 A）。目标：**一条命令对全部 10 平台跑完整上线前检查**，统一、可重复、覆盖全部平台——补 `docs/RELEASE.md` 质量门只覆盖 2 个代表平台的缺口。范围含开箱主流程 E2E、磨损耐久、管理命令、动态 box item、成就、GUI 全量审美评分，以及用户重点的**不同配置箱子（含错误箱子自检）**。

## Key takeaways

- **架构**：前置工程 `testhelper` mod 移植到全部 10 平台（复用 CS2-Box 镜像纪律：legacy 线共享 1.21.x 源码渐进适配，new 线 decoupled API）；测试套件在 `scripts/fullcheck/`（编排器 `run_full_check.py` + 7 个用例模块 `e2e_open`/`wear_durability`/`admin_cmds`/`dynamic_box`/`achievements`/`box_variants`/`aesthetic`），依赖 `mc_tools/scripts/csxlib`（`BoxEnv`/`McpClient`）。
- **执行流**：每平台 `runClient` → 等 MCP 端口 → 进世界 → 安全设置 → 顺序跑 6 用例 → 审美评分 → 停机；报告写 `build/fullcheck/<平台>/`；全平台完成写 `SUMMARY.md` 矩阵。
- **用例重点（box_variants）**：合法变体（不同 key/`drop` 概率/`random` 权重/entity 列表/grade 缺失/单条目箱）+ **错误箱子自检**（非法 JSON/缺 name/缺 key/权重非法/id 不存在/重复/grade 非数组）→ 模组拒绝加载、`/csbox info error` 能查、不崩溃、其他箱不受影响。
- **排除**：批量开箱（自动化套件未覆盖；1.0.7 已恢复，手动回归见 `docs/RELEASE.md`）、CI 接入（10 平台 2-4 小时，本地执行）。
- **退出码**：0=全通过 / 1=用例失败 / 2=前置失败，与现有脚本语义一致。

## Connections

- 概念：[[superpowers-testing]] · [[server-authoritative-rng]] · [[bulk-opening-pipeline]]
- 实体：[[box-json-loader]] · [[csbox-config]] · [[packet-csgo-progress]] · [[packet-csgo-bulk-progress]] · [[opened-box-trigger]]
- 参考：[[animation-aesthetics-test]] · [[box-defaults]] · [[release]]
