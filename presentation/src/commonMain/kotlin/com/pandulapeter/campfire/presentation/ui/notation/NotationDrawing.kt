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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A tune's staff notation as plain data, read from one rendering by [parseNotationDrawing]: what is drawn of each
 * system, every coordinate in the system's own SVG units, its transforms already applied. Nothing here knows a
 * density, a font, or a colour; [NotationPainter] turns it into what a canvas draws. [chordNames] are the page's names
 * of the tune's chord symbols, in the order written ([withChordNames]), none before they are given.
 */
internal class NotationDrawing(val systems: List<NotationSystem>, val chordNames: List<String> = emptyList())

/**
 * One staff system (or the tune's header), [width] by [height] units, playing [bars] bars (none for a system without
 * music, see [barCountOf]), which is what a song that scrolls by itself spends its time on.
 */
internal class NotationSystem(
    val width: Float,
    val height: Float,
    val shapes: List<NotationShape>,
    val glyphs: List<NotationGlyph>,
    val texts: List<NotationText>,
    val bars: Int,
)

/** SVG path data ([path]) in its own units, placed by [transform], filled where it has no [stroke]. */
internal class NotationShape(
    val path: String,
    val transform: NotationTransform,
    val stroke: NotationStroke?,
)

/** The width and dashes of a stroked [NotationShape], in the path's own units. */
internal data class NotationStroke(val width: Float, val dashes: List<Float>?)

/**
 * An SVG transform: a point p lands on (a·pₓ + c·pᵧ + x, b·pₓ + d·pᵧ + y). abc2svg stretches the brace of a grand
 * staff, mirrors inverted decorations and note heads, and turns arpeggios, glissandi, and some decorations, so shapes
 * and music symbols take any; words, which are laid out upright, take only an [isUpright] one.
 */
internal data class NotationTransform(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
) {
    /** How much it enlarges a length on average, which a stroke's width and a font's size are scaled by. */
    val scale get() = sqrt(abs(a * d - b * c))

    /** Whether it only moves and enlarges, the one way words can be drawn. */
    val isUpright get() = b == 0f && c == 0f && a == d && a > 0f

    /** This transform applied after [inner], as SVG applies the transforms of nested elements and of one list. */
    operator fun times(inner: NotationTransform) = NotationTransform(
        a = a * inner.a + c * inner.b,
        b = b * inner.a + d * inner.b,
        c = a * inner.c + c * inner.d,
        d = b * inner.c + d * inner.d,
        x = a * inner.x + c * inner.y + x,
        y = b * inner.x + d * inner.y + y,
    )

    fun translated(dx: Float, dy: Float) = this * NotationTransform(x = dx, y = dy)

    fun scaled(sx: Float, sy: Float = sx) = this * NotationTransform(a = sx, d = sy)

    fun rotated(degrees: Float): NotationTransform {
        val radians = degrees * PI.toFloat() / 180f
        return this * NotationTransform(a = cos(radians), b = sin(radians), c = -sin(radians), d = cos(radians))
    }

    fun mapX(px: Float, py: Float) = a * px + c * py + x

    fun mapY(px: Float, py: Float) = b * px + d * py + y

    companion object {
        val IDENTITY = NotationTransform()
    }
}

/** A music symbol, its outline's font units placed by [transform] (its baseline origin, size, and any turn or stretch). */
internal class NotationGlyph(val glyph: AbcMusicGlyph, val transform: NotationTransform)

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
