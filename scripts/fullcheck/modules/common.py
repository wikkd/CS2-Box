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
import shutil
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


def _clean_last_mp_ip(version: str) -> None:
    """删除 options.txt 的 lastMpIp 残留，防止直连输入框预填旧地址。

    1.21.x 的 DirectJoinServerScreen 打开时用 options.lastMpIp 预填输入框，
    上次失败的拼接地址会残留在输入框里导致 Unknown host。
    """
    opts = RUNS_DIR(version) / "options.txt"
    if not opts.is_file():
        return
    lines = [ln for ln in opts.read_text(encoding="utf-8").splitlines()
             if not ln.startswith("lastMpIp:")]
    opts.write_text("\n".join(lines) + "\n", encoding="utf-8")


def box_config_dirs(version: str) -> list:
    """客户端+服务器两侧 config/csbox 目录。

    独立服务器模式下 /csbox reload 读服务器侧 CONFIGDIR，模块写入的
    fct_*.json 必须落到服务器侧才生效；客户端侧由 BoxFileWatcher
    自动热载（供 GUI 显示）。单机模式（26.x）两侧同目录，无副作用。
    """
    base = RUNS_DIR(version).parent
    return [base / "client" / "config" / "csbox",
            base / "server" / "config" / "csbox"]


def write_box_config(version: str, filename: str, content: str) -> None:
    """把箱子配置写入两侧 config/csbox/<filename>。"""
    for d in box_config_dirs(version):
        d.mkdir(parents=True, exist_ok=True)
        (d / filename).write_text(content, encoding="utf-8")


def remove_box_config(version: str, filename: str) -> None:
    """从两侧 config/csbox 删除配置文件（missing_ok）。"""
    for d in box_config_dirs(version):
        (d / filename).unlink(missing_ok=True)


def _ensure_default_box_config(version: str) -> None:
    """确保 config/csbox/weapon_supply_box.json 存在（动态箱子定义）。

    BoxRegistry 纯动态（无内置箱子）：没有 JSON 就没有箱子物品，
    /give csgobox:weapon_supply_box 会报“未知的物品”。模板来自仓库
    runs/client/config/csbox/（含 _tutorial 文档对象，模组会忽略）。
    """
    template = REPO / "runs" / "client" / "config" / "csbox" / "weapon_supply_box.json"
    if not template.is_file():
        print(f"    [warn] 缺少默认箱子模板: {template}")
        return
    for side in ("client", "server"):
        boxes = RUNS_DIR(version).parent / side / "config" / "csbox"
        boxes.mkdir(parents=True, exist_ok=True)
        target = boxes / "weapon_supply_box.json"
        if not target.is_file():
            target.write_text(template.read_text(encoding="utf-8"),
                              encoding="utf-8")


def launch_client(version: str, log_path: Path) -> subprocess.Popen:
    """后台启动 gradle runClient，等 MCP 端口就绪。

    start_new_session=True：客户端进程独立进程组，stop_client 的 killpg
    不会误杀编排器自身（历史教训：曾整组击杀导致报告丢失）。
    端口被旧客户端占用时拒绝启动（防用例跑错客户端）。
    """
    _clean_last_mp_ip(version)
    _ensure_default_box_config(version)
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
    if not wait_port(PORT, 600):
        return proc  # 由调用方判定失败并收割日志
    time.sleep(3)
    return proc


def stop_client(proc: subprocess.Popen, env, timeout: float = 40) -> None:
    """尽力优雅停机：Esc 关闭屏幕 → 保存并退回 → 杀进程组。

    Gradle daemon 模式下 MC 进程可能在别的进程组，killpg 杀不到，
    最后兜底按 MCP 端口杀监听者（lsof）。整体防御：任何异常不冒泡
    （曾导致报告丢失）。
    """
    try:
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
    except Exception:
        pass


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


def click_open_retry(env, timeout: float = 5, settle: float = 1.5) -> bool:
    """点击开启按钮并重试（26.2 需等 itemGroup 同步解锁 openClicked）。

    settle：CsboxScreen 打开后需等 PacketSyncBoxItems 同步完成才可点击，
    同步前点击被 `!openClicked` 静默吞掉。
    """
    time.sleep(settle)
    end = time.time() + timeout
    while time.time() < end:
        try:
            env.client.call("mc_click", {"x": OPEN_BTN[0], "y": OPEN_BTN[1]})
        except Exception:
            pass
        time.sleep(0.6)
        if env.screen_class() in ("CsboxProgressScreen", "CsLookItemScreen"):
            return True
    return env.screen_class() in ("CsboxProgressScreen", "CsLookItemScreen")


SERVER_PORT = 25565


def launch_server(version: str, log_path: Path) -> subprocess.Popen:
    """后台启动 gradle runServer（本地集成服务器，客户端直连进世界）。

    服务器模式绕开 1.21.x 世界选择/版本降级备份确认等 UI 难题：
    世界由服务器自动生成，客户端直连即进。
    """
    if not port_free(SERVER_PORT):
        raise RuntimeError(f"服务器端口 {SERVER_PORT} 被占用（残留服务器需先清理）")
    log_path.parent.mkdir(parents=True, exist_ok=True)
    _ensure_default_box_config(version)
    srv_dir = RUNS_DIR(version).parent / "server"
    srv_dir.mkdir(parents=True, exist_ok=True)
    eula = srv_dir / "eula.txt"
    eula.write_text("eula=true\n", encoding="utf-8")
    # 独立服务器玩家默认无 OP：gamemode/time/gamerule/clear/give 等
    # 权限 2 命令会被命令树 requires 拦截（报“未知或不完整的命令”）。
    # 单机集成服务器（26.x）玩家自动 OP，无此问题。Dev 为离线模式固定 UUID。
    ops = srv_dir / "ops.json"
    ops.write_text(json.dumps([
        {"uuid": "380df991-f603-344c-a090-369bad2a924a", "name": "Dev",
         "level": 4, "bypassesPlayerLimit": False}
    ], indent=2), encoding="utf-8")
    # NeoForge 连接校验要求服务器/客户端 mod 列表一致（testhelper 声明了
    # 不参与校验，可跳过）：把客户端 mods 的 jar 同步到服务器侧。
    # 1.21.1 的 TACZ（compileOnly 依赖，检视视口集成）即因此需同步。
    srv_mods = srv_dir / "mods"
    srv_mods.mkdir(parents=True, exist_ok=True)
    client_mods = RUNS_DIR(version).parent / "client" / "mods"
    if client_mods.is_dir():
        for jar in client_mods.glob("*.jar"):
            if jar.name.startswith("testhelper"):
                continue
            dst = srv_mods / jar.name
            if not dst.exists():
                shutil.copy2(jar, dst)
    gradle = REPO / "gradlew"
    cmd = [str(gradle), f":{PLATFORMS[version]['module']}:runServer",
           f"-Pactive_versions={version}"]
    f = open(log_path, "w", encoding="utf-8")
    proc = subprocess.Popen(cmd, cwd=REPO, stdout=f, stderr=subprocess.STDOUT,
                            start_new_session=True)
    if not wait_port(SERVER_PORT, 600):
        return proc
    time.sleep(3)
    return proc


def stop_server(proc: subprocess.Popen) -> None:
    if proc is None or proc.poll() is not None:
        return
    try:
        os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
    except (ProcessLookupError, PermissionError):
        proc.terminate()
    try:
        proc.wait(timeout=20)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
        except (ProcessLookupError, PermissionError):
            pass
        proc.wait()


def _click_widget_by_text(env, text: str) -> bool:
    """按文本找 widget 并点其中心（widgets 坐标为 fb）。"""
    try:
        d = env.client.call_full("mc_widgets", {}) or {}
    except Exception:
        return False
    for w in d.get("widgets", []):
        if text in (w.get("text") or ""):
            try:
                env.client.call("mc_click", {
                    "x": w["x"] + w["w"] / 2, "y": w["y"] + w["h"] / 2})
            except Exception:
                pass
            return True
    return False


def _enter_direct_ip(env) -> bool:
    """DirectJoinServerScreen 上：点击输入框聚焦 → Backspace 清空 → 输入 → 加入。

    1.21.x 输入框打开时未聚焦（init() 无 setFocused）且预填 lastMpIp 残留，
    直接注入字符会被 EditBox 拒绝；必须先点击输入框（EditBox 点击时 setFocused），
    再用 keyPressed 路径注入 Backspace 清空残留，最后输入 127.0.0.1。
    """
    box = None
    try:
        d = env.client.call_full("mc_widgets", {"filter": "EditBox"}) or {}
        for w in d.get("widgets", []):
            if w.get("cls") == "EditBox":
                box = w
                break
    except Exception:
        pass
    if box:
        x = int(box["x"] + box["w"] - 6)   # 输入框右端，光标落在末尾
        y = int(box["y"] + box["h"] / 2)
    else:
        x, y = 950, 126  # 1708x960 fb 兜底：输入框 (width/2-100, 116, 200, 20)
    try:
        env.client.call("mc_click", {"x": x, "y": y})
    except Exception:
        pass
    time.sleep(0.4)
    for _ in range(64):  # 清空 lastMpIp 预填残留（输入框 maxLength=128）
        try:
            env.client.call("mc_key", {"key": "key.keyboard.backspace"})
        except Exception:
            pass
    time.sleep(0.2)
    for ch in "127.0.0.1":
        try:
            env.client.call("mc_key", {"key": f"character.{ch}"})
        except Exception:
            pass
    time.sleep(0.5)
    return _click_widget_by_text(env, "加入服务器")


def connect_server(env, tally: Tally, timeout: float = 180) -> bool:
    """UI 直连：主菜单 → 多人游戏 → 安全警告继续 → 直接连接 → 输入 → 加入。"""
    end = time.time() + timeout
    last = ""
    ip_entered = False    # 同一输入框会话只输入一次，防重复追加拼接地址
    disconnects = 0       # 断开重试计数
    while time.time() < end:
        st = env.status()
        cls = st.get("screen_class") or ""
        dim = st.get("dimension") or ""
        if not cls and dim == "minecraft:overworld":
            tally.ok("直连进世界", "overworld")
            return True
        if cls == "TitleScreen":
            _click_widget_by_text(env, "多人游戏")
            ip_entered = False
        elif cls == "SafetyScreen":
            _click_widget_by_text(env, "继续")
        elif cls == "JoinMultiplayerScreen":
            _click_widget_by_text(env, "直接连接")
            ip_entered = False
        elif cls == "DirectJoinServerScreen":
            if not ip_entered:
                _enter_direct_ip(env)
                ip_entered = True
        elif cls == "DisconnectedScreen":
            disconnects += 1
            if disconnects >= 3:
                tally.bad("直连进世界",
                          f"DisconnectedScreen x{disconnects}（服务器未就绪或地址错误）")
                return False
            print(f"    [connect] DisconnectedScreen，Esc 返回重试 ({disconnects}/3)",
                  flush=True)
            try:
                env.client.call("mc_key", {"key": "key.keyboard.escape"})
            except Exception:
                pass
            ip_entered = False
        elif cls in ("ConnectingScreen", "LoadingScreen", "ProgressScreen",
                     "GenericMessageScreen", "LoginScreen"):
            pass  # 连接中，等待
        elif cls != last:
            print(f"    [connect] screen={cls} dim={dim}", flush=True)
        last = cls
        time.sleep(2)
    tally.bad("直连进世界", f"timeout, screen={env.screen_class()}")
    return False
