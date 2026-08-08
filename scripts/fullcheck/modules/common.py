"""fullcheck 公共基元：平台定义、客户端生命周期、报告。

约定：
- MCP 坐标一律帧缓冲 (fb)，1708x960。
- 每个平台顺序跑（端口 41501 冲突自动 +1，但套件内只跑一个实例）。
- 退出码：0=全 PASS  1=有用例 FAIL  2=前置失败（平台起不来/进不了世界）。
"""
import json
import os
import re
import signal
import socket
import subprocess
import time
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent.parent
MC_TOOLS = REPO.parent / "mc_tools"
MC_SCRIPTS = MC_TOOLS / "scripts"

PORT = int(os.environ.get("MCP_PORT", "41501"))
OPEN_BTN = (1244, 924)
CLOSE_BTN = (1516, 924)

PLATFORMS = {
    "1.21.0": dict(module="v1_21_0", jdk=21),
    "1.21.1": dict(module="v1_21_1", jdk=21),
    "1.21.3": dict(module="v1_21_3", jdk=21),
    "1.21.4": dict(module="v1_21_4", jdk=21),
    "1.21.5": dict(module="v1_21_5", jdk=21),
    "1.21.8": dict(module="v1_21_8", jdk=21),
    "1.21.10": dict(module="v1_21_10", jdk=21),
    "1.21.11": dict(module="v1_21_11", jdk=21),
    "26.1.2": dict(module="v26_1_2", jdk=25),
    "26.2": dict(module="v26_2", jdk=25),
}
DEFAULT_PLATFORMS = list(PLATFORMS)

RUNS_DIR = lambda v: REPO / PLATFORMS[v]["module"] / "runs" / "client"  # noqa: E731
OUT_ROOT = REPO / "build" / "fullcheck"
TOKEN_RE = re.compile(r"[0-9a-f]{64}")


def read_token(version: str) -> str:
    """读取平台 testhelper.toml 的 MCP token（首启动自动写回）。"""
    cfg = RUNS_DIR(version) / "config" / "testhelper.toml"
    if cfg.is_file():
        for line in cfg.read_text(encoding="utf-8").splitlines():
            m = TOKEN_RE.search(line)
            if m:
                return m.group(0)
    return ""


# ---------------------------------------------------------------- tally
class Tally:
    def __init__(self):
        self.pass_ = 0
        self.fail = 0
        self.warn = 0
        self.rows = []  # (verdict, name, detail)

    def ok(self, name, detail=""):
        self.pass_ += 1
        self.rows.append(("PASS", name, detail))

    def bad(self, name, detail=""):
        self.fail += 1
        self.rows.append(("FAIL", name, detail))

    def warn_(self, name, detail=""):
        self.warn += 1
        self.rows.append(("WARN", name, detail))

    def merge(self, other: "Tally"):
        self.pass_ += other.pass_
        self.fail += other.fail
        self.warn += other.warn
        self.rows += other.rows


# ---------------------------------------------------------------- client
def wait_port(port: int, timeout: float = 300) -> bool:
    end = time.time() + timeout
    while time.time() < end:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=2):
                return True
        except OSError:
            time.sleep(3)
    return False


def port_free(port: int) -> bool:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=1):
            return False
    except OSError:
        return True


def launch_client(version: str, log_path: Path) -> subprocess.Popen:
    """后台启动 gradle runClient，等 MCP 端口就绪。

    start_new_session=True：客户端进程独立进程组，stop_client 的 killpg
    不会误杀编排器自身（历史教训：曾整组击杀导致报告丢失）。
    端口被旧客户端占用时拒绝启动（防用例跑错客户端）。
    """
    if not port_free(PORT):
        raise RuntimeError(f"端口 {PORT} 被占用，无法启动 {version} 客户端"
                           f"（残留客户端需先清理）")
    log_path.parent.mkdir(parents=True, exist_ok=True)
    gradle = REPO / "gradlew"
    cmd = [str(gradle), f":{PLATFORMS[version]['module']}:runClient",
           f"-Pactive_versions={version}"]
    f = open(log_path, "w", encoding="utf-8")
    proc = subprocess.Popen(cmd, cwd=REPO, stdout=f, stderr=subprocess.STDOUT,
                            start_new_session=True)
    if not wait_port(PORT, 360):
        return proc  # 由调用方判定失败并收割日志
    time.sleep(3)
    return proc


def stop_client(proc: subprocess.Popen, env, timeout: float = 40) -> None:
    """尽力优雅停机：Esc 关闭屏幕 → 保存并退回 → 杀进程组。

    Gradle daemon 模式下 MC 进程可能在别的进程组，killpg 杀不到，
    最后兜底按 MCP 端口杀监听者（lsof）。
    """
    try:
        if proc.poll() is None:
            env.client.call("mc_key", {"key": "key.keyboard.escape"})
            time.sleep(1)
            env.client.call("mc_button", {"button": "保存并退回"})
            time.sleep(6)
    except Exception:
        pass
    if proc.poll() is None:
        try:
            os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
        except (ProcessLookupError, PermissionError):
            proc.terminate()
        try:
            proc.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
            proc.wait()
    # 兜底：MCP 端口上的监听者（可能是 daemon 派生的 MC，跨进程组）
    for _ in range(10):
        if port_free(PORT):
            return
        time.sleep(2)
    try:
        out = subprocess.run(["lsof", "-t", "-iTCP:" + str(PORT)],
                             capture_output=True, text=True, timeout=10)
        for pid in out.stdout.split():
            try:
                os.kill(int(pid), signal.SIGKILL)
            except (ProcessLookupError, ValueError, PermissionError):
                pass
    except Exception:
        pass
    time.sleep(3)


def enter_world(version: str, tally: Tally) -> bool:
    """进世界：归位主菜单 → 单人游戏 → 选第一行 → 进入世界。"""
    sh = MC_SCRIPTS / "enter_world.sh"
    if not sh.is_file():
        tally.bad("enter_world 前置", f"缺少 {sh}")
        return False
    r = subprocess.run(["bash", str(sh), str(PORT)],
                       capture_output=True, text=True, timeout=300)
    if r.returncode != 0:
        tail = "\n".join(r.stdout.splitlines()[-6:]) + r.stderr[-300:]
        tally.bad("进世界", tail.strip())
        return False
    tally.ok("进世界", "overworld")
    return True


def safe_setup(env, version: str = "26.2") -> None:
    """世界安全设置：生存模式/白天/锁夜/防爆/禁刷怪。

    survival 必须：26.2 的 tryConsumeKeys 对 instabuild 玩家豁免钥匙消耗，
    创造模式下 T5 钥匙断言必然失败。
    26.x 的 gamerule 规则名重命名（doDaylightCycle→advanceTime 等）。
    """
    new = version.startswith("26.")
    rules = [
        "/gamemode survival @s",
        "/time set day",
        f"/gamerule {'advanceTime' if new else 'doDaylightCycle'} false",
        f"/gamerule {'mobGriefing' if new else 'mobGriefing'} false",
        f"/gamerule {'spawnMobs' if new else 'doMobSpawning'} false",
    ]
    for cmd in rules:
        try:
            env.exec_cmd(cmd)
        except Exception:
            pass


# ---------------------------------------------------------------- report
def write_report(version: str, tally: Tally, cases: list, out_root: Path = OUT_ROOT):
    d = out_root / version
    d.mkdir(parents=True, exist_ok=True)
    md = [f"# {version} 全量检查报告",
          "",
          f"- 时间: {time.strftime('%Y-%m-%d %H:%M:%S')}",
          f"- 结果: {tally.pass_} PASS / {tally.fail} FAIL / {tally.warn} WARN",
          "",
          "## 用例明细", ""]
    for verdict, name, detail in tally.rows:
        md.append(f"- **{verdict}** {name}" + (f" — {detail}" if detail else ""))
    md.append("")
    (d / "report.md").write_text("\n".join(md), encoding="utf-8")
    json.dump({"platform": version, "cases": [
        {"verdict": v, "name": n, "detail": dt} for v, n, dt in tally.rows
    ]}, (d / "report.json").open("w", encoding="utf-8"), ensure_ascii=False, indent=2)
    return d


def write_summary(results: list, out_root: Path = OUT_ROOT):
    names = [v for v, _ in results]
    if not names:
        return
    md = ["# FULLCHECK 汇总", ""]
    verdicts = {v: ("FAIL" if t.fail else ("WARN" if t.warn else "PASS"))
                for v, t in results}
    md.append("| 平台 | 判定 | PASS | FAIL | WARN |")
    md.append("|---|---|---|---|---|")
    for v, t in results:
        md.append(f"| {v} | {verdicts[v]} | {t.pass_} | {t.fail} | {t.warn} |")
    md.append("")
    overall = "FAIL" if any(t.fail for _, t in results) else "PASS"
    md.append(f"**总体: {overall}**")
    (out_root / "SUMMARY.md").write_text("\n".join(md), encoding="utf-8")
    json.dump([{"platform": v, "verdict": verdicts[v],
                "pass": t.pass_, "fail": t.fail, "warn": t.warn}
               for v, t in results],
              (out_root / "SUMMARY.json").open("w", encoding="utf-8"),
              ensure_ascii=False, indent=2)
