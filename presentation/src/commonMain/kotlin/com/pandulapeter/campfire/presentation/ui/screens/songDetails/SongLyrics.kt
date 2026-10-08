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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
 * @param foldedSections The keys of the sections, tabs and grids of this song the reader has folded away (see
 * [SongSectionContent]), which are saved per song, and [onFoldToggled] is handed each key a fold toggles to save it.
 * Null where nothing folds, which is the editor's preview: it is there to show what is being written, and a section
 * folded on the details screen would hide the lines being typed into it.
 * @param isSongInfoShown Whether the song's first section holds the card of what the song is (see
 * [SongMetadataSection]), which the editor's preview does; the song details screen opens it as a sheet instead.
 * @param songInfoEditing The edit buttons of that card, null for none.
 * @param playingControls What sets the song's key, capo, tempo and time signature, drawn in place of the line that
 * only reads them; null leaves that line, which is what read only mode and the editor's preview get. Each control goes
 * with the values it sets, see [SongPlayingControls].
 * @param readsCapoAndTime Whether that line names the capo and the time signature where the file names neither, which
 * read only mode on the song details screen asks for, see [withMetadataSection].
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
    foldedSections: Set<String> = emptySet(),
    onFoldToggled: ((key: String) -> Unit)? = null,
    isSongInfoShown: Boolean = false,
    songInfoEditing: SongInfoEditing? = null,
    playingControls: SongPlayingControls? = null,
    readsCapoAndTime: Boolean = false,
    shouldShowTempo: Boolean = true,
    onRowsPlaced: ((SongRows) -> Unit)? = null,
    rowViewportHeight: Dp = Dp.Unspecified,
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
    val sections = remember(model.sections, model.song.metadata, model.shouldShowChords, shouldShowTempo, isSongInfoShown, isSongInfoEditable, shownPlayingControls != null, readsCapoAndTime) {
        withMetadataSection(
            sections = model.sections,
            metadata = model.song.metadata,
            shouldShowChords = model.shouldShowChords,
            shouldShowTempo = shouldShowTempo,
            isSongInfoShown = isSongInfoShown,
            isSongInfoEditable = isSongInfoEditable,
            hasPlayingControls = shownPlayingControls != null,
            readsCapoAndTime = readsCapoAndTime,
        )
    }.let { sections ->
        val cells = remember(model, chordDiagrams?.instrument, chordDiagrams?.storedShapes, chordDiagrams?.showsDefinitionsOnly) {
            when {
                chordDiagrams == null -> emptyList()
                chordDiagrams.showsDefinitionsOnly -> definitionCellsOf(model.song, chordDiagrams.notation)
                else -> chordCellsOf(model.chords, chordDiagrams.instrument, chordDiagrams.storedShapes)
            }
        }
        remember(sections, cells, chordDiagrams?.isFolded) { withChordsSection(sections, cells, isFolded = chordDiagrams?.isFolded == true) }
    }
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
    // Everything the height of a section depends on apart from its own content and the width it is measured at, the
    // folded runs included. Within one of these the sizes of a section are reused by content, so an edit or a
    // transposition only measures the sections it changed.
    val sectionSizesPool = remember(fontScale, density, foldedSections, lyricsStyle, headerStyle, defaultLabels) { SectionSizesPool<UnitContent>() }
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
    val sectionMeasurements = remember(units, sectionSizesPool) {
        SectionMeasurements(
            sectionSizesPool.sizesFor(
                units.itemRanges.mapIndexed { unit, items -> UnitContent(sections[units.unitSections[unit]], items.first, items.last) },
            ),
        )
    }
    // Whether each chunk, and then each card, is too tall to be animated, which only the layout finds out.
    val animations = remember(units) { List(units.unitSections.size + units.cardCount) { SectionAnimation() } }
    // Each section is emitted under a key of its content, so a section inserted above the others does not hand each of
    // their nodes the section that used to be its neighbour. Equal sections are told apart by the order they come in.
    val sectionKeys = remember(sections) {
        val occurrences = HashMap<Any, Int>()
        sections.map { section ->
            // The info section is the song's one header card: keyed by its content, every keystroke in a header directive
            // would compose it afresh, and whatever it remembers (the cover waiting for the typing to pause, the cover's
            // crossfade) would start over each time.
            val identity: Any = when (section) {
                is RenderSection.Metadata -> RenderSection.Metadata::class
                // Kept for the same reason: a shape chosen or the section folded is the same section, and folding it
                // has to fade its body in rather than compose the whole of it afresh.
                is RenderSection.Chords -> RenderSection.Chords::class
                else -> section
            }
            val occurrence = (occurrences[identity] ?: 0) + 1
            occurrences[identity] = occurrence
            SectionKey(hash = identity.hashCode(), occurrence = occurrence)
        }
    }
    val unitKeys = remember(units, sectionKeys) {
        List(units.unitSections.size) { unit -> UnitKey(section = sectionKeys[units.unitSections[unit]], firstItem = units.itemRanges[unit].first) }
    }
    val cardKeys = remember(units, sectionKeys) {
        buildList {
            sections.indices.forEach { section ->
                if (units.cardStarts[section] >= 0) {
                    repeat(units.sectionStarts[section + 1] - units.sectionStarts[section]) { piece -> add(CardKey(section = sectionKeys[section], piece = piece)) }
                }
            }
        }
    }
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
    Box(modifier = modifier) {
        LookaheadScope {
            SongSectionsLayout(
                modifier = Modifier.semantics { isTraversalGroup = true },
                minColumnWidth = MIN_COLUMN_WIDTH * fontScale,
                maxColumnWidth = MAX_COLUMN_WIDTH * fontScale,
                columnGap = COLUMN_GAP,
                sectionGap = SECTION_GAP,
                rowGap = ROW_GAP,
                availableHeight = availableHeight,
                maxRowHeight = availableHeight,
                rowViewportHeight = rowViewportHeight,
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
                onRowsPlaced = { rows -> onRowsPlaced?.invoke(rows) },
            ) {
                // Whether a chunk or a card is placed by animateBounds: not while the layout follows a change that keeps
                // coming or a navigation transition, not where it is too tall for it (see maxAnimatedSectionHeight), and
                // not while it is still gliding from a change that kept coming, since the spring would start at the place
                // the glide has not reached yet.
                fun Modifier.movedBy(key: Any, animation: SectionAnimation): Modifier {
                    val isGliding = sectionGlides?.isGliding(key) == true
                    return if (sectionMotion != SectionMotion.SPRING || extraWidth > 0.dp || animation.isTooTallToAnimate || isGliding) {
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
                        when (section) {
                            is RenderSection.Metadata -> SongMetadataSection(
                                modifier = unitModifier,
                                metadata = section.metadata,
                                isSongInfoShown = isSongInfoShown,
                                songInfoEditing = songInfoEditing,
                                playingControls = shownPlayingControls,
                                readsCapoAndTime = section.readsCapoAndTime,
                                animatesControls = sectionMotion == SectionMotion.SPRING && extraWidth <= 0.dp,
                                titleStyle = headerStyle,
                                chordStyle = chordStyle,
                                fontScale = fontScale,
                            )

                            is RenderSection.Chords -> SongChordsSection(
                                modifier = unitModifier.padding(horizontal = CARD_PADDING),
                                section = section,
                                items = items,
                                layouts = checkNotNull(chordLayouts),
                                chordDiagrams = chordDiagrams,
                                onFoldToggled = onChordFoldToggled,
                                isFadingIn = hasChordFoldBeenToggled,
                                headerStyle = headerStyle,
                                fontScale = fontScale,
                            )

                            is RenderSection.Comment -> SongComment(
                                modifier = unitModifier.padding(horizontal = CARD_PADDING),
                                comment = section,
                                notation = model.notation,
                                fontScale = fontScale,
                            )

                            is RenderSection.KeyChange -> SongKeyChange(
                                modifier = unitModifier.padding(horizontal = CARD_PADDING),
                                keyChange = section,
                                chordStyle = chordStyle,
                            )

                            is RenderSection.Timing -> SongTimingLine(
                                modifier = unitModifier,
                                timing = section,
                                style = chordStyle,
                            )

                            is RenderSection.Lines -> if (section.isOnCard) {
                                // The card is drawn by the layout behind the chunks of every piece; the chunk only takes
                                // the card's corners, so that a press on its title row is drawn inside them.
                                val cardShape = MaterialTheme.shapes.large
                                val zeroCorner = CornerSize(0.dp)
                                val isFirstChunk = items.first == 0
                                val isLastChunk = items.last >= section.itemCount - 1
                                CompositionLocalProvider(LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                    SongSectionContent(
                                        modifier = unitModifier.clip(
                                            cardShape.copy(
                                                topStart = if (isFirstChunk) cardShape.topStart else zeroCorner,
                                                topEnd = if (isFirstChunk) cardShape.topEnd else zeroCorner,
                                                bottomEnd = if (isLastChunk) cardShape.bottomEnd else zeroCorner,
                                                bottomStart = if (isLastChunk) cardShape.bottomStart else zeroCorner,
                                            ),
                                        ),
                                        section = section,
                                        items = items,
                                        isOnCard = true,
                                        headerStyle = headerStyle,
                                        lyricsStyle = lyricsStyle,
                                        chordStyle = chordStyle,
                                        textMeasurements = textMeasurements,
                                        foldedRuns = foldedRuns,
                                        defaultLabels = defaultLabels,
                                        notation = model.notation,
                                        fontScale = fontScale,
                                    )
                                }
                            } else {
                                // The same horizontal padding as inside a card, so that every section's text starts at the
                                // same x position whether it is carded or not.
                                SongSectionContent(
                                    modifier = unitModifier.padding(horizontal = CARD_PADDING),
                                    section = section,
                                    items = items,
                                    isOnCard = false,
                                    headerStyle = headerStyle,
                                    lyricsStyle = lyricsStyle,
                                    chordStyle = chordStyle,
                                    textMeasurements = textMeasurements,
                                    foldedRuns = foldedRuns,
                                    defaultLabels = defaultLabels,
                                    notation = model.notation,
                                    fontScale = fontScale,
                                )
                            }
                        }
                    }
                }
                // The cards the pieces of the sections on a card are drawn on, as many as each could be cut into; the
                // ones a layout has no piece for stay unmeasured.
                val cardShape = MaterialTheme.shapes.large
                val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
                cardKeys.forEachIndexed { card, cardKey ->
                    key(cardKey) {
                        Box(
                            modifier = Modifier
                                .movedBy(cardKey, animations[units.unitSections.size + card])
                                .graphicsLayer {
                                    shadowElevation = CARD_ELEVATION.toPx()
                                    shape = cardShape
                                    clip = false
                                }
                                .background(color = cardColor, shape = cardShape),
                        )
                    }
                }
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

private val CARD_ELEVATION = 1.dp

private val MIN_COLUMN_WIDTH = 384.dp
private val MAX_COLUMN_WIDTH = 560.dp
private val COLUMN_GAP = 16.dp
private val SECTION_GAP = 16.dp
private val ROW_GAP = 16.dp
