---
type: source
title: docs/update-1.0.4.md — 1.0.4 更新说明
source_path: docs/update-1.0.4.md
date_ingested: 2026-08-03
tags: [release, network, animation]
key_concepts: [server-authoritative-rng]
key_entities: [packet-csgo-progress]
---

# docs/update-1.0.4.md（1.0.4 更新说明）

## Summary
1.0.4 聚焦服务端授权开箱、更安全的客户端网络、动画正确性、JSON 文档。

## Key takeaways
- 开箱结果由服务端决定，与动画物品条一同发送至客户端（`PacketBoxOpenResult` 含 finalItem/grade/winningIndex/seed/requestId/animationItems/animationGrades）
- 中奖索引从动画窗口后期选取（防止在开头时动画距离过短）
- 动画 ESC 取消不再阻塞下次开箱请求（冷却改为短效防双击）
- 空箱警告文字绘制在 3D 模型上方（不被遮挡）
- 自动生成的默认 JSON 含 `_tutorial` 对象
- 现有 JSON 不会被覆盖（`config/csbox` 为空时才生成）
- 无效/被拒绝请求发送空白结果，客户端可安全关闭

## Connections
- [[server-authoritative-rng]] / [[packet-csgo-progress]] / [[changelog]]