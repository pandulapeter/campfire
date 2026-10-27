/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

/** One symbol of abc2svg's music font ([abcMusicGlyphs]): its advance and outline in font units, the y axis down. */
internal class AbcMusicGlyph(val advance: Int, val path: String) {
    companion object {
        /** The font units of an em, which a glyph is scaled from to the font size it is drawn at. */
        const val UNITS_PER_EM = 1024f
    }
}
