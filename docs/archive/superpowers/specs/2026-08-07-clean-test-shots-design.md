# 清理测试照片功能设计（方案 A）

日期: 2026-08-07 | 状态: 已批准

## 背景

动画审美测试脚本 `scripts/test_animation_aesthetics.py` 每次运行会在输出目录（默认 `build/animation_aesthetics`，多平台用 `build/animation_aesthetics_26` 等）下生成 `shots/*.png` 连拍截图和 `report.md` 报告。多次运行后截图堆积，需要一键清理入口。

## 需求

在测试脚本中提供独立清理子命令，默认只删照片、保留报告，支持参数控制删除范围。删除前有清单预览与确认保护。

## 设计

### 入口

```
python3 scripts/test_animation_aesthetics.py clean [--out DIR] [--report] [--dry-run] [--yes]
```

无子命令时保持现有测试行为不变（向后兼容）。

### 参数

| 参数 | 作用 |
|---|---|
| `--out` | 指定清理目录，默认 `build/animation_aesthetics`（与 test 子命令同约定） |
| `--report` | 连同 `report.md` 一起删；默认只删 `shots/*.png` |
| `--dry-run` | 只打印待删清单，不实际删除 |
| `--yes` | 跳过删除前确认 |

### 流程

1. 解析参数（argparse 子解析器）
2. 检查目录存在；不存在 → 输出"无测试产物可清理"，退出码 0
3. 收集目标文件：`shots/*.png`；加 `--report` 时含 `report.md`
4. 无文件 → 输出"无测试产物可清理"，退出码 0
5. 打印清单（文件数与路径）
6. 无 `--yes` → 输入确认（`y/N`，默认 N）；拒绝 → 中止，退出码 0
7. 逐文件删除；单个失败 → 打印错误继续
8. 打印汇总（删除 n 个，失败 m 个）；m > 0 时退出码 1

### 架构

- `do_clean(args)` 独立函数，不触碰现有测试代码路径
- `main()` 按子命令分派：`clean` → `do_clean`，无子命令 → 原测试流程
- 与主脚本共享 `--out` 的默认路径约定（`OUTPUT_DIR` 常量）

### 错误处理

- 目录不存在 / 无匹配文件：提示后退出码 0（幂等，非错误）
- 单文件删除失败：打印错误、继续处理其余，最终失败数 > 0 → 退出码 1
- 删除失败含 `report.md`（--report 场景）时同规则

### 测试（bash 实测）

临时目录构造假 png + report.md，验证：
1. `--dry-run` 不删任何文件
2. 默认删照片、留报告
3. `--report` 照片+报告全删
4. 确认提示输入 n 取消、输入 y 删除
5. 目录不存在 → 提示且退出码 0

## 版本管理

功能写入 `CHANGELOG.md`（新功能条目）；版本号不 bump，随下次发布。
