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

/**
 * A tune's staff notation as plain data, read from one rendering by [parseNotationDrawing]: what is drawn of each
 * system, every coordinate in the system's own SVG units, its transforms already applied. Nothing here knows a
 * density, a font, or a colour; [NotationPainter] turns it into what a canvas draws. [chordNames] are the page's names
 * of the tune's chord symbols, in the order written ([withChordNames]), none before they are given.
 */
internal class NotationDrawing(val systems: List<NotationSystem>, val chordNames: List<String> = emptyList())

/** One staff system (or the tune's header), [width] by [height] units. */
internal class NotationSystem(
    val width: Float,
    val height: Float,
    val shapes: List<NotationShape>,
    val glyphs: List<NotationGlyph>,
    val texts: List<NotationText>,
)

/** SVG path data ([path]) in its own units, placed by [transform], filled where it has no [stroke]. */
internal class NotationShape(
    val path: String,
    val transform: NotationTransform,
    val stroke: NotationStroke?,
)

/** The width and dashes of a stroked [NotationShape], in the path's own units. */
internal data class NotationStroke(val width: Float, val dashes: List<Float>?)

/** A uniform scale about the origin followed by a move: a point p lands on (x + scale·pₓ, y + scale·pᵧ). */
internal data class NotationTransform(val x: Float, val y: Float, val scale: Float) {

    fun translated(dx: Float, dy: Float) = copy(x = x + scale * dx, y = y + scale * dy)

    fun scaled(factor: Float) = copy(scale = scale * factor)

    fun mapX(value: Float) = x + scale * value

    fun mapY(value: Float) = y + scale * value

    companion object {
        val IDENTITY = NotationTransform(x = 0f, y = 0f, scale = 1f)
    }
}

/** A music symbol with its baseline origin at ([x], [y]), drawn at [size] units to the em. */
internal class NotationGlyph(val glyph: AbcMusicGlyph, val x: Float, val y: Float, val size: Float)

/**
 * Words drawn in the app's typeface ([runs], one after the other from the baseline point ([x], [y])), placed by
 * [anchor] once they are measured, since their width is the typeface's rather than the one abc2svg measured with, and
 * framed where abc2svg boxes them ([isBoxed]: a chord or a part name under `%%gchordbox` or `%%partsbox`).
 */
internal class NotationText(
    val x: Float,
    val y: Float,
    val anchor: NotationAnchor,
    val runs: List<NotationTextRun>,
    val isBoxed: Boolean = false,
)

internal enum class NotationAnchor { START, MIDDLE, END }

/** What a [NotationTextRun.Words] is: a chord symbol takes the chords' colour, a lyric is drawn smaller than measured. */
internal enum class NotationTextRole { CHORD, LYRIC, OTHER }

/** A piece of a [NotationText], its baseline [dy] units under the text's. */
internal sealed interface NotationTextRun {
    val dy: Float

    /** Words in the app's typeface, which a chord symbol and a lyric are drawn apart from the rest by ([role]). */
    data class Words(val text: String, val font: NotationFont, val role: NotationTextRole, override val dy: Float) : NotationTextRun

    /** Music symbols inside a line of words (the note of a tempo, `♩ = 120`). */
    data class Glyphs(val glyphs: List<AbcMusicGlyph>, val size: Float, override val dy: Float) : NotationTextRun
}

/** What abc2svg's CSS says of a font, the family aside: every family but its music font is drawn in the app's own. */
internal data class NotationFont(val size: Float, val isBold: Boolean, val isItalic: Boolean, val isMusic: Boolean) {
    fun scaled(factor: Float) = copy(size = size * factor)
}
