# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Draws the flat, natural, and sharp signs (U+266D, U+266E, U+266F) into the bundled Inter files, which have none.

Inter is the face every chord name is shown in, and without these glyphs a ♭ or ♯ would fall back to whatever font the
platform has (Skia's Roboto on the web, nothing at all in some PDF viewers), so a chord would look and measure
differently on every platform. The signs are built from each file's own stem width and cap height, so the three weights
match the letters around them, and sit raised the way chord symbols set them (`B♭m7`).

Rerunning it replaces the glyphs it drew, so it is run again on any new Inter subset:

    pip install fonttools skia-pathops
    python3 tools/fonts/accidentals.py presentation/src/commonMain/composeResources/font/inter_*.ttf

Inter's license reserves no font name, so the modified files keep theirs.
"""

import sys

import pathops
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont

FLAT, NATURAL, SHARP = 0x266D, 0x266E, 0x266F


def stem_width(font):
    """The width of the font's `l`, which every stroke of the signs is measured from."""
    glyphs = font.getGlyphSet()
    pen = BoundsPen(glyphs)
    glyphs[font.getBestCmap()[ord("l")]].draw(pen)
    return pen.bounds[2] - pen.bounds[0]


def polygon(points):
    path = pathops.Path()
    path.moveTo(*points[0])
    for point in points[1:]:
        path.lineTo(*point)
    path.close()
    return path


def bar(x0, x1, y_left, rise, thickness):
    """A slanted bar from x0 to x1 whose centre line starts at y_left and rises by `rise`, `thickness` tall."""
    half = thickness / 2
    return polygon([(x0, y_left - half), (x1, y_left + rise - half), (x1, y_left + rise + half), (x0, y_left + half)])


def union(paths):
    result = pathops.Path()
    for path in paths:
        result = pathops.op(result, path, pathops.PathOp.UNION)
    return result


def sharp(stem, bottom, top):
    """Two thin uprights, the right one set higher, crossed by two thick bars rising to the right."""
    vertical = stem * 0.72
    thickness = stem * 0.86
    gap = 250 + stem * 0.25
    overhang = 150 + stem * 0.15
    rise = 190
    stems_left = overhang
    stems_right = stems_left + 2 * vertical + gap
    right = stems_right + overhang
    height = top - bottom
    offset = rise * 0.6
    paths = [
        polygon([(stems_left, bottom), (stems_left + vertical, bottom), (stems_left + vertical, top - offset), (stems_left, top - offset)]),
        polygon([(stems_right - vertical, bottom + offset), (stems_right, bottom + offset), (stems_right, top), (stems_right - vertical, top)]),
    ]
    for centre in (0.36, 0.70):
        paths.append(bar(0, right, bottom + height * centre - rise / 2, rise, thickness))
    return union(paths), right


def natural(stem, bottom, top):
    """The left upright comes down to the lower bar, the right one goes up to the upper bar, each cut along its slope."""
    vertical = stem * 0.72
    thickness = stem * 0.86
    gap = 220 + stem * 0.5
    rise = 150
    width = 2 * vertical + gap
    height = top - bottom
    half = thickness / 2
    slope = rise / width
    low_left = bottom + height * 0.3 - rise / 2
    high_left = bottom + height * 0.66 - rise / 2
    paths = [
        polygon([(0, low_left - half), (vertical, low_left - half + slope * vertical), (vertical, top), (0, top)]),
        polygon([
            (width - vertical, bottom),
            (width, bottom),
            (width, high_left + rise + half),
            (width - vertical, high_left + rise + half - slope * vertical),
        ]),
        bar(0, width, low_left, rise, thickness),
        bar(0, width, high_left, rise, thickness),
    ]
    return union(paths), width


def flat(stem, bottom, top):
    """An upright with a bowl on its lower half that swells to the right and comes to a point at its foot."""
    vertical = stem * 0.78
    thickness = stem * 1.05
    bowl_top = bottom + (top - bottom) * 0.56
    bowl_width = 330 + stem * 0.75
    x = vertical
    right = x + bowl_width
    bowl_height = bowl_top - bottom
    outer = pathops.Path()
    outer.moveTo(x, bottom)
    outer.cubicTo(x + bowl_width * 0.55, bottom + bowl_height * 0.22, right, bottom + bowl_height * 0.48, right, bottom + bowl_height * 0.72)
    outer.cubicTo(right, bowl_top - bowl_height * 0.06, x + bowl_width * 0.62, bowl_top + 30, x + bowl_width * 0.32, bowl_top + 20)
    outer.cubicTo(x + bowl_width * 0.1, bowl_top + 14, x, bowl_top - 50, x - 1, bowl_top - 130)
    outer.lineTo(x - 1, bottom)
    outer.close()
    inner_right = right - thickness
    inner_bottom = bottom + thickness * 0.75
    inner_top = bowl_top - thickness * 0.62
    inner_width = inner_right - x
    inner_height = inner_top - inner_bottom
    inner = pathops.Path()
    inner.moveTo(x - 1, inner_bottom)
    inner.cubicTo(x + inner_width * 0.55, inner_bottom + inner_height * 0.25, inner_right, inner_bottom + inner_height * 0.5, inner_right, inner_bottom + inner_height * 0.74)
    inner.cubicTo(inner_right, inner_top, x + inner_width * 0.55, inner_top + 10, x + inner_width * 0.25, inner_top - 15)
    inner.cubicTo(x + 10, inner_top - 25, x - 1, inner_top - 60, x - 1, inner_top - 100)
    inner.close()
    bowl = pathops.op(outer, inner, pathops.PathOp.DIFFERENCE)
    return union([polygon([(0, bottom), (vertical, bottom), (vertical, top), (0, top)]), bowl]), right


def add_glyph(font, codepoint, name, outline, width, side_bearing):
    shifted = pathops.Path()
    outline.draw(TransformPen(shifted.getPen(), (1, 0, 0, 1, side_bearing, 0)))
    shifted.simplify()
    pen = TTGlyphPen(None)
    # TrueType outlines run clockwise, the opposite of what Skia's path operations produce.
    shifted.draw(Cu2QuPen(pen, max_err=1.0, reverse_direction=True))
    glyph = pen.glyph()
    glyph.recalcBounds(font["glyf"])
    font["glyf"][name] = glyph
    if name not in font.getGlyphOrder():
        font.setGlyphOrder(font.getGlyphOrder() + [name])
    font["hmtx"][name] = (round(width + 2 * side_bearing), glyph.xMin)
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap[codepoint] = name


def main(paths):
    for path in paths:
        font = TTFont(path)
        stem = stem_width(font)
        cap = font["OS/2"].sCapHeight
        bottom = round(cap * 0.14)
        for codepoint, (outline, width), side_bearing in (
            (SHARP, sharp(stem, bottom, cap), 70),
            (FLAT, flat(stem, bottom, cap + 40), 90),
            (NATURAL, natural(stem, bottom, cap), 110),
        ):
            add_glyph(font, codepoint, "uni%04X" % codepoint, outline, width, side_bearing)
        font["maxp"].numGlyphs = len(font.getGlyphOrder())
        font.save(path)


if __name__ == "__main__":
    main(sys.argv[1:])
