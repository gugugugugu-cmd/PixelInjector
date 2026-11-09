#!/usr/bin/env python3
"""Compare a translation against the default strings.xml.

Fails on a missing or extra key, a differing placeholder set, and a plurals
entry whose quantities differ. Chinese has no grammatical plural, so a zh
plurals item copies its text into every quantity English declares.
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PLACEHOLDER = re.compile(r"%(?:\d+\$)?[a-z]")
DEFAULT = "values"
TRANSLATIONS = ("values-zh-rCN",)


def load(path: Path) -> dict[str, ET.Element]:
    root = ET.parse(path).getroot()
    return {e.get("name"): e for e in root if e.tag in ("string", "plurals")}


def placeholders(element: ET.Element) -> list[str]:
    text = "".join("".join(i.itertext()) for i in element.iter("item")) if element.tag == "plurals" else "".join(element.itertext())
    return sorted(PLACEHOLDER.findall(text))


def main(res_dir: str) -> int:
    res = Path(res_dir)
    default = load(res / DEFAULT / "strings.xml")
    problems: list[str] = []

    targets = sorted(p for p in res.iterdir() if p.name in TRANSLATIONS)
    if not targets:
        print(f"no translation directory in {res}, nothing to compare")
        return 0

    for target in targets:
        strings = load(target / "strings.xml")

        for name in sorted(set(default) - set(strings)):
            problems.append(f"{target.name}: missing {name}")
        for name in sorted(set(strings) - set(default)):
            problems.append(f"{target.name}: extra {name}")

        for name in sorted(set(default) & set(strings)):
            want, got = default[name], strings[name]
            if want.tag != got.tag:
                problems.append(f"{target.name}: {name} is a {got.tag}, default is a {want.tag}")
                continue
            if want.tag == "plurals":
                want_q = {i.get("quantity") for i in want}
                got_q = {i.get("quantity") for i in got}
                if want_q - got_q:
                    problems.append(f"{target.name}: {name} lacks {sorted(want_q - got_q)}")
            if placeholders(want) != placeholders(got):
                problems.append(
                    f"{target.name}: {name} has {placeholders(got)}, default has {placeholders(want)}"
                )

        print(f"{target.name}: {len(strings)} entries checked")

    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print("translations match the default strings")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res"))
