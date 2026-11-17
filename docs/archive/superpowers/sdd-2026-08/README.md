# SDD 开发过程档案（2026-08）

> ⚠️ 已归档（历史快照 · 2026-11-17）：原位于仓库根目录 `.superpowers/sdd/`（本地未跟踪工作区），
> 已整理移入 `docs/archive/`。**内容不再维护**，仅作历史参考；交付结果的权威记录见 `CHANGELOG.md`。

## 这是什么

2026-08 期间用 Superpowers 技能套件的 **subagent-driven-development（SDD）** 工作流开发时留下的过程产物：
每份计划被拆成 task brief 派给子代理，子代理提交 report，主代理逐任务做 code review 并生成 review 包，
总进度记在 `progress.md`。它不是构建产物，也不被任何代码 / CI / 脚本引用。

## 目录内容

| 路径 | 内容 |
|---|---|
| `progress.md` | 总进度账本：三个里程碑的逐任务状态、终审结论、遗留 Minor 项 triage |
| `final-fix-report.md` | 终端机里程碑整条分支终审的 2 个 Important 修复报告（含三平台 clean 编译证据） |
| `briefs/task-1..8-brief.md` | 发给子代理的任务简报（改动文件、逐步指令、验收标准） |
| `reports/task-1..8-report.md` | 子代理回报（实现说明、编译证据、自审） |

### 任务清单

终端机演示原型对齐（`911b744..7c1ea6f`）：

| # | 任务 | 结果 |
|---|---|---|
| 1 | OfferEntry 状态 + 时序对齐 + 模型测试（TDD） | `9b2eb1f` + `0b1e945`（fix） |
| 2 | 卡片状态标签 + 系统文案带玩家名 + 接受按钮价格 | `cfa0a86` + `385afed`（fix） |
| 3 | 胶囊填充实色 + 拒绝按钮 armed 红框 | `fd9aa9e` |
| 4 | 聊天区滚轮滚动 | `257f54f` + `b8111ce`（fix，修复滚动方向反转） |
| 5 | 聊天区 ♞ 水印 | `6e7f70d` |
| 6 | 底行三件套（区域 11 名称 / 倒计时冒号变暗 / 圆形徽章） | `6395f6f` |
| 7 | 中央展示 = 终端箱池实际物品 3D 模型 | `9624b01`（+ merge `0e5eba7` / `be96205`） |

GUI 动效里程碑（`39fc240..1577a67`）：

| # | 任务 | 结果 |
|---|---|---|
| 8 | v1_21_1 主屏按钮补 hover（对齐 v26 系） | `8f82807` |

## 归档时的取舍

- **保留**：`progress.md`、`final-fix-report.md` 与 16 份 brief/report——这些是手写内容，
  包含 git 历史里没有的决策理由、遗留项 triage 和编译证据。
- **丢弃**：31 份 `*.diff` review 包（约 1.16 MB，占整个目录 95%）。它们是 `git diff base..head`
  加提交清单 / 文件统计的机器生成包装，**内容 100% 可从 git 历史重建**：

  ```bash
  # 单个任务区间（base..head 见原文件名）
  git log --oneline <base>..<head> && git diff --stat <base>..<head> && git diff <base>..<head>

  # 终端机里程碑整条分支终审
  git log --oneline 911b744..HEAD
  ```

  30 组区间涉及的 60 个 commit 均已核验存在于当前仓库。

## 已知局限

- brief/report 文件名按 `task-N` 复用，**后一个里程碑会覆盖前一个**。因此现存 8 组只对应
  「终端机演示原型对齐」（task 1-7）与「GUI 动效补全」（task 8）；
  更早的「创造模式免钥匙开箱」（`9e6fa05..241a41e`，2026-08-06）只剩 `progress.md` 里的账本记录。
- 目录内相对路径（如 `docs/superpowers/plans/...`）指向的是当时的路径，现已分别归档到
  `docs/archive/superpowers/plans/` 或仍留在 `docs/superpowers/specs/`。

## 相关归档

- 对应计划与规格：`docs/archive/superpowers/plans/`、`docs/archive/superpowers/specs/`
- 同期其他 agent 本地工作态：`docs/archive/agent-tools/`
- 根目录 `.gitignore` 仍保留 `.superpowers/` 规则，防止工具重新生成时被误提交。
