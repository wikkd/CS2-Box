# CI 门禁：分支保护（Required Status Checks）设置指南

> 把 `docs/CODE-REVIEW.md` §6.4 的合并硬条件（CI 全绿 + 无未解决 Blocker + ≥1 Reviewer 批准）
> 落到 GitHub 分支保护规则上，让 `main` 不再接受未过门禁的代码。
>
> 前置条件：仓库 admin 权限 + 已登录 `gh`（`gh auth login`）。未登录时请先完成登录再执行 §3 命令。

---

## 1. 需要设为 Required 的 Check Context 清单

> Context 名 = workflow 中 job 的 `name`。matrix job 的**每个组合**是独立 check，需逐个列出。

| Check Context | 来源 workflow / job | 守护内容 |
|---|---|---|
| `1.21.1 (Java 21)` | `build.yml` → `build` job（matrix 行 1.21.1） | 1.21.1 编译 + 冒烟测试 + 打包 |
| `26.1.2 (Java 25)` | `build.yml` → `build` job（matrix 行 26.1.2） | 26.1.2 编译 + 冒烟测试 + 打包 |
| `26.2 (Java 25)` | `build.yml` → `build` job（matrix 行 26.2） | 26.2 编译 + 冒烟测试 + 打包 |
| `fabric-1.21.1 (Java 21)` | `build.yml` → `build` job（matrix 行 fabric-1.21.1） | Fabric 1.21.1 编译 + 冒烟 + `remapJar` |
| `forge-1.18.2 (Java 17)` | `build.yml` → `build` job（matrix 行 forge-1.18.2） | Forge 1.18.2 编译 + 冒烟测试 + 打包 |
| `forge-1.20.1 (Java 17)` | `build.yml` → `build` job（matrix 行 forge-1.20.1） | Forge 1.20.1 编译 + 冒烟测试 + 打包 |
| `forge-26.1.2 (Java 25)` | `build.yml` → `build` job（matrix 行 forge-26.1.2） | Forge 26.1.2 编译 + 冒烟测试 + 打包 |
| `forge-26.2 (Java 25)` | `build.yml` → `build` job（matrix 行 forge-26.2） | Forge 26.2 编译 + 冒烟测试 + 打包 |
| `common unit tests + checks` | `build.yml` → `common-test` job | 版本同步 / AnimRenderOps 漂移 / 平台镜像漂移 / CONSTRAINT-001 / compat-pack id 核查 / common 单测 |
| `GameTest 1.21.1` | `gametest.yml` → `gametest` job（matrix 行 1.21.1） | GameTest 集成测试（6 用例，exit 码 = 失败用例数） |
| `GameTest 26.1.2` | `gametest.yml` → `gametest` job（matrix 行 26.1.2） | GameTest 集成测试（6 用例 + vanilla always_pass） |
| `GameTest 26.2` | `gametest.yml` → `gametest` job（matrix 行 26.2） | GameTest 集成测试（6 用例 + vanilla always_pass） |
| `GameTest forge-1.18.2` | `gametest.yml` → `gametest` job（matrix 行 forge-1.18.2） | GameTest 集成测试（6 用例） |
| `GameTest forge-1.20.1` | `gametest.yml` → `gametest` job（matrix 行 forge-1.20.1） | GameTest 集成测试（6 用例） |
| `PR description check` | `pr-checks.yml` → `description` job | PR 描述模板与自查勾选校验 |

> 注：全部 15 个 check 均为真实执行（GameTest 自 2026-11-07 起五平台各 6 用例全绿）。
> `fabric_1_21_1` 的 GameTest 套件已就位但 dev 运行被上游 Loom bug 阻断（见 `docs/PORTING-fabric.md`），
> 未入矩阵；`box-editor-smoke.yml`（Playwright 冒烟）为独立工作流，可选加入 Required。

---

## 2. 方式 A：Web UI 设置（手动）

1. GitHub → Settings → Branches → **Add branch ruleset**（或 Add rule）
2. 分支：`main`
3. 勾选 **Require status checks to pass before merging**，在搜索框逐个添加 §1 清单中的 15 个 check（8 个 build 矩阵行 + common-test + 5 个 GameTest + PR description）
4. 勾选 **Require a pull request before merging**，`Require approvals` 设为 **1**（与 CODE-REVIEW.md「≥1 Reviewer 批准」一致）
5. 可选：勾选 **Require branches to be up to date**（strict 模式，PR 基于最新 main）
6. `Enforce` 默认开启；`enforce_admins` 建议保持关闭（管理员可救急）
7. Create / Save

---

## 3. 方式 B：gh CLI 一键设置（推荐）

登录后，在仓库根目录执行：

```bash
gh api -X PUT repos/wikkd/CS2-Box/branches/main/protection \
  --input - <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "1.21.1 (Java 21)",
      "26.1.2 (Java 25)",
      "26.2 (Java 25)",
      "fabric-1.21.1 (Java 21)",
      "forge-1.18.2 (Java 17)",
      "forge-1.20.1 (Java 17)",
      "forge-26.1.2 (Java 25)",
      "forge-26.2 (Java 25)",
      "common unit tests + checks",
      "GameTest 1.21.1",
      "GameTest 26.1.2",
      "GameTest 26.2",
      "GameTest forge-1.18.2",
      "GameTest forge-1.20.1",
      "PR description check"
    ]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF
```

验证已生效：

```bash
gh api repos/wikkd/CS2-Box/branches/main/protection --jq '.required_status_checks.contexts'
```

回滚（删除保护规则）：

```bash
gh api -X DELETE repos/wikkd/CS2-Box/branches/main/protection
```

---

## 4. 设置后的行为变化（请知悉）

- **直接 push 到 `main` 会被拒**（含 force push）；日常开发一律走 `feat/*` / `fix/*` 分支 + PR。
- 每个 PR 必须等 §1 清单全部 check 通过 + 1 名 Reviewer 批准才能合并。
- 如果某个 check 在 PR 上**从未运行**（例如新加的 check 名写错），GitHub 默认不阻塞；建议设置后先开一个
  test PR 确认 15 个 check 全部出现且通过。
- `common-test` 与 `build` 在直接 push 时由 `on: push` 触发，可正常满足 Required；`pr-checks.yml` 仅
  在 PR 事件触发，直接 push 时该 check 不存在、不阻塞——符合预期。
- 若误将矩阵组合名写错（如 `26.2 (Java 25)` 与 job name 不一致），保护规则会"永不通过"，
  用 §3 回滚命令删除后按 §1 重新核对 job `name` 字段。

---

## 5. 建议的后续演进（非阻塞）
- `fabric_1_21_1` 的 GameTest 待上游 Loom officialMojangMappings 半重映射 bug 修复后加入矩阵（见 `docs/PORTING-fabric.md` / `docs/TESTING-PIPELINE.md`）。
- 需要更严格时：开启 `enforce_admins`。
