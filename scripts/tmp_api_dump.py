#!/usr/bin/env python3
"""TEMP (porting): list imported Minecraft classes missing from the Minecraft jar. Removed once the port builds."""
import re
import sys
import zipfile
from pathlib import Path

jar, ws = sys.argv[1], Path(sys.argv[2])
classes = {n[:-6].replace("/", ".") for n in zipfile.ZipFile(jar).namelist() if n.endswith(".class")}
imports = set()
for f in ws.rglob("*.java"):
    if "/build/" in str(f):
        continue
    for m in re.finditer(r"^import (?:static )?((?:net\.minecraft|com\.mojang\.blaze3d)\.[\w.]+);", f.read_text(errors="replace"), re.M):
        imports.add(m.group(1))


def found(name: str) -> bool:
    parts = name.split(".")
    # Try a.b.C, a.b.C$D, ... and static members (a.b.C.member).
    for i in range(len(parts), 1, -1):
        outer = ".".join(parts[:i])
        rest = parts[i:]
        cand = outer + "".join("$" + r for r in rest)
        if cand in classes:
            return True
        if outer in classes and rest and rest[-1][:1].islower():
            return True
        if outer in classes and len(rest) == 1 and rest[0].isupper():
            return True  # static constant
    return False


missing = sorted(i for i in imports if not found(i))
print(f"{len(imports)} imports checked, {len(missing)} missing")
for m in missing:
    print("MISSING", m)
