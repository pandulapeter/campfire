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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.pandulapeter.campfire.abc.api.AbcRenderer
import com.pandulapeter.campfire.abc.api.AbcRendering
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The app's [AbcRenderer], provided once by `CampfireApp`; where nothing provides one, ABC stays text. */
internal val LocalAbcRenderer = staticCompositionLocalOf<AbcRenderer?> { null }

/**
 * The staff notation of a song's `abc` runs, by tune, as last rendered: a run with none (not yet, or never, because it
 * failed) is drawn as its text. A new instance arrives once with every tune of the song, so the sections it changes are
 * measured again once rather than tune by tune.
 */
@Immutable
internal class SongNotations(
    private val rendered: RenderedNotations,
    private val painter: NotationPainter?,
    private val unitSize: Float,
) {
    /** What [run] is drawn as, or null where it is drawn as text. */
    fun of(run: List<ChordProLine>): NotationRun? {
        val tune = run.abcTune() ?: return null
        val rendering = rendered.renderings[tune] ?: return null
        val painter = painter ?: return null
        return NotationRun(
            systems = painter.paintOf(rendering.drawing, rendering.unitSize),
            measuredUnitSize = rendering.unitSize,
            unitSize = unitSize,
            isRerendered = tune in rendered.rerenderedTunes,
        )
    }

    companion object {
        val NONE = SongNotations(RenderedNotations.NONE, painter = null, unitSize = 1f)
    }
}

/**
 * The systems one run is drawn as, their words laid out at [measuredUnitSize] pixels to the SVG unit, and the pixels to
 * the unit the song's text size asks for now ([unitSize]), which differ while a pinch waits for its rendering. [isRerendered]
 * says the run was notation before this rendering too, so it replaces one the reader asked to change (a width, a text
 * size, a transposition) and fades in, where the first rendering, replacing the text, is data arriving.
 */
internal class NotationRun(val systems: List<NotationSystemPaint>, val measuredUnitSize: Float, val unitSize: Float, val isRerendered: Boolean)

/**
 * What [rememberSongNotations] renders for: every tune of the song with the key change it stands under
 * ([abcTuneOffsets]), the page and unit it is laid out at, its chords, and the song's title, which a tune named the
 * same leaves to the page.
 */
internal data class NotationRequest(
    val tunes: Map<String, Int>,
    val width: Int,
    val unitSize: Float,
    val chords: NotationChordSpelling,
    val songTitle: String?,
)

/** One tune's drawing and the pixels to the SVG unit it was rendered for. */
internal class NotationRendering(val drawing: NotationDrawing, val unitSize: Float)

/** The renderings made for [request] (null before the first), and the tunes among them that had one before it. */
internal class RenderedNotations(
    val request: NotationRequest?,
    val renderings: Map<String, NotationRendering>,
    val rerenderedTunes: Set<String>,
) {
    companion object {
        val NONE = RenderedNotations(request = null, renderings = emptyMap(), rerenderedTunes = emptySet())
    }
}

/**
 * Renders every `abc` run of the song's [blocks] for a page [width] SVG units wide, each unit [unitSize] pixels, with its chords
 * named as [chords] says and the title it repeats of [songTitle] left out, once the width is known, and again whenever
 * one of those changes. Until the first rendering, and for a tune that fails, the runs stay text, which is what the
 * first frame shows: the notation replaces the text when it arrives rather than animating in over a wrong first frame.
 *
 * A width or a unit size alone changes on every frame of a window being resized or a pinch, while the systems before
 * it are scaled to follow, so it is rendered only once it has stopped changing ([RERENDER_DELAY_MILLIS]); a new tune
 * or a new transposition is rendered at once.
 */
@Composable
internal fun rememberSongNotations(
    blocks: List<ChordProBlock>,
    width: Int?,
    unitSize: Float,
    chords: NotationChordSpelling,
    songTitle: String?,
    painter: NotationPainter,
): SongNotations {
    val renderer = LocalAbcRenderer.current
    val tunes = remember(blocks) { abcTuneOffsets(blocks) }
    var rendered by remember(renderer) { mutableStateOf(RenderedNotations.NONE) }
    val request = if (width == null || width <= 0 || tunes.isEmpty()) null else NotationRequest(tunes, width, unitSize, chords, songTitle)
    LaunchedEffect(renderer, request) {
        if (renderer == null || request == null || request == rendered.request) return@LaunchedEffect
        val previous = rendered.request
        val isOnlyResized = previous != null && previous.tunes == request.tunes && previous.chords == request.chords && previous.songTitle == request.songTitle
        if (isOnlyResized) delay(RERENDER_DELAY_MILLIS)
        val renderings = request.tunes.mapNotNull { (tune, offset) -> render(renderer, tune, offset, request)?.let { tune to it } }.toMap()
        // A tune whose text alone changed (an edit) is new rather than re-rendered, and arrives the way the edit does.
        val isRerendering = previous != null && (previous.width != request.width || previous.unitSize != request.unitSize || previous.chords != request.chords)
        rendered = RenderedNotations(
            request = request,
            renderings = renderings,
            rerenderedTunes = if (isRerendering) renderings.keys.intersect(rendered.renderings.keys) else emptySet(),
        )
    }
    return remember(rendered, painter, unitSize) { if (rendered.renderings.isEmpty()) SongNotations.NONE else SongNotations(rendered, painter, unitSize) }
}

private suspend fun render(renderer: AbcRenderer, tune: String, offset: Int, request: NotationRequest): NotationRendering? =
    renderNotationDrawing(renderer, tune, request.width, request.chords.movedBy(offset), request.songTitle)?.let { NotationRendering(it, request.unitSize) }

/**
 * [tune] engraved for a page [width] SVG units wide and read into a drawing, its staff moved and its chords named as
 * [chords] says and the title it repeats of [songTitle] left out; null where it cannot be rendered or drawn, which
 * leaves it text. What the song page and the PDF both draw.
 */
internal suspend fun renderNotationDrawing(
    renderer: AbcRenderer,
    tune: String,
    width: Int,
    chords: NotationChordSpelling,
    songTitle: String?,
): NotationDrawing? = when (val rendering = renderer.render(tune = abcTuneWithoutSongTitle(tune, songTitle), width = width, transposition = chords.semitones)) {
    is AbcRendering.Systems -> withContext(Dispatchers.Default) {
        parseNotationDrawing(rendering.systems)?.let { drawing ->
            val symbols = abcChordSymbols(tune)
            drawing.withChordNames(symbols, abcChordNames(tune, symbols, chords), chords.semitones)
        }
    }

    is AbcRendering.Failed -> {
        println("Could not render an ABC tune: ${rendering.message}")
        null
    }
}

/** The ABC text of a run of `{start_of_abc}` lines, or null for any other run. */
internal fun List<ChordProLine>.abcTune(): String? {
    if (isEmpty() || any { it !is ChordProLine.Verbatim || it.environment != ABC_ENVIRONMENT }) return null
    return joinToString(separator = "\n") { (it as ChordProLine.Verbatim).text }
}

private const val ABC_ENVIRONMENT = "abc"
private const val RERENDER_DELAY_MILLIS = 300L
