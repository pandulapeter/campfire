# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Copies the website's screenshots from a render of the tool's `website` set into campfire-songbook.com's repository:
every `<name>.png` of the render's Website folder as `assets/screenshots/<name>.webp`, scaled to the size the site
draws that device at (SIZES, which the pages' width and height attributes and the stylesheet's aspect ratios repeat),
and, when one is given, the link preview Screenshot Bro exported as `assets/og-image.jpg`, at the size of the one it
replaces. The repository is the one passed with --website, or else a sibling of this one named as the owner's checkout
is (CampfireWebsite) or as a plain clone is (campfire-website). Where none is there, the files go to the fallback folder
instead, and it says so. A file the site no longer links stays where it is: remove it there.

Usage: python3 -I website_screenshots.py <render folder>/Website <fallback folder> [--link-preview <png>] [--website <repo>]
"""

import argparse
import pathlib
import sys

from PIL import Image

SIBLINGS = ("CampfireWebsite", "campfire-website")
# The laptop's window is 2000 by 1255 pixels, shown no wider than about 1100 device pixels on the page, so its shots are
# saved at 1600, which keeps the text sharp on a 2x screen at half the bytes; the 14 of the text size demo load
# together.
SIZES = {"phone": (600, 1304), "tablet": (1400, 1050), "laptop": (1600, 1004)}
OG_SIZE = (1200, 630)
QUALITY = 86


def website_assets(website):
    """The website repository's assets folder: the one asked for, which has to exist, or the first sibling found."""
    if website is not None:
        assets = website / "assets"
        if not assets.is_dir():
            sys.exit(f"{website} is not the website's repository: it has no assets folder.")
        return assets
    projects = pathlib.Path(__file__).resolve().parents[5]
    return next((projects / name / "assets" for name in SIBLINGS if (projects / name / "assets").is_dir()), None)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("renders", type=pathlib.Path)
    parser.add_argument("fallback", type=pathlib.Path)
    parser.add_argument("--link-preview", type=pathlib.Path)
    parser.add_argument("--website", type=pathlib.Path)
    arguments = parser.parse_args()
    renders = sorted(arguments.renders.glob("*.png"))
    if not renders:
        sys.exit(f"There are no renders in {arguments.renders}: render the website set first (--only website).")
    target = website_assets(arguments.website)
    if target is None:
        target = arguments.fallback
        print(f"The website's repository is not next to this one ({' or '.join(SIBLINGS)}), so the files go to {target}.")
    screenshots = target / "screenshots"
    screenshots.mkdir(parents=True, exist_ok=True)
    for source in renders:
        output = screenshots / f"{source.stem}.webp"
        size = SIZES[source.stem.split("-")[0]]
        Image.open(source).convert("RGB").resize(size, Image.Resampling.LANCZOS).save(output, "WEBP", quality=QUALITY, method=6)
        print(f"{output} {size} {output.stat().st_size // 1024} KB")
    if arguments.link_preview is None:
        print("No link preview given (it is framed in Screenshot Bro), so og-image.jpg is left as it was.")
        return
    og = target / "og-image.jpg"
    size = Image.open(og).size if og.exists() else OG_SIZE
    Image.open(arguments.link_preview).convert("RGB").resize(size, Image.Resampling.LANCZOS).save(og, "JPEG", quality=90, optimize=True)
    print(f"{og} {size} {og.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
