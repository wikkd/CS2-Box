# 上线前全量检查测试套件（FULLCHECK-SUITE）

> 日期：2026-08-08
> 状态：已批准设计（方案 A）
> 目标读者：维护者与执行自动化测试的开发者

## 1. 背景与目标

CS2-Box 有 10 个 NeoForge 平台模块（v1_21_0/v1_21_1/v1_21_3/v1_21_4/v1_21_5/v1_21_8/v1_21_10/v1_21_11/v26_1_2/v26_2）。`docs/RELEASE.md` 质量门要求 2 个代表平台的运行时回归，但缺少一套统一、可重复、覆盖全部平台的上线前完整检查工具。

本套件目标：**一个命令对全部 10 平台跑完整上线前检查**，包括开箱主流程、磨损耐久、管理命令、动态 box item、成就、GUI 审美评分，以及用户重点的**不同配置的箱子（含错误箱子自检）**。

## 2. 范围（已与用户确认）

### 包含
- 开箱主流程 E2E（T1-T9 全量）
- 磨损耐久（damageItemByWear 开/关）
- 管理命令（/csbox reload、tutorial refresh、errors）
- 动态 box item（图标非紫黑、可开）
- 成就触发（enableAchievements 开/关）
- GUI 渲染验证 = 全量审美评分（10 平台都跑 5 维度视觉评分）
- 箱子 JSON 变体（合法变体 + 错误箱子自检）
- 平台范围：全部 10 平台运行时（前置：testhelper 移植）

### 排除
- 批量开箱（当前版本屏蔽，恢复后另行回归）
- CI 接入（10 平台全量 2-4 小时，本地执行）

## 3. 架构

### 3.1 前置工程：testhelper 移植（mc_tools 仓库）

testhelper mod 目前仅支持 1.21.1 与 26.2（`java-1211` / `java` 双源码目录 + `-Pactive_versions` 切换）。移植到全部 10 平台：

- `mc_tools/gradle.properties`：扩到 10 组版本变量（复用 CS2-Box `gradle.properties` 的版本 pin）
- `mc_tools/build.gradle`：按 `active_versions` 选择 MC/Neo 版本、JDK、源码目录与部署路径
- 源码适配纪律（与 CS2-Box 平台模块镜像纪律一致）：
  - legacy 线（1.21.0/1.21.1/1.21.3-1.21.11）：共享 1.21.x 源码，渐进差异定点适配（1.21.3+ API 变更、1.21.8+ per-item bounding box 等）
  - new 线（26.1.2/26.2）：decoupled API；26.2 已适配，26.1.2 复用旧 API
- 每平台配置 `testhelper.toml` 并部署 jar 到对应 `runs/client/mods/`

### 3.2 测试套件（CS2-Box 仓库 `scripts/fullcheck/`）

```
scripts/fullcheck/
  run_full_check.py        # 编排器：顺序跑 10 平台，--platform 筛选
  modules/
    __init__.py
    common.py              # 平台启动/停机/进世界/安全设置/报告基元
    e2e_open.py            # 开箱主流程 T1-T9
    wear_durability.py     # 磨损耐久开/关
    admin_cmds.py          # reload / tutorial refresh / errors
    dynamic_box.py         # 动态 box item
    achievements.py        # 成就触发
    box_variants.py        # 箱子 JSON 变体 + 错误自检
    aesthetic.py           # 复用 test_animation_aesthetics.py（10 平台全量评分）
```

依赖 `mc_tools/scripts/csxlib`（BoxEnv/McpClient，现有脚本已同款引用）。

### 3.3 执行流

每平台：
1. `gradlew runClient -Pactive_versions=<v>`（前台/后台受控启动）
2. 等 MCP 端口就绪 → 进世界（复用 mc_tools 的导航原语）
3. 安全设置（白天/锁夜/防爆/禁刷怪）
4. 顺序执行 6 个用例模块（各自发放物品、测试、清理现场）
5. 审美评分（复用 test_animation_aesthetics.py 逻辑）
6. 停机（ESC → 保存退出），清理临时产物
7. 写 `build/fullcheck/<平台>/` 报告（MD + JSON）

全部平台完成后写根汇总矩阵。

### 3.4 报告与退出码

- 单平台：`build/fullcheck/<平台>/report.md` + `report.json` + 截图目录
- 汇总：`build/fullcheck/SUMMARY.md`（10 平台 × 用例矩阵表）+ `SUMMARY.json`
- 退出码：0=全部通过；1=有用例失败；2=前置失败（平台起不来/进不了世界）；与现有脚本语义一致

## 4. 用例清单

### 4.1 e2e_open（开箱主流程）
| # | 用例 | 期望 |
|---|---|---|
| T1 | 右键打开 CsboxScreen | screen=CsboxScreen |
| T2 | 点开启 | 进入 CsboxProgressScreen 动画 |
| T3 | 等待结果 | CsLookItemScreen |
| T4 | 结果屏截图存证 | 文件存在、尺寸=帧缓冲 |
| T5 | 钥匙消耗 | -1 |
| T6 | 背包新增 | 开出物品在包 |
| T7 | 关闭结果屏 | 回世界 |
| T8 | 动画中 ESC 取消 | 无屏幕残留；钥匙已结算；物品已入包 |
| T9 | 取消后立即重开 | 无冷却阻塞，成功到结果屏 |

### 4.2 wear_durability（磨损耐久）
- 开箱得到有耐久物品 → 查看界面 wear 显示与实际扣损一致
- `damageItemByWear=false` → 扣损不生效
- 无耐久物品仍为随机磨损率（不扣）

### 4.3 admin_cmds（管理命令）
- `/csbox reload`：改箱子 JSON → reload → 生效
- `/csbox tutorial refresh`：刷新成功
- `/csbox errors`：无错时输出无错误；有错时列出

### 4.4 dynamic_box（动态 box item）
- `/give @p csgobox:<filename>` → 物品存在、图标非紫黑（截图采样）、可开启

### 4.5 achievements（成就）
- 开箱 → `csgobox:opened_boxes` 统计 +1
- `enableAchievements=false` → 成就禁用但统计仍累计

### 4.6 box_variants（箱子 JSON 变体 + 错误自检）——用户重点
**合法变体**（每平台预置 `config/csbox/` 测试集合，跑完恢复现场）：
- 不同 key：`csgo_key0/1/2`（`csgo_key3` 仅锻造台配方，只验证配方文件存在）
- 不同 `drop` 概率（0.5/1.0/2.0）
- `random` 权重不同组合（和为 100 / 任意正整数）
- 有/无 `entity` 列表
- grade 数量缺失（仅 grade1-3）
- 单条目箱（每 grade 1 个物品）

**错误箱子自检**：
- 非法 JSON / 缺 `name` / 缺 `key` / random 权重非法（负值/零）/ id 不存在 / 重复条目 / grade 非数组
- 期望：模组拒绝加载该箱；`/csbox errors` 能查出错误；游戏不崩溃；其他箱子不受影响

## 5. 验证策略

1. 先 1.21.1 + 26.2（已有 testhelper）跑通套件全流程
2. testhelper 移植完成后逐平台启用，每平台 clean 编译 + 套件全跑
3. 全部 10 平台跑完 → 核对 SUMMARY.md 矩阵全绿
4. 回归文档 `docs/RELEASE.md` 增补"全量检查"章节

## 6. 风险与对策

| 风险 | 对策 |
|---|---|
| testhelper 在 1.21.3+/1.21.8+ 等有 API 差异编译失败 | 逐平台 clean 编译验证；参考 CS2-Box 平台适配先例 |
| 10 平台全量运行时间长（约 3 小时） | 支持 `--platform` 单平台跑；顺序执行避免端口/资源冲突 |
| 审美评分模型波动 | 复用现有评分逻辑与人工抽验流程 |
| 测试污染 runs 世界 | 每平台独立 world 目录 + 用例后清理现场 |
