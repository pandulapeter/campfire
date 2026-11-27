# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Fails when the Android release build lost a constructor that a library calls by reflection, for tests.yml.

Glance makes a widget button's `ActionCallback` from its class name, through its no-argument constructor, which no code
calls: R8 removes it unless a keep rule names it, and the button then does nothing, with only a line in the log. The
release check on the emulator cannot press a widget's button (Glance's receiver is not exported and wants a Bundle
extra), so this reads what R8 says it kept (`seeds.txt`, every class and member a keep rule held) for every such class
the app's sources declare.

    python3 .github/scripts/check_reflected_constructors.py SEEDS SOURCE_ROOT
"""

import os
import re
import sys

PACKAGE = re.compile(r"^package\s+([\w.]+)", re.MULTILINE)
# The supertypes whose subclasses a library makes through their no-argument constructor.
REFLECTED = re.compile(r"\bclass\s+(\w+)\s*(?:\(\s*\))?\s*:\s*(?:[\w.]+\.)?ActionCallback\b")


def reflected_classes(sources):
    """The fully qualified name of every class in `sources` (texts of Kotlin files) that a library makes by reflection."""
    found = []
    for text in sources:
        package = PACKAGE.search(text)
        prefix = package.group(1) + "." if package else ""
        found.extend(prefix + name for name in REFLECTED.findall(text))
    return found


def missing_constructors(classes, seeds):
    """The classes whose no-argument constructor R8's `seeds` text does not list as kept."""
    kept = set(line.strip() for line in seeds.splitlines())
    return [name for name in classes if f"{name}: {name.rsplit('.', 1)[-1]}()" not in kept]


def main(seeds_path, source_root):
    sources = []
    for directory, _, files in os.walk(source_root):
        for file in files:
            if file.endswith(".kt"):
                with open(os.path.join(directory, file), encoding="utf-8") as source:
                    sources.append(source.read())
    classes = reflected_classes(sources)
    if not classes:
        print(f"::error::No ActionCallback found under {source_root}; the check reads the wrong folder.")
        return 1
    with open(seeds_path, encoding="utf-8") as seeds:
        missing = missing_constructors(classes, seeds.read())
    for name in missing:
        print(f"::error::R8 removed the constructor {name}() that Glance calls by reflection: keep it in proguard-rules.pro.")
    if not missing:
        print(f"Kept the constructors of {len(classes)} class(es) made by reflection.")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:]))
