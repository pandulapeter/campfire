# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Copies the website's screenshots from a render of the tool's `website` set into campfire-songbook.com's repository
(the sibling CampfireWebsite folder): every `<name>.png` of the render's Website folder that the site already has as
`assets/screenshots/<name>.webp`, scaled to that file's own size and saved as WebP, and the link preview Screenshot Bro
exported as `assets/og-image.jpg`, at the size of the one it replaces. Where the website's repository is not there, the
files go to the output folder given instead, with the sizes the site has always used.

Usage: python3 -I website_screenshots.py <render folder>/Website <link preview png> <fallback folder>
"""

import pathlib
import sys

from PIL import Image

WEBSITE = pathlib.Path(__file__).resolve().parents[5] / "CampfireWebsite" / "assets"
SIZES = {"phone": (600, 1304), "tablet": (1400, 1050), "laptop": (2000, 1255)}
OG_SIZE = (1200, 630)
QUALITY = 86


def main():
    renders, link_preview, fallback = (pathlib.Path(argument) for argument in sys.argv[1:4])
    target = WEBSITE if WEBSITE.is_dir() else fallback
    screenshots = target / "screenshots"
    screenshots.mkdir(parents=True, exist_ok=True)
    for source in sorted(renders.glob("*.png")):
        output = screenshots / f"{source.stem}.webp"
        size = Image.open(output).size if output.exists() else SIZES[source.stem.split("-")[0]]
        Image.open(source).convert("RGB").resize(size, Image.Resampling.LANCZOS).save(output, "WEBP", quality=QUALITY, method=6)
        print(f"{output} {size} {output.stat().st_size // 1024} KB")
    og = target / "og-image.jpg"
    size = Image.open(og).size if og.exists() else OG_SIZE
    Image.open(link_preview).convert("RGB").resize(size, Image.Resampling.LANCZOS).save(og, "JPEG", quality=90, optimize=True)
    print(f"{og} {size} {og.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
