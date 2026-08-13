#!/usr/bin/env python3
"""生成「武库商小屋」结构模板 NBT（纯标准库，无第三方依赖）。

小屋是军火商（arms_dealer）的野外据点：
- 内置武库拆解台（柜台展示）+ 军火商村民（arms_dealer 职业 Lv.1，带名字牌）
- 商店宝箱（LootTable 指向 csgobox:chests/arms_dealer_hut）
- 深板岩砖地基 + 红砖墙 + 深板岩石砖房顶 + 深板岩柜台 + 铁栏杆窗 + 灯笼/火把 + 一张床（村民睡觉）

模板格式与 1.21.1 / 26.x 原版一致（已在两版本 vanilla jar 中核验）：
- 根复合标签名为空；size 为 List[Int]；blocks 覆盖全部坐标（含 air）
- 实体 nbt 用 VillagerData.profession 直接写职业（两版本同格式）
- 结构放置时游戏会移除 UUID 并重新生成，故模板不带 UUID
- 不带 DataVersion：两版本均按原样加载，不触发 DataFixer

用法：python3 scripts/gen-arms-dealer-hut.py
输出：common/src/main/resources/data/csgobox/structure/arms_dealer_hut.nbt
"""
import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "common/src/main/resources/data/csgobox/structure/arms_dealer_hut.nbt"

SIZE = (9, 6, 8)  # x, y, z
AIR = "minecraft:air"

# ---------------------------------------------------------------------------
# 极简 NBT writer（big-endian，仅本脚本所需类型）
# ---------------------------------------------------------------------------

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


def _name(name: str) -> bytes:
    b = name.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def tag(t: int, payload: bytes) -> bytes:
    return bytes([t]) + payload


def pl_byte(v: int) -> bytes:
    return struct.pack(">b", v)


def pl_int(v: int) -> bytes:
    return struct.pack(">i", v)


def pl_float(v: float) -> bytes:
    return struct.pack(">f", v)


def pl_double(v: float) -> bytes:
    return struct.pack(">d", v)


def pl_string(s: str) -> bytes:
    return _name(s)


def nbt_byte(v: int) -> bytes:
    return tag(BYTE, pl_byte(v))


def nbt_int(v: int) -> bytes:
    return tag(INT, pl_int(v))


def nbt_float(v: float) -> bytes:
    return tag(FLOAT, pl_float(v))


def nbt_double(v: float) -> bytes:
    return tag(DOUBLE, pl_double(v))


def nbt_string(s: str) -> bytes:
    return tag(STRING, pl_string(s))


def nbt_int_array(values: list[int]) -> bytes:
    return tag(INT_ARRAY, pl_int(len(values)) + b"".join(pl_int(v) for v in values))


def nbt_list(elem_type: int, payloads: list[bytes]) -> bytes:
    """NBT 列表：元素不带类型字节，类型只在列表头声明一次。"""
    return tag(LIST, bytes([elem_type]) + pl_int(len(payloads)) + b"".join(payloads))


def compound_payload(entries: list[tuple[str, bytes]]) -> bytes:
    body = b"".join(bytes([payload[0]]) + _name(name) + payload[1:] for name, payload in entries)
    return body + b"\x00"


def nbt_compound(entries: list[tuple[str, bytes]]) -> bytes:
    return tag(COMPOUND, compound_payload(entries))


# 便捷构造：带标签名的复合标签项
def item(name: str, payload: bytes) -> tuple[str, bytes]:
    return name, payload


def root_compound(entries: list[tuple[str, bytes]]) -> bytes:
    """根标签：名字为空字符串（与原版模板一致）。"""
    body = b"".join(bytes([payload[0]]) + _name(name) + payload[1:] for name, payload in entries)
    return bytes([COMPOUND]) + _name("") + body + b"\x00"


# ---------------------------------------------------------------------------
# 小屋布局
# ---------------------------------------------------------------------------

def parse_state(spec: str) -> tuple[str, dict[str, str]]:
    """'minecraft:xxx[prop=val,...]' -> (Name, Properties)。"""
    if "[" not in spec:
        return spec, {}
    name, props = spec.split("[", 1)
    props = props.rstrip("]")
    return name, dict(p.split("=", 1) for p in props.split(",") if p)


def build_hut() -> dict[tuple[int, int, int], str]:
    """返回 {(x, y, z): state_spec}，未列出的位置为空气。"""
    X, Y, Z = SIZE
    cells: dict[tuple[int, int, int], str] = {}

    def set_box(x0, x1, y0, y1, z0, z1, spec):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    cells[(x, y, z)] = spec

    # 地板：深板岩砖
    set_box(0, X - 1, 0, 0, 0, Z - 1, "minecraft:deepslate_bricks")
    # 屋顶：深板岩石砖两层
    set_box(0, X - 1, 4, 4, 0, Z - 1, "minecraft:deepslate_tiles")
    set_box(0, X - 1, 5, 5, 0, Z - 1, "minecraft:deepslate_tiles")

    # 墙体（y=1..3）：默认红砖
    for y in range(1, 4):
        for x in range(X):
            cells.setdefault((x, y, 0), "minecraft:bricks")  # 前墙 z=0
            cells.setdefault((x, y, Z - 1), "minecraft:bricks")  # 后墙
        for z in range(Z):
            cells.setdefault((0, y, z), "minecraft:bricks")  # 左墙 x=0
            cells.setdefault((X - 1, y, z), "minecraft:bricks")  # 右墙 x=8

    # 四角 + 门柱：红砖柱（1 格门，两侧 2×2 铁栏杆窗）
    for x, z in ((0, 0), (X - 1, 0), (0, Z - 1), (X - 1, Z - 1)):
        set_box(x, x, 1, 3, z, z, "minecraft:bricks")
    set_box(3, 3, 1, 3, 0, 0, "minecraft:bricks")
    set_box(5, 5, 1, 3, 0, 0, "minecraft:bricks")

    # 门洞：x=4, y=1..2（空气）；门上方 y=3 恢复木板
    set_box(4, 4, 1, 2, 0, 0, AIR)
    set_box(4, 4, 3, 3, 0, 0, "minecraft:bricks")

    # 窗户（铁栏杆，连接状态由算法自动计算）
    window_cells = [
        (1, 1, 0), (1, 2, 0), (2, 1, 0), (2, 2, 0),          # 前墙左窗 2×2
        (6, 1, 0), (6, 2, 0), (7, 1, 0), (7, 2, 0),          # 前墙右窗 2×2
        (3, 2, 7), (4, 2, 7), (5, 2, 7),                     # 后墙横窗
        (0, 1, 3), (0, 1, 4), (0, 2, 3), (0, 2, 4),          # 左墙 2×2
        (8, 1, 3), (8, 1, 4), (8, 2, 3), (8, 2, 4),          # 右墙 2×2
    ]
    for c in window_cells:
        cells[c] = "minecraft:iron_bars"

    # 室内
    set_box(2, 6, 1, 1, 6, 6, "minecraft:cobbled_deepslate")       # 柜台底座
    set_box(2, 6, 2, 2, 6, 6, "minecraft:deepslate_bricks")        # 柜台台面
    cells[(4, 2, 6)] = "csgobox:armory_recycler[facing=north]"     # 武库拆解台（朝向店内）
    cells[(1, 1, 6)] = "minecraft:chest[facing=north]"             # 商店宝箱（靠后墙）
    cells[(6, 1, 5)] = "minecraft:crafting_table"                  # 工作台
    cells[(1, 1, 2)] = "minecraft:red_bed[facing=north,part=head]"  # 床
    cells[(1, 1, 3)] = "minecraft:red_bed[facing=north,part=foot]"
    cells[(6, 1, 3)] = "minecraft:torch"
    cells[(2, 1, 4)] = "minecraft:torch"
    cells[(3, 3, 2)] = "minecraft:lantern[hanging=true,waterlogged=false]"
    cells[(5, 3, 5)] = "minecraft:lantern[hanging=true,waterlogged=false]"

    # 铁栏杆连接状态：仅与相邻铁栏杆相连
    bars = {c for c, s in cells.items() if s == "minecraft:iron_bars"}
    for c in bars:
        x, y, z = c
        flags = {
            "north": (x, y, z - 1) in bars,
            "east": (x + 1, y, z) in bars,
            "south": (x, y, z + 1) in bars,
            "west": (x - 1, y, z) in bars,
        }
        props = ",".join(f"{k}={str(v).lower()}" for k, v in flags.items())
        cells[c] = f"minecraft:iron_bars[{props}]"

    return cells


def villager_nbt() -> bytes:
    """军火商村民：职业直接写进 VillagerData（1.21.1 与 26.x 同格式）。

    26.x 注意：原版新增 ResetProfession 行为——村民若带职业、无 job_site
    记忆、Xp=0 且等级<=1，几秒内会被“解雇”重置为 minecraft:none（实测原版
    farmer 同样触发）。模板村民没有世界坐标的 job_site 记忆（旋转后位置会变），
    因此给 Xp:1 免疫解雇，保证武库拆解台认领前职业稳定。
    """
    return nbt_compound([
        item("id", nbt_string("minecraft:villager")),
        item("Age", nbt_int(0)),
        item("PersistenceRequired", nbt_byte(1)),
        item("Health", nbt_float(20.0)),
        item("CanPickUpLoot", nbt_byte(1)),
        item("Xp", nbt_int(1)),
        item("CustomName", nbt_string('{"translate":"entity.csgobox.arms_dealer"}')),
        item("VillagerData", nbt_compound([
            item("profession", nbt_string("csgobox:arms_dealer")),
            item("level", nbt_int(1)),
            item("type", nbt_string("minecraft:plains")),
        ])),
        item("VillagerDataFinalized", nbt_byte(1)),
        item("Pos", nbt_list(DOUBLE, [pl_double(4.5), pl_double(1.0), pl_double(4.5)])),
        item("Rotation", nbt_list(FLOAT, [pl_float(180.0), pl_float(0.0)])),
    ])


def chest_nbt() -> bytes:
    """宝箱方块实体 NBT：LootTable 字段两版本一致。"""
    return nbt_compound([
        item("id", nbt_string("minecraft:chest")),
        item("LootTable", nbt_string("csgobox:chests/arms_dealer_hut")),
    ])


def build_nbt(cells: dict[tuple[int, int, int], str]) -> bytes:
    X, Y, Z = SIZE

    # palette：按首次出现顺序去重（air 排最前，与原版惯例一致）
    palette: list[tuple[str, dict[str, str]]] = []
    index: dict[str, int] = {}
    for y in range(Y):
        for z in range(Z):
            for x in range(X):
                spec = cells.get((x, y, z), AIR)
                if spec not in index:
                    index[spec] = len(palette)
                    palette.append(parse_state(spec))

    blocks = []
    for y in range(Y):
        for z in range(Z):
            for x in range(X):
                spec = cells.get((x, y, z), AIR)
                blocks.append(compound_payload([
                    item("pos", nbt_list(INT, [pl_int(x), pl_int(y), pl_int(z)])),
                    item("state", nbt_int(index[spec])),
                ]))

    palette_tags = []
    for name, props in palette:
        entries = [item("Name", nbt_string(name))]
        if props:
            entries.append(item("Properties", nbt_compound(
                [item(k, nbt_string(v)) for k, v in props.items()])))
        palette_tags.append(compound_payload(entries))

    entity = compound_payload([
        item("pos", nbt_list(DOUBLE, [pl_double(4.5), pl_double(1.0), pl_double(4.5)])),
        item("blockPos", nbt_list(INT, [pl_int(4), pl_int(1), pl_int(4)])),
        item("nbt", villager_nbt()),
    ])

    return root_compound([
        item("size", nbt_list(INT, [pl_int(X), pl_int(Y), pl_int(Z)])),
        item("entities", nbt_list(COMPOUND, [entity])),
        item("blocks", nbt_list(COMPOUND, blocks)),
        item("palette", nbt_list(COMPOUND, palette_tags)),
    ])


def main() -> None:
    cells = build_hut()
    payload = build_nbt(cells)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(gzip.compress(payload, compresslevel=9))
    n_blocks = len(cells) + sum(
        1 for x in range(SIZE[0]) for y in range(SIZE[1]) for z in range(SIZE[2])
        if (x, y, z) not in cells)
    print(f"size={SIZE} solid_blocks={len(cells)} air={n_blocks}")
    print(f"palette_entries={len(set(cells.values())) + 1}")
    print(f"written: {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
