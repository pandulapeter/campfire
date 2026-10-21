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
replaces. The renders of a playing click's beats (`<name>-beat-<n>-<theme>.png`) are not copied as screenshots: what each
changes of `<name>-<theme>.png`, the lit beat and the metronome mark's pulse, goes side by side into one transparent
`<name>-beats-<theme>.webp`, a layer per beat at the screenshot's size, which the page fades over it in turn.
The repository is the one passed with --website, or else a sibling of this one named as the owner's checkout
is (CampfireWebsite) or as a plain clone is (campfire-website). Where none is there, the files go to the fallback folder
instead, and it says so. A file the site no longer links stays where it is: remove it there.

Usage: python3 -I website_screenshots.py <render folder>/Website <fallback folder> [--link-preview <png>] [--website <repo>]
"""

import argparse
import pathlib
import re
import sys

from PIL import Image, ImageChops, ImageFilter

SIBLINGS = ("CampfireWebsite", "campfire-website")
# The page draws each device no wider than it ever needs: at its widest (a 768 pixel window, two columns becoming one)
# a laptop or a tablet screen is about 600 CSS pixels across, so 1200 device pixels on a 2x screen, and a phone 170, so
# 340. Each is saved at about that, in its own proportions, which keeps the text sharp at the fewest bytes; the 14 of
# the text size demo load together.
SIZES = {"phone": (450, 978), "tablet": (1200, 900), "laptop": (1200, 753)}
OG_SIZE = (1200, 630)
QUALITY = 86
BEAT = re.compile(r"(?P<name>.+)-beat-(?P<beat>\d+)-(?P<theme>light|dark)")
# A pixel a beat changes by more than this (of 255, after scaling) is the beat's, with a few pixels around it, so that
# the layer takes the edges of what lights up rather than cutting them where the change fades into the anti-aliasing.
BEAT_THRESHOLD = 2
BEAT_MARGIN = 5


def website_assets(website):
    """The website repository's assets folder: the one asked for, which has to exist, or the first sibling found."""
    if website is not None:
        assets = website / "assets"
        if not assets.is_dir():
            sys.exit(f"{website} is not the website's repository: it has no assets folder.")
        return assets
    projects = pathlib.Path(__file__).resolve().parents[5]
    return next((projects / name / "assets" for name in SIBLINGS if (projects / name / "assets").is_dir()), None)


def scaled(path, size):
    return Image.open(path).convert("RGB").resize(size, Image.Resampling.LANCZOS)


def save_beats(renders, screenshots, name, theme):
    """The beats of the shot called name in theme, as a layer each, transparent wherever the beat changes nothing."""
    size = SIZES[name.split("-")[0]]
    still = scaled(renders / f"{name}-{theme}.png", size)
    beats = sorted(renders.glob(f"{name}-beat-*-{theme}.png"), key=lambda path: int(BEAT.fullmatch(path.stem)["beat"]))
    layers = Image.new("RGBA", (size[0] * len(beats), size[1]), (0, 0, 0, 0))
    for index, path in enumerate(beats):
        beat = scaled(path, size)
        changed = ImageChops.difference(still, beat).convert("L").point(lambda value: 255 if value > BEAT_THRESHOLD else 0)
        layers.paste(beat, (size[0] * index, 0), changed.filter(ImageFilter.MaxFilter(BEAT_MARGIN)))
    output = screenshots / f"{name}-beats-{theme}.webp"
    # Lossless, since what is drawn is a few flat blocks and the rest is empty: smaller than lossy, and without a seam.
    layers.save(output, "WEBP", lossless=True, method=6)
    print(f"{output} {len(beats)} × {size} {output.stat().st_size // 1024} KB")


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
    for name, theme in sorted({(match["name"], match["theme"]) for match in map(BEAT.fullmatch, (source.stem for source in renders)) if match}):
        save_beats(arguments.renders, screenshots, name, theme)
    for source in renders:
        if BEAT.fullmatch(source.stem):
            continue
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
