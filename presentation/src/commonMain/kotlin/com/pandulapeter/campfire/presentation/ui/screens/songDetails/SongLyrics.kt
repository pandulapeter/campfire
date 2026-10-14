/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_cut
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
 * @param availableHeight The height the song can occupy without scrolling; the column count is picked so that it
 * fits into this if it can. It is the height the page has at rest, which may differ from [rowViewportHeight] for the
 * frames an app bar or a panel above it is moving, so that the grid is not searched again on every one of them.
 * @param extraWidth How much wider this layout is going to be once the animation that is currently resizing it has
 * finished (see [SongDetailsScreen]'s settled width). The column count is decided for that final width, so that the
 * sections do not flow into a different number of columns for the duration of a navigation transition and then jump
 * back. While it is not zero the sections also stop springing to their new place: the layout is following a width
 * that changes on every frame, and springing after each of those only makes it lag behind.
 * @param sectionMotion How the sections get to the place a change of the layout gives them, see [SectionMotion].
 * @param fontScale Multiplier applied to the text sizes (and to the column widths, so that larger text does not get
 * squeezed into narrow columns).
 * @param canFitText Whether a song a few lines too long for its pages may be set a little smaller to save one. False
 * while what the grid is decided for keeps changing - a pinch, a window edge being dragged, which is not the same as
 * [SectionMotion.GLIDE] - since fitting lays the song out two or three more times on every frame; it is fitted once the
 * change holds still.
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
 * height, followed frame by frame, where [availableHeight] waits for the viewport to settle.
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
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SongLyrics(
    modifier: Modifier = Modifier,
    model: SongLyricsModel,
    availableHeight: Dp = Dp.Unspecified,
    extraWidth: Dp = 0.dp,
    sectionMotion: SectionMotion = SectionMotion.SPRING,
    fontScale: Float = 1f,
    canFitText: Boolean = true,
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
    rowViewportHeight: Dp = Dp.Unspecified,
    rowViewportTopPadding: Dp = 0.dp,
    rowViewportBottomPadding: Dp = 0.dp,
    stepButtonInset: Dp = 0.dp,
    keepsStepButtonInset: Boolean = false,
    canCutSections: Boolean = false,
    isSingleColumn: Boolean = false,
    chordDiagrams: ChordDiagrams? = null,
) {
    // The fold toggles of the runs inside a section are named by these too, where the file names them nothing.
    val defaultLabels = rememberDefaultSectionLabels()
    val isSongInfoEditable = songInfoEditing != null
    // The controls are only drawn where the values they set are, so a change of either decides the section's content.
    val shownPlayingControls = playingControls?.takeIf { model.shouldShowChords || shouldShowTempo }
    val hasTuner = playingTools?.onTunerClicked != null
    val hasMetronome = playingTools?.metronome != null
    val shownTuning = chordDiagrams?.instrumentTuning?.takeIf { chordDiagrams.instrument.tunings.size > 1 }
    val tuningChipVisibility = remember { MutableTransitionState(shownTuning != null) }.apply { targetState = shownTuning != null }
    // A section is measured in the frame its content first appears in, and a leaving tuning chip is still laid out while it
    // fades: the content keeps naming that chip's tuning until the fade has ended, so that the section is measured again
    // without it then. Not a state: the fade's own state is what changes the content.
    val lastShownTuning = remember { LastShownTuning() }
    shownTuning?.let { lastShownTuning.value = it }
    val instrumentChips = chordDiagrams?.takeIf { it.onInstrumentClicked != null }?.let {
        InstrumentChipsContent(
            instrument = it.instrument,
            tuning = shownTuning ?: lastShownTuning.value.takeIf { !tuningChipVisibility.isIdle || tuningChipVisibility.currentState },
            notation = it.notation,
        )
    }
    val sections = rememberShownSections(
        model = model,
        chordDiagrams = chordDiagrams,
        instrumentChips = instrumentChips,
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
    val glideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val sectionGlides = if (sectionMotion == SectionMotion.NONE) null else remember(glideScope, glideSpec) { SectionGlides(glideScope, glideSpec) }
    val density = LocalDensity.current
    // Kept across a new song text rather than keyed on it, since a transposition or a tag put on rewrites the song and
    // must not make what was just unfolded fade in a second time.
    var toggledFolds by remember { mutableStateOf(emptySet<String>()) }
    // Whether the Chords section was folded or unfolded while the page is open, so that its rows fade in as they come back
    // in every chunk they come back in, rather than only in the one the pill is in.
    var hasChordFoldBeenToggled by remember { mutableStateOf(false) }
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
    val lyricsStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge).scaled(fontScale).copy(
        color = Color.Unspecified,
        textDirection = TextDirection.Content,
    )
    val headerStyle = MaterialTheme.typography.titleSmall.scaled(fontScale)
    val chordStyle = lyricsStyle.copy(fontWeight = FontWeight.Bold)
    // Annotations ([*text]) sit in the chord row but are not chords, so they are drawn in the lyrics' colour.
    val annotationStyle = lyricsStyle.copy(fontStyle = FontStyle.Italic)
    // Everything the height of a section depends on apart from its own content and the width it is measured at. Within
    // one of these the sizes of a section are reused by content, so an edit or a transposition only measures the
    // sections it changed. The folds are not one of its keys but part of each chunk's content (UnitContent.foldedKeys),
    // so a fold only measures its own section again.
    val sectionSizesPool = remember(fontScale, density, lyricsStyle, headerStyle, defaultLabels) { SectionSizesPool<UnitContent>() }
    // Every section that may be cut is composed as the chunks it may be cut into, which the layout keeps together unless
    // it cuts between two of them (see flowLikeAMagazine): its lines, and the Chords section's slots, one per row of
    // diagrams. A folded section is one, since what is left of it is its header.
    val units = remember(sections, foldedSections, canCutSections) {
        SongUnits.of(sections) { section ->
            when (section) {
                is RenderSection.Lines -> canCutSections && section.foldKey !in foldedSections
                // Folded, it is one item - its pill - whatever this says.
                is RenderSection.Chords -> canCutSections
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
    // Whether each chunk, and then each card, is too tall to be animated, which only the layout finds out.
    val animations = remember(units) { List(units.unitSections.size + units.cardCount) { SectionAnimation() } }
    val sectionKeys = remember(sections) { sectionKeysOf(sections) }
    val unitKeys = remember(units, sectionKeys) { unitKeysOf(units, sectionKeys) }
    val cardKeys = remember(units, sectionKeys) { cardKeysOf(units, sectionKeys) }
    // One measurer for the whole page. Everything measured through it is kept by whoever asked for it (see
    // [SongTextMeasurements] and [TabRows]), so a cache of its own would only hold every layout a second time.
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    val textMeasurements = remember(textMeasurer, lyricsStyle, chordStyle, annotationStyle) {
        SongTextMeasurements(
            textMeasurer = textMeasurer,
            lyricsStyle = lyricsStyle,
            chordStyle = chordStyle,
            annotationStyle = annotationStyle,
        )
    }
    val chordsSection = sections.firstNotNullOfOrNull { it as? RenderSection.Chords }
    val chordLayouts = remember(chordsSection?.cells, chordStyle, textMeasurer, density, fontScale) {
        chordsSection?.let { ChordCellLayouts(it.cells, chordStyle, textMeasurer, density, fontScale) }
    }
    // Only a song cut into pages is set smaller to save one; the editor's preview is set as it is typed.
    val fitScales = remember(fontScale, canCutSections) { if (canCutSections) fitScalesFor(fontScale) else emptyList() }
    val fitDeferral = remember { FitDeferral() }
    // Only where a fold can be toggled, which the editor's preview cannot.
    val centeringHold = if (onFoldToggled == null && chordDiagrams?.onFoldToggled == null) {
        null
    } else {
        rememberColumnCenteringHold(foldedSections, chordDiagrams?.isFolded)
    }
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
                animations = animations,
                cardPadding = CARD_PADDING,
                canCutSections = canCutSections,
                isSingleColumn = isSingleColumn,
                sectionGlides = sectionGlides,
                sectionMeasurements = sectionMeasurements,
                lyricsLineHeight = textMeasurements.lyricsLineHeight,
                fitScales = fitScales,
                canFitText = canFitText,
                fitDeferral = fitDeferral,
                centeringHold = centeringHold,
                onRowsPlaced = { rows -> onRowsPlaced?.invoke(rows) },
            ) {
                // Whether a chunk or a card is placed by animateBounds: not while the layout follows a change that keeps
                // coming or a navigation transition, not where it is too tall for it (see maxAnimatedSectionHeight), and
                // not while it is still gliding from a change that kept coming, since the spring would start at the place
                // the glide has not reached yet. Nor while the grid on screen still waits to be fitted: the pass that fits
                // it is the first one after a change stopped coming, and an animateBounds attached in that pass has no
                // bounds to animate from, so the fitted grid would land in one frame rather than be glided to.
                fun Modifier.movedBy(key: Any, animation: SectionAnimation): Modifier {
                    val isGliding = sectionGlides?.isGliding(key) == true
                    val isHeldStill = sectionMotion != SectionMotion.SPRING || extraWidth > 0.dp || fitDeferral.isPending
                    return if (isHeldStill || animation.isTooTallToAnimate || isGliding) {
                        this
                    } else {
                        animateBounds(this@LookaheadScope).layoutId(AnimatedSectionLayoutId)
                    }
                }
                units.itemRanges.forEachIndexed { unit, items ->
                    val section = sections[units.unitSections[unit]]
                    key(unitKeys[unit]) {
                        // Each chunk is read as a whole and in the order the song declares, whatever column it was put in:
                        // the reading order is otherwise worked out from the geometry, line by line across the page, which
                        // with two columns reads the first line of each, then the second line of each.
                        val unitModifier = Modifier.movedBy(unitKeys[unit], animations[unit]).semantics {
                            isTraversalGroup = true
                            traversalIndex = unit.toFloat()
                        }
                        SongSectionItem(
                            modifier = unitModifier,
                            section = section,
                            items = items,
                            notation = model.notation,
                            isSongInfoShown = isSongInfoShown,
                            songInfoEditing = songInfoEditing,
                            playingControls = shownPlayingControls,
                            playingTools = playingTools,
                            animatesControls = sectionMotion == SectionMotion.SPRING && extraWidth <= 0.dp,
                            chordDiagrams = chordDiagrams,
                            tuningChipVisibility = tuningChipVisibility,
                            chordLayouts = chordLayouts,
                            onChordFoldToggled = onChordFoldToggled,
                            isChordsSectionFadingIn = hasChordFoldBeenToggled,
                            headerStyle = headerStyle,
                            lyricsStyle = lyricsStyle,
                            chordStyle = chordStyle,
                            textMeasurements = textMeasurements,
                            foldedRuns = foldedRuns,
                            defaultLabels = defaultLabels,
                            fontScale = fontScale,
                        )
                    }
                }
                // The cards the pieces of the sections on a card are drawn on, as many as each could be cut into; the
                // ones a layout has no piece for stay unmeasured.
                SectionCards(cardKeys = cardKeys) { card, cardKey -> Modifier.movedBy(cardKey, animations[units.unitSections.size + card]) }
                // The dividers between the pages, one for every place a page can start at - a section, or a place a section
                // may be cut at - since that is as many as there can be; the ones a song laid out on fewer pages has no
                // place for stay unmeasured.
                repeat((sections.size - 1 + units.isCuttableBefore.count { it }).coerceAtLeast(0)) { HorizontalDivider() }
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

/** What the tuning chip of [SongLyrics] last named. */
private class LastShownTuning(var value: InstrumentTuning? = null)

private val MIN_COLUMN_WIDTH = 384.dp

/**
 * The narrowest a column may be, three quarters of [MIN_COLUMN_WIDTH]: taken only by a song whose lines mostly fit it,
 * and only where that saves a page (see [fitsNarrowColumns]).
 */
private val NARROW_COLUMN_WIDTH = 288.dp

private val MAX_COLUMN_WIDTH = 560.dp
private val COLUMN_GAP = 16.dp
private val SECTION_GAP = 16.dp
private val ROW_GAP = 16.dp
