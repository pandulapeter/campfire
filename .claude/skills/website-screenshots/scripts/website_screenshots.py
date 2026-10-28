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
and, when one is given, the link preview the screenshot tool composed as `assets/og-image.jpg`, at the size of the one it
replaces. The renders of a playing click's beats (`<name>-beat-<n>-<theme>.png`) are not copied as screenshots: what each
changes of `<name>-<theme>.png`, the lit beat and the metronome mark's pulse, goes side by side into one transparent
`<name>-beats-<theme>.webp`, a layer per beat at the screenshot's size, which the page fades over it in turn.
The renders of the app's other languages, in a folder per language (`hu/<name>.png`), go to the same folder under
`assets/screenshots` (`hu/<name>.webp`), where the site's pages in that language find them; one that comes out the
same as the English one (nothing in it is translated) is left out, and an earlier copy of it removed, so those pages
show the English file, which the visitor's browser may already have.
The repository is the one passed with --website, or else a sibling of this one named as the owner's checkout
is (CampfireWebsite) or as a plain clone is (campfire-website); where none is there, it stops and says so. A file the site no longer links stays where it is: remove it there.

Usage: python3 -I website_screenshots.py <render folder>/Website [--link-preview <png>] [--website <repo>]
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


def beat_layers(renders, name, theme):
    """The beats of the shot called name in theme, as a layer each, transparent wherever the beat changes nothing."""
    size = SIZES[name.split("-")[0]]
    still = scaled(renders / f"{name}-{theme}.png", size)
    beats = sorted(renders.glob(f"{name}-beat-*-{theme}.png"), key=lambda path: int(BEAT.fullmatch(path.stem)["beat"]))
    layers = Image.new("RGBA", (size[0] * len(beats), size[1]), (0, 0, 0, 0))
    for index, path in enumerate(beats):
        beat = scaled(path, size)
        changed = ImageChops.difference(still, beat).convert("L").point(lambda value: 255 if value > BEAT_THRESHOLD else 0)
        layers.paste(beat, (size[0] * index, 0), changed.filter(ImageFilter.MaxFilter(BEAT_MARGIN)))
    return layers


def same(image, english):
    """Whether image is the English one, so that a language's pages can show the English file instead."""
    return english is not None and image.mode == english.mode and image.size == english.size and ImageChops.difference(image, english).getbbox() is None


def write(image, output, english, **options):
    """Saves image as output, unless it is the same as the English image, in which case any earlier copy is removed."""
    if same(image, english):
        if output.exists():
            output.unlink()
        print(f"{output} left out: the same as the English one")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output, "WEBP", method=6, **options)
    print(f"{output} {image.size} {output.stat().st_size // 1024} KB")


def copy_screenshots(renders, screenshots, english_renders=None):
    """Every render in the folder renders as a screenshot in screenshots, compared with the English renders if given."""
    for name, theme in sorted({(match["name"], match["theme"]) for match in map(BEAT.fullmatch, (source.stem for source in renders.glob("*.png"))) if match}):
        # Lossless, since what is drawn is a few flat blocks and the rest is empty: smaller than lossy, and without a seam.
        english = beat_layers(english_renders, name, theme) if english_renders else None
        write(beat_layers(renders, name, theme), screenshots / f"{name}-beats-{theme}.webp", english, lossless=True)
    for source in sorted(renders.glob("*.png")):
        if BEAT.fullmatch(source.stem):
            continue
        size = SIZES[source.stem.split("-")[0]]
        english = scaled(english_renders / source.name, size) if english_renders and (english_renders / source.name).exists() else None
        write(scaled(source, size), screenshots / f"{source.stem}.webp", english, quality=QUALITY)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("renders", type=pathlib.Path)
    parser.add_argument("--link-preview", type=pathlib.Path)
    parser.add_argument("--website", type=pathlib.Path)
    arguments = parser.parse_args()
    if not any(arguments.renders.glob("*.png")):
        sys.exit(f"There are no renders in {arguments.renders}: render the website set first (--only website).")
    target = website_assets(arguments.website)
    if target is None:
        sys.exit(f"The website's repository is not next to this one ({' or '.join(SIBLINGS)}): pass its path with --website.")
    screenshots = target / "screenshots"
    copy_screenshots(arguments.renders, screenshots)
    for language in sorted(path for path in arguments.renders.iterdir() if path.is_dir()):
        copy_screenshots(language, screenshots / language.name, english_renders=arguments.renders)
    if arguments.link_preview is None:
        print("No link preview given (the screenshot tool composes it with --frame readme), so og-image.jpg is left as it was.")
        return
    og = target / "og-image.jpg"
    size = Image.open(og).size if og.exists() else OG_SIZE
    Image.open(arguments.link_preview).convert("RGB").resize(size, Image.Resampling.LANCZOS).save(og, "JPEG", quality=90, optimize=True)
    print(f"{og} {size} {og.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
