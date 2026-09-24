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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.ui.unit.isSpecified
import com.pandulapeter.campfire.chordpro.ChordProHighlighter
import com.pandulapeter.campfire.chordpro.ChordProTabWrapper
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.song_details_album
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_composer
import com.pandulapeter.campfire.presentation.resources.song_details_duration
import com.pandulapeter.campfire.presentation.resources.song_details_grid_collapse
import com.pandulapeter.campfire.presentation.resources.song_details_grid_expand
import com.pandulapeter.campfire.presentation.resources.song_details_language
import com.pandulapeter.campfire.presentation.resources.song_details_lyricist
import com.pandulapeter.campfire.presentation.resources.song_details_section_bridge
import com.pandulapeter.campfire.presentation.resources.song_details_section_chorus
import com.pandulapeter.campfire.presentation.resources.song_details_section_grid
import com.pandulapeter.campfire.presentation.resources.song_details_section_tab
import com.pandulapeter.campfire.presentation.resources.song_details_tab_collapse
import com.pandulapeter.campfire.presentation.resources.song_details_tab_expand
import com.pandulapeter.campfire.presentation.resources.song_details_tag_add
import com.pandulapeter.campfire.presentation.resources.song_details_tag_remove
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_time
import com.pandulapeter.campfire.presentation.resources.song_details_year
import com.pandulapeter.campfire.presentation.ui.components.ExpandChevron
import com.pandulapeter.campfire.presentation.ui.components.TagFlowRow
import com.pandulapeter.campfire.presentation.ui.components.TagPill
import com.pandulapeter.campfire.presentation.ui.components.languageLabel
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders the raw song data with the chords displayed above the lyrics, aligned to the syllable they belong to.
 * When [shouldShowChords] is false, only the lyrics are rendered: the chords are dropped and lines that consisted of
 * nothing but chords (e.g. an intro) are skipped entirely.
 *
 * The song is split into sections (verse, chorus, ...) which are flowed into columns by [SongSectionsLayout], either
 * top to bottom or, when [isHorizontalFlow] is set, in rows across the columns. Choruses are drawn on a raised card
 * of their own so that they stand out from the surrounding sections. A section is never split between columns, and
 * sections animate to their new place when the column count changes (e.g. when a window is resized) - except one that
 * is too tall for `animateBounds` to measure, which simply appears there.
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
    onEditLanguages: (() -> Unit)? = null,
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
    // Kept across a new song text rather than keyed on it, since a transposition or a tag put on rewrites the song and
    // must not unfold what the reader has folded away.
    val foldedRuns = remember { FoldedRuns() }
    // Everything the height of a section depends on apart from the width it is measured at, the folded runs included.
    val sectionMeasurements = remember(sections, fontScale, density, foldedRuns.collapsed) { SectionMeasurements(sectionCount = sections.size) }
    val coroutineScope = rememberCoroutineScope()
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
            onEditLanguages = onEditLanguages,
        )
        LookaheadScope {
            SongSectionsLayout(
                modifier = Modifier.semantics { isTraversalGroup = true },
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
                sectionMeasurements = sectionMeasurements,
            ) {
                sections.forEachIndexed { index, section ->
                    val bounds = sectionBounds[index]
                    // A section the layout found too tall is measured in full only once this is off it: see maxAnimatedSectionHeight.
                    // Each section is read as a whole and in the order the song declares, whatever column it was put in:
                    // the reading order is otherwise worked out from the geometry, line by line across the page, which
                    // with two columns reads the first line of each, then the second line of each.
                    val sectionModifier = if (extraWidth > 0.dp || bounds.isTooTallToAnimate) {
                        Modifier
                    } else {
                        Modifier.animateBounds(this@LookaheadScope).layoutId(AnimatedSectionLayoutId)
                    }.semantics {
                        isTraversalGroup = true
                        traversalIndex = index.toFloat()
                    }
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
                                    sectionIndex = index,
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
                                modifier = sectionModifier.padding(horizontal = CARD_PADDING),
                                section = section,
                                sectionIndex = index,
                                isOnCard = false,
                                headerStyle = headerStyle,
                                lyricsStyle = lyricsStyle,
                                chordStyle = chordStyle,
                                textMeasurements = textMeasurements,
                                foldedRuns = foldedRuns,
                                defaultLabels = defaultLabels,
                                fontScale = fontScale,
                                // Scrolls the section back to the top of the screen. The sections start under the
                                // song's header, so its height is part of where they are; the top padding the caller
                                // puts above everything is left showing, as it is at the start of the song.
                                onHeaderClick = { coroutineScope.launch { scrollState.animateScrollTo(headerHeight + bounds.top) } },
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
 * @param onEditLanguages Null wherever [onAddTag] is, and for the same reason. The chip is then shown only by a song
 *   that declares a language, since there is nothing to say about one that does not and nothing to tap to change it.
 */
@Composable
private fun SongMetadataHeader(
    modifier: Modifier = Modifier,
    song: ChordProSong,
    fontScale: Float,
    onAddTag: (() -> Unit)?,
    onRemoveTag: ((String) -> Unit)?,
    onEditLanguages: (() -> Unit)?,
) = Column(modifier = modifier.padding(bottom = SECTION_GAP)) {
    val metadata = song.metadata
    if (metadata.tags.isNotEmpty() || metadata.languages.isNotEmpty() || onAddTag != null) {
        TagFlowRow(
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            // The language comes before the tags because it is the one label of a song that is not the user's own
            // word for it: it is a single chip however many languages it names, and it is where they are edited.
            if (metadata.languages.isNotEmpty() || onEditLanguages != null) {
                TagPill(
                    text = metadata.languages.map { languageLabel(it) }.joinToString(separator = ", ")
                        .ifEmpty { stringResource(Res.string.song_details_language) },
                    onClick = onEditLanguages,
                    leadingIcon = painterResource(Res.drawable.ic_language),
                )
            }
            metadata.tags.forEach { tag ->
                TagPill(
                    text = tag,
                    trailingIcon = if (onRemoveTag == null) null else painterResource(Res.drawable.ic_clear),
                    trailingIconContentDescription = onRemoveTag?.let { textResource(Res.string.song_details_tag_remove, tag) },
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
            metadata.tempo?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_tempo, it) },
            metadata.time?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_time, it) },
        ),
        style = MaterialTheme.typography.labelLarge.scaled(fontScale),
        color = MaterialTheme.colorScheme.primary,
    )
    MetadataLine(
        values = listOfNotNull(
            metadata.composer?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_composer, it) },
            metadata.lyricist?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_lyricist, it) },
            metadata.album?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_album, it) },
            metadata.year?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_year, it) },
            metadata.duration?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_duration, it) },
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

/**
 * A `{comment}` line: a layout section of its own where it stands between two sections, so that it can sit between two
 * columns freely, and a part of the section it cut in two otherwise (see [toRenderSections]).
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
    val chordColor = MaterialTheme.colorScheme.primary
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
 *
 * Every run of tablature and every run of a chord grid can be folded away ([foldedRuns]), since a solo written out
 * fret by fret or bar by bar is as tall as several verses and is of no use to somebody who only sings the song. A
 * section that is nothing but one of the two is folded as a whole, and from its header wherever it has one: the header
 * then does that instead of scrolling back to the start of the section, a folded section being short enough to have
 * nothing to scroll back to. Anywhere else a run is folded from a toggle of its own, named the way a section of nothing
 * else would be ([defaultLabels]), which is what stays behind of it once it is folded. Lyrics only mode drops both
 * altogether (see [prepareForDisplay]), so there is nothing to fold there.
 */
@Composable
private fun SongSectionContent(
    modifier: Modifier = Modifier,
    section: RenderSection.Lines,
    sectionIndex: Int,
    isOnCard: Boolean,
    headerStyle: TextStyle,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
    textMeasurements: SongTextMeasurements,
    foldedRuns: FoldedRuns,
    defaultLabels: DefaultSectionLabels,
    fontScale: Float,
    onHeaderClick: () -> Unit = {},
) = Column(
    modifier = modifier
) {
    val wholeSectionKind = section.lines.wholeFoldableKind()
    val wholeSectionRun = wholeSectionKind?.let { FoldableRunId(section = sectionIndex, run = 0) }
    val chevronSize = FOLD_CHEVRON_SIZE * fontScale
    // Tablature and grids are both columns of characters that have to line up with the ones above and below them.
    val monospaceFontFamily = LocalMonospaceFontFamily.current
    val monospaceLyricsStyle = lyricsStyle.copy(fontFamily = monospaceFontFamily)
    val monospaceChordStyle = chordStyle.copy(fontFamily = monospaceFontFamily)
    fun foldToggle(id: FoldableRunId) = FoldToggle(isExpanded = !foldedRuns.isCollapsed(id), onToggled = { foldedRuns.toggle(id) })
    section.header?.let { header ->
        val headerFoldToggle = wholeSectionRun?.let(::foldToggle)
        val headerContent = @Composable { textModifier: Modifier ->
            Row(
                modifier = textModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = header,
                    style = headerStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (headerFoldToggle != null && wholeSectionKind != null) {
                    FoldChevron(
                        modifier = Modifier.padding(start = FOLD_CHEVRON_GAP).size(chevronSize),
                        kind = wholeSectionKind,
                        isExpanded = headerFoldToggle.isExpanded,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (isOnCard) {
            headerContent(
                Modifier
                    .fillMaxWidth()
                    .then(if (headerFoldToggle == null) Modifier else Modifier.foldToggleClickable(headerFoldToggle))
                    .padding(bottom = HEADER_GAP)
            )
        } else {
            // The pill is laid out at its own size: the touch target enforcement would grow it to 48dp and push the
            // lines of the section down, just as it would in the lists (see [SectionHeader]).
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Surface(
                    onClick = headerFoldToggle?.onToggled ?: onHeaderClick,
                    modifier = Modifier
                        .offset(x = -HEADER_HORIZONTAL_PADDING)
                        .padding(bottom = HEADER_GAP),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = HEADER_ELEVATION,
                ) {
                    headerContent(Modifier.padding(horizontal = HEADER_HORIZONTAL_PADDING, vertical = HEADER_VERTICAL_PADDING))
                }
            }
        }
    }
    if (wholeSectionKind != null && wholeSectionRun != null) {
        if (section.header == null) {
            FoldToggleRow(
                kind = wholeSectionKind,
                label = section.lines.firstNotNullOfOrNull { it.environmentLabel } ?: defaultLabels.labelOf(wholeSectionKind),
                toggle = foldToggle(wholeSectionRun),
                style = headerStyle,
                chevronSize = chevronSize,
            )
        }
        if (foldedRuns.isCollapsed(wholeSectionRun)) return@Column
    }
    // Tablature and grids are runs of lines inside a section rather than sections of their own, so the lines are
    // grouped: each run is folded as one, a run of tablature is also measured as one block (its columns only line up
    // while they are measured together), and everything else is laid out line by line around them. A comment that cut
    // the section stands where the file has it, between the lines around it.
    var foldableRunIndex = 0
    section.parts.forEach { part ->
        when (part) {
            is RenderSection.Comment -> SongComment(
                modifier = Modifier.padding(vertical = INLINE_COMMENT_GAP),
                comment = part,
                fontScale = fontScale,
            )

            is SectionPart.Lines -> part.lines.groupIntoRuns().forEach { group ->
                val kind = group.first().foldableKind()
                if (kind == null) {
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
                val run = wholeSectionRun ?: FoldableRunId(section = sectionIndex, run = foldableRunIndex++)
                if (wholeSectionRun == null) {
                    FoldToggleRow(
                        kind = kind,
                        label = group.first().environmentLabel ?: defaultLabels.labelOf(kind),
                        toggle = foldToggle(run),
                        style = headerStyle,
                        chevronSize = chevronSize,
                    )
                    if (foldedRuns.isCollapsed(run)) return@forEach
                }
                val fadeModifier = Modifier.fadingIn(isFadingIn = foldedRuns.hasBeenToggled(run))
                when (kind) {
                    FoldableKind.TAB -> SongTabRun(
                        modifier = fadeModifier.fillMaxWidth(),
                        lines = group.map { (it as? ChordProLine.Tab)?.text.orEmpty() },
                        style = monospaceLyricsStyle,
                        textMeasurer = textMeasurements.textMeasurer,
                    )

                    FoldableKind.GRID -> Column(modifier = fadeModifier.fillMaxWidth()) {
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

/**
 * One run of `{start_of_tab}` lines. A run with a staff in it is tablature, wrapped into rows that fit ([SongTabBlock]);
 * one with no staff in it is preformatted text, chord names over lyrics most often. Its columns only line up while no
 * line is cut, and there is no column a cut would be harmless on the way there is on a staff, so it scrolls sideways
 * instead.
 */
@Composable
private fun SongTabRun(
    modifier: Modifier = Modifier,
    lines: List<String>,
    style: TextStyle,
    textMeasurer: TextMeasurer,
) = if (ChordProTabWrapper.isTablature(lines)) {
    SongTabBlock(
        modifier = modifier,
        lines = lines,
        style = style,
        textMeasurer = textMeasurer,
    )
} else {
    Column(modifier = modifier.horizontalScroll(rememberScrollState())) {
        lines.forEach { line ->
            Text(
                text = line,
                style = style,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The two ways of writing lines down that can be folded away: neither says anything to somebody who only sings. */
private enum class FoldableKind {
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

/** One run of tablature or of a grid on a page: the section it is in and its place among that section's runs. */
private data class FoldableRunId(
    val section: Int,
    val run: Int,
)

/**
 * Which runs of tablature and grids on one page the reader has folded away. A run nobody has touched is unfolded, and
 * it is told apart from one that was folded and unfolded again, so that only the latter fades in: a page opening onto
 * a tab that fades in would be an animation nobody asked for.
 */
private class FoldedRuns {

    private var states by mutableStateOf(emptyMap<FoldableRunId, Boolean>())

    /** The folded runs, which is what the heights of the sections depend on. */
    val collapsed: Set<FoldableRunId> get() = states.filterValues { it }.keys

    fun isCollapsed(id: FoldableRunId) = states[id] == true

    fun hasBeenToggled(id: FoldableRunId) = id in states

    fun toggle(id: FoldableRunId) {
        states = states + (id to !isCollapsed(id))
    }
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

/** The app's fold chevron, named for what pressing it does to a tab or a grid. */
@Composable
private fun FoldChevron(
    modifier: Modifier = Modifier,
    kind: FoldableKind,
    isExpanded: Boolean,
    tint: Color,
) = ExpandChevron(
    modifier = modifier,
    isExpanded = isExpanded,
    contentDescription = stringResource(
        when (kind) {
            FoldableKind.TAB -> if (isExpanded) Res.string.song_details_tab_collapse else Res.string.song_details_tab_expand
            FoldableKind.GRID -> if (isExpanded) Res.string.song_details_grid_collapse else Res.string.song_details_grid_expand
        }
    ),
    tint = tint,
)

/**
 * Fades a run of tablature or a grid in as it is unfolded, while the section around it grows to make room on its own
 * spring (`animateBounds`). Only the opacity is animated, never the size: the column layout decides where every
 * section goes from their intrinsic heights, and a run whose height was still on its way would be measured halfway
 * there.
 */
@Composable
private fun Modifier.fadingIn(isFadingIn: Boolean): Modifier {
    val alpha = remember { Animatable(if (isFadingIn) 0f else 1f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(alpha) { alpha.animateTo(1f, spec) }
    return graphicsLayer { this.alpha = alpha.value }
}

/**
 * One run of tablature, cut into as many rows as it takes to fit the width, the way a tab book breaks a staff into
 * systems (see [ChordProTabWrapper]). The rows are a blank line apart, so that the last string of one is never read
 * as the first string of the next.
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

    /** The height of a blank line in the tab's own font: the gap between two rows. */
    val rowGap = textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE), style).size.height

    // A monospace font, so the width of one character is the width of many divided by their count, measured with
    // enough of them for the rounding of the total not to matter.
    private val characterWidth = textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE.repeat(CHARACTER_WIDTH_SAMPLE_LENGTH)), style).size.width /
            CHARACTER_WIDTH_SAMPLE_LENGTH.toFloat()
    private val rowsByWidth = mutableMapOf<Int, List<List<TextLayoutResult>>>()
    private val recentWidths = ArrayDeque<Int>()

    fun at(width: Int): List<List<TextLayoutResult>> {
        recentWidths.remove(width)
        recentWidths.addLast(width)
        if (recentWidths.size > MAX_TAB_WIDTHS) rowsByWidth.remove(recentWidths.removeFirst())
        return rowsByWidth.getOrPut(width) {
            val maxColumns = if (width == Constraints.Infinity || characterWidth <= 0f) Int.MAX_VALUE else (width / characterWidth).toInt()
            ChordProTabWrapper.wrap(lines, maxColumns).map { row ->
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
 * breaks between bars rather than being cut off, the way a staff of tablature is broken into systems, so every chord
 * of it stays on the page at any text size.
 */
@Composable
private fun SongGridLine(
    line: ChordProLine.Grid,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
) = FlowRow(modifier = Modifier.fillMaxWidth()) {
    line.tokens.bars().forEach { bar ->
        Row {
            bar.forEach { token ->
                val (text, style, color) = when (token) {
                    is GridToken.Bar -> Triple(token.text, lyricsStyle, MaterialTheme.colorScheme.outline)
                    is GridToken.Chord -> Triple(token.name, chordStyle, MaterialTheme.colorScheme.primary)
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
 * the next column, since whatever has been scrolled past has been played. Every row gets as many columns as its own
 * sections fill, so a row of two sections is split in two wider columns rather than leaving a hole where a third one
 * would go, and consecutive short sections are stacked into the same column of a row as long as the stack is no
 * taller than the tallest section of the row, so that the rows stay compact. The rows are told apart by a divider
 * drawn in the gap between them. (A single column reads the same way in both modes, so it is always laid out as a
 * plain column, without dividers.)
 *
 * The columns are made as wide (and therefore as few) as possible while the whole song still fits into
 * [availableHeight], so that the lyrics wrap as little as they can and the vertical space is actually used: a song
 * that needs three columns is not squeezed into five just because the window is wide enough for five. Songs that do
 * not fit no matter what get as many columns as the width allows (in the horizontal flow: at most as many in each
 * row). Column widths stay between [minColumnWidth] and [maxColumnWidth] and the whole block is centered, so a short
 * song does not end up as one screen-wide column of short lines. Within it a row narrower than the widest one is
 * centered too, except for a row of a single column, which is aligned to the start of the others.
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
 * The layout is measured on every frame of a navigation transition and of a window being resized, so what the search
 * finds is kept in [sectionMeasurements]: the intrinsic height of every section at every width it was asked about,
 * and the grid decided for the last settled width, which is the same on every frame of a transition.
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
    sectionMeasurements: SectionMeasurements,
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

    val gridKey = SectionGridKey(
        settledWidth = settledWidth,
        availableHeight = availableHeightPx,
        maxColumnCount = maxColumnCount,
        isHorizontalFlow = isHorizontalFlow,
    )
    val grid = sectionMeasurements.grid(gridKey) {
        fun heightAt(index: Int, columnCount: Int) = sectionMeasurements.height(
            index = index,
            width = columnWidthFor(settledWidth, columnCount),
            measure = measurables[index]::maxIntrinsicHeight,
        )

        fun gridFor(columnCount: Int) = when {
            // A single column is every section stacked in its order, however tall each of them is, so it is the one
            // grid that is known without an intrinsic measurement. Asking for the heights anyway lays every line of
            // the song out once for a number nobody reads and then once more to be drawn, and on a window too narrow
            // for a second column - which is every phone - this is the only grid there is.
            columnCount == 1 -> singleColumnGrid(measurables.size)
            isHorizontalFlow -> flowIntoRows(
                sectionCount = measurables.size,
                maxColumnCount = columnCount,
                heightAt = ::heightAt,
                sectionGap = sectionGapPx,
                rowGap = rowGapPx,
                maxStackHeight = if (availableHeightPx > 0) availableHeightPx else Int.MAX_VALUE,
            )
            else -> List(measurables.size) { heightAt(it, columnCount) }.balanceIntoColumns(columnCount, sectionGapPx)
        }

        fun SectionGrid.height() = arrange(
            heights = IntArray(measurables.size) { heightAt(it, columnCounts[rows[it]]) },
            sectionGap = sectionGapPx,
            rowGap = rowGapPx,
        ).height

        if (availableHeightPx > 0) {
            var candidate = 1
            var candidateGrid = gridFor(candidate)
            while (candidate < maxColumnCount && candidateGrid.height() > availableHeightPx) {
                // Even a perfectly even split needs this many columns, so there is no point in trying the ones in
                // between. The rows of the horizontal flow may be narrower than the candidate, down to a single
                // column, so the sections are only as tall there as they are in the widest column.
                val boundColumnCount = if (isHorizontalFlow) 1 else candidate
                val totalHeight = measurables.indices.sumOf { heightAt(it, boundColumnCount) } + sectionGapPx * (measurables.size - 1).coerceAtLeast(0)
                candidate = maxOf(candidate + 1, ceil(totalHeight.toDouble() / availableHeightPx).toInt()).coerceAtMost(maxColumnCount)
                candidateGrid = gridFor(candidate)
            }
            candidateGrid
        } else {
            gridFor(maxColumnCount)
        }
    }

    val columnWidths = IntArray(grid.columnCounts.size) { columnWidthFor(width, grid.columnCounts[it]) }
    val placeables = measurables.mapIndexed { index, measurable ->
        val columnWidth = columnWidths[grid.rows[index]]
        val heightLimit = maxAnimatedSectionHeight(columnWidth)
        // Only a section that is being animated is held to the limit. One that reaches it is cut short for the one
        // frame it takes the composition to take the animation off it, which happens far below the screen.
        val maxHeight = if (measurable.layoutId === AnimatedSectionLayoutId) minOf(constraints.maxHeight, heightLimit) else constraints.maxHeight
        val placeable = measurable.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth, maxHeight = maxHeight))
        // The approach pass of an animated section reports the size the animation is at, which says nothing about
        // the size it is going to: only the lookahead pass measures that.
        if (isLookingAhead) sectionBounds[index].isTooTallToAnimate = placeable.height >= heightLimit
        placeable
    }
    val arrangement = grid.arrange(heights = IntArray(placeables.size) { placeables[it].height }, sectionGap = sectionGapPx, rowGap = rowGapPx)
    val centeredRowStarts = IntArray(columnWidths.size) { row ->
        val columnCount = grid.columnCounts[row]
        ((width - columnWidths[row] * columnCount - columnGapPx * (columnCount - 1)) / 2).coerceAtLeast(0)
    }
    // The rows may be of different widths, so the content (and the dividers with it) is as wide as the widest of them.
    val contentStart = centeredRowStarts.minOrNull() ?: 0
    val contentWidth = (width - contentStart * 2).coerceAtLeast(0)
    // A row of a single section starts where the columns of the other rows do, so that its text lines up with the text
    // above and below it instead of floating in the middle of the screen. A song of nothing but single columns has no
    // other rows to line up with, and is centered as a whole.
    val rowStarts = IntArray(columnWidths.size) { row -> if (grid.columnCounts[row] == 1) contentStart else centeredRowStarts[row] }
    val dividers = arrangement.dividerTops.take(dividerMeasurables.size).mapIndexed { index, top ->
        val placeable = dividerMeasurables[index].measure(Constraints(minWidth = contentWidth, maxWidth = contentWidth))
        placeable to IntOffset(x = contentStart, y = top - placeable.height / 2)
    }
    layout(width, arrangement.height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
        placeables.forEachIndexed { index, placeable ->
            val row = grid.rows[index]
            // Published before the section is placed, so that its header can already scroll to the new position.
            sectionBounds[index].top = arrangement.tops[index]
            placeable.place(x = rowStarts[row] + grid.columns[index] * (columnWidths[row] + columnGapPx), y = arrangement.tops[index])
        }
        dividers.forEach { (placeable, position) -> placeable.place(position) }
    }
}

/**
 * What [SongSectionsLayout] has already worked out about one set of sections, remembered for as long as nothing the
 * sections' heights depend on has changed. Neither of the two is state: they are read and written by the measurement
 * alone, and a change to them never has anything to redraw. The heights are kept per width and only for the widths of
 * the last few searches.
 */
private class SectionMeasurements(private val sectionCount: Int) {

    private val heightsByWidth = HashMap<Int, IntArray>()
    private var lastGridKey: SectionGridKey? = null
    private var lastGrid = SectionGrid(rows = IntArray(0), columns = IntArray(0), columnCounts = IntArray(0))

    /** The intrinsic height of the section at [index] when it is [width] wide. */
    fun height(index: Int, width: Int, measure: (Int) -> Int): Int {
        val heights = heightsByWidth.getOrPut(width) { IntArray(sectionCount) { UNMEASURED_HEIGHT } }
        if (heights[index] == UNMEASURED_HEIGHT) heights[index] = measure(width)
        return heights[index]
    }

    /** The grid decided for [key], which is only searched for again once the key has changed. */
    fun grid(key: SectionGridKey, search: () -> SectionGrid): SectionGrid {
        if (key != lastGridKey) {
            // A window being resized searches at a new width on every frame and none of those comes back, so
            // the widths are only kept until there are more of them than a few searches ask about. They are let
            // go of between two searches and never during one, which asks about the same few over and over.
            if (heightsByWidth.size > MAX_SECTION_WIDTHS) heightsByWidth.clear()
            lastGrid = search()
            lastGridKey = key
        }
        return lastGrid
    }
}

/** Everything the [SectionGrid] depends on, apart from the heights of the sections. */
private data class SectionGridKey(
    val settledWidth: Int,
    val availableHeight: Int,
    val maxColumnCount: Int,
    val isHorizontalFlow: Boolean,
)

/**
 * Where a section ended up inside [SongSectionsLayout]. The layout is the only one that knows this, and the sections
 * need it to scroll back to their own start when their header is clicked, so it is handed back to them through this.
 * It is also the only one that knows how tall a section is, which decides whether the section can be animated at
 * all (see [maxAnimatedSectionHeight]).
 */
private class SectionBounds {

    var top by mutableIntStateOf(0)
    var isTooTallToAnimate by mutableStateOf(false)
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
 * Which cell every section goes into: the row it is in, its column within that row, and the number of columns of
 * every row (which decides how wide the columns of that row are). The sections of a cell are stacked in their order.
 * The columns flowing top to bottom are a single row.
 *
 * This is what is decided for the settled width, while the positions are only worked out by [arrange] from the
 * heights the sections have at the width the layout is actually given.
 */
private class SectionGrid(
    val rows: IntArray,
    val columns: IntArray,
    val columnCounts: IntArray,
)

/** The y position of every section, the total height of the layout and the y positions (centers) of the row gaps. */
private class SongArrangement(
    val tops: IntArray,
    val height: Int,
    val dividerTops: List<Int>,
)

/**
 * Positions the sections of the grid, given their [heights] at the width of their own row: the sections of a cell
 * are stacked [sectionGap] apart, a row is as tall as its tallest cell, and the rows are [rowGap] apart.
 */
private fun SectionGrid.arrange(heights: IntArray, sectionGap: Int, rowGap: Int): SongArrangement {
    val tops = IntArray(heights.size)
    val dividerTops = mutableListOf<Int>()
    var rowTop = 0
    var rowHeight = 0
    for (index in heights.indices) {
        val isNewRow = index > 0 && rows[index] != rows[index - 1]
        if (isNewRow) {
            rowTop += rowHeight + rowGap
            dividerTops += rowTop - rowGap / 2
            rowHeight = 0
        }
        tops[index] = if (index == 0 || isNewRow || columns[index] != columns[index - 1]) rowTop else tops[index - 1] + heights[index - 1] + sectionGap
        rowHeight = max(rowHeight, tops[index] + heights[index] - rowTop)
    }
    return SongArrangement(tops = tops, height = rowTop + rowHeight, dividerTops = dividerTops)
}

/** The grid of a layout one column wide: every section in the one cell of the one row, in their order. */
private fun singleColumnGrid(sectionCount: Int) = SectionGrid(
    rows = IntArray(sectionCount),
    columns = IntArray(sectionCount),
    columnCounts = if (sectionCount == 0) IntArray(0) else intArrayOf(1),
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
private fun List<Int>.balanceIntoColumns(columnCount: Int, sectionGap: Int): SectionGrid {
    if (isEmpty()) return SectionGrid(rows = IntArray(0), columns = IntArray(0), columnCounts = IntArray(0))
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
    val columns = IntArray(size)
    var until = size
    for (column in columnCount - 1 downTo 0) {
        val from = splits[column][until]
        for (index in from until until) columns[index] = column
        until = from
    }
    return SectionGrid(rows = IntArray(size), columns = columns, columnCounts = intArrayOf(columnCount))
}

/**
 * Packs [sectionCount] sections into rows that are read across, then downwards, each row having between one and
 * [maxColumnCount] columns: [heightAt] tells how tall a section is in a row of a given number of columns, since fewer
 * columns are wider ones. A row is as tall as its tallest section, and a cell may hold several consecutive sections
 * (stacked [sectionGap] apart) as long as it stays no taller than that, and no taller than [maxStackHeight] (the
 * height of the screen): a section that is taller than the screen has to be scrolled anyway, but the sections stacked
 * next to it must not grow into a column that sends the reader back up once they reach its bottom. Rows are [rowGap]
 * apart.
 *
 * A row has exactly as many columns as its sections fill, so no row is left with a hole in it: a hole in the middle
 * of a song looks like a mistake, and even at its end it is width the sections could have used to wrap less. Where a
 * row ends and how many columns it has decide how well the rest of the song can be packed, so both are chosen by a
 * dynamic program (from the last section backwards) that minimizes the total height. A candidate row with a given
 * number of columns is feasible if a first-fit stacking of its sections at that width fills exactly that many cells;
 * first-fit is optimal for keeping consecutive sections in as few cells as possible. Adding a taller section to a row
 * raises its cap, so a row that does not fit can become feasible again with more sections in it, which is why every
 * length is tried. Every length that still can be feasible, that is: the stacking is carried from one length to the next
 * rather than redone, and a column count is given up for a row once its sections add up to more than that many cells
 * could ever hold. A single section always fits in a row of its own, so there is always a way to pack the song.
 */
private fun flowIntoRows(
    sectionCount: Int,
    maxColumnCount: Int,
    heightAt: (index: Int, columnCount: Int) -> Int,
    sectionGap: Int,
    rowGap: Int,
    maxStackHeight: Int,
): SectionGrid {
    if (sectionCount == 0) return SectionGrid(rows = IntArray(0), columns = IntArray(0), columnCounts = IntArray(0))
    // heights[k - 1][i] is the height of section i in a row of k columns, heightSums[k - 1][i] the total height of
    // the sections before i at that width, and tallestFrom[k - 1][i] the height of the tallest section from i onwards.
    val heights = Array(maxColumnCount) { column -> IntArray(sectionCount) { heightAt(it, column + 1) } }
    val heightSums = Array(maxColumnCount) { column ->
        LongArray(sectionCount + 1).also { sums -> for (index in 0 until sectionCount) sums[index + 1] = sums[index] + heights[column][index] }
    }
    val tallestFrom = Array(maxColumnCount) { column ->
        IntArray(sectionCount + 1).also { tallest -> for (index in sectionCount - 1 downTo 0) tallest[index] = max(tallest[index + 1], heights[column][index]) }
    }
    // Calls onCell with the cell of every section in [start, end) when the sections are stacked first-fit into cells
    // as tall as the tallest section of the row, but never taller than the screen, and returns the number of cells.
    fun stack(start: Int, end: Int, columnCount: Int, tallest: Int, onCell: (index: Int, cell: Int) -> Unit): Int {
        val sectionHeights = heights[columnCount - 1]
        val cap = minOf(tallest, maxStackHeight)
        var cell = 0
        var cellHeight = sectionHeights[start]
        onCell(start, cell)
        for (index in start + 1 until end) {
            if (cellHeight + sectionGap + sectionHeights[index] <= cap) {
                cellHeight += sectionGap + sectionHeights[index]
            } else {
                cell++
                cellHeight = sectionHeights[index]
            }
            onCell(index, cell)
        }
        return cell + 1
    }
    // costs[i] is the smallest total height of the sections from i onwards, rowEnds[i] where their first row ends and
    // rowColumnCounts[i] how many columns that row has.
    val costs = LongArray(sectionCount + 1)
    val rowEnds = IntArray(sectionCount + 1)
    val rowColumnCounts = IntArray(sectionCount + 1)
    // The first-fit stacking of the candidate row, per column count, carried from one end of the row to the next:
    // stacking every candidate from its start again is what makes a song of many short sections cubic, and the
    // search runs on every frame of a window being resized.
    val tallest = IntArray(maxColumnCount)
    val caps = IntArray(maxColumnCount)
    val cells = IntArray(maxColumnCount)
    val cellHeights = IntArray(maxColumnCount)
    val isExhausted = BooleanArray(maxColumnCount)
    for (start in sectionCount - 1 downTo 0) {
        var best = Long.MAX_VALUE
        tallest.fill(0)
        isExhausted.fill(false)
        var exhaustedCount = 0
        var end = start + 1
        while (end <= sectionCount && exhaustedCount < maxColumnCount) {
            for (columnCount in 1..maxColumnCount) {
                val column = columnCount - 1
                if (isExhausted[column]) continue
                val sectionHeights = heights[column]
                // No cell of a feasible row is taller than the row's tallest section, so the sections of a row of
                // this many columns add up to no more than that many times the tallest one still to come. The sum
                // only grows with the row, so past this point no longer row of this many columns has to be tried.
                if (heightSums[column][end] - heightSums[column][start] > columnCount.toLong() * tallestFrom[column][start]) {
                    isExhausted[column] = true
                    exhaustedCount++
                    continue
                }
                val added = end - 1
                tallest[column] = max(tallest[column], sectionHeights[added])
                val cap = minOf(tallest[column], maxStackHeight)
                // A taller section raises the cap, and what was stacked under the lower one may fit into fewer
                // cells under the new one, so the row is stacked again from its start. Otherwise the stacking so
                // far stands, and only the section that was added is placed.
                val from = if (added == start || cap != caps[column]) {
                    caps[column] = cap
                    cells[column] = 0
                    cellHeights[column] = sectionHeights[start]
                    start + 1
                } else {
                    added
                }
                for (index in from until end) {
                    if (cellHeights[column] + sectionGap + sectionHeights[index] <= cap) {
                        cellHeights[column] += sectionGap + sectionHeights[index]
                    } else {
                        cells[column]++
                        cellHeights[column] = sectionHeights[index]
                    }
                }
                if (cells[column] + 1 != columnCount) continue
                val cost = tallest[column] + if (end < sectionCount) rowGap + costs[end] else 0L
                // Ties go to the longer row, so that the slack ends up at the bottom of the song rather than in its
                // middle, and then to the fewer, wider columns, which wrap less.
                if (cost < best || (cost == best && end > rowEnds[start])) {
                    best = cost
                    rowEnds[start] = end
                    rowColumnCounts[start] = columnCount
                }
            }
            end++
        }
        costs[start] = best
    }
    val rows = IntArray(sectionCount)
    val columns = IntArray(sectionCount)
    val columnCounts = mutableListOf<Int>()
    var start = 0
    while (start < sectionCount) {
        val end = rowEnds[start]
        val columnCount = rowColumnCounts[start]
        val row = columnCounts.size
        val tallestInRow = (start until end).maxOf { heights[columnCount - 1][it] }
        stack(start, end, columnCount, tallestInRow) { index, cell ->
            rows[index] = row
            columns[index] = cell
        }
        columnCounts += columnCount
        start = end
    }
    return SectionGrid(rows = rows, columns = columns, columnCounts = columnCounts.toIntArray())
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
        /** The lines, and the comments that stand between them inside the section, in the order the file has them. */
        val parts: List<SectionPart>,
        /** Choruses (and their recalls) are drawn on a raised card so that they stand out. */
        val isOnCard: Boolean,
    ) : RenderSection {

        /** Every line of the section, whatever comments stand between them. */
        val lines get() = parts.flatMap { (it as? SectionPart.Lines)?.lines.orEmpty() }
    }

    /** A comment between two sections, or one inside a section as one of its [SectionPart]s. */
    data class Comment(
        val text: String,
        val style: CommentStyle,
    ) : RenderSection, SectionPart
}

/** A piece of a [RenderSection.Lines]: a run of its lines, or a comment standing between two of them. */
private sealed interface SectionPart {

    data class Lines(val lines: List<ChordProLine>) : SectionPart
}

/**
 * Flattens the parsed song into the sections the layout places.
 *
 * A section a comment cut in two (see `ChordProBlock.Section.isContinuation`) is put back together here, with the
 * comment between its halves, since the layout would otherwise place the comment and each half as units of their own,
 * free to land in different columns or rows. Only a comment with lines on both sides of it can be told apart from one
 * standing between two sections: the parser leaves no continuation behind a comment that ends a section, and puts one
 * that opens a section before it. Breaks and `{transpose}` directives cut a section the same way and draw nothing, so
 * they are joined over as well; a `{chorus}` recall is a section of its own and is not.
 *
 * A `{chorus}` recall repeats the chorus the parser found for it (`ChordProBlock.ChorusRecall.blocks`), every piece of
 * it and the comments that cut it, headed once. In lyrics-only mode the chords go away with the sections that consist
 * of nothing else: tabs and grids say nothing without them, and a line that was only chords would leave a blank behind.
 */
private fun ChordProSong.toRenderSections(
    shouldShowChords: Boolean,
    defaultLabels: DefaultSectionLabels,
): List<RenderSection> {
    val sections = mutableListOf<RenderSection>()

    /**
     * Adds a section and what [joinCutSections] joined to it, or only the comments where lyrics-only mode leaves it
     * with nothing else to show; true when a section was added.
     */
    fun addSection(pieces: List<ChordProBlock>, header: String?): Boolean {
        val comments = pieces.filterIsInstance<ChordProBlock.Comment>().map { RenderSection.Comment(text = it.text, style = it.style) }
        val sectionPieces = pieces.filterIsInstance<ChordProBlock.Section>()
        // A section that is nothing but tablature or a grid goes away entirely in lyrics-only mode, its heading with
        // it: neither says anything without the chords, and a heading over nothing is worse than no heading at all.
        // The comments inside it still say something, so they stay, as the comments between sections do.
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

                is ChordProBlock.Comment -> parts += RenderSection.Comment(text = piece.text, style = piece.style)

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
            parts = parts,
            isOnCard = sectionPieces.first().type == SectionType.Chorus,
        )
        return true
    }

    blocks.joinCutSections().forEach { pieces ->
        when (val block = pieces.first()) {
            is ChordProBlock.Break -> Unit // The column layout makes its own breaks.

            is ChordProBlock.Transpose -> Unit // It moved the chords; there is nothing to draw.

            is ChordProBlock.Comment -> sections += RenderSection.Comment(text = block.text, style = block.style)

            is ChordProBlock.ChorusRecall -> {
                // The heading goes on the first piece of the chorus that is shown, and stays behind on its own when
                // none is: a recall has always said where the chorus is sung, even with nothing under it.
                var header: String? = block.label ?: (block.blocks.firstOrNull() as? ChordProBlock.Section)?.label ?: defaultLabels.chorus
                block.blocks.joinCutSections().forEach { recalled ->
                    when (val first = recalled.first()) {
                        is ChordProBlock.Section -> if (addSection(recalled, header)) header = null
                        is ChordProBlock.Comment -> sections += RenderSection.Comment(text = first.text, style = first.style)
                        else -> Unit
                    }
                }
                header?.let { sections += RenderSection.Lines(header = it, parts = emptyList(), isOnCard = true) }
            }

            // The rest of a section a comment or a break cut in two was headed where it started.
            is ChordProBlock.Section -> addSection(pieces, if (block.isContinuation) null else block.header(defaultLabels))
        }
    }
    return sections
}

/**
 * Groups the blocks so that a section comes with every continuation of it that follows and the comments, breaks and
 * transpositions that cut it there, in their order. Every other block is a group of its own, and so is one of those
 * three when no continuation follows it, since it then stands between two sections rather than inside one.
 */
private fun List<ChordProBlock>.joinCutSections(): List<List<ChordProBlock>> {
    val groups = mutableListOf<List<ChordProBlock>>()
    var index = 0
    while (index < size) {
        val group = mutableListOf(this[index])
        if (this[index] is ChordProBlock.Section) {
            while (true) {
                var next = index + 1
                while (next < size && this[next].isSectionCut()) next++
                val continuation = getOrNull(next) as? ChordProBlock.Section
                if (continuation?.isContinuation != true) break
                group += subList(index + 1, next + 1)
                index = next
            }
        }
        groups += group
        index++
    }
    return groups
}

/** Whether the block is one that cuts a section in two without being a section itself (see `ChordProParser`). */
private fun ChordProBlock.isSectionCut() = this is ChordProBlock.Comment || this is ChordProBlock.Break || this is ChordProBlock.Transpose

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
    val chordColor = MaterialTheme.colorScheme.primary
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
    // Lines without any lyrics (e.g. an intro) only need to be as tall as the chords themselves.
    val lineHeight = with(density) { (if (line.text.isBlank()) chordLineHeight else chordLineHeight + textMeasurements.lyricsLineHeight).toSp() }
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
                    val maxX = max(0f, size.width - chordLayout.size.width)
                    val position = layout.getHorizontalPosition(offset, usePrimaryDirection = true)
                    val x = if (isRightToLeft) {
                        // The chord hangs to the left of its character, the way it hangs to the right of it in a line
                        // that runs the other way, so the position is its right edge.
                        (min(position, previousChordEdge) - chordLayout.size.width).coerceIn(0f, maxX)
                    } else {
                        max(position, previousChordEdge).coerceIn(0f, maxX)
                    }
                    drawText(
                        textLayoutResult = chordLayout,
                        color = if (chord.isAnnotation) annotationColor else chordColor,
                        topLeft = Offset(x, layout.getLineTop(lineIndex)),
                    )
                    previousChordEdge = if (isRightToLeft) x - gap else x + chordLayout.size.width + gap
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
 */
private fun ChordProLine.Lyrics.padLyricsToFitChords(
    chordWidths: List<Float>,
    gap: Float,
    paddingWidth: Float,
    measureWidth: (String) -> Float,
): ChordProLine.Lyrics {
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
private val INLINE_COMMENT_GAP = 4.dp
private val HEADER_ELEVATION = 2.dp
private val HEADER_HORIZONTAL_PADDING = 12.dp
private val HEADER_VERTICAL_PADDING = 6.dp
private val FOLD_CHEVRON_SIZE = 20.dp
private val FOLD_CHEVRON_GAP = 4.dp
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
private const val UNMEASURED_HEIGHT = -1
private const val MAX_MEASURED_TEXTS = 4096
private const val MAX_ANIMATED_SECTION_HEIGHT = 1 shl 17
private const val MAX_ANIMATED_WIDE_SECTION_HEIGHT = 1 shl 15
private const val WIDE_SECTION_WIDTH = (1 shl 13) - 1
private const val PADDING = '\u00A0' // Non-breaking space, so that the padding never gets trimmed or wrapped.
private const val BEAT_SYMBOL = "\u00B7"
private const val CHIP_SEPARATOR = "\u00B7"
