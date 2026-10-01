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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import com.pandulapeter.campfire.chordpro.ChordProHighlighter
import com.pandulapeter.campfire.chordpro.ChordProTabWrapper
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.CommentPlacement
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_cut
import com.pandulapeter.campfire.presentation.resources.song_details_grid_collapse
import com.pandulapeter.campfire.presentation.resources.song_details_grid_expand
import com.pandulapeter.campfire.presentation.resources.song_details_section_bridge
import com.pandulapeter.campfire.presentation.resources.song_details_section_chorus
import com.pandulapeter.campfire.presentation.resources.song_details_section_collapse
import com.pandulapeter.campfire.presentation.resources.song_details_section_expand
import com.pandulapeter.campfire.presentation.resources.song_details_section_grid
import com.pandulapeter.campfire.presentation.resources.song_details_section_tab
import com.pandulapeter.campfire.presentation.resources.song_details_tab_collapse
import com.pandulapeter.campfire.presentation.resources.song_details_tab_expand
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.presentation.ui.components.ExpandChevron
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders the song [model] with the chords displayed above the lyrics, aligned to the syllable they belong to. A model
 * prepared without chords (see [prepareSongLyrics]) renders only the lyrics: the chords are dropped and lines that
 * consisted of nothing but chords (e.g. an intro) are skipped entirely.
 *
 * The song is split into sections (verse, chorus, ...) which are flowed into rows across the columns by
 * [SongSectionsLayout]. Choruses are drawn on a raised card of their own so that they stand out from the surrounding
 * sections. A section is only cut between columns as a last resort, where [canCutSections] allows it, and sections move
 * to their new place when the column count changes (e.g. when a window is resized), see [sectionMotion].
 *
 * @param model The song and its sections, built away from the main thread by [rememberSongLyricsModel].
 * @param availableHeight The height the song can occupy without scrolling; the column count is picked so that it
 * fits into this if it can.
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
 * @param onRowsPlaced Handed the rows of the song ([SongRows]: where a scroll comes to rest on each - the
 * bottom edge of the divider above it, so that the divider itself is just out of view - and where its content ends),
 * and the stops the song is stepped through, measured from the top of this composable's content, every time they are
 * placed, where they settle rather than where an animation has got them to. The song details screen snaps its scroll to
 * the rows and steps between the stops.
 * @param rowViewportHeight The height of the viewport the song is scrolled in, where that scroll comes to rest on the
 * dividers between the rows: the last row is then followed by empty space down to the bottom of that viewport, so
 * that it can be brought to the top like the others. Unspecified where nothing snaps, which is the editor's preview.
 * @param isOneRowAtATime Whether every row, not only the last one, is followed by empty space down to the bottom of
 * [rowViewportHeight], so that a scroll resting on a divider shows no row but the one under it, in the middle of the
 * screen.
 * @param rowViewportBottomPadding How much the scroll holds under this composable, which is part of the room the last
 * row needs to be brought to the top of the viewport.
 * @param stepButtonInset How much of the end edge a song that has to be scrolled leaves to what is drawn over it there:
 * the song details screen's buttons that step through it. A song that fits the screen has none of them, and takes the
 * width, unless [keepsStepButtonInset] is set: in a setlist the same buttons page to the songs beside it, whatever the
 * song. Zero where there are no buttons at all, which is the editor's preview.
 * @param canCutSections Whether a section may be cut into pieces put side by side in the columns of a row, where the
 * song would not fit the screen otherwise (see [flowIntoRowsCuttingSections]). Off in the editor's preview, which
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
    onRowsPlaced: ((SongRows) -> Unit)? = null,
    rowViewportHeight: Dp = Dp.Unspecified,
    isOneRowAtATime: Boolean = false,
    rowViewportBottomPadding: Dp = 0.dp,
    stepButtonInset: Dp = 0.dp,
    keepsStepButtonInset: Boolean = false,
    canCutSections: Boolean = false,
    isSingleColumn: Boolean = false,
) {
    // The fold toggles of the runs inside a section are named by these too, where the file names them nothing.
    val defaultLabels = rememberDefaultSectionLabels()
    val isSongInfoEditable = songInfoEditing != null
    val sections = remember(model.sections, model.song.metadata, model.shouldShowChords, isSongInfoShown, isSongInfoEditable) {
        withMetadataSection(
            sections = model.sections,
            metadata = model.song.metadata,
            shouldShowChords = model.shouldShowChords,
            isSongInfoShown = isSongInfoShown,
            isSongInfoEditable = isSongInfoEditable,
        )
    }
    val glideScope = rememberCoroutineScope()
    val glideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val sectionGlides = if (sectionMotion == SectionMotion.NONE) null else remember(glideScope, glideSpec) { SectionGlides(glideScope, glideSpec) }
    val density = LocalDensity.current
    // Kept across a new song text rather than keyed on it, since a transposition or a tag put on rewrites the song and
    // must not make what was just unfolded fade in a second time.
    var toggledFolds by remember { mutableStateOf(emptySet<String>()) }
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
    // Every section that may be cut is composed as the chunks it may be cut into, which the layout keeps together until
    // it has no other way of fitting the song (see flowIntoRowsCuttingSections). A folded section is one, since what is
    // left of it is its header.
    val units = remember(sections, foldedSections, canCutSections) {
        SongUnits.of(sections) { section -> canCutSections && section.foldKey !in foldedSections }
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
        val occurrences = HashMap<RenderSection, Int>()
        sections.map { section ->
            val occurrence = (occurrences[section] ?: 0) + 1
            occurrences[section] = occurrence
            SectionKey(hash = section.hashCode(), occurrence = occurrence)
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
                isOneRowAtATime = isOneRowAtATime,
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
                                titleStyle = headerStyle,
                                fontScale = fontScale,
                            )

                            is RenderSection.Comment -> SongComment(
                                modifier = unitModifier.padding(horizontal = CARD_PADDING),
                                comment = section,
                                fontScale = fontScale,
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
                // The dividers between the rows, one for each at most; the ones a song laid out in fewer rows has no place
                // for stay unmeasured.
                repeat((sections.size - 1).coerceAtLeast(0)) { HorizontalDivider() }
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

/**
 * A `{comment}` line: a layout section of its own where it stands between two sections, so that it can sit between two
 * columns freely, and a part of the section it was written in otherwise, folded away with it (see [toRenderSections]).
 */
@Composable
private fun SongComment(
    modifier: Modifier = Modifier,
    comment: RenderSection.Comment,
    fontScale: Float,
) {
    val style = MaterialTheme.typography.bodyMedium.scaled(fontScale).let {
        if (comment.style == CommentStyle.ITALIC) it.copy(fontStyle = FontStyle.Italic) else it
    }
    val chordColor = LocalSecondAccentColor.current
    // The transposition moves the brackets of a comment as it moves those of the lyrics, so they are drawn as chords.
    val annotatedText = remember(comment.text, chordColor) {
        buildAnnotatedString {
            append(comment.text)
            ChordProHighlighter.chordsOfShownText(comment.text).forEach { addStyle(SpanStyle(color = chordColor, fontWeight = FontWeight.Bold), it.start, it.end) }
        }
    }
    val text = @Composable { boxModifier: Modifier ->
        Text(
            modifier = boxModifier,
            text = annotatedText,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (comment.style == CommentStyle.BOX) {
        text(
            modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = MaterialTheme.shapes.small,
                )
                // The box grows with the text it holds, so the room inside its border grows too, or a large text size
                // would set the words against the line.
                .padding(horizontal = COMMENT_BOX_HORIZONTAL_PADDING * fontScale, vertical = COMMENT_BOX_VERTICAL_PADDING * fontScale)
        )
    } else {
        text(modifier)
    }
}

/**
 * The optional header of a section followed by its lines, with the chords drawn above the lyrics.
 *
 * The header is the same raised pill the song lists use for their section headers, so that the sections are told
 * apart at a glance, and it scrolls with its section like everything else on the screen. A chorus already stands out
 * through its card, where another raised surface would only add noise, so there ([isOnCard]) the header is the card's
 * own title row instead. The pill hangs into the section's left padding, so that its text starts on the same keyline
 * as the lyrics below it.
 *
 * **Every section with a header folds from it** ([foldedRuns]), so that a song can be read as the handful of parts that
 * are still to be learned: the pill is the toggle, its chevron after the name, and on a card the whole title row is,
 * its chevron at the far end the way an expandable card carries it — so that a folded chorus is a slim card of its
 * own that still stands out, and pressing anywhere along its top answers. A section with no header has nothing to fold
 * it from, except one that is nothing but tablature or a grid, which gets a toggle named after that.
 *
 * Inside a section every run of tablature and every run of a chord grid folds on its own as well, since a solo written
 * out fret by fret or bar by bar is as tall as several verses and is of no use to somebody who only sings the song. It
 * is folded from a toggle of its own, named by the environment's label or the way a section of nothing else would be
 * ([defaultLabels]), which is what stays behind of it once it is folded. Lyrics only mode drops both altogether (see
 * [prepareForDisplay]), so there is nothing of that kind to fold there.
 *
 * On a card the paddings are laid out here rather than by the caller, so that the title row's press reaches the edges
 * of the card. The card itself is drawn by `SongSectionsLayout` behind what this draws, since it has to be drawn once
 * for every piece a section may be cut into.
 *
 * What this draws is one chunk of the section ([sectionChunkStarts]): the items from [items] (a line, a comment standing
 * between two lines, or a whole run of tablature or grid lines, counted in the section's order), headed by the header
 * where it is the first one, and on a card ending in its bottom padding where it is the last one.
 */
@Composable
private fun SongSectionContent(
    modifier: Modifier = Modifier,
    section: RenderSection.Lines,
    items: IntRange,
    isOnCard: Boolean,
    headerStyle: TextStyle,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
    textMeasurements: SongTextMeasurements,
    foldedRuns: FoldedRuns?,
    defaultLabels: DefaultSectionLabels,
    fontScale: Float,
) = Column(
    modifier = modifier
) {
    val header = section.header
    val wholeSectionKind = section.wholeFoldableKind
    val sectionFold = section.foldKey
    fun foldToggle(key: String) = foldedRuns?.let { FoldToggle(isExpanded = !it.isCollapsed(key), onToggled = { it.toggle(key) }) }
    // A header over nothing (a recalled chorus with nothing left to show) has nothing to fold.
    val sectionToggle = if (section.parts.isNotEmpty() && (header != null || wholeSectionKind != null)) foldToggle(sectionFold) else null
    val hasBody = section.parts.isNotEmpty() && sectionToggle?.isExpanded != false
    val chevronSize = FOLD_CHEVRON_SIZE * fontScale
    val isFirstChunk = items.first == 0
    val isLastChunk = items.last >= section.itemCount - 1
    // Tablature and grids are both columns of characters that have to line up with the ones above and below them.
    val monospaceFontFamily = LocalMonospaceFontFamily.current
    val monospaceLyricsStyle = lyricsStyle.copy(fontFamily = monospaceFontFamily)
    val monospaceChordStyle = chordStyle.copy(fontFamily = monospaceFontFamily)
    if (isFirstChunk) when {
        header != null && isOnCard -> CardTitleRow(
            // The gap under the title belongs to the lines under it, so that a folded card is as deep above its title
            // as below it.
            modifier = Modifier.padding(
                start = CARD_PADDING,
                top = CARD_PADDING,
                end = CARD_PADDING,
                bottom = if (hasBody) HEADER_GAP else CARD_PADDING,
            ),
            header = header,
            toggle = sectionToggle,
            style = headerStyle,
            chevronSize = chevronSize,
        )

        header != null -> SectionHeaderPill(
            header = header,
            toggle = sectionToggle,
            style = headerStyle,
            chevronSize = chevronSize,
        )

        sectionToggle != null && wholeSectionKind != null -> FoldToggleRow(
            modifier = if (isOnCard) Modifier.padding(start = CARD_PADDING, top = CARD_PADDING, end = CARD_PADDING) else Modifier,
            kind = wholeSectionKind,
            label = section.firstEnvironmentLabel ?: defaultLabels.labelOf(wholeSectionKind),
            toggle = sectionToggle,
            style = headerStyle,
            chevronSize = chevronSize,
        )
    }
    // The body leaves the composition while it is folded, so that unfolding it composes it afresh to fade in.
    if (!hasBody) return@Column
    val hasTitle = header != null || sectionToggle != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fadingIn(isFadingIn = foldedRuns?.hasBeenToggled(sectionFold) == true)
            .padding(
                if (isOnCard) {
                    PaddingValues(
                        start = CARD_PADDING,
                        top = if (isFirstChunk && !hasTitle) CARD_PADDING else 0.dp,
                        end = CARD_PADDING,
                        bottom = if (isLastChunk) CARD_PADDING else 0.dp,
                    )
                } else {
                    PaddingValues(top = if (isFirstChunk && header != null) HEADER_GAP else 0.dp)
                }
            ),
    ) {
        // Tablature and grids are runs of lines inside a section rather than sections of their own, so the lines are
        // grouped: each run is folded as one, a run of tablature is also measured as one block (its columns only line
        // up while they are measured together), and everything else is laid out line by line around them. A comment
        // that cut the section stands where the file has it, between the lines around it. A section that is nothing
        // but one run is folded as the section it is, so its run has no toggle of its own.
        // Every item is counted, drawn or not, and so is every run's fold, since a run is named by how many
        // runs of its name came before it in the section.
        val runNameCounts = mutableMapOf<String, Int>()
        var item = 0
        section.parts.forEach { part ->
            when (part) {
                is RenderSection.Comment -> if (item++ in items) {
                    SongComment(
                        modifier = Modifier.padding(vertical = INLINE_COMMENT_GAP),
                        comment = part,
                        fontScale = fontScale,
                    )
                }

                is SectionPart.Lines -> part.runs.forEach { group ->
                    val kind = group.first().foldableKind()
                    if (kind == null) {
                        group.forEach { line ->
                            if (item++ in items) when (line) {
                                is ChordProLine.Lyrics -> if (line.chords.isEmpty()) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = line.text,
                                        style = lyricsStyle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                } else {
                                    SongLineWithChords(
                                        line = line,
                                        lyricsStyle = lyricsStyle,
                                        textMeasurements = textMeasurements,
                                    )
                                }

                                // Never reached: tablature and grid lines are always part of a run of their own.
                                is ChordProLine.Tab, is ChordProLine.Grid -> Unit

                                ChordProLine.Blank -> Text(
                                    text = "",
                                    style = lyricsStyle,
                                )
                            }
                        }
                        return@forEach
                    }
                    val isShown = item++ in items
                    val runModifier = if (wholeSectionKind != null || foldedRuns == null) {
                        Modifier
                    } else {
                        val runName = group.first().environmentLabel ?: kind.name.lowercase()
                        val run = "$sectionFold/${runNameCounts.nextFoldKey(runName)}"
                        if (!isShown) return@forEach
                        FoldToggleRow(
                            kind = kind,
                            label = group.first().environmentLabel ?: defaultLabels.labelOf(kind),
                            toggle = FoldToggle(isExpanded = !foldedRuns.isCollapsed(run), onToggled = { foldedRuns.toggle(run) }),
                            style = headerStyle,
                            chevronSize = chevronSize,
                        )
                        if (foldedRuns.isCollapsed(run)) return@forEach
                        Modifier.fadingIn(isFadingIn = foldedRuns.hasBeenToggled(run))
                    }
                    if (!isShown) return@forEach
                    when (kind) {
                        FoldableKind.TAB -> SongTabBlock(
                            modifier = runModifier.fillMaxWidth(),
                            lines = group.map { (it as? ChordProLine.Tab)?.text.orEmpty() },
                            style = monospaceLyricsStyle,
                            textMeasurer = textMeasurements.textMeasurer,
                        )

                        FoldableKind.GRID -> Column(modifier = runModifier.fillMaxWidth()) {
                            group.forEach { line ->
                                if (line is ChordProLine.Grid) {
                                    SongGridLine(
                                        line = line,
                                        lyricsStyle = monospaceLyricsStyle,
                                        chordStyle = monospaceChordStyle,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The raised pill a section is headed by, which folds the section where it has anything to fold ([toggle]). It is laid
 * out at its own size: the touch target enforcement would grow it to 48dp and push the lines of the section down, just
 * as it would in the lists (see [SectionHeader]).
 */
@Composable
private fun SectionHeaderPill(
    modifier: Modifier = Modifier,
    header: String,
    toggle: FoldToggle?,
    style: TextStyle,
    chevronSize: Dp,
) = CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    val pillModifier = modifier.offset(x = -HEADER_HORIZONTAL_PADDING)
    val content = @Composable {
        SectionTitle(
            modifier = Modifier.padding(horizontal = HEADER_HORIZONTAL_PADDING, vertical = HEADER_VERTICAL_PADDING),
            header = header,
            toggle = toggle,
            style = style,
            chevronSize = chevronSize,
        )
    }
    if (toggle == null) {
        Surface(
            modifier = pillModifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = HEADER_ELEVATION,
            content = content,
        )
    } else {
        Surface(
            onClick = toggle.onToggled,
            modifier = pillModifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = HEADER_ELEVATION,
            content = content,
        )
    }
}

/**
 * The title row of a section drawn on a card, which folds the section where it has anything to fold ([toggle]). It is
 * as wide as the card and pressed as a whole, with the chevron at its far end, since a card has no pill to press.
 */
@Composable
private fun CardTitleRow(
    modifier: Modifier = Modifier,
    header: String,
    toggle: FoldToggle?,
    style: TextStyle,
    chevronSize: Dp,
) = SectionTitle(
    modifier = Modifier
        .fillMaxWidth()
        .then(if (toggle == null) Modifier else Modifier.foldToggleClickable(toggle))
        .then(modifier),
    header = header,
    toggle = toggle,
    style = style,
    chevronSize = chevronSize,
    isChevronAtEnd = true,
)

/** A section's name, followed by the chevron that folds it where it can be folded. */
@Composable
private fun SectionTitle(
    modifier: Modifier = Modifier,
    header: String,
    toggle: FoldToggle?,
    style: TextStyle,
    chevronSize: Dp,
    isChevronAtEnd: Boolean = false,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        modifier = if (isChevronAtEnd) Modifier.weight(1f) else Modifier,
        text = header,
        style = style,
        color = MaterialTheme.colorScheme.primary,
    )
    toggle?.let {
        FoldChevron(
            modifier = Modifier.padding(start = FOLD_CHEVRON_GAP).size(chevronSize),
            kind = null,
            isExpanded = it.isExpanded,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The two ways of writing lines down that can be folded away: neither says anything to somebody who only sings. */
internal enum class FoldableKind {
    TAB,
    GRID,
}

private fun ChordProLine.foldableKind() = when (this) {
    is ChordProLine.Tab -> FoldableKind.TAB
    is ChordProLine.Grid -> FoldableKind.GRID
    is ChordProLine.Lyrics, ChordProLine.Blank -> null
}

/** The kind a section is written in from start to end, which is then folded as a whole, or null for any other. */
private fun List<ChordProLine>.wholeFoldableKind() = when {
    areAll<ChordProLine.Tab>() -> FoldableKind.TAB
    areAll<ChordProLine.Grid>() -> FoldableKind.GRID
    else -> null
}

/** What the `{start_of_tab}` or `{start_of_grid}` a line was written in was labelled, which names its fold. */
private val ChordProLine.environmentLabel
    get() = when (this) {
        is ChordProLine.Tab -> label
        is ChordProLine.Grid -> label
        is ChordProLine.Lyrics, ChordProLine.Blank -> null
    }

/** The name of a fold whose environment was given no label, the one a section of nothing but it would be headed by. */
private fun DefaultSectionLabels.labelOf(kind: FoldableKind) = when (kind) {
    FoldableKind.TAB -> tab
    FoldableKind.GRID -> grid
}

/**
 * Splits lines into runs of tablature, runs of grid lines and runs of everything else, keeping their order. Each of
 * the first two is folded as one, and tablature is also measured and scrolled as a block, so the lines of a run have to
 * reach the layout together. Two environments written one after the other are two runs where they were labelled
 * differently, since each is then folded under its own name.
 */
private fun List<ChordProLine>.groupIntoRuns(): List<List<ChordProLine>> {
    val groups = mutableListOf<List<ChordProLine>>()
    var group = mutableListOf<ChordProLine>()
    forEach { line ->
        val first = group.firstOrNull()
        if (first != null && (first.foldableKind() != line.foldableKind() || first.environmentLabel != line.environmentLabel)) {
            groups += group
            group = mutableListOf()
        }
        group += line
    }
    if (group.isNotEmpty()) groups += group
    return groups
}

/**
 * Which sections, tabs and grids of the song are folded away: [collapsed] is what the preferences hold for it, and a
 * toggle goes to [onToggled] to be saved there. What the page keeps of its own is which keys were [toggled] while it
 * was open, so that only those fade in as they unfold: a page opening onto a section that fades in would be an
 * animation nobody asked for.
 *
 * A section is keyed by what the file calls it and which of the sections called that it is (`chorus#2`, see
 * [RenderSection.Lines.foldKey]), and a run inside it by the section's key and the same two things of the run
 * (`intro#1/picking pattern#1`), never by its position on the page, so that what is saved still names the same part of
 * the song after a verse was added above it. A key that names nothing any more (the section was renamed or removed)
 * simply unfolds nothing.
 */
private class FoldedRuns(
    private val collapsed: Set<String>,
    private val toggled: Set<String>,
    private val onToggled: (key: String) -> Unit,
) {

    fun isCollapsed(key: String) = key in collapsed

    fun hasBeenToggled(key: String) = key in toggled

    fun toggle(key: String) = onToggled(key)
}

/** The next key of a fold named [name] where the ones before it were counted in this map: `name#1`, `name#2`… */
private fun MutableMap<String, Int>.nextFoldKey(name: String): String {
    val occurrence = (this[name] ?: 0) + 1
    this[name] = occurrence
    return "$name#$occurrence"
}

/** Whether a run (or a section that is nothing else) is unfolded, and how to fold or unfold it. */
private class FoldToggle(
    val isExpanded: Boolean,
    val onToggled: () -> Unit,
)

/**
 * What folds a run of tablature or a grid that shares its section with other lines, or sits in a section without a
 * header, and what is left of the run once it is folded. It is named like a section that is nothing but that run would
 * be, and hangs into the section's padding the way a header pill does, so that its text starts on the keyline of the
 * lyrics around it.
 */
@Composable
private fun FoldToggleRow(
    modifier: Modifier = Modifier,
    kind: FoldableKind,
    label: String,
    toggle: FoldToggle,
    style: TextStyle,
    chevronSize: Dp,
) = Row(
    modifier = modifier
        .offset(x = -FOLD_TOGGLE_HORIZONTAL_PADDING)
        .clip(MaterialTheme.shapes.small)
        .foldToggleClickable(toggle)
        .padding(horizontal = FOLD_TOGGLE_HORIZONTAL_PADDING, vertical = FOLD_TOGGLE_VERTICAL_PADDING),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        text = label,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FoldChevron(
        modifier = Modifier.padding(start = FOLD_CHEVRON_GAP).size(chevronSize),
        kind = kind,
        isExpanded = toggle.isExpanded,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun Modifier.foldToggleClickable(toggle: FoldToggle) = clickable(
    role = Role.Button,
    onClick = toggle.onToggled,
)

/** The app's fold chevron, named for what pressing it does to a tab, a grid, or a whole section where [kind] is null. */
@Composable
internal fun FoldChevron(
    modifier: Modifier = Modifier,
    kind: FoldableKind?,
    isExpanded: Boolean,
    tint: Color,
) = ExpandChevron(
    modifier = modifier,
    isExpanded = isExpanded,
    contentDescription = stringResource(
        when (kind) {
            FoldableKind.TAB -> if (isExpanded) Res.string.song_details_tab_collapse else Res.string.song_details_tab_expand
            FoldableKind.GRID -> if (isExpanded) Res.string.song_details_grid_collapse else Res.string.song_details_grid_expand
            null -> if (isExpanded) Res.string.song_details_section_collapse else Res.string.song_details_section_expand
        }
    ),
    tint = tint,
)

/**
 * Fades a run of tablature, a grid or the song's info card in as it is unfolded, while the section around it grows to
 * make room on its own spring (`animateBounds`). Only the opacity is animated, never the size: the column layout decides where every
 * section goes from their intrinsic heights, and a run whose height was still on its way would be measured halfway
 * there.
 */
@Composable
internal fun Modifier.fadingIn(isFadingIn: Boolean): Modifier {
    val alpha = remember { Animatable(if (isFadingIn) 0f else 1f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(alpha) { alpha.animateTo(1f, spec) }
    return graphicsLayer { this.alpha = alpha.value }
}

/**
 * One run of `{start_of_tab}` lines, cut into as many rows as it takes to fit the width. A run with a staff in it is
 * tablature, cut the way a tab book breaks a staff into systems, its rows a blank line apart so that the last string of
 * one is never read as the first string of the next; one with no staff in it is preformatted text - chord names over
 * lyrics most often - cut between words, each line of chord names together with the lyrics under it, and read like
 * wrapped text with no gap between its rows (see [ChordProTabWrapper] for both). Neither scrolls sideways, since a song
 * may be read with nothing but a pedal that scrolls it up and down.
 *
 * The text is drawn rather than composed, because where the cuts fall depends on the width the block is measured
 * at, and the section this block is in is measured at several widths before one wins (see [SongSectionsLayout]):
 * a layout with no children answers every intrinsic measurement by running its measure block, so the height it
 * reports for a candidate width already counts the rows the tab would wrap into at that width.
 */
@Composable
private fun SongTabBlock(
    modifier: Modifier = Modifier,
    lines: List<String>,
    style: TextStyle,
    textMeasurer: TextMeasurer,
) {
    val color = MaterialTheme.colorScheme.onSurface
    val rows = remember(lines, style, textMeasurer) { TabRows(lines, style, textMeasurer) }
    Layout(
        // Drawn rather than composed (see above), so the lines are handed to a screen reader here, as they stand in the file.
        modifier = modifier
            .semantics { contentDescription = lines.joinToString(separator = "\n") }
            .drawBehind {
                var y = 0f
                rows.at(size.width.roundToInt()).forEach { row ->
                    row.forEach { line ->
                        drawText(textLayoutResult = line, color = color, topLeft = Offset(0f, y))
                        y += line.size.height
                    }
                    y += rows.rowGap
                }
            },
    ) { _, constraints ->
        val width = constraints.maxWidth
        val rowsAtWidth = rows.at(width)
        val height = rowsAtWidth.sumOf { row -> row.sumOf { it.size.height } } + rows.rowGap * (rowsAtWidth.size - 1).coerceAtLeast(0)
        layout(
            width = if (width == Constraints.Infinity) rowsAtWidth.maxOf { row -> row.maxOf { it.size.width } } else width,
            height = height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {}
    }
}

/**
 * The laid out rows of one run of tablature, per width. A run is measured at every width the column layout tries
 * and drawn at the one that won, so the widths it has been asked about most recently are kept: the same few come
 * back on every pass, and a row's text is laid out once rather than once per measurement. Only the last few
 * ([MAX_TAB_WIDTHS]), since a window being resized asks about a new width on every frame and none of those comes back.
 */
private class TabRows(
    private val lines: List<String>,
    private val style: TextStyle,
    private val textMeasurer: TextMeasurer,
) {

    private val isTablature = ChordProTabWrapper.isTablature(lines)

    /** The height of a blank line in the tab's own font between the systems of tablature, and nothing elsewhere. */
    val rowGap = if (isTablature) textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE), style).size.height else 0

    // A monospace font, so the width of one character is the width of many divided by their count, measured with
    // enough of them for the rounding of the total not to matter.
    private val characterWidth = textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE.repeat(CHARACTER_WIDTH_SAMPLE_LENGTH)), style).size.width /
            CHARACTER_WIDTH_SAMPLE_LENGTH.toFloat()
    // The width the widest line takes whole, which is also the minimum intrinsic width of the block. A block given that
    // much must not wrap: the character width above is an estimate a fraction of a pixel off, and counting characters
    // with it at exactly this width can come out one short and cut the last bar off onto a staff of its own.
    private val naturalWidth by lazy(LazyThreadSafetyMode.NONE) {
        lines.maxOfOrNull { line -> textMeasurer.measure(AnnotatedString(line), style, softWrap = false).size.width } ?: 0
    }
    private val rowsByWidth = mutableMapOf<Int, List<List<TextLayoutResult>>>()
    private val recentWidths = ArrayDeque<Int>()

    fun at(width: Int): List<List<TextLayoutResult>> {
        recentWidths.remove(width)
        recentWidths.addLast(width)
        if (recentWidths.size > MAX_TAB_WIDTHS) rowsByWidth.remove(recentWidths.removeFirst())
        return rowsByWidth.getOrPut(width) {
            val maxColumns = if (width >= naturalWidth || characterWidth <= 0f) Int.MAX_VALUE else (width / characterWidth).toInt()
            val rows = if (isTablature) ChordProTabWrapper.wrap(lines, maxColumns) else ChordProTabWrapper.wrapPreformatted(lines, maxColumns)
            rows.map { row ->
                row.map { line -> textMeasurer.measure(AnnotatedString(line), style, softWrap = false) }
            }
        }
    }
}

/**
 * What the chorded lines of one page have had measured, shared between them because they keep asking for the
 * same things: a song has a handful of chord names and hundreds of lines carrying them, a repeated verse or a
 * recalled chorus is the same fragments again, and the height of a line and the width of the padding are the
 * same for all of them.
 *
 * It lives for as long as the measurer and the three styles do - which is everything a width depends on: the
 * styles carry the text size, the measurer is rebuilt with the density, the layout direction and the font
 * resolver - and deliberately not for as long as the song does. A transposition renames the chords and leaves
 * the lyrics alone, so the fragment widths it measured before are the ones it needs after.
 *
 * None of this is state: it is filled in by whoever asks first, and the answers never change.
 */
private class SongTextMeasurements(
    val textMeasurer: TextMeasurer,
    private val lyricsStyle: TextStyle,
    private val chordStyle: TextStyle,
    private val annotationStyle: TextStyle,
) {

    /** The height of one line of lyrics, which a chorded line is taller than by the height of its chords. */
    val lyricsLineHeight by lazy(LazyThreadSafetyMode.NONE) {
        textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE), lyricsStyle).size.height
    }

    /** The height of the text of one line of lyrics, without the leading its style's line height adds around it. */
    val lyricsTextHeight by lazy(LazyThreadSafetyMode.NONE) {
        textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE), lyricsStyle.copy(lineHeight = TextUnit.Unspecified)).size.height
    }

    /** The width of one [PADDING] character, which the lyrics under a chord wider than they are get filled up with. */
    val paddingWidth by lazy(LazyThreadSafetyMode.NONE) { measureFragment(PADDING.toString()) }

    private val chordLayouts = HashMap<String, TextLayoutResult>()
    private val annotationLayouts = HashMap<String, TextLayoutResult>()
    private val fragmentWidths = HashMap<String, Float>()

    /** The laid out name of [chord], which is drawn as it is: the colour is given where it is drawn. */
    fun chordLayout(chord: ChordProLine.Lyrics.Chord) = if (chord.isAnnotation) {
        annotationLayouts.bounded().getOrPut(chord.name) { textMeasurer.measure(AnnotatedString(chord.name), annotationStyle) }
    } else {
        chordLayouts.bounded().getOrPut(chord.name) { textMeasurer.measure(AnnotatedString(chord.name), chordStyle) }
    }

    /** The width of a piece of lyrics. Only the number is kept, since nothing is ever drawn from this layout. */
    fun fragmentWidth(fragment: String) = fragmentWidths.bounded().getOrPut(fragment) { measureFragment(fragment) }

    private fun measureFragment(fragment: String) = textMeasurer.measure(AnnotatedString(fragment), lyricsStyle).size.width.toFloat()

    /**
     * The editor's preview is one page for as long as the editor is open and sees every fragment that is ever typed,
     * so a map that only grew would grow for the whole session. Starting over costs one measurement of whatever is
     * still on screen, which is what opening the page cost.
     */
    private fun <T> HashMap<String, T>.bounded() = also { if (size >= MAX_MEASURED_TEXTS) clear() }
}

/**
 * One `{start_of_grid}` line: bars, chords, beats and repeats laid out as a chord chart. A line wider than its column
 * breaks between bars rather than being cut off, the way a staff of tablature is broken into systems, and a single bar
 * wider than the column breaks between its own tokens, so every chord of it stays on the page at any text size.
 */
@Composable
private fun SongGridLine(
    line: ChordProLine.Grid,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
) = FlowRow(modifier = Modifier.fillMaxWidth()) {
    line.tokens.bars().forEach { bar ->
        FlowRow {
            bar.forEach { token ->
                val (text, style, color) = when (token) {
                    is GridToken.Bar -> Triple(token.text, lyricsStyle, MaterialTheme.colorScheme.outline)
                    is GridToken.Chord -> Triple(token.name, chordStyle, LocalSecondAccentColor.current)
                    GridToken.Beat -> Triple(BEAT_SYMBOL, lyricsStyle, MaterialTheme.colorScheme.onSurfaceVariant)
                    is GridToken.Repeat -> Triple(token.text, lyricsStyle, MaterialTheme.colorScheme.onSurfaceVariant)
                    is GridToken.Text -> Triple(token.text, lyricsStyle, Color.Unspecified)
                }
                Text(
                    modifier = Modifier.padding(end = GRID_TOKEN_GAP),
                    text = text,
                    style = style,
                    softWrap = false,
                    color = color,
                )
            }
        }
    }
}

/**
 * The tokens of a grid line cut into bars, each ending on the bar line that closes it, so that a wrapped line never
 * starts with a stray bar line. The line that opens the first bar stays with it, and whatever follows the last bar
 * line (a repeat count, a comment) is a piece of its own.
 */
private fun List<GridToken>.bars(): List<List<GridToken>> {
    val bars = mutableListOf<List<GridToken>>()
    var bar = mutableListOf<GridToken>()
    forEach { token ->
        bar += token
        if (token is GridToken.Bar && bar.size > 1) {
            bars += bar
            bar = mutableListOf()
        }
    }
    if (bar.isNotEmpty()) bars += bar
    return bars
}

internal fun TextStyle.scaled(scale: Float) = copy(
    fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
)

/**
 * Flows its children (the song sections, followed by the dividers that may be drawn between rows of them) into
 * columns.
 *
 * The sections are read across the columns, the way the systems of sheet music are, rather than filling the columns
 * top to bottom and sending the reader on to the top of the next one: they are packed into rows, so that a song that
 * needs to be scrolled never sends the reader back to the top of
 * the next column, since whatever has been scrolled past has been played. Every row gets as many columns as its own
 * sections fill, so a row of two sections is split in two wider columns rather than leaving a hole where a third one
 * would go, and the rows are chosen to take as few pages as possible, the first ones as full as they can be (see
 * [flowIntoRows]). A row of several columns is never taller
 * than [maxRowHeight], the whole of the screen, since the reader could not reach the top of its next column without
 * scrolling back past what was just played, and a section taller than the screen gets a row of its own - but up to
 * that its columns are as tall as they need, so a song that fits the screen in columns is laid out exactly
 * as it would be read top to bottom (see [flowIntoRows]). A section with lines that do not wrap - a staff of tablature
 * longer than a column - may have a row of its own as wide as those lines, where that makes the song shorter. The rows
 * are told apart by a divider drawn in the gap between them. (A single column has no rows to tell apart, so it is laid
 * out as a plain column, without dividers.)
 *
 * The columns are made as wide (and therefore as few) as possible while the whole song still fits into
 * [availableHeight] so that the lyrics wrap as little as they can and the vertical space is actually used: a song
 * that needs three columns is not squeezed into five just because the window is wide enough for five. That is the
 * fewest columns that fit the song in a single row, wherever some number of them does, rather than fewer that fit it in
 * rows stepped through one at a time (see [searchColumnCount]). Songs that do not fit no matter what get as many
 * columns as the width allows in each row. Column widths stay between
 * [minColumnWidth] and [maxColumnWidth] and the whole block is centered, so a short
 * song does not end up as one screen-wide column of short lines. Within it a row narrower than the widest one is
 * centered too, a row of a single column included.
 *
 * What it places are the chunks of [units]: every section whole, or as the chunks it may be cut into, which follow each
 * other in one cell unless the grid cuts between them. Where the song would scroll with its sections whole and
 * [canCutSections], the grid is searched for once more with cuts allowed, and taken where it fits the song on the screen
 * or cuts only sections taller than the screen (see [flowIntoRowsCuttingSections]). The cards of the sections drawn on
 * one are placed here, one behind every piece, from as many as each section could be cut into ([cardKeys]).
 *
 * The candidate column counts are evaluated with the sections' intrinsic heights (they are only measured once, with
 * the width that won), starting from a single column and jumping straight to the smallest count that could possibly
 * fit whenever the current one does not, and a window with room for a single column asks for none of them, since one
 * column is the same grid whatever the heights are.
 *
 * The [SectionGrid] is decided for the width the layout settles at ([extraWidth]), the columns themselves are laid
 * out in the width that is available right now, so that a layout that is still being resized keeps its sections
 * where they are and only lets them grow into the space as it arrives.
 *
 * A section that is not carried by `animateBounds` glides across a change of the grid ([sectionGlides]): where it was
 * placed before and where the new grid places it are measured in the lookahead pass, and the approach pass places it
 * that far from its new place, the distance running down to nothing. Only the grid changing moves a section by more
 * than the frame's own change, so everything else is followed as it comes.
 *
 * The layout is measured on every frame of a navigation transition and of a window being resized, so what the search
 * finds is kept in [sectionMeasurements]: the intrinsic height of every section at every width it was asked about,
 * and the grid decided for the last settled width, which is the same on every frame of a transition.
 *
 * [onRowsPlaced] is handed where the scroll rests on each row (the bottom edge of its divider) and where each ends as they settle,
 * every time the layout is measured ahead, which is before any of them is animated to its place, along with the stops
 * the song is stepped through: those rows wherever the song is read across the columns and scrolls, however many
 * sections a column of them holds, or otherwise a stop above every section. A song that has to be scrolled leaves [stepButtonInset] of the end edge empty, for the buttons that step
 * through it drawn over it there; the grid is searched for at that narrower width, and a song that fits the screen there
 * is laid out across the full one - unless [keepsStepButtonInset], where the buttons page through a setlist and are
 * there whatever the song is.
 *
 * Where the scroll comes to rest on those dividers ([rowViewportHeight]), the last row read across is followed by as
 * much empty space as it leaves of the viewport, so that it can be scrolled to the top of the screen like every other
 * row rather than being read wherever the end of the song happens to leave it - a song of a single row too. That space
 * is only there where the song scrolls anyway: where it is taller than the screen, or where [isOneRowAtATime] makes it
 * so. With [isOneRowAtATime] every other row
 * is followed by that space too, so that a row is read with nothing but itself on the screen: the next row peeking in
 * under it would be read as part of it, which is what the dividers are there to prevent. A row shorter than the screen
 * is then read in the middle of it, the space split above and below it, rather than at the top of a screen that is
 * otherwise empty. The grid is still decided without that space, since it is not something a shorter song could save.
 */
@Composable
private fun SongSectionsLayout(
    modifier: Modifier = Modifier,
    minColumnWidth: Dp,
    maxColumnWidth: Dp,
    columnGap: Dp,
    sectionGap: Dp,
    rowGap: Dp,
    availableHeight: Dp,
    maxRowHeight: Dp,
    rowViewportHeight: Dp,
    isOneRowAtATime: Boolean,
    rowViewportBottomPadding: Dp,
    stepButtonInset: Dp,
    keepsStepButtonInset: Boolean,
    extraWidth: Dp,
    units: SongUnits,
    unitKeys: List<UnitKey>,
    cardKeys: List<CardKey>,
    animations: List<SectionAnimation>,
    cardPadding: Dp,
    canCutSections: Boolean,
    isSingleColumn: Boolean,
    sectionGlides: SectionGlides?,
    sectionMeasurements: SectionMeasurements,
    onRowsPlaced: (SongRows) -> Unit,
    content: @Composable () -> Unit,
) = Layout(
    modifier = modifier,
    content = content,
) { allMeasurables, constraints ->
    val unitCount = units.unitSections.size
    val sectionCount = units.sectionStarts.size - 1
    val measurables = allMeasurables.take(unitCount)
    val cardMeasurables = allMeasurables.subList(unitCount, unitCount + units.cardCount)
    val dividerMeasurables = allMeasurables.drop(unitCount + units.cardCount)
    val width = constraints.maxWidth
    val settledWidth = width + extraWidth.roundToPx()
    val columnGapPx = columnGap.roundToPx()
    val sectionGapPx = sectionGap.roundToPx()
    val rowGapPx = rowGap.roundToPx()
    val maxColumnWidthPx = maxColumnWidth.roundToPx()
    val availableHeightPx = if (availableHeight.isSpecified) availableHeight.roundToPx() else 0
    val maxRowHeightPx = if (maxRowHeight.isSpecified && maxRowHeight > 0.dp) maxRowHeight.roundToPx() else Int.MAX_VALUE
    val endInsetPx = stepButtonInset.roundToPx()
    val piecePadding = IntArray(sectionCount) { if (units.cardStarts[it] >= 0) cardPadding.roundToPx() else 0 }
    fun widthColumnCountFor(totalWidth: Int) = if (isSingleColumn) {
        1
    } else {
        ((totalWidth + columnGapPx) / (minColumnWidth.roundToPx() + columnGapPx)).coerceAtLeast(1)
    }
    fun maxColumnCountFor(totalWidth: Int) = widthColumnCountFor(totalWidth).coerceAtMost(maxOf(1, sectionCount))
    fun columnWidthFor(totalWidth: Int, columnCount: Int) = ((totalWidth - columnGapPx * (columnCount - 1)) / columnCount).coerceIn(0, maxColumnWidthPx)
    fun unitsOf(section: Int) = units.sectionStarts[section] until units.sectionStarts[section + 1]

    // A card is as wide as its widest line, up to the width of its column, so that a short chorus does not stretch a wide
    // empty surface across the column, and every chunk of it is laid out at that width, so that its pieces are as wide
    // as each other. A line that is wider still wraps at the column.
    fun unitWidthFor(unit: Int, columnWidth: Int): Int {
        val section = units.unitSections[unit]
        if (units.cardStarts[section] < 0) return columnWidth
        return minOf(columnWidth, unitsOf(section).maxOf { sectionMeasurements.maxWidth(it, measurables[it]::maxIntrinsicWidth) })
    }

    // The width of a row the section has to itself, or null where that would be no
    // wider than a column: a section can be narrower than its minimum intrinsic width only by breaking the lines that
    // do not wrap (a staff of tablature, the bars of a grid), while everything else in it wraps as a column's would.
    fun wideWidthFor(section: Int, totalWidth: Int) = unitsOf(section).maxOf { sectionMeasurements.minWidth(it, measurables[it]::minIntrinsicWidth) }
        .takeIf { it > maxColumnWidthPx && totalWidth > maxColumnWidthPx }
        ?.let { minOf(it, totalWidth) }

    // A wide row is a single column in which every section is as wide as it needs: the ones whose lines do not wrap as
    // wide as those lines, and the rest as wide as a single column, so that what wraps is not read in lines longer
    // than a column's because a staff of tablature shares the row.
    fun SectionGrid.columnWidthOf(unit: Int, totalWidth: Int) = if (wideRows[rows[unit]]) {
        wideWidthFor(units.unitSections[unit], totalWidth) ?: columnWidthFor(totalWidth, 1)
    } else {
        columnWidthFor(totalWidth, columnCounts[rows[unit]])
    }

    val gridKey = SectionGridKey(
        settledWidth = settledWidth,
        availableHeight = availableHeightPx,
        maxRowHeight = maxRowHeightPx,
        maxColumnCount = maxColumnCountFor(settledWidth),
        endInset = endInsetPx,
        keepsEndInset = keepsStepButtonInset,
    )

    fun searchGrid(totalWidth: Int): SearchedGrid {
        val maxColumnCount = maxColumnCountFor(totalWidth)

        fun unitHeightAt(unit: Int, columnWidth: Int) = sectionMeasurements.height(
            index = unit,
            width = unitWidthFor(unit, columnWidth),
            measure = measurables[unit]::maxIntrinsicHeight,
        )

        fun sectionHeightAt(section: Int, columnWidth: Int) = unitsOf(section).sumOf { unitHeightAt(it, columnWidth) }

        fun heightAt(section: Int, columnCount: Int) = sectionHeightAt(section, columnWidthFor(totalWidth, columnCount))

        fun wideHeightAt(section: Int) = wideWidthFor(section, totalWidth)?.let { sectionHeightAt(section, it) }

        fun gridFor(columnCount: Int) = when {
            // A single column is every section stacked in its order, however tall each of them is, so it is the one
            // grid that is known without an intrinsic measurement. Asking for the heights anyway lays every line of
            // the song out once for a number nobody reads and then once more to be drawn, and on a window too narrow
            // for a second column - which is every phone - this is the only grid there is.
            columnCount == 1 -> singleColumnGrid(sectionCount)
            else -> flowIntoRows(
                sectionCount = sectionCount,
                maxColumnCount = columnCount,
                heightAt = ::heightAt,
                wideHeightAt = ::wideHeightAt,
                sectionGap = sectionGapPx,
                maxRowHeight = maxRowHeightPx,
            )
        }.expandedTo(units.unitSections)

        fun SectionGrid.height() = arrange(
            heights = IntArray(unitCount) { unitHeightAt(it, columnWidthOf(it, totalWidth)) },
            sectionGap = sectionGapPx,
            rowGap = rowGapPx,
            unitSections = units.unitSections,
            piecePadding = piecePadding,
        ).height

        val searched = if (availableHeightPx > 0) {
            searchColumnCount(
                maxColumnCount = maxColumnCount,
                availableHeight = availableHeightPx,
                stackedHeight = { (0 until sectionCount).sumOf { heightAt(it, 1) } + sectionGapPx * (sectionCount - 1).coerceAtLeast(0) },
                gridFor = ::gridFor,
                heightOf = { it.height() },
            )
        } else {
            SearchedGrid(gridFor(maxColumnCount), fits = false, height = Int.MAX_VALUE)
        }

        // Only a song that has to be scrolled with its sections whole is ever cut, and only where the width has room for
        // the pieces side by side - which is what keeps a phone, whose single column is never measured, from measuring
        // anything for it. A song of one section has only ever been tried in a single column, so whether it fits the
        // screen whole is only found out here.
        val cutColumnCount = widthColumnCountFor(totalWidth).coerceAtMost(unitCount)
        if (searched.fits || !canCutSections || availableHeightPx <= 0 || cutColumnCount < 2 || sectionCount > MAX_CUT_SECTION_COUNT) return searched
        val height = searched.grid.height()
        if (height <= availableHeightPx) return SearchedGrid(searched.grid, fits = true, height = height)

        fun cutGrid(cutsEverySection: Boolean) = flowIntoRowsCuttingSections(
            sectionStarts = units.sectionStarts,
            maxColumnCount = cutColumnCount,
            heightAt = { unit, columnCount -> unitHeightAt(unit, columnWidthFor(totalWidth, columnCount)) },
            isCuttableBefore = { unit -> units.isCuttableBefore[unit] },
            wideHeightAt = ::wideHeightAt,
            piecePadding = piecePadding,
            sectionGap = sectionGapPx,
            maxRowHeight = maxRowHeightPx,
            minCutSaving = availableHeightPx / MIN_CUT_SAVING_FRACTION,
            cutsEverySection = cutsEverySection,
        ).takeIf { cutsAnySection(it, units.unitSections) }

        // A song that fits the screen once a section is cut is read without a single scroll, which is worth a cut
        // wherever it is. Otherwise only a section taller than the screen is cut, into columns side by side in which
        // it fits.
        cutGrid(cutsEverySection = true)?.let { grid ->
            val cutHeight = grid.height()
            if (cutHeight <= availableHeightPx) return SearchedGrid(grid, fits = true, height = cutHeight)
        }
        return cutGrid(cutsEverySection = false)?.let { grid -> SearchedGrid(grid, fits = false, height = Int.MAX_VALUE) } ?: searched
    }

    val decidedGrid = sectionMeasurements.grid(gridKey) {
        // The buttons that step through the song sit at the end of the screen, so a song that has to be scrolled leaves
        // them that edge. One that fits the screen has no buttons to leave room for, and is laid out
        // across the whole width - but only where it still fits there, since the grid found for the whole width may be
        // one of fewer, taller columns, which would scroll with the buttons over it. Several rows read across are
        // stepped through however short they are, since each is followed by empty space down to the bottom of the screen.
        val insetSearch = if (endInsetPx > 0) searchGrid(settledWidth - endInsetPx) else null
        fun SearchedGrid.isReadWithoutStepping() = isReadWithoutStepping(fits, grid.columnCounts.size)
        when {
            insetSearch == null -> DecidedGrid(searchGrid(settledWidth).grid, isInset = false)
            keepsStepButtonInset || !insetSearch.isReadWithoutStepping() -> DecidedGrid(insetSearch.grid, isInset = true)
            else -> searchGrid(settledWidth).let { full ->
                if (full.isReadWithoutStepping()) DecidedGrid(full.grid, isInset = false) else DecidedGrid(insetSearch.grid, isInset = true)
            }
        }
    }
    val (grid, isInset) = decidedGrid
    val layoutWidth = if (isInset) (width - endInsetPx).coerceAtLeast(0) else width
    val columnWidths = IntArray(unitCount) { grid.columnWidthOf(it, layoutWidth) }
    // A column is only as wide as the widest thing in it, up to the width it was given, so that a row can be centered
    // by what it shows rather than by the empty space its columns leave after short lines. Only a card needs its width
    // to decide the grid; everything else wraps at the column's width, which is the same height at any width it fits
    // in, so the widths of the rest are only asked for here, once, for the grid that won.
    val cellWidths = Array(grid.columnCounts.size) { row -> IntArray(grid.columnCounts[row]) }
    for (unit in 0 until unitCount) {
        val row = grid.rows[unit]
        val columnWidth = columnWidths[unit]
        val contentWidth = if (units.cardStarts[units.unitSections[unit]] >= 0) {
            unitWidthFor(unit, columnWidth)
        } else {
            minOf(columnWidth, sectionMeasurements.maxWidth(unit, measurables[unit]::maxIntrinsicWidth))
        }
        cellWidths[row][grid.columns[unit]] = maxOf(cellWidths[row][grid.columns[unit]], contentWidth)
    }
    // A card narrower than its cell keeps its own width; everything else takes the cell's, so that its lines, which
    // take their direction from their own content, start at the same edge as the other lines of the cell. In a wide row,
    // where every section has a width of its own, a section is as wide as its widest line instead, every chunk of it
    // alike, so that it can be centered whole.
    val unitWidths = IntArray(unitCount) { unit ->
        val section = units.unitSections[unit]
        when {
            units.cardStarts[section] >= 0 -> unitWidthFor(unit, columnWidths[unit])
            grid.wideRows[grid.rows[unit]] -> unitsOf(section).maxOf {
                minOf(columnWidths[it], sectionMeasurements.maxWidth(it, measurables[it]::maxIntrinsicWidth))
            }
            else -> cellWidths[grid.rows[unit]][grid.columns[unit]]
        }
    }
    val placeables = measurables.mapIndexed { index, measurable ->
        val unitWidth = unitWidths[index]
        val heightLimit = maxAnimatedSectionHeight(unitWidth)
        // Only a section that is being animated is held to the limit. One that reaches it is cut short for the one
        // frame it takes the composition to take the animation off it, which happens far below the screen.
        val maxHeight = if (measurable.layoutId === AnimatedSectionLayoutId) minOf(constraints.maxHeight, heightLimit) else constraints.maxHeight
        val placeable = measurable.measure(Constraints(minWidth = unitWidth, maxWidth = unitWidth, maxHeight = maxHeight))
        // The approach pass of an animated section reports the size the animation is at, which says nothing about
        // the size it is going to: only the lookahead pass measures that.
        if (isLookingAhead) animations[index].isTooTallToAnimate = placeable.height >= heightLimit
        placeable
    }
    val hasSeveralRows = grid.columnCounts.size > 1
    val unitHeights = IntArray(placeables.size) { placeables[it].height }
    // A single column has no rows to tell apart, so it is laid out as one, without dividers or space under it. Rows
    // read across only scroll where they are taller than the screen, or where every one of them is followed by the rest
    // of the screen. A song that fits the screen is left as it is: space under it would only make it scrollable.
    val isScrolledByRow = (hasSeveralRows || grid.columnCounts.any { it > 1 }) && (
        grid.arrange(unitHeights, sectionGapPx, rowGapPx, unitSections = units.unitSections, piecePadding = piecePadding).height > availableHeightPx ||
            hasSeveralRows && isOneRowAtATime && rowViewportHeight.isSpecified
        )
    // The space a row leaves is shared out evenly: as much of it before the first column, between every two and after
    // the last, so that no column looks pushed to one side of the row. Two columns are never closer than a column gap,
    // what is left of the space then being split between the two edges. The whole width is shared out wherever that
    // keeps the row out of the edge left to the buttons, and only the rest of it otherwise.
    fun spacedEvenly(widths: IntArray, regionStart: Int, regionWidth: Int): IntArray {
        val shownCount = widths.count { it > 0 }
        val freeSpace = (regionWidth - widths.sum()).coerceAtLeast(0)
        val gap = maxOf(columnGapPx, freeSpace / (shownCount + 1))
        var start = regionStart + ((freeSpace - gap * (shownCount - 1).coerceAtLeast(0)) / 2).coerceAtLeast(0)
        // A column left empty takes no room at all.
        return IntArray(widths.size) { column -> start.also { if (widths[column] > 0) start += widths[column] + gap } }
    }
    val cellStarts = Array(cellWidths.size) { row ->
        val widths = cellWidths[row]
        val acrossWholeWidth = spacedEvenly(widths, regionStart = 0, regionWidth = width)
        val rowEnd = widths.indices.filter { widths[it] > 0 }.maxOfOrNull { acrossWholeWidth[it] + widths[it] } ?: 0
        val rowStart = widths.indices.filter { widths[it] > 0 }.minOfOrNull { acrossWholeWidth[it] } ?: 0
        when {
            !isInset -> acrossWholeWidth
            layoutDirection == LayoutDirection.Ltr && rowEnd <= layoutWidth -> acrossWholeWidth
            layoutDirection == LayoutDirection.Rtl && rowStart >= endInsetPx -> acrossWholeWidth
            layoutDirection == LayoutDirection.Ltr -> spacedEvenly(widths, regionStart = 0, regionWidth = layoutWidth)
            else -> spacedEvenly(widths, regionStart = endInsetPx, regionWidth = layoutWidth)
        }
    }
    // The dividers span the whole width rather than the rows, which are as wide as the text size makes the columns: a
    // divider that grew and shrank with a pinch would read as part of the song rather than as the page's own.
    val dividerConstraints = Constraints(minWidth = width, maxWidth = width)
    val dividerPlaceables = dividerMeasurables.take((grid.columnCounts.size - 1).coerceAtLeast(0)).map { it.measure(dividerConstraints) }
    val dividerHeight = dividerPlaceables.firstOrNull()?.height ?: 0
    val isPaddedToViewport = isScrolledByRow && rowViewportHeight.isSpecified
    val rowViewportHeightPx = if (isPaddedToViewport) rowViewportHeight.roundToPx() else 0
    // How much of the viewport a row has with the scroll resting on the divider above it: measured from the row's top
    // rather than from where the scroll rests (the bottom of its divider), and less what the scroll holds below this.
    val readableRowHeight = if (isPaddedToViewport) {
        rowViewportHeightPx - rowGapPx / 2 + (dividerHeight - dividerHeight / 2) - rowViewportBottomPadding.roundToPx()
    } else {
        0
    }
    val arrangement = grid.arrange(
        heights = unitHeights,
        sectionGap = sectionGapPx,
        rowGap = rowGapPx,
        // Resting on a divider, half a row gap above its row, the viewport ends half a row gap short of the next row's
        // divider, so that the divider stays out of it too.
        minRowPitch = if (isPaddedToViewport && isOneRowAtATime) rowViewportHeightPx + rowGapPx else 0,
        minLastRowHeight = readableRowHeight,
        // A row read with nothing but itself on the screen is read in the middle of what the screen shows of it.
        centeredRowHeight = if (isOneRowAtATime) readableRowHeight else 0,
        unitSections = units.unitSections,
        piecePadding = piecePadding,
    )
    val dividerTops = arrangement.dividerTops
    // The approach pass places the rows where their sections' animations have got to, which is not where a scroll
    // comes to rest: a fold toggled a moment before a fling would otherwise have it snap to a divider still moving.
    // What is reported is the bottom edge of each divider, so a scroll resting there has the divider just above it.
    if (isLookingAhead) {
        val dividerBottoms = dividerTops.map { it - dividerHeight / 2 + dividerHeight }
        // The first row has no divider above it, and is rested on at its own top.
        val restingOffsets = if (dividerBottoms.isEmpty()) dividerBottoms else listOf(0) + dividerBottoms
        // A song read in rows is stepped through by them, however many sections each column holds: stepping to every
        // section would stop in the middle of a row. A single row has no stops of its own, and is stepped through by
        // its sections.
        val isSteppedByRow = isScrolledByRow && restingOffsets.isNotEmpty()
        val singleColumnUnits = (0 until unitCount).filter { grid.columnCounts[grid.rows[it]] == 1 }
        onRowsPlaced(
            SongRows(
                restingOffsets = restingOffsets,
                bottoms = if (dividerBottoms.isEmpty()) emptyList() else arrangement.rowBottoms,
                // A section is stepped to as far above it as the song fades out under the top of the screen, so that it
                // starts where that fade ends and is read whole, its label included. The first one's stop is above the
                // top of this layout, which whoever is handed them clamps to the top of the song.
                stepOffsets = if (isSteppedByRow) {
                    restingOffsets
                } else {
                    val fadePx = EDGE_FADE_SIZE.roundToPx()
                    List(sectionCount) { section -> arrangement.tops[units.sectionStarts[section]] - fadePx }
                },
                stepSections = if (isSteppedByRow) {
                    List(grid.columnCounts.size) { row -> units.unitSections[grid.rows.indexOfFirst { it == row }] }
                } else {
                    List(sectionCount) { it }
                },
                isSteppedByRow = isSteppedByRow,
                // Only a single column is paged through: a row of several is never taller than the screen.
                lineTops = singleColumnUnits.map { arrangement.tops[it] },
                lineBottoms = singleColumnUnits.map { arrangement.tops[it] + unitHeights[it] },
                lineSections = singleColumnUnits.map { units.unitSections[it] },
            ),
        )
    }
    val dividers = dividerPlaceables.mapIndexed { index, placeable ->
        placeable to IntOffset(x = 0, y = dividerTops[index] - placeable.height / 2)
    }
    val positions = Array(placeables.size) { index ->
        val row = grid.rows[index]
        val column = grid.columns[index]
        val cellStart = cellStarts[row][column]
        // A card narrower than its cell sits at the cell's start, which is its right edge in a right to left layout. A
        // wide row is a single column of sections as wide as each needs, so each of them is centered in it, as the
        // columns of every other row are centered in the width.
        val x = when {
            grid.wideRows[row] -> cellStart + (cellWidths[row][column] - unitWidths[index]) / 2
            layoutDirection == LayoutDirection.Rtl -> cellStart + cellWidths[row][column] - unitWidths[index]
            else -> cellStart
        }
        IntOffset(x = x, y = arrangement.tops[index])
    }
    // Every piece of a section on a card - the whole section, where it is not cut - is drawn on a card of its own,
    // which reaches over the padding the arrangement leaves at a cut: the piece's card, and where it is placed.
    val cards = mutableListOf<Pair<Int, IntRect>>()
    var pieceStart = 0
    var piece = 0
    for (index in 0 until unitCount) {
        val section = units.unitSections[index]
        val isLastOfSection = index == units.sectionStarts[section + 1] - 1
        if (!isLastOfSection && grid.rows[index + 1] == grid.rows[index] && grid.columns[index + 1] == grid.columns[index]) continue
        if (units.cardStarts[section] >= 0) {
            val top = positions[pieceStart].y - if (pieceStart > units.sectionStarts[section]) piecePadding[section] else 0
            val bottom = positions[index].y + placeables[index].height + if (isLastOfSection) 0 else piecePadding[section]
            val left = positions[pieceStart].x
            cards += (units.cardStarts[section] + piece) to IntRect(left, top, left + unitWidths[pieceStart], bottom)
        }
        piece = if (isLastOfSection) 0 else piece + 1
        pieceStart = index + 1
    }
    val cardPlaceables = cards.map { (card, bounds) ->
        val measurable = cardMeasurables[card]
        val heightLimit = maxAnimatedSectionHeight(bounds.width)
        val height = if (measurable.layoutId === AnimatedSectionLayoutId) minOf(bounds.height, heightLimit) else bounds.height
        if (isLookingAhead) animations[unitCount + card].isTooTallToAnimate = bounds.height >= heightLimit
        measurable.measure(Constraints.fixed(bounds.width, height))
    }
    if (isLookingAhead && sectionGlides != null) {
        sectionGlides.follow(
            keys = unitKeys + cards.map { cardKeys[it.first] },
            grid = decidedGrid,
            positions = Array(unitCount + cards.size) { if (it < unitCount) positions[it] else cards[it - unitCount].second.topLeft },
            isCarriedByBounds = { index ->
                (if (index < unitCount) measurables[index] else cardMeasurables[cards[index - unitCount].first]).layoutId === AnimatedSectionLayoutId
            },
        )
    }
    layout(width, arrangement.height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
        // Read while placing, so that a glide only places the sections again on every frame it runs.
        fun glideOf(key: Any) = if (isLookingAhead || sectionGlides == null) IntOffset.Zero else sectionGlides.offsetOf(key)
        // The cards first, since what is placed later is drawn over what was placed before it.
        cardPlaceables.forEachIndexed { index, placeable ->
            val (card, bounds) = cards[index]
            placeable.place(bounds.topLeft + glideOf(cardKeys[card]))
        }
        placeables.forEachIndexed { index, placeable -> placeable.place(positions[index] + glideOf(unitKeys[index])) }
        dividers.forEach { (placeable, position) -> placeable.place(position) }
    }
}

/**
 * What [SongSectionsLayout] has already worked out about one set of sections: the [sizes] of each, which outlive it for
 * the sections a change leaves as they were (see [SectionSizesPool]), and the grid last decided for them. None of it
 * is state: it is read and written by the measurement alone, and a change to it never has anything to redraw. The
 * heights are kept per width and only for the widths of the last few searches.
 */
private class SectionMeasurements(private val sizes: List<SectionSizes>) {

    private var lastGridKey: SectionGridKey? = null
    private var lastGrid = DecidedGrid(emptyGrid(), isInset = false)

    /** The intrinsic height of the section at [index] when it is [width] wide. */
    fun height(index: Int, width: Int, measure: (Int) -> Int): Int = sizes[index].heightsByWidth.getOrPut(width) { measure(width) }

    /** The minimum intrinsic width of the section at [index], which no width changes. */
    fun minWidth(index: Int, measure: (Int) -> Int): Int {
        val sectionSizes = sizes[index]
        if (sectionSizes.minWidth == UNMEASURED) sectionSizes.minWidth = measure(Constraints.Infinity)
        return sectionSizes.minWidth
    }

    /** The maximum intrinsic width of the section at [index], which is how wide a card around it grows. */
    fun maxWidth(index: Int, measure: (Int) -> Int): Int {
        val sectionSizes = sizes[index]
        if (sectionSizes.maxWidth == UNMEASURED) sectionSizes.maxWidth = measure(Constraints.Infinity)
        return sectionSizes.maxWidth
    }

    /** The grid decided for [key], which is only searched for again once the key has changed. */
    fun grid(key: SectionGridKey, search: () -> DecidedGrid): DecidedGrid {
        if (key != lastGridKey) {
            // A window being resized searches at a new width on every frame and none of those comes back, so
            // the widths are only kept until there are more of them than a few searches ask about. They are let
            // go of between two searches and never during one, which asks about the same few over and over.
            sizes.forEach { if (it.heightsByWidth.size > MAX_SECTION_WIDTHS) it.heightsByWidth.clear() }
            lastGrid = search()
            lastGridKey = key
        }
        return lastGrid
    }
}

/** What one section measures, kept for as long as a section equal to it is on the page. */
internal class SectionSizes {

    val heightsByWidth = HashMap<Int, Int>()
    var minWidth = UNMEASURED
    var maxWidth = UNMEASURED
}

/**
 * Hands out the [SectionSizes] of a list of sections, reusing those of the sections the previous list held by content:
 * a section equal to one before measures the same at every width, since nothing outside the section decides its size
 * that the pool is not remembered by. Equal sections take the sizes in the order they come in; a section that is new
 * starts with nothing measured.
 */
internal class SectionSizesPool<T> {

    private var sizesBySection = HashMap<T, ArrayDeque<SectionSizes>>()

    fun sizesFor(sections: List<T>): List<SectionSizes> {
        val next = HashMap<T, ArrayDeque<SectionSizes>>()
        val sizes = sections.map { section ->
            (sizesBySection[section]?.removeFirstOrNull() ?: SectionSizes()).also { next.getOrPut(section) { ArrayDeque() }.addLast(it) }
        }
        sizesBySection = next
        return sizes
    }
}

/** How the sections of [SongLyrics] get to the place a change of its layout gives them. */
internal enum class SectionMotion {

    /**
     * A change that comes on its own - a window maximised, a step of the text size - is narrated: every section springs
     * to its new place and size (`animateBounds`), and one too tall for that glides like [GLIDE].
     */
    SPRING,

    /**
     * A change that keeps coming - a pinch, a window edge being dragged - is followed as it comes, and only the jump a
     * section makes where the grid changes is glided over. A spring restarted on every frame would trail behind the
     * change, and `animateBounds` measures every section at its animated size and at its target size in every frame,
     * while a line of text keeps only one of those layouts, so each would be laid out twice a frame.
     */
    GLIDE,

    /**
     * Every section is simply where the layout puts it, which is the editor's preview: it shows what is being typed rather
     * than narrating it, and every edit that changes a section's height would otherwise move every section below it.
     */
    NONE,
}

/** The key a section is emitted under in [SongLyrics]: its content's hash, and which of the sections equal to it it is. */
private data class SectionKey(
    val hash: Int,
    val occurrence: Int,
)

/** The key of a chunk of a section ([SongUnits]): its section's, and the first of the section's items it holds. */
private data class UnitKey(
    val section: SectionKey,
    val firstItem: Int,
)

/** The key of the card a piece of a section is drawn on: its section's, and which of its pieces it is. */
private data class CardKey(
    val section: SectionKey,
    val piece: Int,
)

/**
 * What a chunk of a section holds, which is what its measurements are reused by (see [SectionSizesPool]): the section
 * and its items from [firstItem] to [lastItem].
 */
private data class UnitContent(
    val section: RenderSection,
    val firstItem: Int,
    val lastItem: Int,
)

/**
 * The chunks the sections of a song are composed and laid out as: every line of a section that may be cut, and every
 * other section whole. The chunks of section `s` are [sectionStarts]`[s]` to [sectionStarts]`[s + 1]`, each of them the
 * section's items in [itemRanges] and each belonging to the section in [unitSections], and the section may only be cut
 * in front of the ones [isCuttableBefore] says so of (see [sectionChunkStarts]). Every line is a chunk of its own, rather
 * than every stretch between two places a cut may fall, so that the layout knows where every line starts, which is
 * where a step through a section taller than the screen brings the next page to (see [SongRows.lineTops]).
 *
 * A section on a card is drawn on as many cards as it has chunks, since no more pieces of it can ever be placed than
 * that: its first is [cardStarts]`[s]` of the [cardCount] the layout is handed, -1 for a section drawn without a card.
 */
private class SongUnits(
    val sectionStarts: IntArray,
    val unitSections: IntArray,
    val itemRanges: List<IntRange>,
    val isCuttableBefore: BooleanArray,
    val cardStarts: IntArray,
    val cardCount: Int,
) {

    companion object {

        /** The chunks of [sections], where each section [isCuttable] says so is composed line by line. */
        fun of(sections: List<RenderSection>, isCuttable: (RenderSection.Lines) -> Boolean): SongUnits {
            val sectionStarts = IntArray(sections.size + 1)
            val unitSections = mutableListOf<Int>()
            val itemRanges = mutableListOf<IntRange>()
            val isCuttableBefore = mutableListOf<Boolean>()
            val cardStarts = IntArray(sections.size) { -1 }
            var cardCount = 0
            sections.forEachIndexed { index, section ->
                val ranges = if (section is RenderSection.Lines && isCuttable(section) && section.itemCount > 1) {
                    List(section.itemCount) { item -> item..item }
                } else {
                    listOf(0 until ((section as? RenderSection.Lines)?.itemCount ?: 1))
                }
                val cutStarts = (section as? RenderSection.Lines)?.chunkStarts
                ranges.forEach { range ->
                    unitSections += index
                    itemRanges += range
                    isCuttableBefore += range.first > 0 && cutStarts?.contains(range.first) == true
                }
                sectionStarts[index + 1] = sectionStarts[index] + ranges.size
                if (section is RenderSection.Lines && section.isOnCard) {
                    cardStarts[index] = cardCount
                    cardCount += ranges.size
                }
            }
            return SongUnits(
                sectionStarts = sectionStarts,
                unitSections = unitSections.toIntArray(),
                itemRanges = itemRanges,
                isCuttableBefore = isCuttableBefore.toBooleanArray(),
                cardStarts = cardStarts,
                cardCount = cardCount,
            )
        }
    }
}

/** Everything the [SectionGrid] depends on, apart from the heights of the sections. */
private data class SectionGridKey(
    val settledWidth: Int,
    val availableHeight: Int,
    val maxRowHeight: Int,
    val maxColumnCount: Int,
    val endInset: Int,
    val keepsEndInset: Boolean,
)

/** The grid [SongSectionsLayout] decided on, and whether it was decided for the width less the end inset. */
private data class DecidedGrid(
    val grid: SectionGrid,
    val isInset: Boolean,
) {

    /** Whether [other] lays every section out in the same cell and in the same width as this one. */
    fun hasSameCellsAs(other: DecidedGrid) = isInset == other.isInset && grid.hasSameCellsAs(other.grid)
}

/**
 * Whether a section can be animated to its place inside [SongSectionsLayout]. The layout is the only one that knows
 * how tall a section is, which decides that (see [maxAnimatedSectionHeight]), so it is handed back to the section
 * through this.
 */
private class SectionAnimation {

    var isTooTallToAnimate by mutableStateOf(false)
}

/**
 * The glide of every section across a change of the grid, see [SongSectionsLayout]. The positions are followed in the
 * lookahead pass and the offsets read in the approach pass, both by the key of each section, so a section that is
 * added or edited has no position to glide from.
 */
private class SectionGlides(
    private val scope: CoroutineScope,
    private val spec: AnimationSpec<IntOffset>,
) {

    private val glides = HashMap<Any, SectionGlide>()
    private var lastKeys: List<Any>? = null
    private var lastGrid: DecidedGrid? = null

    /** Whether the chunk or the card of [key] is still on its way to its place, which is state. */
    fun isGliding(key: Any) = glides[key]?.isGliding == true

    /** How far from its place the chunk or the card of [key] is drawn, which is state. */
    fun offsetOf(key: Any) = glides[key]?.offset ?: IntOffset.Zero

    /**
     * Takes the [positions] the sections of [keys] are placed at in [grid], and starts a glide for every one whose
     * position moved because the grid changed, unless it [isCarriedByBounds], which animates its own.
     */
    fun follow(keys: List<Any>, grid: DecidedGrid, positions: Array<IntOffset>, isCarriedByBounds: (Int) -> Boolean) {
        if (keys !== lastKeys) {
            val current = keys.toHashSet()
            glides.entries.removeAll { (key, glide) -> (key !in current).also { if (it) glide.stop() } }
            lastKeys = keys
        }
        val isNewGrid = lastGrid.let { it != null && !it.hasSameCellsAs(grid) }
        lastGrid = grid
        keys.forEachIndexed { index, key ->
            val glide = glides.getOrPut(key) { SectionGlide() }
            val previous = glide.target
            glide.target = positions[index]
            if (isNewGrid && previous != null && previous != positions[index] && !isCarriedByBounds(index)) {
                glide.start(jump = previous - positions[index], scope = scope, spec = spec)
            }
        }
    }
}

/**
 * The glide of one section: where it was last placed, and how far from there it is still drawn. A jump is added to
 * [offset] in the pass that finds it rather than when the coroutine that animates it gets to run, which is after that
 * frame has been placed, so the section would otherwise be drawn at its new place for a frame before being sent back.
 */
private class SectionGlide {

    var target: IntOffset? = null
    var isGliding by mutableStateOf(false)
        private set
    private val animatable = Animatable(IntOffset.Zero, IntOffset.VectorConverter)
    private var pendingJump = IntOffset.Zero
    private var job: Job? = null

    val offset get() = animatable.value + pendingJump

    fun start(jump: IntOffset, scope: CoroutineScope, spec: AnimationSpec<IntOffset>) {
        pendingJump += jump
        isGliding = true
        job?.cancel()
        job = scope.launch {
            val velocity = animatable.velocity
            animatable.snapTo(animatable.value + pendingJump)
            pendingJump = IntOffset.Zero
            animatable.animateTo(targetValue = IntOffset.Zero, animationSpec = spec, initialVelocity = velocity)
            isGliding = false
        }
    }

    fun stop() {
        job?.cancel()
    }
}

/**
 * The layout id of a section that carries `animateBounds`. It is part of the same modifier chain, so what
 * [SongSectionsLayout] reads can never disagree with what is actually attached, not even for the one frame
 * between a measurement and the composition that answers it.
 */
private object AnimatedSectionLayoutId

/**
 * The tallest a section of [columnWidth] is measured while it carries `animateBounds`, which measures its content
 * with `Constraints.fixed` of the section's own size on every pass. A `Constraints` has 31 bits for a width and a
 * height together: 18 of them are left for the height next to a width of less than [WIDE_SECTION_WIDTH], and 16
 * next to a wider one. Both limits are half of what would fit, since a spring that is turned around on its way
 * can carry the animated size past both of its ends.
 */
private fun maxAnimatedSectionHeight(columnWidth: Int) =
    if (columnWidth < WIDE_SECTION_WIDTH) MAX_ANIMATED_SECTION_HEIGHT else MAX_ANIMATED_WIDE_SECTION_HEIGHT

/**
 * A song ready to be laid out: what [SongLyrics] draws, which can be built on any thread (see [prepareSongLyrics]).
 *
 * @param isCut Whether [sections] stop short of the end of [song], see [LayoutBudget].
 * @param shouldShowChords False for lyrics-only mode, see [prepareSongLyrics].
 */
internal class SongLyricsModel(
    val song: ChordProSong,
    val sections: List<RenderSection>,
    val isCut: Boolean,
    val shouldShowChords: Boolean,
)

/**
 * Builds the sections of [song] the way [SongLyrics] lays them out. It touches nothing but its arguments, so it is
 * run away from the main thread: it is a pass over the whole song that would otherwise land on the frame being waited
 * for, after a transposition or a pause in typing.
 *
 * @param shouldShowChords False for lyrics-only mode, which drops the chords, the sections that are nothing else and
 * the key, capo, tempo and time of the metadata section.
 */
internal fun prepareSongLyrics(
    song: ChordProSong,
    shouldShowChords: Boolean,
    labels: DefaultSectionLabels,
) = LayoutBudget.fit(song.toRenderSections(shouldShowChords, labels)).let { (sections, isCut) ->
    SongLyricsModel(song = song, sections = sections, isCut = isCut, shouldShowChords = shouldShowChords)
}

/** Everything a [SongLyricsModel] is built from, see [rememberSongLyricsModel]. */
internal data class SongLyricsInputs(
    val text: String,
    val transposition: Int,
    val spelling: UserPreferences.ChordSpelling,
    val shouldShowChords: Boolean,
    val labels: DefaultSectionLabels,
)

/**
 * The model [prepare] builds of [inputs]. The first one is built right here, so that a page never opens on an empty
 * frame; every later one is built on [Dispatchers.Default], with the one before it staying on screen until it is
 * ready. A change that arrives meanwhile cancels the wait, although not the parse itself, which is not cooperative:
 * that one finishes in the background and its result is dropped.
 */
@Composable
internal fun rememberSongLyricsModel(
    inputs: SongLyricsInputs,
    prepare: (SongLyricsInputs) -> SongLyricsModel,
): SongLyricsModel {
    val latestPrepare by rememberUpdatedState(prepare)
    val state = remember { mutableStateOf(inputs to prepare(inputs)) }
    LaunchedEffect(inputs) {
        if (state.value.first != inputs) state.value = inputs to withContext(Dispatchers.Default) { latestPrepare(inputs) }
    }
    return state.value.second
}

/** The fallback labels of the environments that have one, read here since they are string resources. */
@Composable
internal fun rememberDefaultSectionLabels() = DefaultSectionLabels(
    chorus = stringResource(Res.string.song_details_section_chorus),
    bridge = stringResource(Res.string.song_details_section_bridge),
    tab = stringResource(Res.string.song_details_section_tab),
    grid = stringResource(Res.string.song_details_section_grid),
)

/** The fallback names of the environments that have one. Everything else is named by the file itself. */
internal data class DefaultSectionLabels(
    val chorus: String,
    val bridge: String,
    val tab: String,
    val grid: String,
)

/**
 * One section of the song. The column layout places sections whole, or as the chunks they may be cut into as a last
 * resort (see [SongUnits]).
 *
 * Immutable, and marked so on every class rather than on the interface alone, since the compiler would infer the lists
 * they hold as unstable: they are built fresh by [toRenderSections] and never changed, which is what lets a section
 * whose content did not change skip recomposition.
 */
@Immutable
internal sealed interface RenderSection {

    /** The descriptive metadata, inserted after the body budget is applied and kept whole in the first grid cell. */
    @Immutable
    data class Metadata(val metadata: ChordProMetadata) : RenderSection

    /** A titled block of lines: an environment, an implicit paragraph, or a repeated chorus. */
    @Immutable
    data class Lines(
        val header: String?,
        /** What the section is folded away by, see [FoldedRuns]. */
        val foldKey: String,
        /** The lines, and the comments that stand between them inside the section, in the order the file has them. */
        val parts: List<SectionPart>,
        /** Choruses (and their recalls) are drawn on a raised card so that they stand out. */
        val isOnCard: Boolean,
    ) : RenderSection {

        /** Text-dependent values are built with the section, never again during zoom or theme recomposition. */
        val lines = parts.flatMap { (it as? SectionPart.Lines)?.lines.orEmpty() }

        /**
         * What [SongSectionContent] draws the section as, one under the other: every comment between two of its lines,
         * every line, and every run of tablature or grid lines as one.
         */
        val itemKinds = parts.flatMap { part ->
            when (part) {
                is Comment -> listOf(SectionItemKind.COMMENT)
                is SectionPart.Lines -> part.runs.flatMap { run ->
                    if (run.first().foldableKind() != null) {
                        listOf(SectionItemKind.CONTENT)
                    } else {
                        run.map { line -> if (line == ChordProLine.Blank) SectionItemKind.BLANK else SectionItemKind.CONTENT }
                    }
                }
            }
        }
        val itemCount get() = itemKinds.size

        /** Where the section may be cut, see [sectionChunkStarts]. */
        val chunkStarts = sectionChunkStarts(itemKinds)
        val wholeFoldableKind = lines.wholeFoldableKind()
        val firstEnvironmentLabel = lines.firstNotNullOfOrNull { it.environmentLabel }
    }

    /** A comment between two sections, or one inside a section as one of its [SectionPart]s. */
    @Immutable
    data class Comment(
        val text: String,
        val style: CommentStyle,
    ) : RenderSection, SectionPart
}

/** A piece of a [RenderSection.Lines]: a run of its lines, or a comment standing between two of them. */
@Immutable
internal sealed interface SectionPart {

    @Immutable
    data class Lines(val lines: List<ChordProLine>) : SectionPart {
        /** Run boundaries depend on the lines alone, including their environment labels. */
        val runs = lines.groupIntoRuns()
    }
}

/**
 * Flattens the parsed song into the sections the layout places.
 *
 * A section a comment cut in two (see `ChordProBlock.Section.isContinuation`) is put back together here, with the
 * comment between its halves, since the layout would otherwise place the comment and each half as units of their own,
 * free to land in different columns or rows. The comments a section opens or ends with are put inside it too
 * (`ChordProBlock.Comment.placement`), so that every comment written in a section folds away with it, while one written
 * between two sections stays a unit of its own that nothing folds. Breaks and `{transpose}` directives cut a section
 * the same way and draw nothing, so they are joined over as well; a `{chorus}` recall is a section of its own and is not.
 *
 * A `{chorus}` recall repeats the chorus the parser found for it (`ChordProBlock.ChorusRecall.blocks`), every piece of
 * it and the comments inside it, headed once. In lyrics-only mode the chords go away with the sections that consist
 * of nothing else: tabs and grids say nothing without them, and a line that was only chords would leave a blank behind.
 * A comment written inside a tab or a grid goes with it, since it is a note about what is no longer there.
 */
private fun ChordProSong.toRenderSections(
    shouldShowChords: Boolean,
    defaultLabels: DefaultSectionLabels,
): List<RenderSection> {
    val sections = mutableListOf<RenderSection>()
    // Counted for every section the file has, whether or not this mode shows it, so that lyrics-only mode dropping a
    // tab does not rename the sections after it.
    val foldNameCounts = mutableMapOf<String, Int>()

    /**
     * Adds a section and what [joinCutSections] joined to it, or only the comments where lyrics-only mode leaves it
     * with nothing else to show; true when a section was added.
     */
    fun addSection(pieces: List<ChordProBlock>, header: String?, foldKey: String): Boolean {
        val comments = pieces.filterIsInstance<ChordProBlock.Comment>().mapNotNull { it.toRenderSection(shouldShowChords) }
        val sectionPieces = pieces.filterIsInstance<ChordProBlock.Section>()
        // A section that is nothing but tablature or a grid goes away entirely in lyrics-only mode, its heading with
        // it: neither says anything without the chords, and a heading over nothing is worse than no heading at all.
        // The comments in it that are not about the tablature or the grid still say something, so they stay.
        if (!shouldShowChords && sectionPieces.all { piece -> piece.lines.all { it.needsChords() || it.isBlank() } }) {
            sections += comments
            return false
        }
        val parts = mutableListOf<SectionPart>()
        pieces.forEach { piece ->
            when (piece) {
                is ChordProBlock.Section -> {
                    val lines = piece.lines.prepareForDisplay(shouldShowChords)
                    if (lines.isEmpty()) return@forEach
                    // What cut the section there drew nothing (a break, a transposition), so the halves are one run of
                    // lines again, and a tab on either side of the cut is folded and wrapped as the one run it is.
                    val previous = parts.lastOrNull()
                    if (previous is SectionPart.Lines) {
                        parts[parts.lastIndex] = SectionPart.Lines(previous.lines + lines)
                    } else {
                        parts += SectionPart.Lines(lines)
                    }
                }

                is ChordProBlock.Comment -> piece.toRenderSection(shouldShowChords)?.let { parts += it }

                else -> Unit
            }
        }
        // A section that ended up with no line to show is dropped, unless its header still says something.
        if (parts.none { it is SectionPart.Lines } && header == null) {
            sections += comments
            return false
        }
        sections += RenderSection.Lines(
            header = header,
            foldKey = foldKey,
            parts = parts,
            isOnCard = sectionPieces.first().type == SectionType.Chorus,
        )
        return true
    }

    blocks.joinCutSections().forEach { pieces ->
        when (val block = pieces.firstSection()) {
            is ChordProBlock.Break -> Unit // The column layout makes its own breaks.

            is ChordProBlock.Transpose -> Unit // It moved the chords; there is nothing to draw.

            is ChordProBlock.Comment -> block.toRenderSection(shouldShowChords)?.let { sections += it }

            is ChordProBlock.ChorusRecall -> {
                // The heading goes on the first piece of the chorus that is shown, and stays behind on its own when
                // none is: a recall has always said where the chorus is sung, even with nothing under it.
                val label = block.label ?: (block.blocks.firstOrNull() as? ChordProBlock.Section)?.label
                var header: String? = label ?: defaultLabels.chorus
                // A recall is folded apart from the chorus it repeats, as the chorus it is, and whatever of it is
                // shown after its first piece is named after that piece.
                val recallFoldKey = foldNameCounts.nextFoldKey(label ?: SectionType.Chorus.foldName)
                block.blocks.joinCutSections().forEachIndexed { index, recalled ->
                    when (val first = recalled.firstSection()) {
                        is ChordProBlock.Section -> {
                            val foldKey = if (index == 0) recallFoldKey else "$recallFoldKey/$index"
                            if (addSection(recalled, header, foldKey)) header = null
                        }

                        is ChordProBlock.Comment -> first.toRenderSection(shouldShowChords)?.let { sections += it }
                        else -> Unit
                    }
                }
                header?.let { sections += RenderSection.Lines(header = it, foldKey = recallFoldKey, parts = emptyList(), isOnCard = true) }
            }

            // The rest of a section a comment or a break cut in two was headed where it started.
            is ChordProBlock.Section -> addSection(
                pieces = pieces,
                header = if (block.isContinuation) null else block.header(defaultLabels),
                foldKey = foldNameCounts.nextFoldKey(block.foldName()),
            )
        }
    }
    return sections
}

/**
 * Groups the blocks so that a section comes with the comments it opens with, every continuation of it that follows
 * and the comments, breaks and transpositions that cut it there, and the comments it ends with, in their order. Every
 * other block is a group of its own, and so is a comment written between two sections, or a break or a transposition
 * that no continuation follows.
 */
private fun List<ChordProBlock>.joinCutSections(): List<List<ChordProBlock>> {
    val groups = mutableListOf<List<ChordProBlock>>()
    var start = 0
    while (start < size) {
        var end = start
        while (end < lastIndex && this[end].isCommentPlaced(CommentPlacement.START_OF_SECTION)) end++
        if (this[end] !is ChordProBlock.Section) end = start
        if (this[end] is ChordProBlock.Section) {
            while (true) {
                var next = end + 1
                while (next < size && this[next].isSectionCut()) next++
                val continuation = getOrNull(next) as? ChordProBlock.Section
                if (continuation?.isContinuation != true) break
                end = next
            }
            // A break or a transposition between the section and a comment it ends with is joined over, as it would be
            // between two of its halves.
            var next = end + 1
            while (next < size && (this[next] is ChordProBlock.Break || this[next] is ChordProBlock.Transpose || this[next].isCommentPlaced(CommentPlacement.IN_SECTION))) {
                if (this[next] is ChordProBlock.Comment) end = next
                next++
            }
        }
        groups += subList(start, end + 1)
        start = end + 1
    }
    return groups
}

/** The section a group of [joinCutSections] is of, or its only block where it is not one. */
private fun List<ChordProBlock>.firstSection() = firstOrNull { it is ChordProBlock.Section } ?: first()

private fun ChordProBlock.isCommentPlaced(placement: CommentPlacement) = this is ChordProBlock.Comment && this.placement == placement

/** What a comment is drawn as, or null where lyrics-only mode leaves out the tab or grid it is a note about. */
private fun ChordProBlock.Comment.toRenderSection(shouldShowChords: Boolean) =
    if (isInTabOrGrid && !shouldShowChords) null else RenderSection.Comment(text = text, style = style)

/** Whether the block is one that cuts a section in two without being a section itself (see `ChordProParser`). */
private fun ChordProBlock.isSectionCut() = this is ChordProBlock.Comment || this is ChordProBlock.Break || this is ChordProBlock.Transpose

/**
 * What a section's fold is keyed by: its label as the file writes it, or what kind of section it is where it has
 * none. Never the heading it is shown under, which is translated for the sections the file leaves unnamed, and a
 * folded chorus would unfold with the app's language otherwise.
 */
private fun ChordProBlock.Section.foldName() = label ?: when (val sectionType = type) {
    is SectionType.Custom -> sectionType.name
    SectionType.Paragraph -> when {
        lines.areAll<ChordProLine.Tab>() -> FoldableKind.TAB.name.lowercase()
        lines.areAll<ChordProLine.Grid>() -> FoldableKind.GRID.name.lowercase()
        else -> sectionType.foldName
    }

    else -> sectionType.foldName
}

private val SectionType.foldName
    get() = when (this) {
        SectionType.Verse -> "verse"
        SectionType.Chorus -> "chorus"
        SectionType.Bridge -> "bridge"
        SectionType.Paragraph -> "paragraph"
        is SectionType.Custom -> name
    }

private fun ChordProBlock.Section.header(defaultLabels: DefaultSectionLabels): String? = label ?: when (val sectionType = type) {
    SectionType.Chorus -> defaultLabels.chorus
    SectionType.Bridge -> defaultLabels.bridge
    // "pre-chorus" reads as "Pre-chorus": the file's own wording, only capitalised.
    is SectionType.Custom -> sectionType.name.replaceFirstChar { it.uppercaseChar() }
    SectionType.Verse -> null
    // A paragraph that is nothing but tablature or a grid is a bare `{start_of_tab}` / `{start_of_grid}` standing
    // on its own, and those name themselves even where the file gave them no label. One with lyrics around the run
    // is an ordinary paragraph that happens to hold some, and heading that "Tab" would be a lie.
    SectionType.Paragraph -> when {
        lines.areAll<ChordProLine.Tab>() -> defaultLabels.tab
        lines.areAll<ChordProLine.Grid>() -> defaultLabels.grid
        else -> null
    }
}

/** True when every line that says anything is of the given kind, blank lines inside the run notwithstanding. */
private inline fun <reified T : ChordProLine> List<ChordProLine>.areAll() =
    any { it is T } && all { it is T || it == ChordProLine.Blank }

/**
 * Drops the chords when they are not wanted, along with the lines that were nothing but chords, and trims the blank
 * lines off the end so that a section does not carry empty space into the column layout.
 *
 * Tablature and grids go with the chords: both say nothing at all without them, and now that they are runs inside a
 * section rather than sections of their own, the lines are what has to be dropped.
 */
private fun List<ChordProLine>.prepareForDisplay(shouldShowChords: Boolean): List<ChordProLine> = let { lines ->
    if (shouldShowChords) lines else lines.mapNotNull { line ->
        when (line) {
            is ChordProLine.Lyrics -> if (line.text.isBlank()) null else line.copy(chords = emptyList())
            else -> if (line.needsChords()) null else line
        }
    }
}.dropLastWhile { it.isBlank() }

/** Whether a line says nothing at all once the chords are hidden: tablature and a grid are chords and little else. */
private fun ChordProLine.needsChords() = this is ChordProLine.Tab || this is ChordProLine.Grid

private fun ChordProLine.isBlank() = when (this) {
    ChordProLine.Blank -> true
    is ChordProLine.Lyrics -> text.isBlank() && chords.isEmpty()
    is ChordProLine.Tab -> text.isBlank()
    is ChordProLine.Grid -> tokens.isEmpty()
}

/**
 * The lyrics are laid out with a line height that leaves room above every (wrapped) line for the chords,
 * which are then drawn at the horizontal position of the character they are attached to.
 * Whenever a chord is wider than the piece of lyrics beneath it, that piece is padded with non-breaking spaces so
 * that consecutive chords never overlap and the line wraps before the chords would run off the edge.
 * The chords are kept apart in the direction the line runs: a line whose content is right to left is drawn from the
 * right edge leftwards, and a chord then hangs to the left of the character it belongs to.
 */
@Composable
private fun SongLineWithChords(
    line: ChordProLine.Lyrics,
    lyricsStyle: TextStyle,
    textMeasurements: SongTextMeasurements,
) {
    val density = LocalDensity.current
    val chordColor = LocalSecondAccentColor.current
    val annotationColor = MaterialTheme.colorScheme.onSurfaceVariant
    val chordLayouts = remember(line, textMeasurements) { line.chords.map(textMeasurements::chordLayout) }
    val paddedLine = remember(line, textMeasurements, density) {
        line.padLyricsToFitChords(
            chordWidths = chordLayouts.map { it.size.width.toFloat() },
            gap = with(density) { CHORD_GAP.toPx() },
            paddingWidth = textMeasurements.paddingWidth,
            measureWidth = textMeasurements::fragmentWidth,
        )
    }
    val chordLineHeight = chordLayouts.maxOf { it.size.height }
    // Lines without any lyrics (e.g. an intro) only need to be as tall as the chords themselves. The height is in pixels
    // and is handed over as a multiple of the font size rather than in sp: under Android's non-linear font scaling a
    // line height in sp is scaled by the same factor as the font size rather than by its own, so a round trip from
    // pixels through sp comes back taller, and the lyrics, which sit at the bottom of the line, drift away from their
    // chords.
    val lineHeight = with(density) {
        (if (line.text.isBlank()) chordLineHeight else chordLineHeight + textMeasurements.lyricsLineHeight).toFloat() / lyricsStyle.fontSize.toPx()
    }.em
    // The chords are drawn with the leading of their style above and below them, and the lyrics start right under that,
    // so their own leading goes below them instead: a chord then sits as close to the words it is played over as the
    // leading of one line, and the next line's chords are three times that further down. Sat on the very bottom of the
    // line, the lyrics would be exactly the other way around, closer to the chords of the next line than to their own.
    val lyricsLeading = (textMeasurements.lyricsLineHeight - textMeasurements.lyricsTextHeight).coerceAtLeast(0)
    val lyricsAlignment = LineHeightStyle.Alignment(topRatio = chordLineHeight.toFloat() / (chordLineHeight + lyricsLeading))
    var lyricsLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // The chords are drawn rather than composed, so a screen reader would read the padded lyrics alone. It is given
    // the line the way ChordPro writes it instead, each chord in brackets where it falls.
    val description = remember(line) { line.withChordsInline() }
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .drawBehind {
                val layout = lyricsLayout ?: return@drawBehind
                val textLength = layout.layoutInput.text.length
                val gap = CHORD_GAP.toPx()
                // Each chord is drawn at its row's top, inside the line's height, so the clip only cuts what reaches past
                // the end edge: a chord or an annotation wider than the line, which is clipped rather than wrapped, since a
                // second line of it would land on the lyrics under it. The content description keeps its whole text.
                clipRect {
                    var previousLineIndex = -1
                    // The edge the next chord of this line must not cross: where it has to start on a line that runs to
                    // the right, where it has to end on one that runs to the left. One variable rather than two, since
                    // there is one rule - a chord never sits on the chord before it.
                    var previousChordEdge = 0f
                    paddedLine.chords.forEachIndexed { index, chord ->
                        val chordLayout = chordLayouts[index]
                        val offset = chord.position.coerceIn(0, textLength)
                        val lineIndex = layout.getLineForOffset(offset)
                        // A right to left paragraph is laid out from the right edge leftwards, so x falls as the offset
                        // grows and the chords have to be kept apart the other way. The paragraph's direction rather than
                        // that of the run the chord lands in, since "further along the line" is the paragraph's to say: two
                        // chords of one line answering differently would be drawn on top of each other.
                        val isRightToLeft = layout.getParagraphDirection(offset) == ResolvedTextDirection.Rtl
                        if (lineIndex != previousLineIndex) {
                            previousLineIndex = lineIndex
                            previousChordEdge = if (isRightToLeft) size.width else 0f
                        }
                        val chordWidth = chordLayout.size.width
                        val maxX = max(0f, size.width - chordWidth)
                        val position = layout.getHorizontalPosition(offset, usePrimaryDirection = true)
                        // Kept inside the line where that leaves the chord before it alone; where it cannot, the chord stays
                        // where it belongs and what reaches past the edge is clipped, since a chord drawn over another one
                        // cannot be read at all.
                        val x = if (isRightToLeft) {
                            // The chord hangs to the left of its character, the way it hangs to the right of it in a line
                            // that runs the other way, so the position is its right edge.
                            min(previousChordEdge - chordWidth, max(min(position, previousChordEdge) - chordWidth, 0f))
                        } else {
                            max(previousChordEdge, min(max(position, previousChordEdge), maxX))
                        }
                        drawText(
                            textLayoutResult = chordLayout,
                            color = if (chord.isAnnotation) annotationColor else chordColor,
                            topLeft = Offset(x, layout.getLineTop(lineIndex)),
                        )
                        previousChordEdge = if (isRightToLeft) x - gap else x + chordWidth + gap
                    }
                }
            },
        text = paddedLine.text,
        style = lyricsStyle.copy(
            lineHeight = lineHeight,
            lineHeightStyle = LineHeightStyle(
                alignment = lyricsAlignment,
                trim = LineHeightStyle.Trim.None,
            ),
        ),
        color = MaterialTheme.colorScheme.onSurface,
        onTextLayout = { lyricsLayout = it },
    )
}

/** The line with each chord written into it in brackets, where it sits: "[Am]There is a [C]house". */
private fun ChordProLine.Lyrics.withChordsInline() = buildString {
    var start = 0
    chords.sortedBy { it.position }.forEach { chord ->
        val position = chord.position.coerceIn(start, text.length)
        append(text, start, position)
        append('[').append(chord.name).append(']')
        start = position
    }
    append(text, start, text.length)
}

/**
 * Returns a copy of the line where every piece of lyrics that sits under a chord is at least as wide as the chord
 * (plus [gap]), by appending non-breaking spaces, each [paddingWidth] wide, to it. Chord positions are updated to point
 * into the padded lyrics.
 *
 * The padded line may only wrap between two chords, and only where the original text has a word boundary: the
 * whitespace at either end of a padded piece is made non-breaking too, so that a chord, the space it sits on and the
 * padding that makes room for it never end up on two rows, and a [BREAK_OPPORTUNITY] follows every piece that ends at a
 * word boundary. Without it a chord-only line would be one unbreakable run the layout can only break at an arbitrary
 * character, leaving the chord at the end of a row with no room for it; with it every chord starts whatever row it is
 * sent to. The inner spaces of a piece stay ordinary, so a long piece of lyrics under one chord still wraps between its
 * words.
 */
internal fun ChordProLine.Lyrics.padLyricsToFitChords(
    chordWidths: List<Float>,
    gap: Float,
    paddingWidth: Float,
    measureWidth: (String) -> Float,
): ChordProLine.Lyrics {
    val paddedLyrics = StringBuilder(text.substring(0, chords.first().position))
    val paddedChords = chords.mapIndexed { index, chord ->
        val end = chords.getOrNull(index + 1)?.position ?: text.length
        val fragment = text.substring(chord.position, end)
        val paddedChord = chord.copy(position = paddedLyrics.length)
        val missingWidth = chordWidths[index] + gap - measureWidth(fragment)
        if (missingWidth > 0 && paddingWidth > 0) {
            val innerStart = fragment.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) fragment.length else it }
            val innerEnd = fragment.indexOfLast { !it.isWhitespace() } + 1
            repeat(innerStart) { paddedLyrics.append(PADDING) }
            if (innerEnd > innerStart) paddedLyrics.append(fragment, innerStart, innerEnd)
            repeat(fragment.length - maxOf(innerStart, innerEnd)) { paddedLyrics.append(PADDING) }
            repeat(ceil(missingWidth / paddingWidth).toInt()) { paddedLyrics.append(PADDING) }
        } else {
            paddedLyrics.append(fragment)
        }
        val isWordBoundary = end == 0 || text.getOrNull(end - 1)?.isWhitespace() == true || text.getOrNull(end)?.isWhitespace() == true
        if (index < chords.lastIndex && isWordBoundary) paddedLyrics.append(BREAK_OPPORTUNITY)
        paddedChord
    }
    return ChordProLine.Lyrics(text = paddedLyrics.toString(), chords = paddedChords)
}

private val CHORD_GAP = 4.dp
private val GRID_TOKEN_GAP = 6.dp
private val CARD_PADDING = 12.dp
private val CARD_ELEVATION = 1.dp
private val HEADER_GAP = 8.dp
private val INLINE_COMMENT_GAP = 12.dp
private val COMMENT_BOX_HORIZONTAL_PADDING = 12.dp
private val COMMENT_BOX_VERTICAL_PADDING = 8.dp
private val HEADER_ELEVATION = 2.dp
private val HEADER_HORIZONTAL_PADDING = 12.dp
private val HEADER_VERTICAL_PADDING = 6.dp
internal val FOLD_CHEVRON_SIZE = 20.dp
internal val FOLD_CHEVRON_GAP = 4.dp
private val FOLD_TOGGLE_HORIZONTAL_PADDING = 8.dp
private val FOLD_TOGGLE_VERTICAL_PADDING = 4.dp
private val MIN_COLUMN_WIDTH = 384.dp
private val MAX_COLUMN_WIDTH = 560.dp
private val COLUMN_GAP = 32.dp
private val SECTION_GAP = 20.dp
private val ROW_GAP = 40.dp
private const val LINE_HEIGHT_SAMPLE = "X"
private const val CHARACTER_WIDTH_SAMPLE_LENGTH = 64
private const val MAX_TAB_WIDTHS = 8
private const val MAX_SECTION_WIDTHS = 32

/**
 * A row may cut a section to save more of its height than it would otherwise, but not for less than this fraction of
 * the screen, since the cut costs the reader more than a sliver of height saves them.
 */
private const val MIN_CUT_SAVING_FRACTION = 8

/**
 * The most sections a song may have for its sections to be cut at all. A file of more than this is a songbook rather
 * than a song, and is read by paging through it; the search for cuts grows faster than the song does, runs on the main
 * thread, and runs again on every frame of a pinch.
 */
private const val MAX_CUT_SECTION_COUNT = 200
private const val UNMEASURED = -1
private const val MAX_MEASURED_TEXTS = 4096
private const val MAX_ANIMATED_SECTION_HEIGHT = 1 shl 17
private const val MAX_ANIMATED_WIDE_SECTION_HEIGHT = 1 shl 15
private const val WIDE_SECTION_WIDTH = (1 shl 13) - 1
private const val PADDING = '\u00A0' // Non-breaking space, so that the padding never gets trimmed or wrapped.

/**
 * A zero-width space, which is where a line padded to fit its chords may wrap: default-ignorable, so no font draws
 * anything for it, and a break opportunity after it to every line breaker (class ZW).
 */
private const val BREAK_OPPORTUNITY = '\u200B'
private const val BEAT_SYMBOL = "\u00B7"
