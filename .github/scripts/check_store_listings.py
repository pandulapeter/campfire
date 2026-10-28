# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Keeps every field of the store listings in documentation/store-listings within the limit its heading names, since Play
Console, App Store Connect, and Partner Center refuse a field past it and the listings are pasted by hand at release.
The English listings are the folder's own files, each translation a file of the same name in a folder per language
(`de/app-store.md`), and every one is judged, since a translation runs longer than its English.

A field is a `## <name> (<limit>)` heading and the fenced blocks under it, up to the next `## ` heading. Three limits
are understood: `(N characters max)`, one block of at most N characters as Python's len() counts them; `(N bytes max)`,
one block of at most N UTF-8 bytes (Apple's keywords); and `(K max, N characters each)`, at most K blocks of at most N
characters each (Microsoft's product features). Any other heading is not a field with a limit and is skipped; a field
with a limit and no block is an error.

The listings are few and short and are never meant to be over, so the whole folder is judged rather than the change:
no exception list, nothing to update. Run by .githooks/pre-commit before every commit, by tests.yml, and by the
add-language skill's `language.py wiring`, which grows each listing's languages line. Prints one line per failure and
exits 1, or prints nothing and exits 0.
"""

import re
import sys
from pathlib import Path

LISTINGS = Path(__file__).resolve().parents[2] / "documentation/store-listings"

HEADING = re.compile(r"^## (.+?)\s*\(([^)]*)\)\s*$")
CHARACTERS = re.compile(r"^([\d,]+) characters max$")
BYTES = re.compile(r"^([\d,]+) bytes max$")
ITEMS = re.compile(r"^([\d,]+) max, ([\d,]+) characters each$")
BLOCK = re.compile(r"^```[^\n]*\n(.*?)\n?^```", re.M | re.S)


def number(text):
    return int(text.replace(",", ""))


def sections(text):
    """Every `## ` heading of the text with what lies between it and the next one."""
    parts = re.split(r"^(?=## )", text, flags=re.M)
    for part in parts:
        if part.startswith("## "):
            heading, _, body = part.partition("\n")
            yield heading, body


def field_errors(name, text):
    """The failures of the listing called `name` whose Markdown is `text`, one line each."""
    errors = []
    for heading, body in sections(text):
        match = HEADING.match(heading)
        if not match:
            continue
        field, limit = match.group(1), match.group(2)
        blocks = BLOCK.findall(body)
        characters, size, items = CHARACTERS.match(limit), BYTES.match(limit), ITEMS.match(limit)
        if not (characters or size or items):
            continue
        if not blocks:
            errors.append(f"{name}: {field} has no text under its heading")
        elif characters:
            if len(blocks) > 1:
                errors.append(f"{name}: {field} has {len(blocks)} blocks, 1 at most")
            maximum = number(characters.group(1))
            if len(blocks[0]) > maximum:
                errors.append(f"{name}: {field} is {len(blocks[0])} characters, {maximum} at most")
        elif size:
            if len(blocks) > 1:
                errors.append(f"{name}: {field} has {len(blocks)} blocks, 1 at most")
            maximum = number(size.group(1))
            length = len(blocks[0].encode("utf-8"))
            if length > maximum:
                errors.append(f"{name}: {field} is {length} bytes, {maximum} at most")
        else:
            count, maximum = number(items.group(1)), number(items.group(2))
            if len(blocks) > count:
                errors.append(f"{name}: {field} has {len(blocks)} items, {count} at most")
            for index, block in enumerate(blocks, start=1):
                if len(block) > maximum:
                    errors.append(f"{name}: {field} item {index} is {len(block)} characters, {maximum} at most")
    return errors


def listing_errors(folder=LISTINGS):
    errors = []
    for path in sorted(folder.rglob("*.md")):
        errors += field_errors(path.relative_to(folder).as_posix(), path.read_text(encoding="utf-8"))
    return errors


def main():
    errors = listing_errors()
    for error in errors:
        print(error)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
