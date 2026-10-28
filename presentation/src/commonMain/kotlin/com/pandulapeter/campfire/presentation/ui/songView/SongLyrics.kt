/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.presentation.ui.songView.model.ScaleSuggestions
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongMetadataSection
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingTools
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.songView.model.FoldedRuns
import com.pandulapeter.campfire.presentation.ui.songView.model.InstrumentPillContent
import com.pandulapeter.campfire.presentation.ui.songView.model.LayoutBudget
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModel
import com.pandulapeter.campfire.presentation.ui.songView.model.SongUnits
import com.pandulapeter.campfire.presentation.ui.songView.model.UnitContent
import com.pandulapeter.campfire.presentation.ui.songView.model.cardKeysOf
import com.pandulapeter.campfire.presentation.ui.songView.model.foldedKeysOf
import com.pandulapeter.campfire.presentation.ui.songView.model.prepareSongLyrics
import com.pandulapeter.campfire.presentation.ui.songView.model.sectionKeysOf
import com.pandulapeter.campfire.presentation.ui.songView.model.unitKeysOf
import com.pandulapeter.campfire.presentation.ui.songView.model.withMetadataSection
import com.pandulapeter.campfire.presentation.ui.songView.grid.SectionMeasurements
import com.pandulapeter.campfire.presentation.ui.songView.grid.SectionSizesPool
import com.pandulapeter.campfire.presentation.ui.songView.grid.fitsNarrowColumns
import com.pandulapeter.campfire.presentation.ui.songView.grid.flowLikeAMagazine
import com.pandulapeter.campfire.presentation.ui.songInfo.SongInfoEditing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import com.pandulapeter.campfire.presentation.ui.notation.NotationPainting
import com.pandulapeter.campfire.presentation.ui.notation.AbcTune
import com.pandulapeter.campfire.presentation.ui.notation.abcTune
import com.pandulapeter.campfire.presentation.ui.notation.notationPageWidth
import com.pandulapeter.campfire.presentation.ui.notation.notationSlotLines
import com.pandulapeter.campfire.presentation.ui.notation.rememberSongNotations
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_cut
import com.pandulapeter.campfire.presentation.resources.song_details_key_change
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.ui.components.scaled
import com.pandulapeter.campfire.presentation.ui.instrument.tunings
import com.pandulapeter.campfire.presentation.ui.songLayout.rememberDefaultSectionLabels
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * Renders the song [model] with the chords displayed above the lyrics, aligned to the syllable they belong to. A model
 * prepared without chords (see [prepareSongLyrics]) renders only the lyrics: the chords are dropped and lines that
 * consisted of nothing but chords (e.g. an intro) are skipped entirely.
 *
 * The song is split into sections (verse, chorus, ...) which are flowed into rows across the columns by
 * [SongSectionsLayout]. Choruses are drawn on a raised card of their own so that they stand out from the surrounding
 * sections. A song that is not read whole in a single row is set like a magazine, its sections running on from one
 * column into the next and from one row into the next, where [canCutSections] allows it, and sections move
 * to their new place when the column count changes (e.g. when a window is resized), see [sectionMotion].
 *
 * @param model The song and its sections, built away from the main thread by [rememberSongLyricsModel].
 * @param availableWidth The width this layout's content has inside the modifier's padding once any transition resizing
 * it has ended, which its tunes are engraved for: known before the first layout, so a tune rendered before is notation
 * on the first frame, and a push that hides a navigation rail engraves once, for the page it settles into.
 * @param availableHeight The height the song can occupy without scrolling; the column count is picked so that it
 * fits into this if it can. It is the height the page has at rest, which may differ from [rowViewportHeight] for the
 * frames an app bar or a panel above it is moving, so that the grid is not searched again on every one of them.
 * @param extraWidth How much wider this layout is going to be once the animation that is currently resizing it has
 * finished (see `SongDetailsScreen`'s settled width). The column count is decided for that final width, so that the
 * sections do not flow into a different number of columns for the duration of a navigation transition and then jump
 * back. While it is not zero the sections also stop springing to their new place: the layout is following a width
 * that changes on every frame, and springing after each of those only makes it lag behind.
 * @param sectionMotion How the sections get to the place a change of the layout gives them, see [SectionMotion].
 * @param isTextScaling Whether the [SectionMotion.GLIDE] is a pinch, which changes the playing controls' sizes on every
 * frame, so that they follow it as the sections do; where it is a window edge being dragged they keep their sizes and
 * only rearrange now and then, and travel to their new places as they do.
 * @param fontScale Multiplier applied to the text sizes (and to the column widths, so that larger text does not get
 * squeezed into narrow columns).
 * @param foldedSections The keys of the sections, tabs and grids of this song the reader has folded away (see
 * [SongSectionContent]), which are saved per song, and [onFoldToggled] is handed each key a fold toggles to save it.
 * Null where nothing folds, which is the editor's preview: it is there to show what is being written, and a section
 * folded on the details screen would hide the lines being typed into it.
 * @param isSongInfoShown Whether the song's first section is the card of what the song is (see
 * [SongMetadataSection]), its Song defaults tiles in place of the line, which the editor's preview does; the song
 * details screen opens it as a sheet instead.
 * @param songInfoEditing The edit buttons of that card, null for none.
 * @param playingControls What sets the song's key, capo, tempo and time signature, drawn in place of the line that
 * only reads them; null leaves that line, which is what read only mode gets. Each control goes
 * with the values it sets, see [SongPlayingControls].
 * @param playingTools The tuner and metronome buttons the song's first section holds next to those values, whether they
 * are set there or read; null where it holds none, which is the editor's preview.
 * @param readsDefaults Whether that line names the capo, the tempo and the time signature where the file names none,
 * which read only mode on the song details screen asks for, see [withMetadataSection].
 * @param shouldShowCapo False on the keyboard, which reads the capo as a transposition, see [withMetadataSection].
 * @param shouldShowTempo False with the metronome switched off, which leaves the tempo and the time signature out of
 * the song's first section, see [withMetadataSection]. The chords' own switch is the model's, since it shapes the lyrics.
 * @param onRowsPlaced Handed the rows of the song ([SongRows]: where a scroll comes to rest on each - the
 * bottom edge of the divider above it, so that the divider itself is just out of view - and where its content ends),
 * and the stops the song is stepped through, measured from the top of this composable's content, every time they are
 * placed, where they settle rather than where an animation has got them to. The song details screen snaps its scroll to
 * the rows and steps between the stops.
 * @param rowViewportHeight The height of the viewport the song is scrolled in, where that scroll comes to rest on the
 * dividers between the rows: every row is then followed by empty space down to the bottom of that viewport, so that
 * a scroll resting on a divider shows no row but the one under it, in the middle of the screen, and the last one can be
 * brought to the top like the others. Unspecified where nothing snaps, which is the editor's preview. It is the live
 * height, followed frame by frame, where [availableHeight] waits for the viewport to settle, so it is read only by the
 * layout's measure: handed over as a provider that reads a snapshot state, it is the same lambda on every one of those
 * frames, and the song is measured again on them without being composed again.
 * @param rowViewportTopPadding How much the scroll holds above this composable, which a song that fits the screen
 * leaves above the room it is spread out over.
 * @param rowViewportBottomPadding How much the scroll holds under this composable, which is part of the room the last
 * row needs to be brought to the top of the viewport.
 * @param stepButtonInset How much of the end edge a song that has to be scrolled leaves to what is drawn over it there:
 * the song details screen's buttons that step through it. A song that fits the screen has none of them, and takes the
 * width, unless [keepsStepButtonInset] is set: in a setlist the same buttons page to the songs beside it, whatever the
 * song. Zero where there are no buttons at all, which is the editor's preview.
 * @param canCutSections Whether a section may be cut into pieces that run on from one column or row into the next, where
 * the song is not read whole in a single row otherwise (see [flowLikeAMagazine]). Off in the editor's preview, which
 * follows every edit, and would move the pieces of a section from column to column as it is typed into.
 * @param isSingleColumn Whether the sections are stacked in one column however wide the window is, the way a phone
 * lays them out. The editor's preview next to the text sets it: half a window flows a song into columns that the
 * next keystroke reshuffles, and the lines being typed are only easy to find in the order they are written.
 */
@Composable
internal fun SongLyrics(
    modifier: Modifier = Modifier,
    model: SongLyricsModel,
    availableWidth: Dp,
    availableHeight: Dp = Dp.Unspecified,
    extraWidth: Dp = 0.dp,
    sectionMotion: SectionMotion = SectionMotion.SPRING,
    isTextScaling: Boolean = false,
    fontScale: Float = 1f,
    foldedSections: Set<String> = emptySet(),
    onFoldToggled: ((key: String) -> Unit)? = null,
    isSongInfoShown: Boolean = false,
    songInfoEditing: SongInfoEditing? = null,
    playingControls: SongPlayingControls? = null,
    playingTools: SongPlayingTools? = null,
    readsDefaults: Boolean = false,
    shouldShowCapo: Boolean = true,
    shouldShowTempo: Boolean = true,
    onRowsPlaced: ((SongRows) -> Unit)? = null,
    rowViewportHeight: () -> Dp = { Dp.Unspecified },
    rowViewportTopPadding: Dp = 0.dp,
    rowViewportBottomPadding: Dp = 0.dp,
    stepButtonInset: Dp = 0.dp,
    keepsStepButtonInset: Boolean = false,
    canCutSections: Boolean = false,
    isSingleColumn: Boolean = false,
    chordDiagrams: ChordDiagrams? = null,
    scaleSuggestions: ScaleSuggestions? = null,
) {
    // The fold toggles of the runs inside a section are named by these too, where the file names them nothing.
    val defaultLabels = rememberDefaultSectionLabels()
    val isSongInfoEditable = songInfoEditing != null
    // The controls are only drawn where the values they set are, so a change of either decides the section's content.
    val shownPlayingControls = playingControls?.takeIf { model.shouldShowChords || shouldShowTempo }
    val hasTuner = playingTools?.onTunerClicked != null
    val hasMetronome = playingTools?.metronome != null
    val shownTuning = chordDiagrams?.instrumentTuning?.takeIf { chordDiagrams.instrument.tunings.size > 1 }
    val tuningVisibility = remember { MutableTransitionState(shownTuning != null) }.apply { targetState = shownTuning != null }
    // A section is measured in the frame its content first appears in, and a leaving tuning half of the instrument pill is still laid
    // out while it fades: the content keeps naming that half's tuning until the fade has ended, so that the section is measured again
    // without it then. Not a state: the fade's own state is what changes the content.
    val lastShownTuning = remember { LastShownTuning() }
    shownTuning?.let { lastShownTuning.value = it }
    val instrumentPill = chordDiagrams?.takeIf { it.onInstrumentClicked != null }?.let {
        InstrumentPillContent(
            instrument = it.instrument,
            tuning = shownTuning ?: lastShownTuning.value.takeIf { !tuningVisibility.isIdle || tuningVisibility.currentState },
            notation = it.notation,
        )
    }
    val sections = rememberShownSections(
        model = model,
        chordDiagrams = chordDiagrams,
        scaleSuggestions = scaleSuggestions,
        instrumentPill = instrumentPill,
        shouldShowTempo = shouldShowTempo,
        isSongInfoShown = isSongInfoShown,
        isSongInfoEditable = isSongInfoEditable,
        hasPlayingControls = shownPlayingControls != null,
        readsDefaults = readsDefaults,
        shouldShowCapo = shouldShowCapo,
        hasTuner = hasTuner,
        hasMetronome = hasMetronome,
    )
    val glideScope = rememberCoroutineScope()
    val sectionGlides = if (sectionMotion == SectionMotion.NONE) null else remember(glideScope) { SectionGlides(glideScope, SongMotionSpec) }
    val density = LocalDensity.current
    // Kept across a new song text rather than keyed on it, since a transposition or a tag put on rewrites the song and
    // must not make what was just unfolded fade in a second time.
    var toggledFolds by remember { mutableStateOf(emptySet<String>()) }
    // Whether the Chords or the Scales section was folded or unfolded while the page is open, so that its rows fade in as
    // they come back in every chunk they come back in, rather than only in the one the pill is in.
    var hasChordFoldBeenToggled by remember { mutableStateOf(false) }
    var hasScaleFoldBeenToggled by remember { mutableStateOf(false) }
    val shownScaleSuggestions = scaleSuggestions?.let { suggestions ->
        remember(suggestions) {
            suggestions.copy(
                onFoldToggled = {
                    hasScaleFoldBeenToggled = true
                    suggestions.onFoldToggled()
                },
            )
        }
    }
    val onChordFoldToggled = chordDiagrams?.onFoldToggled?.let { toggle ->
        remember(toggle) {
            {
                hasChordFoldBeenToggled = true
                toggle()
            }
        }
    }
    // Remembered rather than built on every composition, since every section's content is handed it and compares it
    // by identity: it is rebuilt exactly when what it answers changes, and the sections skip on everything else.
    val latestOnFoldToggled by rememberUpdatedState(onFoldToggled)
    val foldedRuns = if (onFoldToggled == null) null else remember(foldedSections, toggledFolds) {
        FoldedRuns(
            collapsed = foldedSections,
            toggled = toggledFolds,
            onToggled = { key ->
                toggledFolds += key
                latestOnFoldToggled?.invoke(key)
            },
        )
    }
    // The styles the lines are measured with carry no color, which is given where the text is drawn instead: every
    // line of the song is measured again whenever a key of its measurement changes, and the color scheme changes on
    // every frame of a theme cross-fade while the size of the text does not.
    // The direction of a line is its own content's rather than the app's, so that a Hebrew or Arabic line is a right to
    // left paragraph starting at the right edge, as its reader expects, and the chords above it are laid out that way.
    // Remembered, so that every comparison of them below and in the sections is one of identity (TextStyle.equals checks
    // that first) rather than a deep one on every composition.
    val localTextStyle = LocalTextStyle.current
    val typography = MaterialTheme.typography
    val lyricsStyle = remember(localTextStyle, typography, fontScale) {
        localTextStyle.merge(typography.bodyLarge).scaled(fontScale).copy(
            color = Color.Unspecified,
            textDirection = TextDirection.Content,
        )
    }
    val headerStyle = remember(typography, fontScale) { typography.titleSmall.scaled(fontScale) }
    val chordStyle = remember(lyricsStyle) { lyricsStyle.copy(fontWeight = FontWeight.Bold) }
    // Annotations ([*text]) sit in the chord row but are not chords, so they are drawn in the lyrics' colour.
    val annotationStyle = remember(lyricsStyle) { lyricsStyle.copy(fontStyle = FontStyle.Italic) }
    // One measurer for the whole page. Everything measured through it is kept by whoever asked for it (see
    // [SongTextMeasurements] and [TabRows]), so a cache of its own would only hold every layout a second time.
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    // The staff notation of the song's ABC tunes, which replaces their text once it has been rendered: for a page as wide
    // as the widest column the song can have here, each SVG unit as large as the text size makes it. Its words are laid
    // out in the lyrics' typeface at the sizes abc2svg asks for, so the style they start from is not scaled.
    val notationTextStyle = remember(localTextStyle, typography) { localTextStyle.merge(typography.bodyLarge).copy(color = Color.Unspecified) }
    val fontResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current
    val notationPainting = remember(density, layoutDirection, fontResolver, notationTextStyle) {
        NotationPainting(density, layoutDirection, fontResolver, notationTextStyle)
    }
    val notationUnit = NOTATION_UNIT * fontScale
    val notations = rememberSongNotations(
        blocks = model.song.blocks,
        width = notationPageWidth(
            availableWidth = availableWidth,
            maxColumnWidth = MAX_COLUMN_WIDTH * fontScale,
            cardPadding = CARD_PADDING,
            notationUnit = notationUnit,
        ),
        unitSize = with(density) { notationUnit.toPx() },
        chords = model.notationChords,
        songTitle = model.song.metadata.title,
        painting = notationPainting,
    )
    // Everything the height of a section depends on apart from its own content and the width it is measured at. Within
    // one of these the sizes of a section are reused by content, so an edit or a transposition only measures the
    // sections it changed. The folds are not one of its keys but part of each chunk's content (UnitContent.foldedKeys),
    // so a fold only measures its own section again. The notation arriving measures everything again, once.
    val sectionSizesPool = remember(fontScale, density, lyricsStyle, headerStyle, defaultLabels, notations) { SectionSizesPool<UnitContent>() }
    // Every section that may be cut is composed as the chunks it may be cut into, which the layout keeps together unless
    // it cuts between two of them (see flowLikeAMagazine): its lines, and the Chords and Scales sections' slots, one per
    // row of diagrams or chips. A folded section is one, since what is left of it is its header.
    // An engraved tune plays its bars, two a line, rather than a line a staff system; one still drawn as text a line a row.
    val systemLines = notations.systemLines
    val units = remember(sections, foldedSections, canCutSections, systemLines) {
        SongUnits.of(
            sections = sections,
            isFolded = { section -> section is RenderSection.Lines && section.foldKey in foldedSections },
            runSlotLines = { slot ->
                slot.run.abcTune()?.let { systemLines[AbcTune(it, slot.tuneOffset)] }
                    ?.let { notationSlotLines(it, slot.slot, isLastSlot = slot.slot == slot.slotCount - 1) } ?: 1
            },
        ) { section ->
            when (section) {
                is RenderSection.Lines -> canCutSections && section.foldKey !in foldedSections
                // Folded, it is one item - its pill - whatever this says.
                is RenderSection.Chords -> canCutSections
                is RenderSection.Scales -> canCutSections
                else -> false
            }
        }
    }
    val sectionMeasurements = remember(units, sectionSizesPool, foldedSections) {
        val foldedKeys = sections.map { foldedKeysOf(it, foldedSections) }
        SectionMeasurements(
            sectionSizesPool.sizesFor(
                units.itemRanges.mapIndexed { unit, items ->
                    val section = units.unitSections[unit]
                    UnitContent(sections[section], items.first, items.last, foldedKeys[section])
                },
            ),
        )
    }
    val sectionKeys = remember(sections) { sectionKeysOf(sections) }
    val unitKeys = remember(units, sectionKeys) { unitKeysOf(units, sectionKeys) }
    val cardKeys = remember(units, sectionKeys) { cardKeysOf(units, sectionKeys) }
    val textMeasurements = remember(textMeasurer, lyricsStyle, chordStyle, annotationStyle) {
        SongTextMeasurements(
            textMeasurer = textMeasurer,
            lyricsStyle = lyricsStyle,
            chordStyle = chordStyle,
            annotationStyle = annotationStyle,
        )
    }
    val chordsSection = sections.firstNotNullOfOrNull { it as? RenderSection.Chords }
    // The template rather than a sentence per key, which the layouts fill in only for the keys the cells are in.
    val keyHeading = textResource(Res.string.song_details_key_change)
    val chordLayouts = remember(chordsSection?.cells, chordStyle, keyHeading, textMeasurer, density, fontScale) {
        chordsSection?.let { ChordCellLayouts(it.cells, chordStyle, keyHeading, textMeasurer, density, fontScale) }
    }
    // Only where a fold can be toggled, which the editor's preview cannot.
    val centeringHold = if (onFoldToggled == null && chordDiagrams?.onFoldToggled == null) {
        null
    } else {
        rememberColumnCenteringHold(foldedSections, chordDiagrams?.isFolded)
    }
    // A boolean rather than the width itself, which changes on every frame of a navigation rail animating.
    val isResizing = extraWidth > 0.dp
    // The composition a tune's first rendering lands in lays the song out anew without narrating it (see SectionGlides).
    val notationArrival = notations.arrival
    val seenArrival = remember { SeenArrival() }
    val isNotationArriving = notationArrival != null && notationArrival !== seenArrival.value
    SideEffect { seenArrival.value = notationArrival }
    Box(modifier = modifier) {
        LookaheadScope {
            SongSectionsLayout(
                modifier = Modifier.semantics { isTraversalGroup = true },
                minColumnWidth = MIN_COLUMN_WIDTH * fontScale,
                narrowColumnWidth = NARROW_COLUMN_WIDTH * fontScale,
                maxColumnWidth = MAX_COLUMN_WIDTH * fontScale,
                columnGap = COLUMN_GAP,
                sectionGap = SECTION_GAP,
                rowGap = ROW_GAP,
                availableHeight = availableHeight,
                maxRowHeight = availableHeight,
                rowViewportHeight = rowViewportHeight,
                rowViewportTopPadding = rowViewportTopPadding,
                rowViewportBottomPadding = rowViewportBottomPadding,
                stepButtonInset = stepButtonInset,
                keepsStepButtonInset = keepsStepButtonInset,
                extraWidth = extraWidth,
                units = units,
                unitKeys = unitKeys,
                cardKeys = cardKeys,
                cardPadding = CARD_PADDING,
                canCutSections = canCutSections,
                isSingleColumn = isSingleColumn,
                sectionGlides = sectionGlides,
                glidesEveryMove = sectionMotion == SectionMotion.SPRING && !isResizing,
                arrival = notationArrival,
                sectionMeasurements = sectionMeasurements,
                lyricsLineHeight = textMeasurements.lyricsLineHeight,
                centeringHold = centeringHold,
                onRowsPlaced = { rows -> onRowsPlaced?.invoke(rows) },
            ) {
                SongSectionChildren(
                    sections = sections,
                    units = units,
                    unitKeys = unitKeys,
                    cardKeys = cardKeys,
                    sectionMotion = sectionMotion,
                    placesAtOnce = isResizing || isNotationArriving,
                    isTextScaling = isTextScaling,
                    notation = model.notation,
                    isSongInfoShown = isSongInfoShown,
                    songInfoEditing = songInfoEditing,
                    playingControls = shownPlayingControls,
                    playingTools = playingTools,
                    chordDiagrams = chordDiagrams,
                    scaleSuggestions = shownScaleSuggestions,
                    tuningVisibility = tuningVisibility,
                    chordLayouts = chordLayouts,
                    onChordFoldToggled = onChordFoldToggled,
                    hasChordFoldBeenToggled = hasChordFoldBeenToggled,
                    hasScaleFoldBeenToggled = hasScaleFoldBeenToggled,
                    headerStyle = headerStyle,
                    lyricsStyle = lyricsStyle,
                    chordStyle = chordStyle,
                    textMeasurements = textMeasurements,
                    notations = notations,
                    foldedRuns = foldedRuns,
                    defaultLabels = defaultLabels,
                    fontScale = fontScale,
                )
            }
        }
        if (model.isCut) {
            CutSongNotice(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CARD_PADDING)
                    .padding(top = 16.dp),
            )
        }
    }
}

/** Where a song too long to be laid out whole stops, see [LayoutBudget]: the file is whole, and the editor shows it all. */
@Composable
private fun CutSongNotice(
    modifier: Modifier = Modifier,
) = Text(
    modifier = modifier,
    text = stringResource(Res.string.song_details_cut),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** What the instrument pill's tuning half of [SongLyrics] last named. */
private class LastShownTuning(var value: InstrumentTuning? = null)

/** The tunes' arrival (`SongNotations.arrival`) [SongLyrics] was last composed with. Not a state. */
private class SeenArrival(var value: Any? = null)

private val MIN_COLUMN_WIDTH = 384.dp

/**
 * The narrowest a column may be, three quarters of [MIN_COLUMN_WIDTH]: taken only by a song whose lines mostly fit it,
 * and only where that saves a page (see [fitsNarrowColumns]).
 */
private val NARROW_COLUMN_WIDTH = 288.dp

private val MAX_COLUMN_WIDTH = 560.dp

/** What one SVG unit of engraved notation is drawn as at the default text size, where the column is wide enough. */
private val NOTATION_UNIT = 1.dp
private val COLUMN_GAP = 16.dp
private val SECTION_GAP = 16.dp
private val ROW_GAP = 16.dp
