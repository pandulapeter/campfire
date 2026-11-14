# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
The store images a release publishes, for screenshots.yml, which checks them, and the store scripts, which replace each
store's images with them.

    store_images.py --check <folder>

The folder is what the screenshot tool's `--frame store` writes (the `store/` folder of the run's `screenshots`
artifact): `<store>/<language>/<device>-<n>.png` for the listing, numbered in its order, and the stores' other images
by name (`google-play/<language>/feature-graphic.png`). Which of them a store's API takes, and into which of its sets,
is SETS; the rest stay by hand (MANUAL, with the reason), and a file neither names fails the check, so a device or an
image added to the tool is never left out unnoticed. Each set is held to its store's rules (RULES): how many, the exact
sizes or the sides' range, the long side against the short, no alpha channel, the file's size, PNG.

With --check (screenshots.yml, before the artifact is uploaded): every store has every language of the app, every set
there, and every image within its rules. Prints a Markdown table of the counts and a list of what MANUAL leaves to be
uploaded by hand, where it is in the artifact, for the job's summary, each failure as an error, and exits 1 on any
failure. Standard library only, like the store scripts that read it.
"""

import re
import struct
import sys
from dataclasses import dataclass
from pathlib import Path

from store_listings import app_languages

# The framing's name of a listing device or an image -> the set the store's API puts it in: Play's image types, App
# Store Connect's screenshot display types, Partner Center's image types. The Mac App Store is App Store Connect's too.
SETS = {
    "google-play": {
        "phone": "phoneScreenshots",
        "small-tablet": "sevenInchScreenshots",
        "large-tablet": "tenInchScreenshots",
        "feature-graphic": "featureGraphic",
    },
    "app-store": {
        "iphone": "APP_IPHONE_67",
        "ipad": "APP_IPAD_PRO_3GEN_129",
    },
    "mac-app-store": {
        "mac": "APP_DESKTOP",
    },
    "microsoft-store": {
        "windows": "Screenshot",
    },
}
# Framed, and uploaded by hand, since no API takes them.
MANUAL = {
    "google-play": {
        "chromebook": "Play's API has no image type for Chromebook screenshots",
        "icon": "set once, in the Play Console",
    },
    "app-store": {
        "iphone-duo": "App Store Connect has no display type for a foldable iPhone yet",
        "header": "the product page's header is not in App Store Connect's API",
        "search-results": "the search results image is not in App Store Connect's API",
    },
    "mac-app-store": {},
    "microsoft-store": {
        "poster-art": "set once, in Partner Center",
        "box-art": "set once, in Partner Center",
    },
}

NAME = re.compile(r"(?P<name>[a-z]+(?:-[a-z]+)*)(?:-(?P<number>\d+))?\.png")
MEGABYTE = 1024 * 1024


@dataclass(frozen=True)
class Rules:
    """What a store takes in one set: from minimum to maximum images, each one of sizes or with sides in the ranges."""
    minimum: int
    maximum: int
    sizes: frozenset = None
    short_side: tuple = None
    long_side: tuple = None
    max_ratio: float = None
    max_bytes: int = None


# Play: https://support.google.com/googleplay/android-developer/answer/9866151 (sides 320-3840, the long one at most
# twice the short, 8 MB, at least two phone screenshots); App Store Connect: the screenshot specifications of each display
# type (exact sizes, ten at most, no alpha); Partner Center: 1366 x 768 or larger, at most 3840 x 2160, 50 MB, ten.
PLAY_SCREENSHOT = {"short_side": (320, 3840), "long_side": (320, 3840), "max_ratio": 2.0, "max_bytes": 8 * MEGABYTE}
RULES = {
    "phoneScreenshots": Rules(minimum=2, maximum=8, **PLAY_SCREENSHOT),
    "sevenInchScreenshots": Rules(minimum=1, maximum=8, **PLAY_SCREENSHOT),
    "tenInchScreenshots": Rules(minimum=1, maximum=8, **PLAY_SCREENSHOT),
    "featureGraphic": Rules(minimum=1, maximum=1, sizes=frozenset({(1024, 500)}), max_bytes=15 * MEGABYTE),
    "APP_IPHONE_67": Rules(minimum=1, maximum=10, sizes=frozenset({(1320, 2868), (2868, 1320), (1290, 2796), (2796, 1290)})),
    "APP_IPAD_PRO_3GEN_129": Rules(minimum=1, maximum=10, sizes=frozenset({(2064, 2752), (2752, 2064), (2048, 2732), (2732, 2048)})),
    "APP_DESKTOP": Rules(minimum=1, maximum=10, sizes=frozenset({(1280, 800), (1440, 900), (2560, 1600), (2880, 1800)})),
    "Screenshot": Rules(minimum=1, maximum=10, short_side=(768, 2160), long_side=(1366, 3840), max_bytes=50 * MEGABYTE),
}


def png_info(path):
    """(width, height, whether it has an alpha channel) of a PNG file, or None for a file that is not one."""
    with open(path, "rb") as file:
        if file.read(8) != b"\x89PNG\r\n\x1a\n":
            return None
        width = height = None
        alpha = False
        while True:
            header = file.read(8)
            if len(header) < 8:
                break
            length, kind = struct.unpack(">I4s", header)
            if kind == b"IHDR":
                data = file.read(length)
                width, height, _, color_type = struct.unpack(">IIBB", data[:10])
                # Grey with alpha and RGBA; a palette or a plain colour carries transparency in a tRNS chunk instead.
                alpha = color_type in (4, 6)
                file.seek(4, 1)
            elif kind == b"tRNS":
                alpha = True
                break
            elif kind in (b"IDAT", b"IEND"):
                break
            else:
                file.seek(length + 4, 1)
        return None if width is None else (width, height, alpha)


def images(folder, store, language):
    """{set: [image, …] in the listing's order} of one language of a store, the sets in SETS' order."""
    found = {}
    directory = Path(folder) / store / language
    for path in sorted(directory.glob("*.png")) if directory.is_dir() else []:
        match = NAME.fullmatch(path.name)
        if match and match["name"] in SETS[store]:
            found.setdefault(SETS[store][match["name"]], []).append((int(match["number"] or 0), path))
    order = list(SETS[store].values())
    return {kind: [path for _, path in sorted(found[kind])] for kind in sorted(found, key=order.index)}


def languages(folder, store):
    """The languages a store's images are in."""
    directory = Path(folder) / store
    return sorted(path.name for path in directory.iterdir() if path.is_dir()) if directory.is_dir() else []


def image_errors(path, rules, label):
    info = png_info(path)
    if info is None:
        return [f"{label}: is not a PNG"]
    width, height, alpha = info
    errors = []
    short, long = sorted((width, height))
    if rules.sizes is not None and (width, height) not in rules.sizes:
        accepted = ", ".join(f"{w} x {h}" for w, h in sorted(rules.sizes))
        errors.append(f"{label}: is {width} x {height}, and the store takes {accepted}")
    if rules.short_side is not None and not rules.short_side[0] <= short <= rules.short_side[1]:
        errors.append(f"{label}: its short side is {short}, and the store takes {rules.short_side[0]}-{rules.short_side[1]}")
    if rules.long_side is not None and not rules.long_side[0] <= long <= rules.long_side[1]:
        errors.append(f"{label}: its long side is {long}, and the store takes {rules.long_side[0]}-{rules.long_side[1]}")
    if rules.max_ratio is not None and long > short * rules.max_ratio:
        errors.append(f"{label}: is {width} x {height}, its long side more than {rules.max_ratio:g} times the short")
    if alpha:
        errors.append(f"{label}: has an alpha channel, which the store refuses")
    if rules.max_bytes is not None and path.stat().st_size > rules.max_bytes:
        errors.append(f"{label}: is {path.stat().st_size // MEGABYTE} MB, and the store takes {rules.max_bytes // MEGABYTE}")
    return errors


def check(folder, app_codes=None):
    """([every failure], {store: {set: images in every language}}) of the framed images in folder."""
    folder = Path(folder)
    app_codes = app_languages() if app_codes is None else app_codes
    errors = []
    counts = {}
    for store, sets in SETS.items():
        counts[store] = {kind: 0 for kind in sets.values()}
        present = languages(folder, store)
        for code in app_codes:
            if code not in present:
                errors.append(f"{store}: has no images in {code}")
        for code in present:
            if code not in app_codes:
                errors.append(f"{store}/{code}: is a language the app does not have")
                continue
            for path in sorted((folder / store / code).iterdir()):
                match = NAME.fullmatch(path.name)
                if not match or (match["name"] not in sets and match["name"] not in MANUAL[store]):
                    errors.append(f"{store}/{code}/{path.name}: is neither in store_images.py's SETS nor in its MANUAL")
            found = images(folder, store, code)
            for kind in sets.values():
                rules = RULES[kind]
                paths = found.get(kind, [])
                counts[store][kind] += len(paths)
                if not rules.minimum <= len(paths) <= rules.maximum:
                    errors.append(f"{store}/{code}: has {len(paths)} {kind}, and the store takes {rules.minimum}-{rules.maximum}")
                for path in paths:
                    errors += image_errors(path, rules, f"{store}/{code}/{path.name}")
    return errors, counts


def summary(counts):
    lines = ["| Store | Set | Images |", "|---|---|---|"]
    for store, sets in counts.items():
        lines += [f"| {store} | {kind} | {count} |" for kind, count in sets.items()]
    return "\n".join(lines)


def manual_summary(folder):
    """The images the release cannot upload, where they are in the artifact, and why, for the job's summary."""
    folder = Path(folder)
    lines = ["### Uploaded by hand", "",
             "In the run's `screenshots` artifact, under `store/` (download it from this page, or "
             "`gh run download <run id> -n screenshots`), every language in a folder of its own:", ""]
    for store, names in MANUAL.items():
        for name, reason in names.items():
            found = [path for path in (folder / store).rglob("*.png")
                     if (match := NAME.fullmatch(path.name)) and match["name"] == name]
            if not found:
                continue
            numbered = any(NAME.fullmatch(path.name)["number"] for path in found)
            per_language = any(path.parent != folder / store for path in found)
            pattern = f"{'<language>/' if per_language else ''}{name}{'-<n>' if numbered else ''}.png"
            lines.append(f"- `{store}/{pattern}`: {reason}.")
    return "\n".join(lines)


def main():
    if len(sys.argv) != 3 or sys.argv[1] != "--check":
        print(__doc__.strip().split("\n\n")[1].strip(), file=sys.stderr)
        return 2
    errors, counts = check(sys.argv[2])
    print(summary(counts))
    print()
    print(manual_summary(sys.argv[2]))
    for error in errors:
        print(f"::error::{error}", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
