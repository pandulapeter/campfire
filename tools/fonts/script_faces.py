# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Subsets a script face to the code point ranges its row of the script-face table names, and writes it where the app
reads it from: `presentation/src/commonMain/composeResources/font/<resource>.ttf`.

The table is `scriptFaces` in `ScriptFaces.kt` (`:presentation`'s `ui/theme`), one `ScriptFace(...)` per script Inter
and JetBrains Mono lack, each naming its ranges as `0x0590..0x05FF` and its file as `Res.font.<resource>`. This script
reads both from there, so a face for another script is a row in that table and one run of this script on the face's
upstream file (an unhinted static instance from the notofonts GitHub releases), and nothing else:

    pip install fonttools
    python3 tools/fonts/script_faces.py noto_sans_hebrew_regular NotoSansHebrew-Regular.ttf

Only the ranges are kept, with every layout feature the face has for them (the marks of a pointed text are placed by
its `mark` and `mkmk` lookups), every name record (the license and its address among them), and no hinting, as
Inter and JetBrains Mono have none either. A face whose license reserves its font name (the OFL's "Reserved Font
Name", which Noto's does not) cannot be used this way: a subset is a modified version, which would have to be renamed.
"""

import pathlib
import re
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
TABLE = ROOT / "presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/theme/ScriptFaces.kt"
FONTS = ROOT / "presentation/src/commonMain/composeResources/font"


def rows(table):
    """Every `ScriptFace(...)` of the table, as the resource it names and the ranges it lists."""
    for row in re.findall(r"ScriptFace\((.*?)\n    \)", table, re.DOTALL):
        resources = re.findall(r"Res\.font\.(\w+)", row)
        ranges = [(int(start, 16), int(end, 16)) for start, end in re.findall(r"0x([0-9A-Fa-f]+)\.\.0x([0-9A-Fa-f]+)", row)]
        if len(resources) != 1 or not ranges:
            sys.exit(f"A row of {TABLE.name} names no single font or no range:\n{row}")
        yield resources[0], ranges


def main(resource, source):
    ranges = dict(rows(TABLE.read_text(encoding="utf-8"))).get(resource)
    if ranges is None:
        sys.exit(f"No row of {TABLE.name} names Res.font.{resource}.")
    font = TTFont(source)
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.hinting = False
    options.drop_tables += ["FFTM"]
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=[code_point for start, end in ranges for code_point in range(start, end + 1)])
    subsetter.subset(font)
    target = FONTS / f"{resource}.ttf"
    font.save(target)
    print(f"{target.relative_to(ROOT)}: {len(font.getBestCmap())} characters, {target.stat().st_size} bytes")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
