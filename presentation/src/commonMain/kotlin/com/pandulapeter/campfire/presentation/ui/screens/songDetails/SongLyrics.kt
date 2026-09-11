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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.song_details_album
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_composer
import com.pandulapeter.campfire.presentation.resources.song_details_duration
import com.pandulapeter.campfire.presentation.resources.song_details_lyricist
import com.pandulapeter.campfire.presentation.resources.song_details_section_bridge
import com.pandulapeter.campfire.presentation.resources.song_details_section_chorus
import com.pandulapeter.campfire.presentation.resources.song_details_section_grid
import com.pandulapeter.campfire.presentation.resources.song_details_section_tab
import com.pandulapeter.campfire.presentation.resources.song_details_tag_add
import com.pandulapeter.campfire.presentation.resources.song_details_tag_remove
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_time
import com.pandulapeter.campfire.presentation.resources.song_details_year
import com.pandulapeter.campfire.presentation.ui.components.TagFlowRow
import com.pandulapeter.campfire.presentation.ui.components.TagPill
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.ceil
import kotlin.math.max

/**
 * Renders the raw song data with the chords displayed above the lyrics, aligned to the syllable they belong to.
 * When [shouldShowChords] is false, only the lyrics are rendered: the chords are dropped and lines that consisted of
 * nothing but chords (e.g. an intro) are skipped entirely.
 *
 * The song is split into sections (verse, chorus, ...) which are flowed into columns by [SongSectionsLayout], either
 * top to bottom or, when [isHorizontalFlow] is set, in rows across the columns. Choruses are drawn on a raised card
 * of their own so that they stand out from the surrounding sections. A section is never split between columns, and
 * sections animate to their new place when the column count changes (e.g. when a window is resized).
 *
 * @param availableHeight The height the song can occupy without scrolling; the column count is picked so that it
 * fits into this if it can.
 * @param extraWidth How much wider this layout is going to be once the animation that is currently resizing it has
 * finished (see [SongDetailsScreen]'s settled width). The column count is decided for that final width, so that the
 * sections do not flow into a different number of columns for the duration of a navigation transition and then jump
 * back. While it is not zero the sections also stop animating to their new place: the layout is following a width
 * that changes on every frame, and springing after each of those only makes it lag behind.
 * @param fontScale Multiplier applied to the text sizes (and to the column widths, so that larger text does not get
 * squeezed into narrow columns).
 * @param isHorizontalFlow Whether the sections should be read across the columns and then downwards (see
 * [SongSectionsLayout]) instead of column by column.
 * @param scrollState The state of the scrolling container this layout is placed in, so that clicking a section
 * header can scroll back to the start of its section.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SongLyrics(
    modifier: Modifier = Modifier,
    song: ChordProSong,
    availableHeight: Dp = Dp.Unspecified,
    extraWidth: Dp = 0.dp,
    shouldShowChords: Boolean = true,
    fontScale: Float = 1f,
    isHorizontalFlow: Boolean = false,
    scrollState: ScrollState,
    onAddTag: (() -> Unit)? = null,
    onRemoveTag: ((String) -> Unit)? = null,
) {
    // The fallback labels of the environments that have one; everything else is named by the file itself.
    val defaultLabels = DefaultSectionLabels(
        chorus = stringResource(Res.string.song_details_section_chorus),
        bridge = stringResource(Res.string.song_details_section_bridge),
        tab = stringResource(Res.string.song_details_section_tab),
        grid = stringResource(Res.string.song_details_section_grid),
    )
    val sections = remember(song, shouldShowChords, defaultLabels) { song.toRenderSections(shouldShowChords, defaultLabels) }
    // Where the sections ended up, published by the layout so that a header can scroll back to its own section.
    val sectionBounds = remember(sections) { List(sections.size) { SectionBounds() } }
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lyricsStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge).scaled(fontScale)
    val headerStyle = MaterialTheme.typography.titleSmall.scaled(fontScale)
    val chordStyle = lyricsStyle.copy(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    // Annotations ([*text]) sit in the chord row but are not chords, so they borrow the lyrics' colour.
    val annotationStyle = lyricsStyle.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontStyle = FontStyle.Italic,
    )
    // The metadata header scrolls with the song, so the columns below it have that much less room to fit into.
    var headerHeight by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeight.toDp() }
    Column(modifier = modifier) {
        SongMetadataHeader(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CARD_PADDING)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    if (placeable.height != headerHeight) headerHeight = placeable.height
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
            song = song,
            fontScale = fontScale,
            onAddTag = onAddTag,
            onRemoveTag = onRemoveTag,
        )
        LookaheadScope {
            SongSectionsLayout(
                minColumnWidth = MIN_COLUMN_WIDTH * fontScale,
                maxColumnWidth = MAX_COLUMN_WIDTH * fontScale,
                columnGap = COLUMN_GAP,
                sectionGap = SECTION_GAP,
                rowGap = ROW_GAP,
                availableHeight = if (availableHeight.isSpecified) (availableHeight - headerHeightDp).coerceAtLeast(0.dp) else availableHeight,
                extraWidth = extraWidth,
                sectionCount = sections.size,
                isHorizontalFlow = isHorizontalFlow,
                sectionBounds = sectionBounds,
            ) {
                sections.forEachIndexed { index, section ->
                    val bounds = sectionBounds[index]
                    val sectionModifier = if (extraWidth > 0.dp) Modifier else Modifier.animateBounds(this@LookaheadScope)
                    when (section) {
                        is RenderSection.Comment -> SongComment(
                            modifier = sectionModifier.padding(horizontal = CARD_PADDING),
                            comment = section,
                            fontScale = fontScale,
                        )

                        is RenderSection.Lines -> if (section.isOnCard) {
                            Surface(
                                modifier = sectionModifier,
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = CARD_ELEVATION,
                            ) {
                                SongSectionContent(
                                    modifier = Modifier.padding(CARD_PADDING),
                                    section = section,
                                    isOnCard = true,
                                    headerStyle = headerStyle,
                                    lyricsStyle = lyricsStyle,
                                    chordStyle = chordStyle,
                                    annotationStyle = annotationStyle,
                                )
                            }
                        } else {
                            // The same horizontal padding as inside a card, so that every section's text starts at the
                            // same x position whether it is carded or not.
                            SongSectionContent(
                                modifier = sectionModifier.padding(horizontal = CARD_PADDING),
                                section = section,
                                isOnCard = false,
                                headerStyle = headerStyle,
                                lyricsStyle = lyricsStyle,
                                chordStyle = chordStyle,
                                annotationStyle = annotationStyle,
                                // Scrolls the section back to the top of the screen.
                                onHeaderClick = { coroutineScope.launch { scrollState.animateScrollTo(bounds.top) } },
                            )
                        }
                    }
                }
                // The dividers between the rows of the horizontal flow. At most one fewer than there are sections is
                // ever placed, the rest stay unmeasured.
                repeat((sections.size - 1).coerceAtLeast(0)) { HorizontalDivider() }
            }
        }
    }
}

/**
 * The tags of the song and everything else its directives say, above the lyrics and scrolling with them. Every
 * directive the editor can insert has to be visible somewhere, and this is where the ones that are neither lyrics
 * nor chords end up - so what is missing here is only what the app bar already carries: the title, the subtitle
 * that is drawn in parentheses after it, the artist, and the key, which lives inside the transposition control that
 * is the only reason to look at it.
 *
 * @param onAddTag Null where the tags are only read, which is the editor's preview: there the file itself is under
 *   the caret, and a chip writing into it from the side would be editing the text the editor has not saved yet.
 */
@Composable
private fun SongMetadataHeader(
    modifier: Modifier = Modifier,
    song: ChordProSong,
    fontScale: Float,
    onAddTag: (() -> Unit)?,
    onRemoveTag: ((String) -> Unit)?,
) = Column(modifier = modifier.padding(bottom = SECTION_GAP)) {
    val metadata = song.metadata
    if (metadata.tags.isNotEmpty() || onAddTag != null) {
        TagFlowRow(
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            metadata.tags.forEach { tag ->
                TagPill(
                    text = tag,
                    trailingIcon = if (onRemoveTag == null) null else painterResource(Res.drawable.ic_clear),
                    trailingIconContentDescription = onRemoveTag?.let { stringResource(Res.string.song_details_tag_remove, tag) },
                    onTrailingIconClick = onRemoveTag?.let { { it(tag) } },
                )
            }
            onAddTag?.let { onClick ->
                TagPill(
                    text = stringResource(Res.string.song_details_tag_add),
                    onClick = onClick,
                    leadingIcon = painterResource(Res.drawable.ic_add),
                )
            }
        }
    }
    // What is played, in the accent colour, above who wrote it: one is read off the page while playing and the
    // other is only ever looked up.
    MetadataLine(
        values = listOfNotNull(
            metadata.capo?.takeIf { it != 0 }?.let { stringResource(Res.string.song_details_capo, it) },
            metadata.tempo?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_tempo, it) },
            metadata.time?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_time, it) },
        ),
        style = MaterialTheme.typography.labelLarge.scaled(fontScale),
        color = MaterialTheme.colorScheme.primary,
    )
    MetadataLine(
        values = listOfNotNull(
            metadata.composer?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_composer, it) },
            metadata.lyricist?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_lyricist, it) },
            metadata.album?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_album, it) },
            metadata.year?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_year, it) },
            metadata.duration?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_duration, it) },
        ),
        style = MaterialTheme.typography.bodyMedium.scaled(fontScale),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One line of the header's metadata, or nothing at all where the song declares none of it. */
@Composable
private fun MetadataLine(
    modifier: Modifier = Modifier,
    values: List<String>,
    style: TextStyle,
    color: Color,
) {
    if (values.isEmpty()) return
    Text(
        modifier = modifier.padding(top = 4.dp),
        text = values.joinToString("  $CHIP_SEPARATOR  "),
        style = style,
        color = color,
    )
}

/** A `{comment}` line. Its own layout section, so that it can sit between two columns freely. */
@Composable
private fun SongComment(
    modifier: Modifier = Modifier,
    comment: RenderSection.Comment,
    fontScale: Float,
) {
    val style = MaterialTheme.typography.bodyMedium.scaled(fontScale).let {
        if (comment.style == CommentStyle.ITALIC) it.copy(fontStyle = FontStyle.Italic) else it
    }
    val text = @Composable { boxModifier: Modifier ->
        Text(
            modifier = boxModifier,
            text = comment.text,
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
                .padding(horizontal = 8.dp, vertical = 4.dp)
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
 * through its card, where another raised surface would only add noise, so there ([isOnCard]) the header stays a
 * plain label. The pill hangs into the section's left padding, so that its text starts on the same keyline as the
 * lyrics below it.
 */
@Composable
private fun SongSectionContent(
    modifier: Modifier = Modifier,
    section: RenderSection.Lines,
    isOnCard: Boolean,
    headerStyle: TextStyle,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
    annotationStyle: TextStyle,
    onHeaderClick: () -> Unit = {},
) = Column(
    modifier = modifier
) {
    section.header?.let { header ->
        if (isOnCard) {
            Text(
                modifier = Modifier.fillMaxWidth().padding(bottom = HEADER_GAP),
                text = header,
                style = headerStyle,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            // The pill is laid out at its own size: the touch target enforcement would grow it to 48dp and push the
            // lines of the section down, just as it would in the lists (see [SectionHeader]).
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Surface(
                    onClick = onHeaderClick,
                    modifier = Modifier
                        .offset(x = -HEADER_HORIZONTAL_PADDING)
                        .padding(bottom = HEADER_GAP),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = HEADER_ELEVATION,
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = HEADER_HORIZONTAL_PADDING, vertical = HEADER_VERTICAL_PADDING),
                        text = header,
                        style = headerStyle,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
    // Tablature is a run of lines inside a section rather than a section of its own, so the lines are grouped:
    // each run is one sideways scrolling block (its columns only line up while they are measured together), and
    // everything else is laid out line by line around it.
    section.lines.groupConsecutiveTabs().forEach { group ->
        if (group.first() is ChordProLine.Tab) {
            // Tablature only makes sense with its columns intact, so it never wraps and scrolls sideways instead.
            Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                group.forEach { line ->
                    Text(
                        text = (line as? ChordProLine.Tab)?.text.orEmpty(),
                        style = lyricsStyle.copy(fontFamily = FontFamily.Monospace),
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            return@forEach
        }
        group.forEach { line ->
            when (line) {
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
                        chordStyle = chordStyle,
                        annotationStyle = annotationStyle,
                    )
                }

                is ChordProLine.Grid -> SongGridLine(
                    line = line,
                    lyricsStyle = lyricsStyle,
                    chordStyle = chordStyle,
                )

                // Never reached: a tab line is always part of a group the branch above has taken.
                is ChordProLine.Tab -> Unit

                ChordProLine.Blank -> Text(
                    text = "",
                    style = lyricsStyle,
                )
            }
        }
    }
}

/**
 * Splits lines into runs of tablature and runs of everything else, keeping their order. Tablature is measured and
 * scrolled as a block, so the lines of one have to reach the layout together.
 */
private fun List<ChordProLine>.groupConsecutiveTabs(): List<List<ChordProLine>> {
    val groups = mutableListOf<List<ChordProLine>>()
    var group = mutableListOf<ChordProLine>()
    forEach { line ->
        if (group.isNotEmpty() && (group.first() is ChordProLine.Tab) != (line is ChordProLine.Tab)) {
            groups += group
            group = mutableListOf()
        }
        group += line
    }
    if (group.isNotEmpty()) groups += group
    return groups
}

/** One `{start_of_grid}` line: bars, chords, beats and repeats laid out in a row, as a chord chart. */
@Composable
private fun SongGridLine(
    line: ChordProLine.Grid,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
) = Row(modifier = Modifier.fillMaxWidth()) {
    line.tokens.forEach { token ->
        val (text, style) = when (token) {
            is GridToken.Bar -> token.text to lyricsStyle.copy(color = MaterialTheme.colorScheme.outline)
            is GridToken.Chord -> token.name to chordStyle
            GridToken.Beat -> BEAT_SYMBOL to lyricsStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            is GridToken.Repeat -> token.text to lyricsStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            is GridToken.Text -> token.text to lyricsStyle
        }
        Text(
            modifier = Modifier.padding(end = GRID_TOKEN_GAP),
            text = text,
            style = style,
            softWrap = false,
            color = style.color,
        )
    }
}

private fun TextStyle.scaled(scale: Float) = copy(
    fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
)

/**
 * Flows its children (the song sections, followed by the dividers that may be drawn between rows of them) into
 * columns.
 *
 * By default the sections fill the columns top to bottom and the reader continues at the top of the next column.
 * With [isHorizontalFlow] they are read across the columns instead, the way the systems of sheet music are: the
 * sections are packed into rows, so that a song that needs to be scrolled never sends the reader back to the top of
 * the next column, since whatever has been scrolled past has been played. Consecutive short sections are stacked
 * into the same column of a row as long as the stack is no taller than the tallest section of the row, so that the
 * rows stay compact. The rows are told apart by a divider drawn in the gap between them. (A single column reads the
 * same way in both modes, so it is always laid out as a plain column, without dividers.)
 *
 * The columns are made as wide (and therefore as few) as possible while the whole song still fits into
 * [availableHeight], so that the lyrics wrap as little as they can and the vertical space is actually used: a song
 * that needs three columns is not squeezed into five just because the window is wide enough for five. Songs that do
 * not fit no matter what get as many columns as the width allows. Column widths stay between [minColumnWidth] and
 * [maxColumnWidth] and the whole block is centered, so a short song does not end up as one screen-wide column of
 * short lines.
 *
 * The candidate column counts are evaluated with the sections' intrinsic heights (they are only measured once, with
 * the width that won), starting from a single column and jumping straight to the smallest count that could possibly
 * fit whenever the current one does not.
 *
 * The count is decided for the width the layout settles at ([extraWidth]), the columns themselves are laid out in
 * the width that is available right now, so that a layout that is still being resized keeps its sections where they
 * are and only lets them grow into the space as it arrives.
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
    extraWidth: Dp,
    sectionCount: Int,
    isHorizontalFlow: Boolean,
    sectionBounds: List<SectionBounds>,
    content: @Composable () -> Unit,
) = Layout(
    modifier = modifier,
    content = content,
) { allMeasurables, constraints ->
    val measurables = allMeasurables.take(sectionCount)
    val dividerMeasurables = allMeasurables.drop(sectionCount)
    val width = constraints.maxWidth
    val settledWidth = width + extraWidth.roundToPx()
    val columnGapPx = columnGap.roundToPx()
    val sectionGapPx = sectionGap.roundToPx()
    val rowGapPx = rowGap.roundToPx()
    val maxColumnWidthPx = maxColumnWidth.roundToPx()
    val availableHeightPx = if (availableHeight.isSpecified) availableHeight.roundToPx() else 0
    val maxColumnCount = ((settledWidth + columnGapPx) / (minColumnWidth.roundToPx() + columnGapPx)).coerceIn(1, maxOf(1, measurables.size))
    fun columnWidthFor(totalWidth: Int, columnCount: Int) = ((totalWidth - columnGapPx * (columnCount - 1)) / columnCount).coerceIn(0, maxColumnWidthPx)
    fun List<Int>.arrangeInto(columnCount: Int) = if (isHorizontalFlow && columnCount > 1) {
        flowIntoRows(columnCount, sectionGapPx, rowGapPx, maxStackHeight = if (availableHeightPx > 0) availableHeightPx else Int.MAX_VALUE)
    } else {
        balanceIntoColumns(columnCount, sectionGapPx)
    }

    var columnCount = maxColumnCount
    if (availableHeightPx > 0) {
        var candidate = 1
        while (candidate < maxColumnCount) {
            val heights = measurables.map { it.maxIntrinsicHeight(columnWidthFor(settledWidth, candidate)) }
            if (heights.arrangeInto(candidate).height <= availableHeightPx) break
            // Even a perfectly even split needs this many columns, so there is no point in trying the ones in between.
            val totalHeight = heights.sum() + sectionGapPx * (heights.size - 1).coerceAtLeast(0)
            candidate = maxOf(candidate + 1, ceil(totalHeight.toDouble() / availableHeightPx).toInt())
        }
        columnCount = candidate.coerceAtMost(maxColumnCount)
    }

    val columnWidth = columnWidthFor(width, columnCount)
    val placeables = measurables.map { it.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth, maxHeight = constraints.maxHeight)) }
    val arrangement = placeables.map { it.height }.arrangeInto(columnCount)
    val contentWidth = columnWidth * columnCount + columnGapPx * (columnCount - 1)
    val startX = ((width - contentWidth) / 2).coerceAtLeast(0)
    val dividers = arrangement.dividerTops.take(dividerMeasurables.size).mapIndexed { index, top ->
        val placeable = dividerMeasurables[index].measure(Constraints(minWidth = contentWidth, maxWidth = contentWidth))
        placeable to IntOffset(x = startX, y = top - placeable.height / 2)
    }
    layout(width, arrangement.height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
        placeables.forEachIndexed { index, placeable ->
            // Published before the section is placed, so that its header can already scroll to the new position.
            sectionBounds[index].top = arrangement.tops[index]
            placeable.place(x = startX + arrangement.columns[index] * (columnWidth + columnGapPx), y = arrangement.tops[index])
        }
        dividers.forEach { (placeable, position) -> placeable.place(position) }
    }
}

/**
 * Where a section ended up inside [SongSectionsLayout]. The layout is the only one that knows this, and the sections
 * need it to scroll back to their own start when their header is clicked, so it is handed back to them through this.
 */
private class SectionBounds {

    var top by mutableIntStateOf(0)
}

/**
 * Where every section goes: its column, its y position, the total height of the layout and the y positions
 * (centers) of the gaps between rows that should get a divider.
 */
private class SongArrangement(
    val columns: IntArray,
    val tops: IntArray,
    val height: Int,
    val dividerTops: List<Int> = emptyList(),
)

/**
 * Distributes the sections (given by their heights, in order) into [columnCount] columns, filled top to bottom, so
 * that the columns end up as close to equally tall as possible without ever splitting a section.
 *
 * A greedy fill would leave every column a little short of the ideal height and dump all of the accumulated slack on
 * the last one, so instead this is a dynamic program over the split points that minimizes the squared deviation of
 * the columns from the ideal height. Every column gets at least one section, so the columns always span the full
 * width of the layout.
 */
private fun List<Int>.balanceIntoColumns(columnCount: Int, sectionGap: Int): SongArrangement {
    if (isEmpty()) return SongArrangement(columns = IntArray(0), tops = IntArray(0), height = 0)
    val prefixHeights = IntArray(size + 1)
    forEachIndexed { index, height -> prefixHeights[index + 1] = prefixHeights[index] + height + sectionGap }
    fun heightOf(from: Int, until: Int) = prefixHeights[until] - prefixHeights[from] - sectionGap
    val idealHeight = heightOf(0, size).toDouble() / columnCount
    val splits = Array(columnCount) { IntArray(size + 1) }
    // costs[i] holds the cost of the best distribution of the first i sections into the columns processed so far.
    var costs = DoubleArray(size + 1) { Double.MAX_VALUE }
    costs[0] = 0.0
    for (column in 0 until columnCount) {
        val nextCosts = DoubleArray(size + 1) { Double.MAX_VALUE }
        // The first `column` sections are taken by the previous columns and the last ones are still needed by the
        // remaining columns, since no column may be left empty.
        for (until in column + 1..size - (columnCount - column - 1)) {
            for (from in column until until) {
                if (costs[from] == Double.MAX_VALUE) continue
                val deviation = heightOf(from, until) - idealHeight
                val cost = costs[from] + deviation * deviation
                if (cost < nextCosts[until]) {
                    nextCosts[until] = cost
                    splits[column][until] = from
                }
            }
        }
        costs = nextCosts
    }
    val starts = IntArray(columnCount)
    var until = size
    for (column in columnCount - 1 downTo 0) {
        starts[column] = splits[column][until]
        until = starts[column]
    }
    val columns = IntArray(size)
    val tops = IntArray(size)
    var height = 0
    for (column in 0 until columnCount) {
        val end = if (column == columnCount - 1) size else starts[column + 1]
        var top = 0
        for (index in starts[column] until end) {
            columns[index] = column
            tops[index] = top
            top += this[index] + sectionGap
        }
        height = max(height, top - sectionGap)
    }
    return SongArrangement(columns = columns, tops = tops, height = height)
}

/**
 * Packs the sections (given by their heights, in order) into rows of [columnCount] cells that are read across, then
 * downwards. A row is as tall as its tallest section, and a cell may hold several consecutive sections (stacked
 * [sectionGap] apart) as long as it stays no taller than that, and no taller than [maxStackHeight] (the height of the
 * screen): a section that is taller than the screen has to be scrolled anyway, but the sections stacked next to it
 * must not grow into a column that sends the reader back up once they reach its bottom. Rows are [rowGap] apart.
 *
 * Where a row ends decides how well the rest of the song can be packed, so the row boundaries are chosen by a
 * dynamic program (from the last section backwards) that minimizes the total height. A candidate row is feasible if
 * a first-fit stacking of its sections needs no more than [columnCount] cells; first-fit is optimal for keeping
 * consecutive sections in as few cells as possible. Adding a taller section to a row raises its cap, so a row that
 * does not fit can become feasible again with more sections in it, which is why every length is tried. Leaving a
 * cell empty can make the song shorter (a row of two short sections next to a hole is not as tall as one with a
 * chorus in the third cell), but a hole in the middle of a song looks like a mistake while slack at its end looks
 * natural, so rows with empty cells are avoided before the height is minimized, except for the last row.
 */
private fun List<Int>.flowIntoRows(columnCount: Int, sectionGap: Int, rowGap: Int, maxStackHeight: Int): SongArrangement {
    if (isEmpty()) return SongArrangement(columns = IntArray(0), tops = IntArray(0), height = 0)
    // Returns the number of cells that first-fit stacking needs for the sections in [start, end) when a stack may be
    // as tall as the tallest section of the row, but never taller than the screen.
    fun cellCount(start: Int, end: Int, tallest: Int): Int {
        val cap = minOf(tallest, maxStackHeight)
        var cells = 1
        var cellHeight = this[start]
        for (index in start + 1 until end) {
            if (cellHeight + sectionGap + this[index] <= cap) {
                cellHeight += sectionGap + this[index]
            } else {
                cells++
                cellHeight = this[index]
            }
        }
        return cells
    }
    // costs[i] is the smallest cost (the number of rows with an empty cell, then the total height) of the sections
    // from i onwards, rowEnds[i] where their first row ends.
    val costs = LongArray(size + 1)
    val rowEnds = IntArray(size + 1)
    for (start in size - 1 downTo 0) {
        var best = Long.MAX_VALUE
        var tallest = 0
        for (end in start + 1..size) {
            tallest = max(tallest, this[end - 1])
            val cells = cellCount(start, end, tallest)
            if (cells > columnCount) continue
            val holePenalty = if (end < size && cells < columnCount) HOLE_PENALTY else 0L
            val cost = holePenalty + tallest + if (end < size) rowGap + costs[end] else 0
            // Ties go to the longer row, so that the slack ends up at the bottom of the song rather than in its middle.
            if (cost <= best) {
                best = cost
                rowEnds[start] = end
            }
        }
        costs[start] = best
    }
    val columns = IntArray(size)
    val tops = IntArray(size)
    val dividerTops = mutableListOf<Int>()
    var start = 0
    var rowTop = 0
    while (start < size) {
        val end = rowEnds[start]
        val tallest = subList(start, end).max()
        val cap = minOf(tallest, maxStackHeight)
        var column = 0
        var cellHeight = 0
        for (index in start until end) {
            if (index > start && cellHeight + sectionGap + this[index] > cap) {
                column++
                cellHeight = 0
            }
            columns[index] = column
            tops[index] = rowTop + cellHeight + if (cellHeight > 0) sectionGap else 0
            cellHeight = tops[index] - rowTop + this[index]
        }
        rowTop += tallest + rowGap
        if (end < size) dividerTops += rowTop - rowGap / 2
        start = end
    }
    return SongArrangement(columns = columns, tops = tops, height = rowTop - rowGap, dividerTops = dividerTops)
}

/** The fallback names of the environments that have one. Everything else is named by the file itself. */
private data class DefaultSectionLabels(
    val chorus: String,
    val bridge: String,
    val tab: String,
    val grid: String,
)

/** One unit the column layout places. Sections are never split, so this is also the granularity of the balancing. */
private sealed interface RenderSection {

    /** A titled block of lines: an environment, an implicit paragraph, or a repeated chorus. */
    data class Lines(
        val header: String?,
        val lines: List<ChordProLine>,
        /** Choruses (and their recalls) are drawn on a raised card so that they stand out. */
        val isOnCard: Boolean,
    ) : RenderSection

    data class Comment(
        val text: String,
        val style: CommentStyle,
    ) : RenderSection
}

/**
 * Flattens the parsed song into the sections the layout places.
 *
 * A `{chorus}` recall repeats the most recent chorus, so the choruses are remembered as they go by. In lyrics-only
 * mode the chords go away with the sections that consist of nothing else: tabs and grids say nothing without them,
 * and a line that was only chords would leave a blank behind.
 */
private fun ChordProSong.toRenderSections(
    shouldShowChords: Boolean,
    defaultLabels: DefaultSectionLabels,
): List<RenderSection> {
    val sections = mutableListOf<RenderSection>()
    var lastChorus: ChordProBlock.Section? = null
    blocks.forEach { block ->
        when (block) {
            is ChordProBlock.Break -> Unit // The column layout makes its own breaks.

            is ChordProBlock.Comment -> sections += RenderSection.Comment(text = block.text, style = block.style)

            is ChordProBlock.ChorusRecall -> {
                val chorus = lastChorus
                sections += RenderSection.Lines(
                    header = block.label ?: chorus?.label ?: defaultLabels.chorus,
                    lines = chorus?.lines?.prepareForDisplay(shouldShowChords).orEmpty(),
                    isOnCard = true,
                )
            }

            is ChordProBlock.Section -> {
                if (block.type == SectionType.Chorus) lastChorus = block
                // A section that is nothing but tablature or a grid goes away entirely in lyrics-only mode, its
                // heading with it: neither says anything without the chords, and a heading over nothing is worse
                // than no heading at all.
                if (!shouldShowChords && block.lines.all { it.needsChords() || it.isBlank() }) return@forEach
                val lines = block.lines.prepareForDisplay(shouldShowChords)
                val header = block.header(defaultLabels)
                // A section that ended up with nothing to show is dropped, unless its header still says something.
                if (lines.isNotEmpty() || header != null) {
                    sections += RenderSection.Lines(
                        header = header,
                        lines = lines,
                        isOnCard = block.type == SectionType.Chorus,
                    )
                }
            }
        }
    }
    return sections
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
 */
@Composable
private fun SongLineWithChords(
    line: ChordProLine.Lyrics,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
    annotationStyle: TextStyle,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val chordLayouts = remember(line, chordStyle, annotationStyle, textMeasurer) {
        line.chords.map { textMeasurer.measure(AnnotatedString(it.name), if (it.isAnnotation) annotationStyle else chordStyle) }
    }
    val paddedLine = remember(line, lyricsStyle, chordLayouts, textMeasurer, density) {
        line.padLyricsToFitChords(
            chordWidths = chordLayouts.map { it.size.width.toFloat() },
            gap = with(density) { CHORD_GAP.toPx() },
            measureWidth = { textMeasurer.measure(AnnotatedString(it), lyricsStyle).size.width.toFloat() },
        )
    }
    val lyricsLineHeight = remember(lyricsStyle, textMeasurer) {
        textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE), lyricsStyle).size.height
    }
    val chordLineHeight = chordLayouts.maxOf { it.size.height }
    // Lines without any lyrics (e.g. an intro) only need to be as tall as the chords themselves.
    val lineHeight = with(density) { (if (line.text.isBlank()) chordLineHeight else chordLineHeight + lyricsLineHeight).toSp() }
    var lyricsLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val layout = lyricsLayout ?: return@drawBehind
                val textLength = layout.layoutInput.text.length
                val gap = CHORD_GAP.toPx()
                var previousLineIndex = -1
                var previousChordEnd = 0f
                paddedLine.chords.forEachIndexed { index, chord ->
                    val chordLayout = chordLayouts[index]
                    val offset = chord.position.coerceIn(0, textLength)
                    val lineIndex = layout.getLineForOffset(offset)
                    if (lineIndex != previousLineIndex) {
                        previousLineIndex = lineIndex
                        previousChordEnd = 0f
                    }
                    val maxX = max(0f, size.width - chordLayout.size.width)
                    val x = max(layout.getHorizontalPosition(offset, usePrimaryDirection = true), previousChordEnd).coerceIn(0f, maxX)
                    drawText(
                        textLayoutResult = chordLayout,
                        topLeft = Offset(x, layout.getLineTop(lineIndex)),
                    )
                    previousChordEnd = x + chordLayout.size.width + gap
                }
            },
        text = paddedLine.text,
        style = lyricsStyle.copy(
            lineHeight = lineHeight,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Bottom,
                trim = LineHeightStyle.Trim.None,
            ),
        ),
        color = MaterialTheme.colorScheme.onSurface,
        onTextLayout = { lyricsLayout = it },
    )
}

/**
 * Returns a copy of the line where every piece of lyrics that sits under a chord is at least as wide as the chord
 * (plus [gap]), by appending non-breaking spaces to it. Chord positions are updated to point into the padded lyrics.
 */
private fun ChordProLine.Lyrics.padLyricsToFitChords(
    chordWidths: List<Float>,
    gap: Float,
    measureWidth: (String) -> Float,
): ChordProLine.Lyrics {
    val paddingWidth = measureWidth(PADDING.toString())
    val paddedLyrics = StringBuilder(text.substring(0, chords.first().position))
    val paddedChords = chords.mapIndexed { index, chord ->
        val fragment = text.substring(chord.position, chords.getOrNull(index + 1)?.position ?: text.length)
        val paddedChord = chord.copy(position = paddedLyrics.length)
        paddedLyrics.append(fragment)
        val missingWidth = chordWidths[index] + gap - measureWidth(fragment)
        if (missingWidth > 0 && paddingWidth > 0) {
            repeat(ceil(missingWidth / paddingWidth).toInt()) { paddedLyrics.append(PADDING) }
        }
        paddedChord
    }
    return ChordProLine.Lyrics(text = paddedLyrics.toString(), chords = paddedChords)
}

private val CHORD_GAP = 4.dp
private val GRID_TOKEN_GAP = 6.dp
private val CARD_PADDING = 12.dp
private val CARD_ELEVATION = 1.dp
private val HEADER_GAP = 8.dp
private val HEADER_ELEVATION = 2.dp
private val HEADER_HORIZONTAL_PADDING = 12.dp
private val HEADER_VERTICAL_PADDING = 6.dp
private val MIN_COLUMN_WIDTH = 384.dp
private val MAX_COLUMN_WIDTH = 560.dp
private val COLUMN_GAP = 32.dp
private val SECTION_GAP = 20.dp
private val ROW_GAP = 40.dp
private const val HOLE_PENALTY = 1L shl 40 // Larger than any height, so that a hole always costs more than height does.
private const val LINE_HEIGHT_SAMPLE = "X"
private const val PADDING = '\u00A0' // Non-breaking space, so that the padding never gets trimmed or wrapped.
private const val BEAT_SYMBOL = "\u00B7"
private const val CHIP_SEPARATOR = "\u00B7"
