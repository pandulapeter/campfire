# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.
"""
Converts the app's vector drawables (`ic_*.xml`) into SVG files of the same name, for the video's chips to draw as masks:
only the shape matters, so every path is filled black, keeping its fill rule.

    python3 drawables_to_svg.py <drawable folder> <output folder>
"""
import glob
import os
import sys
import xml.etree.ElementTree as ElementTree

ANDROID = "{http://schemas.android.com/apk/res/android}"


def convert(source, target):
    root = ElementTree.parse(source).getroot()
    width = root.get(ANDROID + "viewportWidth")
    height = root.get(ANDROID + "viewportHeight")
    paths = []
    for path in root.iter("path"):
        rule = "evenodd" if path.get(ANDROID + "fillType") == "evenOdd" else "nonzero"
        paths.append(f'<path fill-rule="{rule}" d="{path.get(ANDROID + "pathData")}"/>')
    with open(target, "w") as file:
        file.write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}">{"".join(paths)}</svg>\n')


def main(source_folder, target_folder):
    os.makedirs(target_folder, exist_ok=True)
    for source in sorted(glob.glob(os.path.join(source_folder, "ic_*.xml"))):
        name = os.path.splitext(os.path.basename(source))[0]
        convert(source, os.path.join(target_folder, name + ".svg"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
