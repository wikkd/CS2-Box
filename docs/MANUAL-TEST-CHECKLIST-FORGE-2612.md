# forge_26_1_2（MinecraftForge 26.1.2）1.0.6 手动测试清单

> 用途：发布前人工回归清单，可直接打勾。
> 关联：`docs/TESTING-FORGE-2612.md`（测试流程）、`scripts/test-forge-2612.sh`（L0-L3 自动化）、`build/test-reports/forge-2612-report.md`（测试报告）。

## 0. 环境准备

- [x] 仓库：`/Users/shuangyuexingxun/Desktop/CS2-Box`（main worktree，HEAD `b60d7bd`）
- [ ] JDK 25 toolchain 可用（Gradle 自动检测）
- [ ] 每次 Gradle 调用带 `-Pactive_versions=forge-26.1.2`（默认是 NeoForge 26.1.2，别忘参数）
- [ ] 清理旧数据：删除 `forge_26_1_2/run/config/csgobox.toml` 与 `forge_26_1_2/run/config/csbox/`（避免脏数据）
- [ ] 测试箱子定义就位：`forge_26_1_2/run/config/csbox/weapon_supply_box.json`（已有样例）

## 1. 自动化门禁 L0-L3 — 已通过 7/7（2026-08-11）

- [x] 一键回归：`./scripts/test-forge-2612.sh` → 退出码 0，PASS=7 FAIL=0
  - [x] S1 `clean + compileJava`（含 common 架构约束）
  - [x] S2 jar 产物校验（`csgobox-forge-26.1.2-1.0.6.jar`，`mods.toml` 内 `version="1.0.6"`）
  - [x] S3 `scripts/check-version.sh` 版本四同步
  - [x] S4 `scripts/check-animops-drift.sh` 3 平台渲染门面一致
  - [x] S5 `PlatformSmokeTest`（入口可加载 + 1.0.6 基线守卫）

## 2. L4 服务端（headless）— 已通过（2026-08-11）

- [x] `runServer` 启动至 `Done`，无 ERROR
- [x] 数据包加载：recipes 1519 / advancements 1620，无错误
- [x] 动态箱子：`weapon_supply_box.json` → `csgobox:weapon_supply_box`；`CS2 Box server started with 1 box definitions`
- [x] 命令可用：`/csbox info` / `info error` / `reload` / `nbt hand` / `/stop`（优雅停机）

## 3. L4 客户端 GUI 回归（runClient）— 2026-08-11 已用 mc_tools MCP 自动化跑通

> 2026-08-11 依据 mc_tools 最新文档（`--client forge_26_1_2`）重启 E2E：
> `test_csbox.sh` 11P/0F/0W；`test_csbox_ext.py` 21P/12F/1W（E8 批量屏蔽属预期、
> E10a/E11a/b/d 为视觉 OCR 误判、**E1a/E1c 发现 `/csbox info` 无参形式缺失**——
> 见 `docs/TEST-REPORT-FORGE-2612-2026-08-11.md`）。

启动：IDE 运行配置 **`MC Forge 26.1.2 - Client`**，或命令行：

```bash
./gradlew :forge_26_1_2:runClient -Pactive_versions=forge-26.1.2
```

**关键路径（发布门禁）：F1 → F2 → F3 → F4 → F5**

- [x] F1 启动无异常：正常进主菜单、进世界；日志仅 Realms 环境报错 + 通道空 payload 告警（非阻塞，见报告 §6）
- [x] F2 动态箱子注册：`/give @p csgobox:weapon_supply_box 1` → 获得物品，E10a 截图图标渲染正常（非紫黑）
- [x] F3 开箱主流程：右键 → 进度动画 → 结果屏（E2E T1-T4/T8、E9 覆盖），无卡屏
- [x] F4 消耗与产出：钥匙 1→0（T5）；结果物品进背包（T6）；关闭回世界（T7/E10e）
- [x] F5 批量开箱（1.0.6 基线=已屏蔽）：Shift+右键 → **不触发**批量总览屏（E8a 实测，屏蔽开关生效）
- [ ] F6 配置热重载：改 `config/csbox/*.json`（权重/分级）→ `/csbox reload` → 开箱结果随之变化；`enableHotReload=true` 时文件改动自动生效
- [ ] F7 磨损耐久：开出有耐久物品，查看界面 `wear` 与实际扣损一致；`damageItemByWear=false` 时不扣
- [x] F8 成就/统计：first_box 成就触发（E2）；`opened_boxes` 统计 24→25→26 累加（E3）
- [ ] F9 教程下载：首次启动 `config/csbox/` 生成教程 md；`/csbox reload tutorial` 可重下（dev 运行版本号 `unknown` 属已知限制，正式 jar 安装不受影响）
- [ ] F10 语言：中/英 locale 下 GUI、提示、物品名无乱码、无 key 原文
- [ ] F11 服务端权威：单机/联机开箱结果由服务端 RNG 决定（日志 `[CS2 Box]` 与客户端结果一致）
- [ ] F12 持久化：开箱所得物品重进世界仍在；`csgobox.toml` 配置项读回一致

命令速查（创造模式）：

```text
/give @p csgobox:csgo_box 5
/give @p csgobox:csgo_key0 10
/give @p csgobox:csgo_key3 1
/advancement revoke @s everything   # 重置成就便于复测
```

## 4. 发布门禁汇总

- [x] L0-L3 全绿（`./scripts/test-forge-2612.sh` 退出码 0，7/7）
- [x] L4 关键路径 F1-F5 通过（mc_tools MCP 自动化，2026-08-11）
- [ ] **修复 `/csbox info` 无参形式**（E1a/E1c 失败项：forge 移植缺口，1.0.6 基线在
      v26_1_2 有该行为）后重跑 E1
- [x] 模组物品模型定义 `assets/csgobox/items/` 齐全（箱/钥匙图标与 GUI 3D 预览正常，
      2026-08-11 修复，详见 `TEST-REPORT-FORGE-2612-2026-08-11.md` §2.1）
- [ ] 版本四同步 OK（S3 已自动覆盖）
- [ ] `mods.toml`：forge 版本区间 `[64,)`、MC 区间 `[26.1.2,26.2)` 与目标环境匹配
- [ ] jar 内无 1.0.7 线内容（terminal / armory / villager_trade / premium / 回收机）

## 5. IDE 启动配置（本次新增，供回归用）

- [x] `MC Forge 26.1.2 - Client` → `:forge_26_1_2:runClient`（`.idea/runConfigurations/MC_Forge_26_1_2_Client.xml`）
- [x] `MC Forge 26.1.2 - Server` → `:forge_26_1_2:runServer`（`MC_Forge_26_1_2_Server.xml`）
- [x] `MC Forge 26.1.2 - Data` → `:forge_26_1_2:runData`（`MC_Forge_26_1_2_Data.xml`）
- [x] `MC Forge 26.1.2 - GameTestServer` → `:forge_26_1_2:runGameTestServer`（`MC_Forge_26_1_2_GameTestServer.xml`）
