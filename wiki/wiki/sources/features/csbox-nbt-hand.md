---
type: source
title: /csbox nbt hand 实现计划
source_path: docs/superpowers/plans/2026-08-09-csbox-nbt-hand.md
date_ingested: 2026-08-10
tags: [feature, command, nbt, tooling]
key_concepts: [multiloader-architecture, platform-mirror-discipline]
key_entities: [csgo-box, box-json-loader, csbox-config]
---

# /csbox nbt hand 实现计划

> source: `docs/superpowers/plans/2026-08-09-csbox-nbt-hand.md`

## Summary

新增 `/csbox nbt hand` 子命令（**所有玩家可用**），输出主手物品的完整 JSON（`{id, count, components}`），可直接粘贴进 `config/csbox/*.json` 的 items 条目——降低用户手写箱子配置的门槛。复用 `BoxItemCodec.serializeItemStack()`（唯一序列化事实来源，从包私有改 public），命令层只做「取主手物品 → 序列化 → 聊天输出」。

## Key takeaways

- **架构**：复用各平台已有 `BoxItemCodec.serializeItemStack()`（改 `public static`）；命令层取主手物品 → 序列化 → 聊天输出。权限从「根节点 `requires`」改为「每子命令 `requires`」以放行 `nbt` 分支（现有子命令保持管理员级，`nbt` 分支不设权限）。
- **平台落地**：基准 `v26_1_2`，`v26_2` 定点合入（禁整文件覆盖），`v1_21_1` 手工适配（`isGameMaster` = `hasPermission(2)`，无 Permissions 类）；每平台改动后 clean `compileJava`。`forge_26_1_2` 由 `port-forge-2612.py` 再生（不提交）。
- **输出**：`{id, count, components}` JSON，超长（`MAX_NBT_CHARS=20000`）截断提示；空手给 empty 提示；序列化异常给 error 提示。
- **文档同步**：README 功能清单加 `/csbox nbt hand`、`CHANGELOG.md` 顶部加 `[Unreleased]`。
- **验证**：玩家/管理员两种身份 × 空手/带 components 物品/粘贴回 config 闭环 reload+info+开箱/超长书截断，三平台各一次。

## Connections

- 概念：[[multiloader-architecture]] · [[platform-mirror-discipline]]
- 实体：[[csgo-box]] · [[box-json-loader]] · [[csbox-config]]
- 参考：[[code-review]]（§4.2/§4.7）· [[configuration]]
