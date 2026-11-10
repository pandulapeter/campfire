/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.implementation

/**
 * [tune] with its own page geometry, overall scale, and text fonts turned into comments, so that the renderer's header
 * decides them for every tune. Notation programs export the page they were printed on (xml2abc writes `%%scale 0.83`,
 * `%%pagewidth 21.59cm`, and 1 cm margins): read as written, such a tune is engraved for a page twice as wide as the
 * column and then shrunk to fit it, so its staff came out half the size of a hand-written tune's at the same text size.
 * A font of the tune's own for chord symbols, annotations, or lyrics also drops the class the app recognises them by.
 * Only the directive's first two characters change (`%%` or `I:` becomes `% `), so the positions abc2svg reports are
 * still the tune's. A voice's own scale (`%%staffscale`, `%%voicescale`, `V:… scale=`) is relative to the rest of the
 * score and is kept.
 */
internal fun withoutOwnPageFormat(tune: String): String = PAGE_FORMAT.replace(tune) { match -> COMMENT + match.value.drop(COMMENT.length) }

private val PAGE_FORMAT = Regex(
    """(?m)^(?:%%|I:)[ \t]*(?:scale|pagescale|pagewidth|leftmargin|rightmargin|staffwidth|printmargin|print-leftmargin|landscape|gchordfont|annotationfont|vocalfont)\b""",
)
private const val COMMENT = "% "
