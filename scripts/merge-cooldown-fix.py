#!/usr/bin/env python3
"""Merge version-neutral fixes into every platform module.

The 9 platform modules are NOT pure copies: v1_21_3+ and v26_2 carry their own
API adaptations (e.g. BuiltInRegistries.ITEM.get() -> Optional<Holder.Reference>,
spawnAtLocation(ServerLevel,...), lookup()). Blindly mirroring v1_21_1 /
v26_1_2 over them destroys those adaptations. This script instead applies
small, version-neutral text edits (cooldown map concurrency + periodic prune)
to each module's own copy.

Usage: python3 scripts/merge-cooldown-fix.py [--check]
  --check  only report which files would change / already have the change.

Edits applied per module (PacketCsgoProgress.java):
  1. import java.util.HashMap;  ->  import java.util.concurrent.ConcurrentHashMap;
  2. new HashMap<>()            ->  new ConcurrentHashMap<>()
  3. insert public static void tickOpenBlockMap(long) after blockFurtherOpensStatic()
And per module (ModEvents.java):
  4. insert ServerTickEvent import after LivingDeathEvent import
  5. insert serverTick(ServerTickEvent.Pre) handler before closing brace
"""
import os
import re
import sys

CHECK = "--check" in sys.argv

MODULES = [
    "v1_21_1", "v1_21_3", "v1_21_4", "v1_21_5", "v1_21_8",
    "v1_21_10", "v1_21_11", "v26_1_2", "v26_2",
]

PKG_HINT = {
    "v1_21_1": "v1_21_1", "v1_21_3": "v1_21_3", "v1_21_4": "v1_21_4",
    "v1_21_5": "v1_21_5", "v1_21_8": "v1_21_8", "v1_21_10": "v1_21_10",
    "v1_21_11": "v1_21_11", "v26_1_2": "v26_1_2", "v26_2": "v26_2",
}

TICK_METHOD = """
    /**
     * Removes expired cooldown entries so the map does not grow without bound.
     * Invoked periodically from the server tick loop ({@code ModEvents#serverTick}).
     */
    public static void tickOpenBlockMap(long nowGameTime) {
        OPEN_BLOCKED_UNTIL_TICK.entrySet().removeIf(entry -> nowGameTime >= entry.getValue());
    }
"""

TICK_HANDLER_TMPL = """
    /**
     * Periodically prunes expired open-cooldown entries from
     * {@link %PKG%.packet.PacketCsgoProgress#tickOpenBlockMap(long)} so the map stays bounded.
     */
    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Pre event) {
        if (event.getServer().getTickCount() % 100 == 0) {
            %PKG%.packet.PacketCsgoProgress.tickOpenBlockMap(event.getServer().overworld().getGameTime());
        }
    }
"""


def patch_packet(path):
    """Return (changed_bool, report_lines)."""
    src = open(path, encoding="utf-8").read()
    orig = src
    report = []

    # 1. import swap
    if "import java.util.concurrent.ConcurrentHashMap;" in src:
        report.append("  [skip] ConcurrentHashMap import already present")
    elif "import java.util.HashMap;" in src:
        src = src.replace("import java.util.HashMap;", "import java.util.concurrent.ConcurrentHashMap;", 1)
        report.append("  [edit] HashMap import -> ConcurrentHashMap")
    else:
        report.append("  [WARN] no HashMap import found")

    # 2. constructor swap
    if "new ConcurrentHashMap<>()" in src:
        report.append("  [skip] ConcurrentHashMap constructor already present")
    elif "new HashMap<>()" in src:
        src = src.replace("new HashMap<>()", "new ConcurrentHashMap<>()", 1)
        report.append("  [edit] new HashMap<>() -> new ConcurrentHashMap<>()")
    else:
        report.append("  [WARN] no new HashMap<>() found")

    # 3. tickOpenBlockMap method after blockFurtherOpensStatic body
    if "tickOpenBlockMap(long nowGameTime)" in src:
        report.append("  [skip] tickOpenBlockMap already present")
    else:
        # find end of blockFurtherOpensStatic: the closing brace of the method
        m = re.search(r"static void blockFurtherOpensStatic\(Player player\) \{"
                      r"[^}]*?OPEN_BLOCKED_UNTIL_TICK\.put\([^}]*?\);\n(    \})", src, re.S)
        if m:
            insert_at = m.end(1)
            src = src[:insert_at] + TICK_METHOD + src[insert_at:]
            report.append("  [edit] inserted tickOpenBlockMap after blockFurtherOpensStatic")
        else:
            report.append("  [WARN] blockFurtherOpensStatic anchor not found")

    changed = src != orig
    if changed and not CHECK:
        open(path, "w", encoding="utf-8").write(src)
    return changed, report


def patch_mod_events(path, pkg):
    src = open(path, encoding="utf-8").read()
    orig = src
    report = []

    # 4. ServerTickEvent import
    if "import net.neoforged.neoforge.event.tick.ServerTickEvent;" in src:
        report.append("  [skip] ServerTickEvent import already present")
    elif "import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;" in src:
        src = src.replace(
            "import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;",
            "import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;\n"
            "import net.neoforged.neoforge.event.tick.ServerTickEvent;", 1)
        report.append("  [edit] added ServerTickEvent import")
    else:
        report.append("  [WARN] LivingDeathEvent import anchor not found")

    # 5. serverTick handler before final closing brace (last '}' in file)
    if "public static void serverTick(ServerTickEvent.Pre event)" in src:
        report.append("  [skip] serverTick handler already present")
    else:
        handler = TICK_HANDLER_TMPL.replace("%PKG%", f"com.reclizer.csgobox.{pkg}")
        # insert before the final '}' that closes the class
        idx = src.rstrip().rfind("\n}")
        if idx != -1:
            src = src[:idx] + handler + src[idx:]
            report.append("  [edit] inserted serverTick handler")
        else:
            report.append("  [WARN] class closing brace not found")

    changed = src != orig
    if changed and not CHECK:
        open(path, "w", encoding="utf-8").write(src)
    return changed, report


def main():
    any_changed = False
    for mod in MODULES:
        pkg = PKG_HINT[mod]
        packet = os.path.join(mod, "src/main/java/com/reclizer/csgobox",
                              pkg, "packet/PacketCsgoProgress.java")
        events = os.path.join(mod, "src/main/java/com/reclizer/csgobox",
                              pkg, "event/ModEvents.java")
        print(f"== {mod}")
        for path, fn in [(packet, patch_packet), (events, lambda p: patch_mod_events(p, pkg))]:
            if not os.path.isfile(path):
                print(f"  [MISSING] {path}")
                continue
            changed, report = fn(path)
            for line in report:
                print(line)
            any_changed |= changed
    if CHECK:
        print("\n(check mode, no files written)" if any_changed else "\nall modules already up to date")
    return 0 if not CHECK or not any_changed else 1


if __name__ == "__main__":
    sys.exit(main())
