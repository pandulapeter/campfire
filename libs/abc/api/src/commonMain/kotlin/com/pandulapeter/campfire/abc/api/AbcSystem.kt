/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.api

/**
 * One image of an [AbcRendering.Systems]: [svg] is a whole `<svg>` document, [height] SVG user units tall and as wide
 * as the page the tune was rendered at.
 *
 * Music symbols are characters of the renderer's music font (SMuFL code points), so drawing them takes that font's
 * outlines rather than a font of the platform's. Chord symbols carry the class [CHORD_SYMBOL_CLASS] beside their font
 * class, so that they can be coloured apart from the rest.
 *
 * Lyrics carry the class [LYRICS_CLASS], and their font is [LYRICS_ROOM] times the size they are meant to be drawn
 * at: the renderer spaces the notes for the words as its own width tables measure them, which are narrower than the
 * app's typeface, so a lyric drawn at its font's size could run into the next one.
 */
public data class AbcSystem(
    val svg: String,
    val height: Float,
) {
    public companion object {
        public const val CHORD_SYMBOL_CLASS: String = "gch"
        public const val LYRICS_CLASS: String = "lyr"
        public const val LYRICS_ROOM: Float = 1.1f
    }
}
