---
type: source
title: docs/ARCHITECTURE.md — 架构文档
source_path: docs/ARCHITECTURE.md
date_ingested: 2026-08-03
tags: [architecture, dataflow, rendering]
key_concepts: [multiloader-architecture, rendering-pipeline, server-authoritative-rng]
key_entities: [csgo-box, csbox-config, packet-csgo-progress]
---

# docs/ARCHITECTURE.md（架构）

## Summary
模块拓扑、core 抽象、开箱数据流、双平台 GUI 渲染管线对比。

## Key takeaways
- 依赖方向：`平台 → common`，`common/` 不得 import `net.minecraft.*` / `net.neoforged.*`（CONSTRAINT-001）
- 核心抽象：`BoxDefinition`（不可变 Record）/ `BoxRegistry` / `BoxJsonLoader` / `GradeGroup` / `RandomItem`（long 总权重防溢出）
- 数据流：`PacketRequestBoxItems`（C→S）→ 服务端选 winningIndex + 50 动画项 → `PacketSyncBoxItems`（S→C）→ 放钥匙开 → `PacketCsgoProgress`（含 requestId）→ 滚动动画 → `CsLookItemScreen`
- 渲染对比（1.21.1 vs 26.1.2）：`render(GuiGraphics)` vs `extractRenderState(...)`；`PoseStack` vs `Matrix3x2f`；静态 `RenderSystem` vs decoupled `RenderPipelines`；3D 预览 `BakedModel` vs `PictureInPictureRenderer`
- v26_1_2 独有：`gui/pip/Icon3DRenderer`、`ButtonPalette`、`RenderFontTool`
- 5 个网络包（1.0.8 后实际更多）：均带 Codec + StreamCodec
- 成就：原生 `CriteriaTriggers` 持久化，无 Capability

## Connections
- [[rendering-pipeline]] / [[multiloader-architecture]] / [[server-authoritative-rng]]
- [[csgo-box]] / [[packet-csgo-progress]]
