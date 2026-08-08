#!/usr/bin/env python3
"""run_full_check.py — CS2-Box 上线前全量检查编排器。

顺序跑 10 平台（每平台启动客户端 → 进世界 → 用例模块 → 审美评分 → 停机），
产出 build/fullcheck/<平台>/report.md|json + 根 SUMMARY.md|json。

用法:
  python3 scripts/fullcheck/run_full_check.py                 # 全部 10 平台
  python3 scripts/fullcheck/run_full_check.py --platform 1.21.1,26.2
  python3 scripts/fullcheck/run_full_check.py --only e2e_open  # 只跑某模块
  python3 scripts/fullcheck/run_full_check.py --keep-client    # 跑完不关机

退出码: 0=全 PASS  1=有用例失败  2=前置失败（平台起不来/进不了世界）
"""
import argparse
import importlib
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent.parent / "mc_tools" / "scripts"))

from csxlib.helpers import BoxEnv  # noqa: E402
from csxlib.mcp import McpClient  # noqa: E402

from fullcheck.modules import (  # noqa: E402
    achievements, admin_cmds, aesthetic, box_variants, common, dynamic_box,
    e2e_open, wear_durability,
)

MODULES = [
    ("e2e_open", e2e_open.run),
    ("wear_durability", wear_durability.run),
    ("admin_cmds", admin_cmds.run),
    ("dynamic_box", dynamic_box.run),
    ("achievements", achievements.run),
    ("box_variants", box_variants.run),
    ("aesthetic", aesthetic.run),
]

BOX_ID = "csgobox:weapon_supply_box"
KEY_ID = "csgobox:csgo_key0"


def run_platform(version: str, only: list, keep_client: bool) -> tuple:
    pf = common.PLATFORMS[version]
    print(f"\n========== [{version}] 启动客户端 ({pf['module']}) ==========")
    log_path = common.OUT_ROOT / version / "client.log"
    try:
        proc = common.launch_client(version, log_path)
    except RuntimeError as e:
        print(f"[{version}] 启动失败: {e}")
        return version, None
    if proc.poll() is not None:
        tail = "\n".join(log_path.read_text(errors="ignore").splitlines()[-20:])
        print(f"[{version}] 客户端启动失败:\n{tail}")
        return version, None
    if not common.wait_port(common.PORT, 30):
        print(f"[{version}] MCP 端口 {common.PORT} 未就绪")
        common.stop_client(proc, None, timeout=10)
        return version, None

    token = common.read_token(version)
    if not token:
        print(f"[{version}] testhelper.toml 无 token，等待写入")
        for _ in range(20):
            time.sleep(3)
            token = common.read_token(version)
            if token:
                break
    client = McpClient(port=common.PORT, token=token or None)
    env = BoxEnv(client=client, box_id=BOX_ID, key_id=KEY_ID)
    tally = common.Tally()
    cases = []

    try:
        if not common.enter_world(version, tally):
            return version, tally
        common.safe_setup(env, version)
        # 兜底清理：上轮模块异常可能残留 fct_* 测试箱（会污染 /csbox errors）
        csbox_dir = common.RUNS_DIR(version) / "config" / "csbox"
        for f in csbox_dir.glob("fct_*.json"):
            try:
                f.unlink()
            except OSError:
                pass

        for name, fn in MODULES:
            if only and name not in only:
                continue
            print(f"[{version}] 用例模块: {name} ...")
            t0 = time.time()
            sub_tally = common.Tally()
            try:
                fn(env, sub_tally, version, common.OUT_ROOT / version)
            except Exception as e:
                sub_tally.bad(name, f"模块异常: {e!r}")
            tally.merge(sub_tally)
            print(f"[{version}] {name}: {sub_tally.pass_}P/{sub_tally.fail}F/{sub_tally.warn}W "
                  f"({time.time() - t0:.0f}s)")
            if sub_tally.fail and not keep_client:
                break  # 平台级短路：有 FAIL 直接收场，省时间
    finally:
        if not keep_client:
            common.stop_client(proc, env)
        else:
            print(f"[{version}] --keep-client：保留客户端运行")
    common.write_report(version, tally, cases)
    print(f"[{version}] 结果: {tally.pass_} PASS / {tally.fail} FAIL / {tally.warn} WARN")
    return version, tally


def main() -> int:
    ap = argparse.ArgumentParser(description="CS2-Box 全量上线检查")
    ap.add_argument("--platform", default=",".join(common.DEFAULT_PLATFORMS),
                    help="逗号分隔的平台列表（默认全部 10 平台）")
    ap.add_argument("--only", default="",
                    help="只跑指定模块（逗号分隔，如 e2e_open,box_variants）")
    ap.add_argument("--keep-client", action="store_true",
                    help="每平台跑完不关机（调试用）")
    args = ap.parse_args()

    versions = [v.strip() for v in args.platform.split(",") if v.strip()]
    unknown = [v for v in versions if v not in common.PLATFORMS]
    if unknown:
        print(f"未知平台: {unknown}; 支持: {list(common.PLATFORMS)}", file=sys.stderr)
        return 2
    only = [m.strip() for m in args.only.split(",") if m.strip()]

    results = []
    for v in versions:
        version, tally = run_platform(v, only, args.keep_client)
        results.append((version, tally))
        if tally is None and not args.keep_client:
            continue

    ok = [v for v, t in results if t is not None and t.fail == 0]
    failed = [v for v, t in results if t is not None and t.fail > 0]
    broken = [v for v, t in results if t is None]
    common.write_summary([(v, t) for v, t in results if t is not None])

    print("\n========== FULLCHECK 汇总 ==========")
    print(f"  通过: {', '.join(ok) or '无'}")
    if failed:
        print(f"  用例失败: {', '.join(failed)}")
    if broken:
        print(f"  前置失败: {', '.join(broken)}")
    print(f"  报告: {common.OUT_ROOT}")
    if failed or broken:
        return 1 if failed else 2
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(line_buffering=True)
    sys.stderr.reconfigure(line_buffering=True)
    sys.exit(main())
