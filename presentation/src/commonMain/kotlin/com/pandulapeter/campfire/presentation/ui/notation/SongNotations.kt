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
import androidx.compose.runtime.withFrameNanos
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
import com.pandulapeter.campfire.chordpro.abc.AbcTranspositions
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.data.model.preferences.UserPreferences.Instrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
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
    private val maxWidth: Float,
    private val placement: NotationPlacement?,
    private val partChoices: NotationPartChoices?,
    private val instrument: Instrument?,
) {
    /**
     * How many lines of the song each system of each tune plays ([playedLinesOf] its bars), in the systems the slots draw,
     * for a song that scrolls by itself. Made once per rendering, so it is equal across the frames of a pinch.
     */
    val systemLines: Map<AbcTune, List<Int>> = rendered.systemLines

    /**
     * What the run of [tune]'s text ([abcTune], null for a run that is no tune), standing after a key change of
     * [tuneOffset] semitones, is drawn as, or null where it is drawn as text.
     */
    fun of(tune: String?, tuneOffset: Int): NotationRun? {
        val abcTune = AbcTune(tune ?: return null, tuneOffset)
        val rendering = renderingOf(abcTune) ?: return null
        return NotationRun(
            systems = rendering.systems,
            measuredUnitSize = rendering.unitSize,
            unitSize = unitSize,
            maxWidth = maxWidth,
            isRerendered = abcTune in rendered.rerenderedTunes,
            reading = rendering.reading,
            onPlaced = { widthPx -> placement?.place(abcTune, widthPx) },
        )
    }

    /**
     * The rendering itself where it brings tunes their first staff and re-renders none, so that the pass laying it out
     * places the song at once rather than moving it (`SectionGlides`); null otherwise. A rendering that re-renders any
     * tune is a change the reader made and is narrated whole. An instance rebuilt for the same rendering (a pinch changes
     * only the unit) hands the same token, so it is never a second arrival.
     */
    /**
     * Where the layout placed the first slot of the run of [tune]'s text, standing after a key change of [tuneOffset]
     * semitones, while it is drawn as its text: the column its first rendering is then engraved for ([NotationPlacement]),
     * so that the staff replaces the text in one step rather than first in the narrowest column's engraving.
     */
    fun placeText(tune: String?, tuneOffset: Int, widthPx: Int) {
        placement?.place(AbcTune(tune ?: return, tuneOffset), widthPx)
    }

    /** What the picker beside the heading of the run of [tune]'s text offers, null where it has none, or where the run is drawn as text. */
    fun partPickerOf(tune: String?, tuneOffset: Int): NotationPartPicker? {
        if (tune == null || renderingOf(AbcTune(tune, tuneOffset)) == null) return null
        return partChoices?.pickerOf(tune, instrument)
    }

    private fun renderingOf(tune: AbcTune) = rendered.renderings[tune] ?: standIns[tune]?.let { rendered.renderings[it] }

    val arrival: Any? get() = rendered.takeIf { it.arrivingTunes.isNotEmpty() && it.rerenderedTunes.isEmpty() }

    companion object {
        val NONE = SongNotations(RenderedNotations.NONE, standIns = emptyMap(), unitSize = 1f, maxWidth = 0f, placement = null, partChoices = null, instrument = null)
    }
}

/**
 * The systems one run is drawn as, their words laid out at [measuredUnitSize] pixels to the SVG unit, and the pixels to
 * the unit the song's text size asks for now ([unitSize]), which differ while a pinch waits for its rendering. [isRerendered]
 * says the run was notation before this rendering too, so it replaces one the reader asked to change (a width, a text
 * size, a transposition) and cross-fades with it, where the first rendering, replacing the text, is data arriving.
 * [reading] is what a screen reader is told the run is. [maxWidth] is how wide, in pixels, the widest column a tune can
 * have here would show it, which is what it asks its column for whatever page it was engraved for, so that a column is
 * never narrowed to the engraving it holds; its first system tells [onPlaced] how wide a column it was given, which the
 * tunes are then engraved for ([NotationPlacement]).
 */
internal class NotationRun(
    val systems: List<NotationSystemPaint>,
    val measuredUnitSize: Float,
    val unitSize: Float,
    val maxWidth: Float,
    val isRerendered: Boolean,
    val reading: NotationReading,
    val onPlaced: (widthPx: Int) -> Unit,
)

/**
 * What [rememberSongNotations] renders for: every tune of the song under the key change it stands after ([abcTunesOf]),
 * the page and unit it is laid out at (and the whole page's width in pixels, [availableWidth], which tells a new page
 * from the tunes only placed at another width), its chords, the song's title, which a tune named the
 * same leaves to the page, what its words are laid out with, so a new density or style paints again, and the part of
 * each score engraved, by the tune's text (a tune not in [parts] is engraved whole). [facts] are what follows from the
 * tunes, parts, and title, worked out once for them; each request carries its own, since a part picked changes a tune's
 * text. The tab fretting in [chords] is left out where no tune has a tab voice, and [songTitle] where no tune is named as
 * the song, so neither changing makes a new request of a song they cannot change.
 */
internal data class NotationRequest(
    val tunes: Set<AbcTune>,
    val width: Int,
    val availableWidth: Int,
    val unitSize: Float,
    val chords: NotationChordSpelling,
    val songTitle: String?,
    val painting: NotationPainting,
    val parts: Map<String, AbcPart>,
    val facts: NotationTuneFacts,
) {
    /** What [tune]'s engraving reads of its text. */
    fun factsOf(tune: AbcTune) = facts[tune] ?: tuneFactsOf(tune.text, parts[tune.text], songTitle)

    /** The text of [tune] engraved: the part shown, where only one is. */
    fun textOf(tune: AbcTune) = factsOf(tune).engravedText
}

/**
 * What a tune's words are laid out with: the page's density, fonts, and the lyrics' unscaled style. Always from the
 * left, as music is, whatever the song's or the app's direction.
 */
internal data class NotationPainting(
    val density: Density,
    val fontResolver: FontFamily.Resolver,
    val textStyle: TextStyle,
) {
    /** A painter of its own, for one job on any thread: a painter keeps caches that are not thread safe. */
    fun newPainter() = NotationPainter(
        textMeasurer = TextMeasurer(fontResolver, density, LayoutDirection.Ltr, cacheSize = 0),
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

/** The SVG units across a column [columnWidth] wide (or the whole page, [availableWidth], where narrower), less a card's padding. */
internal fun notationPageWidth(availableWidth: Dp, columnWidth: Dp, cardPadding: Dp, notationUnit: Dp): Int? =
    if (!availableWidth.isSpecified || availableWidth <= 0.dp) {
        null
    } else {
        ((minOf(availableWidth, columnWidth) - cardPadding * 2) / notationUnit).toInt().takeIf { it > 0 }
    }

/**
 * Renders every `abc` run of the song's [blocks] for the page it is placed at ([NotationPlacement]), first for the
 * narrowest column's, [narrowestWidth] SVG units wide, each unit [unitSize] pixels, and asking for a column of the widest,
 * [widestWidth] units, with its chords
 * named as [chords] says and the title it repeats of [songTitle] left out, once the width is known, and again whenever
 * one of those changes. Until the first rendering, and for a tune that fails, the runs stay text, which is what the
 * first frame shows: the notation replaces the text when it arrives rather than animating in over a wrong first frame,
 * and the song around it is placed rather than moved ([SongNotations.arrival]). A song whose every tune the
 * [NotationCache] keeps for this request starts from those renderings, on its first frame.
 *
 * A width or a unit size alone changes on every frame of a window being resized or a pinch, while the systems before
 * it are scaled to follow, so it is rendered only once it has stopped changing ([RERENDER_DELAY_MILLIS]); a new tune
 * or a new transposition is rendered at once, and so are the tunes placed at another width than they were engraved for,
 * which arrive as data (placed at once, not cross-faded): the layout settling, not something the reader did.
 */
@Composable
internal fun rememberSongNotations(
    blocks: List<ChordProBlock>,
    availableWidth: Int,
    narrowestWidth: Int?,
    widestWidth: Int?,
    unitSize: Float,
    chords: NotationChordSpelling,
    songTitle: String?,
    painting: NotationPainting,
    partChoices: NotationPartChoices?,
): SongNotations {
    val renderer = LocalAbcRenderer.current
    val cache = LocalNotationCache.current
    val tunes = remember(blocks) { abcTunesOf(blocks) }
    val instrument = LocalNotationInstrument.current
    // Read in composition, so that a part picked or an instrument changed is a new request.
    val parts = partChoices?.let { choices -> tunes.mapNotNull { tune -> choices.shownPartOf(tune.text, instrument)?.let { tune.text to it } }.toMap() }.orEmpty()
    val facts = remember(tunes, parts, songTitle) { notationTuneFactsOf(tunes, parts, songTitle) }
    val placement = remember { NotationPlacement() }
    val request = if (narrowestWidth == null || narrowestWidth <= 0 || tunes.isEmpty()) {
        null
    } else {
        val read = chords.forTune(facts.hasTabVoice)
        val base = NotationRequest(tunes, narrowestWidth, availableWidth, unitSize, read, songTitle.takeIf { facts.namesSongTitle }, painting, parts, facts)
        val width = placement.widthFor(tunes, narrowestWidth, availableWidth, unitSize, rememberedWidth = cache?.placedWidthFor(base))
        if (width != narrowestWidth) cache?.rememberPlacedWidth(base, width)
        base.copy(width = width)
    }
    val isPlacementUnknown = placement.isGuessing
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
        val isOnlyResized = previous != null && previous.tunes == request.tunes && previous.chords == request.chords && previous.songTitle == request.songTitle &&
            previous.parts == request.parts
        val isOnlyPlaced = isOnlyResized && previous.availableWidth == request.availableWidth && previous.unitSize == request.unitSize
        // Nothing to engrave where every tune is kept, so a pinch reversed to a size seen a moment ago is instant.
        val isCached = cache != null && request.tunes.all { cache.isKept(request.keyOf(it)) }
        if (isOnlyResized && !isOnlyPlaced && !isCached) delay(RERENDER_DELAY_MILLIS)
        // Nowhere placed yet, the text is laid out first, and tells where in a frame or two: engraving for the narrowest
        // column meanwhile would show that staff first, then the column's, a step too many. A folded tune never tells.
        if (isPlacementUnknown && !isCached) repeat(PLACEMENT_FRAMES) { withFrameNanos { } }
        // One painter per run, never one shared across runs: a cancelled run's painting goes on until its next suspension,
        // so two runs can overlap and would race on the painter's caches.
        val painter = request.painting.newPainter()
        val renderings = request.tunes.mapNotNull { tune ->
            ensureActive() // a cancelled run (a newer request) stops between tunes rather than painting the rest
            val key = request.keyOf(tune)
            // A tune whose engraving reads nothing that changed keeps the very rendering it has, which neither cross-fades
            // nor waits for a cache that may have let it go. Each key is made from its own request's texts: a part picked
            // since changes the text.
            rendered.renderings[tune]?.takeIf { previous?.keyOf(tune) == key }?.let { return@mapNotNull tune to it }
            if (cache?.isFailed(key) == true) return@mapNotNull null
            val rendering = cache?.get(key) ?: render(renderer, painter, tune, request).also { rendering ->
                // A cancellation throws rather than returning null, but one racing the end of the work must not be
                // remembered as the tune failing.
                if (rendering == null) {
                    if (isActive) cache?.markFailed(key)
                } else {
                    cache?.set(key, rendering)
                }
            }
            rendering?.let { tune to it }
        }.toMap()
        val isRerendering = previous != null && !isOnlyPlaced &&
            (previous.width != request.width || previous.unitSize != request.unitSize || previous.chords != request.chords || previous.parts != request.parts)
        // An edited tune replaces the staff it was drawn as meanwhile, and cross-fades with it; one with no stand-in (a
        // block just added) arrives as data, replacing its text at once.
        val rerenderedTunes = (if (isRerendering) renderings.filter { (tune, rendering) -> rendering !== rendered.renderings[tune] }.keys.intersect(rendered.renderings.keys) else emptySet()) +
            renderings.keys.intersect(shownStandIns.keys)
        rendered = RenderedNotations(
            request = request,
            renderings = renderings,
            rerenderedTunes = rerenderedTunes,
            arrivingTunes = if (isOnlyPlaced) renderings.keys else arrivingTunesOf(previous = rendered.renderings.keys, rendered = renderings.keys, rerendered = rerenderedTunes),
        )
    }
    val maxWidth = (widestWidth ?: narrowestWidth ?: 0) * unitSize
    return remember(tunes.isEmpty(), rendered, standIns, unitSize, maxWidth, partChoices, instrument) {
        // A song with tunes still drawn as text has an instance of its own all the same, which the text tells its place.
        if (tunes.isEmpty()) SongNotations.NONE else SongNotations(rendered, standIns, unitSize, maxWidth, placement, partChoices, instrument)
    }
}

/** [tune] engraved as [request] asks, in the part it shows (see [renderNotationPartOrScore]). */
private suspend fun render(renderer: AbcRenderer, painter: NotationPainter, tune: AbcTune, request: NotationRequest): NotationRendering? {
    val (text, drawing) = renderNotationPartOrScore(
        renderer = renderer,
        tune = tune.text,
        part = request.parts[tune.text],
        partText = request.textOf(tune),
        width = request.width,
        chords = request.chords.movedBy(tune.offset),
        songTitle = request.songTitle,
    ) ?: return null
    return withContext(Dispatchers.Default) {
        NotationRendering(
            drawing = drawing,
            unitSize = request.unitSize,
            systems = painter.paint(drawing, request.unitSize),
            reading = notationReadingOf(text, request.songTitle, drawing.chordNames),
        )
    }
}

/**
 * [tune] engraved with only [part] where one is given, or whole where none is or the part fails on its own (abc2svg
 * engraves a part alone as a score of its own, which can hold what the whole score does not), so a part is never worse
 * than the score: the text engraved and its drawing, null where neither can be drawn. What the song page and the PDF
 * both engrave.
 */
internal suspend fun renderNotationPartOrScore(
    renderer: AbcRenderer,
    tune: String,
    part: AbcPart?,
    width: Int,
    chords: NotationChordSpelling,
    songTitle: String?,
    partText: String = engravedTextOf(tune, part),
): Pair<String, NotationDrawing>? = renderNotationDrawing(renderer, partText, width, chords, songTitle)?.let { partText to it }
        ?: tune.takeIf { it != partText }?.let { renderNotationDrawing(renderer, it, width, chords, songTitle)?.let { drawing -> tune to drawing } }

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
): NotationDrawing? = when (val rendering = renderer.render(tune = abcTuneWithoutSongTitle(tune, songTitle), width = width, transposition = chords.semitones, tablature = chords.tablature)) {
    is AbcRendering.Systems -> withContext(Dispatchers.Default) {
        parseNotationDrawing(rendering.systems)?.let { drawing ->
            // abc2svg adds the tune's own %%transpose to the reader's, so the names are moved by both.
            val named = chords.movedBy(AbcTranspositions.of(tune))
            val symbols = abcChordSymbols(tune)
            drawing.withChordNames(symbols, abcChordNames(tune, symbols, named), named.semitones)
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
private const val PLACEMENT_FRAMES = 2
