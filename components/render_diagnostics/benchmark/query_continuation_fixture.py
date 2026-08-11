"""Compile the production split-dispatch block, not a copy of its branch ordering.

Only optical sampling/closest-hit execution are injected by the small GPU fixture.
The initTransparent function and sibling dispatch body are read verbatim from the pack.
"""

import pathlib
import sys


def block(source, marker):
    start = source.index(marker)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def generate(source, fixture):
    init = block(source, "bool initTransparent(")
    split = block(source, "if (useTransparentSplit) {")
    return fixture.replace("// PRODUCTION_INIT", init).replace("// PRODUCTION_SPLIT", split)


if __name__ == "__main__":
    source, fixture, output = map(pathlib.Path, sys.argv[1:])
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(generate(source.read_text(encoding="utf-8"), fixture.read_text(encoding="utf-8")), encoding="utf-8")
