#!/usr/bin/env python3
"""导入结构方块导出的武库商小屋结构（用户手建）为 mod 结构模板。

从结构方块 Export 的 NBT（如 runs/client/saves/<世界>/generated/minecraft/
structure/111.nbt）生成 common/src/main/resources/data/csgobox/structure/
arms_dealer_hut.nbt：

- 保留 size/blocks/palette 原样（用户手建布局）
- 去掉 DataVersion：1.21.1 与 26.x 均按原样加载，不触发 DataFixer
- 实体 NBT 规范化（只保留稳定字段，与 gen-arms-dealer-hut.py 的 villager_nbt
  字段集合一致）：
  * 删除 Brain 记忆（含绝对世界坐标，放置后指向错误位置）
  * 删除写死的 Offers（交易由 mod trade_set 生成）
  * 删除 UUID（结构放置时游戏重新生成，固定 UUID 会导致多实例冲突）
  * 删除动态/环境字段（LastRestock/LastGossipDecay/RestocksToday/attributes
    随机修饰/neoforge:spawn_type 等）
  * 补 Xp=1（26.x 会"解雇"Xp=0 的带职业村民）、PersistenceRequired=1、
    CustomName 名字牌

用法：python3 scripts/import-arms-dealer-hut.py [源.nbt]
输出：common/src/main/resources/data/csgobox/structure/arms_dealer_hut.nbt
"""
import gzip
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "common/src/main/resources/data/csgobox/structure/arms_dealer_hut.nbt"
DEFAULT_SRC = ROOT / "v26_2/runs/client/saves/新的世界/generated/minecraft/structure/111.nbt"

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Tag:
    """带 NBT 类型标签的节点，保证读写保真（0 不会在 byte/int 间漂移）。"""

    __slots__ = ("t", "v")

    def __init__(self, t: int, v):
        self.t = t
        self.v = v


def _read(f, n: int) -> bytes:
    b = f.read(n)
    if len(b) != n:
        raise EOFError("NBT 截断")
    return b


def _read_payload(f, t: int) -> Tag:
    if t == END:
        raise ValueError("意外 END")
    if t == BYTE:
        return Tag(BYTE, struct.unpack(">b", _read(f, 1))[0])
    if t == SHORT:
        return Tag(SHORT, struct.unpack(">h", _read(f, 2))[0])
    if t == INT:
        return Tag(INT, struct.unpack(">i", _read(f, 4))[0])
    if t == LONG:
        return Tag(LONG, struct.unpack(">q", _read(f, 8))[0])
    if t == FLOAT:
        return Tag(FLOAT, struct.unpack(">f", _read(f, 4))[0])
    if t == DOUBLE:
        return Tag(DOUBLE, struct.unpack(">d", _read(f, 8))[0])
    if t == BYTE_ARRAY:
        n = struct.unpack(">i", _read(f, 4))[0]
        return Tag(BYTE_ARRAY, list(_read(f, n)))
    if t == STRING:
        n = struct.unpack(">H", _read(f, 2))[0]
        return Tag(STRING, _read(f, n).decode("utf-8"))
    if t == LIST:
        et = struct.unpack("b", _read(f, 1))[0]
        n = struct.unpack(">i", _read(f, 4))[0]
        return Tag(LIST, (et, [_read_payload(f, et) for _ in range(n)]))
    if t == COMPOUND:
        d = {}
        while True:
            tb = _read(f, 1)[0]
            if tb == END:
                break
            nl = struct.unpack(">H", _read(f, 2))[0]
            name = _read(f, nl).decode("utf-8")
            d[name] = _read_payload(f, tb)
        return Tag(COMPOUND, d)
    if t == INT_ARRAY:
        n = struct.unpack(">i", _read(f, 4))[0]
        return Tag(INT_ARRAY, list(struct.unpack(">%di" % n, _read(f, 4 * n))))
    if t == LONG_ARRAY:
        n = struct.unpack(">i", _read(f, 4))[0]
        return Tag(LONG_ARRAY, list(struct.unpack(">%dq" % n, _read(f, 8 * n))))
    raise ValueError("未知 NBT 类型 %d" % t)


def read_nbt(path: Path) -> Tag:
    with gzip.open(path, "rb") as f:
        tb = f.read(1)[0]
        nl = struct.unpack(">H", f.read(2))[0]
        f.read(nl)  # 根名（空字符串）
        return _read_payload(f, tb)


def _w_str(s: str) -> bytes:
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _w_payload(tag: Tag) -> bytes:
    t, v = tag.t, tag.v
    if t == BYTE:
        return struct.pack(">b", v)
    if t == SHORT:
        return struct.pack(">h", v)
    if t == INT:
        return struct.pack(">i", v)
    if t == LONG:
        return struct.pack(">q", v)
    if t == FLOAT:
        return struct.pack(">f", v)
    if t == DOUBLE:
        return struct.pack(">d", v)
    if t == BYTE_ARRAY:
        return struct.pack(">i", len(v)) + bytes(v)
    if t == STRING:
        return _w_str(v)
    if t == LIST:
        et, items = v
        return bytes([et]) + struct.pack(">i", len(items)) + b"".join(_w_payload(i) for i in items)
    if t == COMPOUND:
        body = b"".join(bytes([i.t]) + _w_str(name) + _w_payload(i) for name, i in v.items())
        return body + b"\x00"
    if t == INT_ARRAY:
        return struct.pack(">i", len(v)) + b"".join(struct.pack(">i", i) for i in v)
    if t == LONG_ARRAY:
        return struct.pack(">i", len(v)) + b"".join(struct.pack(">q", i) for i in v)
    raise ValueError("未知 NBT 类型 %d" % t)


def write_nbt(path: Path, root: Tag) -> None:
    payload = bytes([root.t]) + _w_str("") + _w_payload(root)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(payload, compresslevel=9))


def tag_str(s: str) -> Tag:
    return Tag(STRING, s)


def tag_double(v: float) -> Tag:
    return Tag(DOUBLE, v)


def tag_float(v: float) -> Tag:
    return Tag(FLOAT, v)


def tag_byte(v: int) -> Tag:
    return Tag(BYTE, v)


def tag_int(v: int) -> Tag:
    return Tag(INT, v)


def tag_list(et: int, items) -> Tag:
    return Tag(LIST, (et, items))


def tag_compound(entries: dict[str, Tag]) -> Tag:
    return Tag(COMPOUND, entries)


def normalize_villager(entity: Tag) -> Tag:
    """把结构方块导出的村民实体规范化成稳定字段集合。"""
    d = entity.v  # COMPOUND
    src = d["nbt"].v  # 实体原始 nbt
    pos = d["pos"].v[1]  # [Tag(DOUBLE)...]
    block_pos = d["blockPos"].v[1]

    pos_list = tag_list(DOUBLE, [tag_double(pos[0].v), tag_double(pos[1].v), tag_double(pos[2].v)])
    block_pos_list = tag_list(INT, [tag_int(block_pos[0].v), tag_int(block_pos[1].v), tag_int(block_pos[2].v)])

    vd = src["VillagerData"].v
    villager_data = tag_compound({
        "profession": vd["profession"],
        "level": vd["level"],
        "type": vd["type"],
    })

    nbt = tag_compound({
        "id": tag_str("minecraft:villager"),
        "Age": tag_int(0),
        "PersistenceRequired": tag_byte(1),
        "Health": tag_float(20.0),
        "CanPickUpLoot": tag_byte(1),
        "Xp": tag_int(1),
        "CustomName": tag_str('{"translate":"entity.csgobox.arms_dealer"}'),
        "VillagerData": villager_data,
        "VillagerDataFinalized": tag_byte(1),
        "Pos": pos_list,
        "Rotation": tag_list(FLOAT, [tag_float(180.0), tag_float(0.0)]),
    })

    return tag_compound({
        "pos": pos_list,
        "blockPos": block_pos_list,
        "nbt": nbt,
    })


def convert(root: Tag) -> Tag:
    d = root.v
    if "DataVersion" in d:
        del d["DataVersion"]
    d["entities"] = tag_list(COMPOUND, [normalize_villager(e) for e in d["entities"].v[1]])
    return root


def main() -> None:
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_SRC
    if not src.exists():
        raise SystemExit(f"源文件不存在: {src}")
    root = read_nbt(src)
    size = root.v["size"].v[1]
    print(f"source: {src}")
    print(f"size={[t.v for t in size]} blocks={len(root.v['blocks'].v[1])} entities={len(root.v['entities'].v[1])}")
    convert(root)
    write_nbt(OUT, root)
    print(f"written: {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
