#!/usr/bin/env python3
"""Writes data/corerpg_quests/structure/empty.nbt: an all-air 5x5x5 structure used as the GameTest template."""
import gzip
import io
import os
import struct
import sys

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def w_string(buf, s):
    b = s.encode("utf-8")
    buf.write(struct.pack(">H", len(b)))
    buf.write(b)


def w_payload(buf, tag, value):
    if tag == TAG_INT:
        buf.write(struct.pack(">i", value))
    elif tag == TAG_STRING:
        w_string(buf, value)
    elif tag == TAG_LIST:
        elem_tag, items = value
        buf.write(struct.pack(">bi", elem_tag if items else TAG_END, len(items)))
        for item in items:
            w_payload(buf, elem_tag, item)
    elif tag == TAG_COMPOUND:
        for name, (t, v) in value.items():
            buf.write(struct.pack(">b", t))
            w_string(buf, name)
            w_payload(buf, t, v)
        buf.write(struct.pack(">b", TAG_END))


def r_string(buf):
    (n,) = struct.unpack(">H", buf.read(2))
    return buf.read(n).decode("utf-8")


def r_payload(buf, tag):
    if tag == TAG_INT:
        return struct.unpack(">i", buf.read(4))[0]
    if tag == TAG_STRING:
        return r_string(buf)
    if tag == TAG_LIST:
        elem, n = struct.unpack(">bi", buf.read(5))
        return [r_payload(buf, elem) for _ in range(n)]
    if tag == TAG_COMPOUND:
        out = {}
        while True:
            (t,) = struct.unpack(">b", buf.read(1))
            if t == TAG_END:
                return out
            name = r_string(buf)
            out[name] = r_payload(buf, t)
    raise ValueError(tag)


def main(path):
    root = {
        "DataVersion": (TAG_INT, 3955),
        "size": (TAG_LIST, (TAG_INT, [5, 5, 5])),
        "blocks": (TAG_LIST, (TAG_COMPOUND, [])),
        "entities": (TAG_LIST, (TAG_COMPOUND, [])),
        "palette": (TAG_LIST, (TAG_COMPOUND, [{"Name": (TAG_STRING, "minecraft:air")}])),
    }
    raw = io.BytesIO()
    raw.write(struct.pack(">b", TAG_COMPOUND))
    w_string(raw, "")
    w_payload(raw, TAG_COMPOUND, root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, "wb") as f:
        f.write(raw.getvalue())
    # Read it back.
    with gzip.open(path, "rb") as f:
        buf = io.BytesIO(f.read())
    (t,) = struct.unpack(">b", buf.read(1))
    assert t == TAG_COMPOUND and r_string(buf) == ""
    data = r_payload(buf, TAG_COMPOUND)
    assert data == {"DataVersion": 3955, "size": [5, 5, 5], "blocks": [], "entities": [], "palette": [{"Name": "minecraft:air"}]}, data
    print("ok", path, data)


if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    target = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "..", "..", "common", "src", "main", "resources", "data", "corerpg_quests", "structure", "empty.nbt")
    main(os.path.normpath(target))
