---
type: source
title: 清理测试照片功能（clean 子命令）
source_path: docs/superpowers/specs/2026-08-07-clean-test-shots-design.md
date_ingested: 2026-08-10
tags: [testing, tooling, cleanup, ux]
key_concepts: [superpowers-testing]
key_entities: []
---

# 清理测试照片功能 — 设计（方案 A）

> source: `docs/superpowers/specs/2026-08-07-clean-test-shots-design.md`

## Summary

2026-08-07 批准。为动画审美测试脚本 `scripts/test_animation_aesthetics.py` 提供独立 `clean` 子命令，一键清理多次运行堆积的连拍截图（默认只删 `shots/*.png` 保留 `report.md`），删除前有清单预览与确认保护。无子命令时保持现有测试行为（向后兼容）。

## Key takeaways

- **入口**：`python3 scripts/test_animation_aesthetics.py clean [--out DIR] [--report] [--dry-run] [--yes]`。
- **参数**：`--out` 指定清理目录（默认 `build/animation_aesthetics`）；`--report` 连同 `report.md` 一起删；`--dry-run` 只打印清单不删；`--yes` 跳过确认。
- **流程**：检查目录/文件存在（不存在 → 提示，退出码 0，幂等）→ 打印清单 → 无 `--yes` 则 `y/N` 确认（默认 N 拒绝中止）→ 逐文件删（单失败继续、最终失败数>0 退出码 1）。
- **架构**：`do_clean(args)` 独立函数不碰测试代码；`main()` 按子命令分派。
- **测试**：bash 实测 5 场景（`--dry-run` 不删 / 默认删照片留报告 / `--report` 全删 / 确认 n取消 y删除 / 目录不存在退出码 0）。
- **版本管理**：写入 `CHANGELOG.md`（新功能条目），版本号不 bump。

## Connections

- 概念：[[superpowers-testing]]
- 参考：[[animation-aesthetics-test]] · [[fullcheck-suite]]
