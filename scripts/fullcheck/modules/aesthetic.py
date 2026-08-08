"""审美评分：复用 scripts/test_animation_aesthetics.py（5 维度视觉评分）。

独立子进程调用（脚本自管 give/开箱/连拍/评分），以退出码为准：
0=全 PASS → ok；1=有 FAIL → bad；2=仅 WARN → warn。
"""
import os
import subprocess
import sys
from pathlib import Path

from .common import MC_TOOLS, REPO, Tally

SCRIPT = REPO / "scripts" / "test_animation_aesthetics.py"


def run(env, tally: Tally, version: str, out_dir: Path) -> None:
    out = out_dir / "aesthetic"
    env_os = dict(os.environ)
    env_os.setdefault("MCP_TOKEN", env.client.token or "")
    env_os.setdefault("MCP_PORT", str(env.client.port))
    env_os["PATH"] = f"{MC_TOOLS / 'scripts'}:{env_os.get('PATH', '')}"
    cmd = [sys.executable, str(SCRIPT), "--port", str(env.client.port),
           "--out", str(out)]
    try:
        r = subprocess.run(cmd, env=env_os, capture_output=True, text=True,
                           timeout=900)
    except subprocess.TimeoutExpired:
        tally.bad("审美评分", "超时 900s")
        return
    tail = "\n".join(r.stdout.splitlines()[-8:])
    if r.returncode == 0:
        tally.ok("审美评分", "5 维度全 PASS（详见 aesthetic/report.md）")
    elif r.returncode == 2:
        tally.warn_("审美评分", f"存在 WARN（退出码 2）\n{tail}")
    else:
        tally.bad("审美评分", f"退出码 {r.returncode}\n{tail}")
