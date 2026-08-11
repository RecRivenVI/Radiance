"""Generate Target-only Java declarations from the C++/GLSL integer ABI table."""
import re
from pathlib import Path
import sys

ENTRY = re.compile(r"RENDER_ABI\(([A-Z][A-Z0-9_]*), (0x[0-9a-fA-F]+|[0-9]+)u\)")


def parse(text):
    entries = []
    names = set()
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("//"):
            continue
        match = ENTRY.fullmatch(line)
        if not match:
            raise ValueError(f"Invalid ABI declaration: {line}")
        name, literal = match.groups()
        value = int(literal, 0)
        if name in names or value > 0xffffffff:
            raise ValueError(f"Duplicate or out-of-range ABI declaration: {name}")
        names.add(name)
        entries.append((name, value))
    if not entries:
        raise ValueError("Empty render ABI")
    return entries


def java(entries):
    rows = ["package com.radiance.render.contract;", "",
            "/** Generated from native_renderer/src/common/render_abi.def. */",
            "public final class RenderAbi {", "    private RenderAbi() {}"]
    rows += [f"    public static final int {name} = {value if value <= 0x7fffffff else value - 0x100000000};" for name, value in entries]
    rows += ["    public static int[] values() {", "        return new int[] {"]
    rows += [f"            {name}," for name, _ in entries]
    rows += ["        };", "    }", "}", ""]
    return "\n".join(rows)


def main():
    source, output = map(Path, sys.argv[1:3])
    content = java(parse(source.read_text(encoding="utf-8")))
    output.parent.mkdir(parents=True, exist_ok=True)
    if not output.exists() or output.read_text(encoding="utf-8") != content:
        output.write_text(content, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
