# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Contact sheets of the images a release publishes, for screenshots.yml: what a run is about to put into every store and
onto the website, to look at on its artifact without downloading 600 images.

    contact_sheets.py <store folder> <website folder> <output folder>

One sheet per store row - a store's device or image, every language a row of it, in the listing's order - named
`<store>-<device>.jpg`, and one of the website's screenshots, a row per language folder (`website.jpg`). The store
folder is the framing's (`<store>/<language>/<device>-<n>.png`), the website folder the website's own tree, whose
`assets/screenshots` holds the English images and a folder per language. Needs Pillow.
"""

import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

THUMBNAIL_HEIGHT = 320
GAP = 16
LABEL_WIDTH = 120
BACKGROUND = (32, 32, 36)
LABEL = (230, 230, 230)
QUALITY = 85
NAME = re.compile(r"(?P<name>[a-z]+(?:-[a-z]+)*?)(?:-(?P<number>\d+))?")


def sheet(rows, output):
    """Draws rows - [(label, [image path, …])] - each image scaled to one height, and saves the sheet as output."""
    thumbnails = []
    for label, paths in rows:
        row = []
        for path in paths:
            with Image.open(path) as image:
                image = image.convert("RGB")
                width = max(1, round(image.width * THUMBNAIL_HEIGHT / image.height))
                row.append(image.resize((width, THUMBNAIL_HEIGHT), Image.Resampling.LANCZOS))
        thumbnails.append((label, row))
    width = LABEL_WIDTH + max((sum(image.width + GAP for image in row) for _, row in thumbnails), default=0) + GAP
    height = GAP + len(thumbnails) * (THUMBNAIL_HEIGHT + GAP)
    canvas = Image.new("RGB", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(canvas)
    for index, (label, row) in enumerate(thumbnails):
        top = GAP + index * (THUMBNAIL_HEIGHT + GAP)
        draw.text((GAP, top), label, fill=LABEL)
        left = LABEL_WIDTH
        for image in row:
            canvas.paste(image, (left, top))
            left += image.width + GAP
    output.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(output, "JPEG", quality=QUALITY)
    print(f"{output.name}: {len(rows)} rows, {sum(len(paths) for _, paths in rows)} images")


def number(path):
    match = NAME.fullmatch(path.stem)
    return int(match["number"] or 0) if match else 0


def store_rows(store):
    """{device or image: [(language, [its images in order])]} of one store's folder."""
    rows = {}
    for language in sorted(path for path in store.iterdir() if path.is_dir()):
        groups = {}
        for path in sorted(language.glob("*.png"), key=number):
            match = NAME.fullmatch(path.stem)
            groups.setdefault(match["name"] if match else path.stem, []).append(path)
        for name, paths in groups.items():
            rows.setdefault(name, []).append((language.name, paths))
    return rows


def main(stores, website, output):
    for store in sorted(path for path in stores.iterdir() if path.is_dir()):
        for name, rows in store_rows(store).items():
            sheet(rows, output / f"{store.name}-{name}.jpg")
    screenshots = website / "assets/screenshots"
    rows = [("en", sorted(screenshots.glob("*.webp")))]
    rows += [(folder.name, sorted(folder.glob("*.webp"))) for folder in sorted(screenshots.iterdir()) if folder.is_dir()]
    sheet([row for row in rows if row[1]], output / "website.jpg")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__.strip().split("\n\n")[1].strip())
    main(*(Path(argument) for argument in sys.argv[1:]))
