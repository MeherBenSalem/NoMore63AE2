#!/usr/bin/env python3
"""Generates every texture of Core RPG - Quests Engine (original art, reproducible).

Run from anywhere:  python3 1.21.1/tools/art/generate_textures.py
Needs Pillow. Writes into 1.21.1/common/src/main/resources/assets/corerpg_quests/.
"""
import math
import os
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "..", "common", "src", "main", "resources", "assets", "corerpg_quests"))


def out(*parts):
    path = os.path.join(ASSETS, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def from_map(rows, palette, size=16):
    """Pixel art from a list of strings; '.' is transparent."""
    assert len(rows) == size, (len(rows), rows)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == size, (y, len(row), row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch])
    return img


# ----------------------------------------------------------------------------- items

ITEM_PALETTE = {
    "k": hexc("2a1610"), "b": hexc("4e2618"), "B": hexc("6e3420"), "L": hexc("8e4a2c"), "H": hexc("a96039"),
    "g": hexc("9a6a1e"), "G": hexc("e8c15a"), "Y": hexc("fff0a8"),
    "p": hexc("efe2c4"), "P": hexc("c9b48c"),
    "e": hexc("e8743b"), "E": hexc("ffb36b"), "r": hexc("a8401c"),
}

QUEST_BOOK = [
    "................",
    "..kkkkkkkkkkk...",
    ".kGgbBBBBBBBGk..",
    ".kgbBLLLLLLLBkp.",
    ".kbBLLLHHLLLBkpk",
    ".kbBLLLGGLLLBkPk",
    ".kbBLLGeeGLLBkpk",
    ".kbBLGeEEeGLBkPk",
    ".kbBLGeEEeGLBkpk",
    ".kbBLLGeeGLLBkPk",
    ".kbBLLLGGLLLBkpk",
    ".kbBLLLLLLLLBkPk",
    ".kgbBBBBBBBBBkpk",
    ".kGgbbbbbbbbGkPk",
    "..kkkkkkkkkkkpk.",
    "...........kkk..",
]

SMART_FILTER = [
    "................",
    "..kkkkkkkkkkkk..",
    ".kYGGGGGGGGGGGk.",
    ".kGgkGkGkGkGggk.",
    ".kgGGGGGGGGGGgk.",
    "..kgGGGGGGGGgk..",
    "...kgGkGkGGgk...",
    "....kgGGGGgk....",
    ".....kgGGgk.....",
    "......kGgk......",
    "......kGgk......",
    "......kYgk......",
    "......kGgk......",
    ".......kk.......",
    ".......ee.......",
    "........E.......",
]

CRATE_PALETTE = {
    "k": hexc("2b1a0e"), "w": hexc("6e4524"), "W": hexc("8f5c30"), "L": hexc("b07a44"),
    "i": hexc("3d4247"), "I": hexc("7d858c"), "S": hexc("b9c0c6"),
    "G": hexc("e8c15a"), "g": hexc("9a6a1e"),
}

LOOT_CRATE = [
    "................",
    ".kkkkkkkkkkkkkk.",
    ".kIiLLLLLLLLiIk.",
    ".kiiWWWWWWWWiik.",
    ".kIiwwwwwwwwiIk.",
    ".kkkkkkkkkkkkkk.",
    ".kIiLLLLLLLLiIk.",
    ".kiiWWWGGWWWiik.",
    ".kIiwwwGgwwwiIk.",
    ".kkkkkkkkkkkkkk.",
    ".kIiLLLLLLLLiIk.",
    ".kiiWWWWWWWWiik.",
    ".kSiwwwwwwwwiSk.",
    ".kIiWWWWWWWWiIk.",
    ".kkkkkkkkkkkkkk.",
    "................",
]

MISSING_PALETTE = {"r": hexc("b3242b"), "d": hexc("1a0a0c"), "X": hexc("f4ece0"), "s": hexc("5a1418")}

MISSING_ITEM = [
    "rrrrrrrrdddddddd",
    "rrrrrrrrdddddddd",
    "rrrrrXXXXXXddddd",
    "rrrrXXrrddXXdddd",
    "rrrrXXrrddXXdddd",
    "rrrrrrrrdXXddddd",
    "rrrrrrrrXXdddddd",
    "rrrrrrsXXddddddd",
    "ddddddsXXrrrrrrr",
    "dddddssdsrrrrrrr",
    "ddddsddXXrrrrrrr",
    "ddddddsXXrrrrrrr",
    "dddddsddrrrrrrrr",
    "ddddsdddrrrrrrrr",
    "ddddddddrrrrrrrr",
    "ddddddddrrrrrrrr",
]


def items():
    from_map(QUEST_BOOK, ITEM_PALETTE).save(out("textures", "item", "quest_book.png"))
    from_map(SMART_FILTER, ITEM_PALETTE).save(out("textures", "item", "smart_filter.png"))
    from_map(LOOT_CRATE, CRATE_PALETTE).save(out("textures", "item", "loot_crate.png"))
    from_map(MISSING_ITEM, MISSING_PALETTE).save(out("textures", "item", "missing_item.png"))


# ----------------------------------------------------------------------------- quest shapes

SS = 4  # supersampling for smooth shapes
S = 64 * SS
C = S / 2


def poly(n, r, rot=-math.pi / 2):
    return [(C + r * math.cos(rot + 2 * math.pi * i / n), C + r * math.sin(rot + 2 * math.pi * i / n)) for i in range(n)]


def shape_mask(name):
    m = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(m)
    pad = 2 * SS
    r = C - pad
    if name == "circle":
        d.ellipse([pad, pad, S - pad, S - pad], fill=255)
    elif name == "square":
        d.rectangle([4 * SS, 4 * SS, S - 4 * SS, S - 4 * SS], fill=255)
    elif name == "rsquare":
        d.rounded_rectangle([3 * SS, 3 * SS, S - 3 * SS, S - 3 * SS], radius=14 * SS, fill=255)
    elif name == "diamond":
        d.polygon([(C, pad), (S - pad, C), (C, S - pad), (pad, C)], fill=255)
    elif name == "pentagon":
        pts = poly(5, r * 1.04)
        dy = (S - pad - max(p[1] for p in pts)) / 2
        d.polygon([(x, y + dy) for x, y in pts], fill=255)
    elif name == "hexagon":
        d.polygon(poly(6, r, 0), fill=255)
    elif name == "octagon":
        d.polygon(poly(8, r / math.cos(math.pi / 8) * 0.98, math.pi / 8), fill=255)
    elif name == "heart":
        pts = []
        for i in range(400):
            t = 2 * math.pi * i / 400
            x = 16 * math.sin(t) ** 3
            y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
            pts.append((x, y))
        minx = min(p[0] for p in pts); maxx = max(p[0] for p in pts)
        miny = min(p[1] for p in pts); maxy = max(p[1] for p in pts)
        sc = (S - 2 * pad) / max(maxx - minx, maxy - miny)
        ox = C - (minx + maxx) / 2 * sc
        oy = C - (miny + maxy) / 2 * sc
        d.polygon([(ox + x * sc, oy + y * sc) for x, y in pts], fill=255)
    elif name == "gear":
        teeth = 10
        pts = []
        steps = 600
        for i in range(steps):
            a = 2 * math.pi * i / steps
            phase = (a * teeth / (2 * math.pi)) % 1.0
            tooth = 1.0 if 0.12 < phase < 0.52 else 0.0
            # soften the tooth edges a little
            if 0.06 < phase <= 0.12:
                tooth = (phase - 0.06) / 0.06
            elif 0.52 <= phase < 0.58:
                tooth = 1 - (phase - 0.52) / 0.06
            rr = r * (0.80 + 0.20 * tooth)
            pts.append((C + rr * math.cos(a), C + rr * math.sin(a)))
        d.polygon(pts, fill=255)
    elif name == "shield":
        top = 4 * SS
        left = 6 * SS
        right = S - 6 * SS
        bottom = S - 2 * SS
        pts = [(left, top), (right, top)]
        mid = top + (bottom - top) * 0.42
        pts.append((right, mid))
        for i in range(1, 41):
            t = i / 40
            x = right + (C - right) * t
            y = mid + (bottom - mid) * math.sin(t * math.pi / 2)
            pts.append((x, y))
        for i in range(1, 41):
            t = i / 40
            x = C + (left - C) * t
            y = bottom - (bottom - mid) * (1 - math.cos(t * math.pi / 2))
            pts.append((x, y))
        pts.append((left, mid))
        d.polygon(pts, fill=255)
    elif name == "star":
        pts = []
        for i in range(10):
            rr = r * 1.05 if i % 2 == 0 else r * 0.46
            a = -math.pi / 2 + math.pi * i / 5
            pts.append((C + rr * math.cos(a), C + 4 * SS + rr * math.sin(a)))
        d.polygon(pts, fill=255)
    else:
        raise ValueError(name)
    return m


def save_mask(mask, path):
    small = mask.resize((64, 64), Image.LANCZOS)
    img = Image.new("RGBA", (64, 64), (255, 255, 255, 0))
    img.putalpha(small)
    img.save(path)


SHAPES = ["circle", "square", "rsquare", "diamond", "pentagon", "hexagon", "octagon", "heart", "gear", "shield", "star"]


def shapes():
    for name in SHAPES:
        mask = shape_mask(name)
        save_mask(mask, out("textures", "gui", "shape", name + ".png"))
        # A 4px ring (at 64px) of the same shape: the mask minus the mask eroded by 4px.
        inner = mask.filter(ImageFilter.MinFilter(4 * SS * 2 + 1))
        ring = ImageChops.subtract(mask, inner)
        save_mask(ring, out("textures", "gui", "shape", name + "_outline.png"))


# ----------------------------------------------------------------------------- GUI icons (16x16, tintable)

ICON_PALETTE = {"X": (255, 255, 255, 255), "#": (200, 200, 200, 255), "+": (255, 255, 255, 140)}

ICONS = {
    "check": [
        "................",
        "................",
        "................",
        "............XX..",
        "...........XXX..",
        "..........XXX...",
        ".........XXX....",
        "..XX....XXX.....",
        "..XXX..XXX......",
        "...XXXXXX.......",
        "....XXXX........",
        ".....XX.........",
        "................",
        "................",
        "................",
        "................",
    ],
    "lock": [
        "................",
        "......XXXX......",
        ".....XX..XX.....",
        "....XX....XX....",
        "....X......X....",
        "....X......X....",
        "...XXXXXXXXXX...",
        "...XXXXXXXXXX...",
        "...XXXXX.XXXX...",
        "...XXXX...XXX...",
        "...XXXXX.XXXX...",
        "...XXXXX.XXXX...",
        "...XXXXXXXXXX...",
        "...XXXXXXXXXX...",
        "................",
        "................",
    ],
    "pin": [
        "................",
        "......XXXX......",
        ".....X....X.....",
        ".....X....X.....",
        "......X..X......",
        "......X..X......",
        "....XX....XX....",
        "...XXXXXXXXXX...",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        ".......X........",
        ".......X........",
        "................",
        "................",
        "................",
    ],
    "pinned": [
        "................",
        "......XXXX......",
        ".....XXXXXX.....",
        ".....XXXXXX.....",
        "......XXXX......",
        "......XXXX......",
        "....XXXXXXXX....",
        "...XXXXXXXXXX...",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        ".......X........",
        ".......X........",
        "................",
        "................",
        "................",
    ],
    "edit": [
        "................",
        "...........XX...",
        "..........X##X..",
        ".........X##XX..",
        "........XXXXX...",
        ".......XXXXX....",
        "......XXXXX.....",
        ".....XXXXX......",
        "....XXXXX.......",
        "...XXXXX........",
        "...XXXX.........",
        "..X#XX..........",
        "..##X...........",
        "..XX............",
        "................",
        "................",
    ],
    "add": [
        "................",
        "................",
        "................",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        "................",
        "................",
        "................",
    ],
    "delete": [
        "................",
        "......XXXX......",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        "................",
        "...XXXXXXXXXX...",
        "...X.X.XX.X.X...",
        "...X.X.XX.X.X...",
        "...X.X.XX.X.X...",
        "...X.X.XX.X.X...",
        "...X.X.XX.X.X...",
        "...X.X.XX.X.X...",
        "...XXXXXXXXXX...",
        "....XXXXXXXX....",
        "................",
        "................",
    ],
    "team": [
        "................",
        "................",
        "....XXX.........",
        "...XXXXX..XXX...",
        "...XXXXX.XXXXX..",
        "....XXX..XXXXX..",
        "..........XXX...",
        "..XXXXXX........",
        ".XXXXXXXX.XXXX..",
        ".XXXXXXXXXXXXXX.",
        ".XXXXXXXXXXXXXX.",
        ".XXXXXXXXXXXXXX.",
        "................",
        "................",
        "................",
        "................",
    ],
    "settings": [
        "................",
        ".......XX.......",
        "...XX.XXXX.XX...",
        "...XXXXXXXXXX...",
        "....XXXXXXXX....",
        "..XXXXX..XXXXX..",
        ".XXXXX....XXXXX.",
        ".XXXX......XXXX.",
        ".XXXX......XXXX.",
        ".XXXXX....XXXXX.",
        "..XXXXX..XXXXX..",
        "....XXXXXXXX....",
        "...XXXXXXXXXX...",
        "...XX.XXXX.XX...",
        ".......XX.......",
        "................",
    ],
    "search": [
        "................",
        "....XXXXX.......",
        "...XX...XX......",
        "..XX.....XX.....",
        "..X.......X.....",
        "..X.......X.....",
        "..X.......X.....",
        "..XX.....XX.....",
        "...XX...XXX.....",
        "....XXXXXXXX....",
        "..........XXX...",
        "...........XXX..",
        "............XXX.",
        ".............XX.",
        "................",
        "................",
    ],
    "gift": [
        "................",
        "....XX....XX....",
        "...X..X..X..X...",
        "....XX.XX.XX....",
        "..XXXXXXXXXXXX..",
        "..XXXXXX.XXXXX..",
        "..XXXXXX.XXXXX..",
        "................",
        "...XXXXX.XXXX...",
        "...XXXXX.XXXX...",
        "...XXXXX.XXXX...",
        "...XXXXX.XXXX...",
        "...XXXXX.XXXX...",
        "...XXXXX.XXXX...",
        "................",
        "................",
    ],
    "exclamation": [
        "................",
        ".......XX.......",
        "......XXXX......",
        "......XXXX......",
        "......XXXX......",
        "......XXXX......",
        "......XXXX......",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        "................",
        "................",
        ".......XX.......",
        "......XXXX......",
        ".......XX.......",
        "................",
    ],
    "close": [
        "................",
        "................",
        "..XX........XX..",
        "..XXX......XXX..",
        "...XXX....XXX...",
        "....XXX..XXX....",
        ".....XXXXXX.....",
        "......XXXX......",
        "......XXXX......",
        ".....XXXXXX.....",
        "....XXX..XXX....",
        "...XXX....XXX...",
        "..XXX......XXX..",
        "..XX........XX..",
        "................",
        "................",
    ],
    "back": [
        "................",
        "................",
        "......X.........",
        ".....XX.........",
        "....XXXXXXXXX...",
        "...XXXXXXXXXXX..",
        "....XXXXXXXXXXX.",
        ".....XX......XX.",
        "......X......XX.",
        ".............XX.",
        ".............XX.",
        "............XX..",
        "........XXXXXX..",
        "........XXXXX...",
        "................",
        "................",
    ],
    "copy": [
        "................",
        "..XXXXXXXX......",
        "..X......X......",
        "..X......X......",
        "..X...XXXXXXXX..",
        "..X...X......X..",
        "..X...X......X..",
        "..X...X......X..",
        "..XXXXX......X..",
        "......X......X..",
        "......X......X..",
        "......X......X..",
        "......XXXXXXXX..",
        "................",
        "................",
        "................",
    ],
    "link": [
        "................",
        "................",
        "..........XXX...",
        ".........XX.XX..",
        "........XX...XX.",
        "........X...XX..",
        "......XX...XX...",
        ".....XX.XXXX....",
        "....XXXX.XX.....",
        "...XX...XX......",
        "..XX...X........",
        ".XX...XX........",
        "..XX.XX.........",
        "...XXX..........",
        "................",
        "................",
    ],
    "image": [
        "................",
        "................",
        ".XXXXXXXXXXXXXX.",
        ".X............X.",
        ".X..XX........X.",
        ".X..XX........X.",
        ".X........X...X.",
        ".X.......XXX..X.",
        ".X..X...XXXXX.X.",
        ".X.XXX.XXXXXXXX.",
        ".XXXXXXXXXXXXXX.",
        ".XXXXXXXXXXXXXX.",
        ".XXXXXXXXXXXXXX.",
        "................",
        "................",
        "................",
    ],
    "info": [
        "................",
        ".....XXXXXX.....",
        "...XX......XX...",
        "..X....XX....X..",
        "..X....XX....X..",
        ".X............X.",
        ".X....XXX.....X.",
        ".X.....XX.....X.",
        ".X.....XX.....X.",
        ".X.....XX.....X.",
        "..X....XX....X..",
        "..X...XXXX...X..",
        "...XX......XX...",
        ".....XXXXXX.....",
        "................",
        "................",
    ],
    "star": [
        "................",
        ".......XX.......",
        ".......XX.......",
        "......XXXX......",
        "......XXXX......",
        ".XXXXXXXXXXXXXX.",
        "..XXXXXXXXXXXX..",
        "...XXXXXXXXXX...",
        "....XXXXXXXX....",
        "....XXXXXXXX....",
        "...XXXXXXXXXX...",
        "...XXXX..XXXX...",
        "..XXX......XXX..",
        "..XX........XX..",
        "................",
        "................",
    ],
    "chapter": [
        "................",
        "................",
        ".XXXXXX..XXXXXX.",
        ".X#####XX#####X.",
        ".X#XXX#XX#XXX#X.",
        ".X#####XX#####X.",
        ".X#XXX#XX#XXX#X.",
        ".X#####XX#####X.",
        ".X#XXX#XX#XXX#X.",
        ".X#####XX#####X.",
        ".X#####XX#####X.",
        ".XXXXXXXXXXXXXX.",
        "......XXXX......",
        "................",
        "................",
        "................",
    ],
    "group": [
        "................",
        "................",
        ".XXXXX..........",
        ".X...XX.........",
        ".X....XXXXXXXXX.",
        ".X............X.",
        ".X............X.",
        ".X............X.",
        ".X............X.",
        ".X............X.",
        ".X............X.",
        ".XXXXXXXXXXXXXX.",
        "................",
        "................",
        "................",
        "................",
    ],
    "table": [
        "................",
        "................",
        ".XXXXXXXXXXXXXX.",
        ".XXXXXXXXXXXXXX.",
        ".X....X....X..X.",
        ".X....X....X..X.",
        ".XXXXXXXXXXXXXX.",
        ".X....X....X..X.",
        ".X....X....X..X.",
        ".XXXXXXXXXXXXXX.",
        ".X....X....X..X.",
        ".X....X....X..X.",
        ".XXXXXXXXXXXXXX.",
        "................",
        "................",
        "................",
    ],
    "eye": [
        "................",
        "................",
        "................",
        "................",
        ".....XXXXXX.....",
        "...XX......XX...",
        "..X....XX....X..",
        ".X....XXXX....X.",
        ".X....XXXX....X.",
        "..X....XX....X..",
        "...XX......XX...",
        ".....XXXXXX.....",
        "................",
        "................",
        "................",
        "................",
    ],
    "eye_off": [
        "................",
        "................",
        "..XX............",
        "...XX...........",
        "....XXXXXXX.....",
        "...XXXX....XX...",
        "..X...XXX....X..",
        ".X....XXXX....X.",
        ".X....XXXXX...X.",
        "..X....XX.XX.X..",
        "...XX......XX...",
        ".....XXXXXX.XX..",
        ".............XX.",
        "................",
        "................",
        "................",
    ],
    "collapse": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "...XX......XX...",
        "....XX....XX....",
        ".....XX..XX.....",
        "......XXXX......",
        ".......XX.......",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "expand": [
        "................",
        "................",
        "................",
        ".....XX.........",
        "......XX........",
        ".......XX.......",
        "........XX......",
        ".........XX.....",
        "........XX......",
        ".......XX.......",
        "......XX........",
        ".....XX.........",
        "................",
        "................",
        "................",
        "................",
    ],
    "arrow_left": [
        "................",
        "................",
        "................",
        "......X.........",
        ".....XX.........",
        "....XXX.........",
        "...XXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        "...XXXXXXXXXXX..",
        "....XXX.........",
        ".....XX.........",
        "......X.........",
        "................",
        "................",
        "................",
    ],
    "arrow_right": [
        "................",
        "................",
        "................",
        ".........X......",
        ".........XX.....",
        ".........XXX....",
        "..XXXXXXXXXXX...",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXX...",
        ".........XXX....",
        ".........XX.....",
        ".........X......",
        "................",
        "................",
        "................",
    ],
    "claim_all": [
        "................",
        "...XX....XX.....",
        "..X..X..X..X....",
        "...XX.XX.XX.....",
        ".XXXXXXXXXXXX...",
        ".XXXXXX.XXXXX...",
        "................",
        "..XXXXX.XXXX....",
        "..XXXXX.XXXX.XX.",
        "..XXXXX.XXXX.XX.",
        "..XXXXX.XXX.XX..",
        "..XXXXX.XX.XX...",
        "..XXXXX.X.XX....",
        "..........X.....",
        "................",
        "................",
    ],
    "move": [
        "................",
        ".......XX.......",
        "......XXXX......",
        ".....XXXXXX.....",
        ".......XX.......",
        "...X...XX...X...",
        "..XX...XX...XX..",
        ".XXXXXXXXXXXXXX.",
        ".XXXXXXXXXXXXXX.",
        "..XX...XX...XX..",
        "...X...XX...X...",
        ".......XX.......",
        ".....XXXXXX.....",
        "......XXXX......",
        ".......XX.......",
        "................",
    ],
    "zoom_in": [
        "................",
        "....XXXXX.......",
        "...XX...XX......",
        "..XX..X..XX.....",
        "..X...X...X.....",
        "..X.XXXXX.X.....",
        "..X...X...X.....",
        "..XX..X..XX.....",
        "...XX...XXX.....",
        "....XXXXXXXX....",
        "..........XXX...",
        "...........XXX..",
        "............XXX.",
        ".............XX.",
        "................",
        "................",
    ],
    "zoom_out": [
        "................",
        "....XXXXX.......",
        "...XX...XX......",
        "..XX.....XX.....",
        "..X.......X.....",
        "..X.XXXXX.X.....",
        "..X.......X.....",
        "..XX.....XX.....",
        "...XX...XXX.....",
        "....XXXXXXXX....",
        "..........XXX...",
        "...........XXX..",
        "............XXX.",
        ".............XX.",
        "................",
        "................",
    ],
    "recenter": [
        "................",
        ".......XX.......",
        ".......XX.......",
        ".....XXXXXX.....",
        "....X..XX..X....",
        "...X........X...",
        "...X...XX...X...",
        ".XXXX.XXXX.XXXX.",
        ".XXXX.XXXX.XXXX.",
        "...X...XX...X...",
        "...X........X...",
        "....X..XX..X....",
        ".....XXXXXX.....",
        ".......XX.......",
        ".......XX.......",
        "................",
    ],
    "book": [
        "................",
        "................",
        "..XXXXXXXXXXX...",
        "..X#########XX..",
        "..X#XXXXXXX#XX..",
        "..X#########XX..",
        "..X#XXXXX###XX..",
        "..X#########XX..",
        "..X####X####XX..",
        "..X###XXX###XX..",
        "..X####X####XX..",
        "..X#########XX..",
        "..XXXXXXXXXXXX..",
        "...X+X+X+X+X+X..",
        "...XXXXXXXXXXX..",
        "................",
    ],
}


def icons():
    for name, rows in ICONS.items():
        from_map(rows, ICON_PALETTE).save(out("textures", "gui", "icon", name + ".png"))


# ----------------------------------------------------------------------------- mod icon (256x256)

def mod_icon():
    N = 1024
    img = Image.new("RGBA", (N, N), (0, 0, 0, 255))
    px = img.load()
    # Ash background with a warm vignette.
    for y in range(N):
        for x in range(N):
            dx = (x - N * 0.5) / N
            dy = (y - N * 0.46) / N
            d = math.sqrt(dx * dx + dy * dy)
            t = max(0.0, 1.0 - d * 1.9)
            r = int(18 + 58 * t * t)
            g = int(13 + 26 * t * t)
            b = int(14 + 12 * t * t)
            px[x, y] = (r, g, b, 255)
    d = ImageDraw.Draw(img)

    # Ember glow behind the book.
    glow = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([180, 150, 844, 814], fill=(232, 116, 59, 150))
    glow = glow.filter(ImageFilter.GaussianBlur(110))
    img.alpha_composite(glow)

    # Pages (back), then the cover.
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([262, 214, 790, 842], radius=34, fill=(201, 180, 140, 255))
    for i in range(7):
        y = 800 + i * 7
        d.line([(280, y), (772, y)], fill=(150, 128, 92, 255), width=3)
    d.rounded_rectangle([232, 184, 768, 806], radius=40, fill=(78, 38, 24, 255))
    d.rounded_rectangle([250, 202, 750, 788], radius=30, fill=(110, 52, 32, 255))
    # Spine shading.
    d.rectangle([250, 210, 312, 780], fill=(84, 40, 26, 255))
    # Gold border.
    d.rounded_rectangle([336, 250, 712, 742], radius=18, outline=(232, 193, 90, 255), width=12)
    d.rounded_rectangle([360, 274, 688, 718], radius=10, outline=(154, 106, 30, 255), width=5)
    # Gold corner caps.
    for cx, cy in [(250, 202), (750, 202), (250, 788), (750, 788)]:
        sx = 1 if cx < 500 else -1
        sy = 1 if cy < 500 else -1
        d.polygon([(cx, cy), (cx + sx * 96, cy), (cx, cy + sy * 96)], fill=(232, 193, 90, 255))
        d.polygon([(cx + sx * 14, cy + sy * 14), (cx + sx * 66, cy + sy * 14), (cx + sx * 14, cy + sy * 66)], fill=(255, 240, 168, 255))
    # Ember rune: a diamond with a flame.
    rune_glow = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rune_glow)
    rd.ellipse([400, 360, 648, 640], fill=(255, 150, 70, 170))
    rune_glow = rune_glow.filter(ImageFilter.GaussianBlur(40))
    img.alpha_composite(rune_glow)
    d = ImageDraw.Draw(img)
    cx, cy = 524, 496
    d.polygon([(cx, cy - 150), (cx + 110, cy), (cx, cy + 150), (cx - 110, cy)], fill=(232, 193, 90, 255))
    d.polygon([(cx, cy - 122), (cx + 86, cy), (cx, cy + 122), (cx - 86, cy)], fill=(70, 30, 18, 255))
    flame = [(cx, cy - 92)]
    for i in range(1, 41):
        t = i / 40
        flame.append((cx + 52 * math.sin(t * math.pi) * (1 - 0.3 * t), cy - 92 + 180 * t))
    for i in range(1, 41):
        t = i / 40
        flame.append((cx - 52 * math.sin((1 - t) * math.pi) * (1 - 0.3 * (1 - t)), cy + 88 - 180 * t))
    d.polygon(flame, fill=(232, 116, 59, 255))
    inner = [(cx, cy - 36)]
    for i in range(1, 31):
        t = i / 30
        inner.append((cx + 26 * math.sin(t * math.pi), cy - 36 + 116 * t))
    for i in range(1, 31):
        t = i / 30
        inner.append((cx - 26 * math.sin((1 - t) * math.pi), cy + 80 - 116 * t))
    d.polygon(inner, fill=(255, 210, 130, 255))
    # Sparks.
    rnd = random.Random(7)
    for _ in range(26):
        x = rnd.uniform(150, 880)
        y = rnd.uniform(90, 330) if rnd.random() < 0.6 else rnd.uniform(820, 960)
        rr = rnd.uniform(4, 11)
        a = int(rnd.uniform(120, 230))
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(255, 170 + int(rnd.uniform(0, 60)), 90, a))
    img.resize((256, 256), Image.LANCZOS).save(out("icon.png"))


def models():
    import json
    for name in ["quest_book", "smart_filter", "loot_crate", "missing_item"]:
        path = out("models", "item", name + ".json")
        with open(path, "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "corerpg_quests:item/" + name}}, f, indent=2)
            f.write("\n")


if __name__ == "__main__":
    items()
    models()
    shapes()
    icons()
    mod_icon()
    print("Wrote textures to", ASSETS)
