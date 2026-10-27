# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Turns the six images Screenshot Bro exports for the two GitHub banner rows into the README's
documentation/screenshots/01.webp ... 06.webp: half their size (1200 x 900, the size the README has always used) and
WebP, which keeps each a few dozen kilobytes.

Usage: python3 -I readme_banners.py <repository root> <banner 1, column 1> ... <banner 1, column 3> <banner 2, column 1> ... <banner 2, column 3>
"""

import pathlib
import sys

from PIL import Image

SIZE = (1200, 900)
QUALITY = 82


def main():
    root = pathlib.Path(sys.argv[1])
    sources = [pathlib.Path(path) for path in sys.argv[2:]]
    if len(sources) != 6:
        sys.exit("Pass the repository root and the six exported images, banner 1's three columns first.")
    target = root / "documentation" / "screenshots"
    for index, source in enumerate(sources, start=1):
        image = Image.open(source).convert("RGB").resize(SIZE, Image.Resampling.LANCZOS)
        output = target / f"{index:02d}.webp"
        image.save(output, "WEBP", quality=QUALITY, method=6)
        print(f"{output} ({output.stat().st_size // 1024} KB) from {source.name}")


if __name__ == "__main__":
    main()
