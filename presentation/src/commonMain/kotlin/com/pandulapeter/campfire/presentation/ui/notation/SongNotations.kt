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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.pandulapeter.campfire.abc.api.AbcRenderer
import com.pandulapeter.campfire.abc.api.AbcRendering
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The app's [AbcRenderer], provided once by `CampfireApp`; where nothing provides one, ABC stays text. */
internal val LocalAbcRenderer = staticCompositionLocalOf<AbcRenderer?> { null }

/**
 * The staff notation of a song's `abc` runs, by tune, as last rendered: a run with none (not yet, or never, because it
 * failed) is drawn as its text. A new instance arrives once with every tune of the song, so the sections it changes are
 * measured again once rather than tune by tune. A tune whose text changed is drawn as the rendering of the tune it
 * replaced ([standIns], see [notationStandInsOf]) until its own arrives, so an edit keeps its staff meanwhile.
 */
@Immutable
internal class SongNotations(
    private val rendered: RenderedNotations,
    private val standIns: Map<AbcTune, AbcTune>,
    private val unitSize: Float,
) {
    /**
     * How many lines of the song each system of each tune plays ([playedLinesOf] its bars), in the systems the slots draw,
     * for a song that scrolls by itself. Made once per rendering, so it is equal across the frames of a pinch.
     */
    val systemLines: Map<AbcTune, List<Int>> = rendered.systemLines

    /** What [run], standing after a key change of [tuneOffset] semitones, is drawn as, or null where it is drawn as text. */
    fun of(run: List<ChordProLine>, tuneOffset: Int): NotationRun? {
        val tune = AbcTune(run.abcTune() ?: return null, tuneOffset)
        val rendering = rendered.renderings[tune] ?: standIns[tune]?.let { rendered.renderings[it] } ?: return null
        return NotationRun(
            systems = rendering.systems,
            measuredUnitSize = rendering.unitSize,
            unitSize = unitSize,
            isRerendered = tune in rendered.rerenderedTunes,
            reading = rendering.reading,
        )
    }

    /**
     * The rendering itself where it brings tunes their first staff and re-renders none, so that the pass laying it out
     * places the song at once rather than moving it (`SectionGlides`); null otherwise. A rendering that re-renders any
     * tune is a change the reader made and is narrated whole. An instance rebuilt for the same rendering (a pinch changes
     * only the unit) hands the same token, so it is never a second arrival.
     */
    val arrival: Any? get() = rendered.takeIf { it.arrivingTunes.isNotEmpty() && it.rerenderedTunes.isEmpty() }

    companion object {
        val NONE = SongNotations(RenderedNotations.NONE, standIns = emptyMap(), unitSize = 1f)
    }
}

/**
 * The systems one run is drawn as, their words laid out at [measuredUnitSize] pixels to the SVG unit, and the pixels to
 * the unit the song's text size asks for now ([unitSize]), which differ while a pinch waits for its rendering. [isRerendered]
 * says the run was notation before this rendering too, so it replaces one the reader asked to change (a width, a text
 * size, a transposition) and cross-fades with it, where the first rendering, replacing the text, is data arriving.
 * [reading] is what a screen reader is told the run is.
 */
internal class NotationRun(
    val systems: List<NotationSystemPaint>,
    val measuredUnitSize: Float,
    val unitSize: Float,
    val isRerendered: Boolean,
    val reading: NotationReading,
)

/**
 * What [rememberSongNotations] renders for: every tune of the song under the key change it stands after ([abcTunesOf]), the page and unit it is laid out at, its chords, the song's title, which a tune named the
 * same leaves to the page, and what its words are laid out with, so a new density or style paints again.
 */
internal data class NotationRequest(
    val tunes: Set<AbcTune>,
    val width: Int,
    val unitSize: Float,
    val chords: NotationChordSpelling,
    val songTitle: String?,
    val painting: NotationPainting,
)

/** What a tune's words are laid out with: the page's density, direction, fonts, and the lyrics' unscaled style. */
internal data class NotationPainting(
    val density: Density,
    val layoutDirection: LayoutDirection,
    val fontResolver: FontFamily.Resolver,
    val textStyle: TextStyle,
) {
    /** A painter of its own, for one job on any thread: a painter keeps caches that are not thread safe. */
    fun newPainter() = NotationPainter(
        textMeasurer = TextMeasurer(fontResolver, density, layoutDirection, cacheSize = 0),
        density = density,
        textStyle = textStyle,
    )
}

/**
 * One tune's drawing, the pixels to the SVG unit it was rendered for, and its systems painted at that unit, kept for as
 * long as the rendering stands so the screen never paints during composition.
 */
internal class NotationRendering(
    val drawing: NotationDrawing,
    val unitSize: Float,
    val systems: List<NotationSystemPaint>,
    val reading: NotationReading,
)

/**
 * The renderings made for [request] (null before the first), the tunes among them that had one before it, and the ones
 * that arrive as data ([arrivingTunesOf]).
 */
internal class RenderedNotations(
    val request: NotationRequest?,
    val renderings: Map<AbcTune, NotationRendering>,
    val rerenderedTunes: Set<AbcTune>,
    val arrivingTunes: Set<AbcTune>,
) {
    /** The lines each system of each tune plays (see [SongNotations.systemLines]). */
    val systemLines: Map<AbcTune, List<Int>> = renderings.mapValues { (_, rendering) -> rendering.drawing.systems.map { playedLinesOf(it.bars) } }

    companion object {
        val NONE = RenderedNotations(request = null, renderings = emptyMap(), rerenderedTunes = emptySet(), arrivingTunes = emptySet())
    }
}

/**
 * The tunes of a new rendering that arrive as data: rendered now and not before, and not replacing a staff the reader
 * changed (or a stand-in), which are [rerendered]. Their relayout places the song at once rather than moving it.
 */
internal fun <T> arrivingTunesOf(previous: Set<T>, rendered: Set<T>, rerendered: Set<T>): Set<T> = rendered - previous - rerendered

/** The SVG units a tune is engraved across on a page [availableWidth] wide: its widest column less a card's padding. */
internal fun notationPageWidth(availableWidth: Dp, maxColumnWidth: Dp, cardPadding: Dp, notationUnit: Dp): Int? =
    if (!availableWidth.isSpecified || availableWidth <= 0.dp) {
        null
    } else {
        ((minOf(availableWidth, maxColumnWidth) - cardPadding * 2) / notationUnit).toInt().takeIf { it > 0 }
    }

/**
 * Renders every `abc` run of the song's [blocks] for a page [width] SVG units wide, each unit [unitSize] pixels, with its chords
 * named as [chords] says and the title it repeats of [songTitle] left out, once the width is known, and again whenever
 * one of those changes. Until the first rendering, and for a tune that fails, the runs stay text, which is what the
 * first frame shows: the notation replaces the text when it arrives rather than animating in over a wrong first frame,
 * and the song around it is placed rather than moved ([SongNotations.arrival]). A song whose every tune the
 * [NotationCache] keeps for this request starts from those renderings, on its first frame.
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
    painting: NotationPainting,
): SongNotations {
    val renderer = LocalAbcRenderer.current
    val cache = LocalNotationCache.current
    val tunes = remember(blocks) { abcTunesOf(blocks) }
    val request = if (width == null || width <= 0 || tunes.isEmpty()) null else NotationRequest(tunes, width, unitSize, chords, songTitle, painting)
    // A song whose tunes were all rendered before at this width, size, and transposition is notation on its first frame.
    var rendered by remember(renderer) { mutableStateOf(request?.let { cache?.renderedFor(it) } ?: RenderedNotations.NONE) }
    val standIns = remember(tunes, rendered) {
        notationStandInsOf(previous = rendered.request?.tunes?.toList().orEmpty(), current = tunes.toList())
            .filterValues { it in rendered.renderings }
    }
    LaunchedEffect(renderer, request) {
        // What the screen draws the edited tunes as while this runs: the composition that launched it computed them for
        // this request, and nothing but this effect changes the renderings meanwhile.
        val shownStandIns = standIns
        if (renderer == null || request == null || request == rendered.request) return@LaunchedEffect
        val previous = rendered.request
        val isOnlyResized = previous != null && previous.tunes == request.tunes && previous.chords == request.chords && previous.songTitle == request.songTitle
        // Nothing to engrave where every tune is kept, so a pinch reversed to a size seen a moment ago is instant.
        val isCached = cache != null && request.tunes.all { cache[request.keyOf(it)] != null }
        if (isOnlyResized && !isCached) delay(RERENDER_DELAY_MILLIS)
        // One painter per run, never one shared across runs: a cancelled run's painting goes on until its next suspension,
        // so two runs can overlap and would race on the painter's caches.
        val painter = request.painting.newPainter()
        val renderings = request.tunes.mapNotNull { tune ->
            ensureActive() // a cancelled run (a newer request) stops between tunes rather than painting the rest
            val key = request.keyOf(tune)
            (cache?.get(key) ?: render(renderer, painter, tune, request)?.also { cache?.set(key, it) })?.let { tune to it }
        }.toMap()
        val isRerendering = previous != null && (previous.width != request.width || previous.unitSize != request.unitSize || previous.chords != request.chords)
        // An edited tune replaces the staff it was drawn as meanwhile, and cross-fades with it; one with no stand-in (a
        // block just added) arrives as data, replacing its text at once.
        val rerenderedTunes = (if (isRerendering) renderings.keys.intersect(rendered.renderings.keys) else emptySet()) +
            renderings.keys.intersect(shownStandIns.keys)
        rendered = RenderedNotations(
            request = request,
            renderings = renderings,
            rerenderedTunes = rerenderedTunes,
            arrivingTunes = arrivingTunesOf(previous = rendered.renderings.keys, rendered = renderings.keys, rerendered = rerenderedTunes),
        )
    }
    return remember(rendered, standIns, unitSize) {
        if (rendered.renderings.isEmpty()) SongNotations.NONE else SongNotations(rendered, standIns, unitSize)
    }
}

private suspend fun render(renderer: AbcRenderer, painter: NotationPainter, tune: AbcTune, request: NotationRequest): NotationRendering? =
    renderNotationDrawing(renderer, tune.text, request.width, request.chords.movedBy(tune.offset), request.songTitle)?.let { drawing ->
        withContext(Dispatchers.Default) {
            NotationRendering(
                drawing = drawing,
                unitSize = request.unitSize,
                systems = painter.paint(drawing, request.unitSize),
                reading = notationReadingOf(tune.text, request.songTitle, drawing.chordNames),
            )
        }
    }

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
