#!/usr/bin/env python3
"""Fail CI when the Android, iOS and Windows RSS rosters drift apart."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def pairs(pattern: str, text: str) -> set[tuple[str, str]]:
    return {(name, url) for name, url in re.findall(pattern, text)}


android = pairs(
    r'RssSource\("([^"]+)",\s*"([^"]+)",\s*Categories\.[A-Z_]+\)',
    read("app/src/main/java/uk/cybertecpro/infosecfeed/Sources.kt"),
)
ios = pairs(
    r'\.init\(name:\s*"([^"]+)",\s*url:\s*URL\(string:\s*"([^"]+)"\)!',
    read("ios/InfoSecFeed/Sources.swift"),
)
windows = pairs(
    r'new\("([^"]+)",\s*U\("([^"]+)"\),\s*Categories\.[A-Za-z]+\)',
    read("windows/InfoSecFeed.Windows/Services/SourceCatalog.cs"),
)

expected_count = 50
failed = False
for label, roster in (("Android", android), ("iOS", ios), ("Windows", windows)):
    if len(roster) != expected_count:
        print(f"{label}: expected {expected_count} RSS sources, found {len(roster)}", file=sys.stderr)
        failed = True

for left_name, left, right_name, right in (
    ("Android", android, "iOS", ios),
    ("Android", android, "Windows", windows),
):
    missing = sorted(left - right)
    extra = sorted(right - left)
    if missing or extra:
        failed = True
        print(f"{left_name} != {right_name}", file=sys.stderr)
        for item in missing:
            print(f"  missing from {right_name}: {item}", file=sys.stderr)
        for item in extra:
            print(f"  extra in {right_name}: {item}", file=sys.stderr)

if failed:
    raise SystemExit(1)

print(f"Source parity OK: {expected_count} RSS sources across Android, iOS and Windows")
