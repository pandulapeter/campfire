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

import com.pandulapeter.campfire.presentation.ui.songView.model.FoldToggle
import com.pandulapeter.campfire.presentation.ui.songView.model.FoldedRuns
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SectionItem
import com.pandulapeter.campfire.presentation.ui.songView.model.SectionPart
import com.pandulapeter.campfire.presentation.ui.songView.model.sectionChunkStarts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_section_collapse_starting
import com.pandulapeter.campfire.presentation.resources.song_details_section_expand_starting
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.songLayout.FoldableKind
import com.pandulapeter.campfire.presentation.ui.songLayout.UNNAMED_SECTION_HEADER
import com.pandulapeter.campfire.presentation.ui.songLayout.environmentLabel
import com.pandulapeter.campfire.presentation.ui.songLayout.labelOf
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily

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
internal fun SongSectionContent(
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
    notation: ChordNotation,
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
    // An unnamed section is headed even where nothing folds (the editor's preview), by a pill as wide as the one with
    // the chevron in it, so that the preview lays the song out the way the details screen will — unless it is nothing
    // but tablature or a grid, which the toggle row named after that heads instead.
    val isHeaderShown = header != null && (header != UNNAMED_SECTION_HEADER || wholeSectionKind == null)
    val hasBody = section.parts.isNotEmpty() && sectionToggle?.isExpanded != false
    val chevronSize = FOLD_CHEVRON_SIZE * fontScale
    val isFirstChunk = items.first == 0
    val isLastChunk = items.last >= section.itemCount - 1
    // Tablature and grids are both columns of characters that have to line up with the ones above and below them.
    val monospaceFontFamily = LocalMonospaceFontFamily.current
    val monospaceLyricsStyle = lyricsStyle.copy(fontFamily = monospaceFontFamily)
    val monospaceChordStyle = chordStyle.copy(fontFamily = monospaceFontFamily)
    if (isFirstChunk) when {
        isHeaderShown && isOnCard -> CardTitleRow(
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

        isHeaderShown -> SectionHeaderPill(
            header = header,
            toggle = sectionToggle,
            style = headerStyle,
            chevronSize = chevronSize,
            // A pill with no name would read as one more "Hide the section" among many, so it is named by its first line.
            chevronDescription = section.firstLyric?.takeIf { header == UNNAMED_SECTION_HEADER }?.let {
                textResource(
                    if (sectionToggle?.isExpanded == true) {
                        Res.string.song_details_section_collapse_starting
                    } else {
                        Res.string.song_details_section_expand_starting
                    },
                    it,
                )
            },
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
    val hasTitle = isHeaderShown || sectionToggle != null
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
                    PaddingValues(top = if (isFirstChunk && isHeaderShown) HEADER_GAP else 0.dp)
                }
            ),
    ) {
        // Tablature and grids are runs of lines inside a section rather than sections of their own, so the lines are
        // grouped: each run is folded as one and drawn as pieces the section may be cut between (see [runSlotCount]) -
        // a system of tablature with all of its strings, a line of a grid - and everything else is laid out line by
        // line around them. A comment that cut the section stands where the file has it, between the lines around it.
        // A section that is nothing but one run is folded as the section it is, so its run has no toggle of its own.
        val sectionItems = section.sectionItems
        val lastItem = minOf(items.last, sectionItems.lastIndex)
        var item = maxOf(items.first, 0)
        while (item <= lastItem) {
            when (val sectionItem = sectionItems[item]) {
                is SectionItem.Inline -> when (val part = sectionItem.part) {
                    is RenderSection.Comment -> SongComment(
                        modifier = Modifier.padding(vertical = INLINE_COMMENT_GAP),
                        comment = part,
                        notation = notation,
                        fontScale = fontScale,
                    )

                    is RenderSection.KeyChange -> SongKeyChange(
                        modifier = Modifier.padding(vertical = INLINE_COMMENT_GAP),
                        keyChange = part,
                        chordStyle = chordStyle,
                    )

                    is SectionPart.Lines -> Unit
                }

                is SectionItem.Line -> when (val line = sectionItem.line) {
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

                is SectionItem.RunSlot -> {
                    // The slots of one run this chunk holds are drawn together, under the run's one toggle.
                    var lastSlotItem = item
                    while (lastSlotItem < lastItem && (sectionItems[lastSlotItem + 1] as? SectionItem.RunSlot)?.runFoldKey == sectionItem.runFoldKey) {
                        lastSlotItem++
                    }
                    SectionRun(
                        run = sectionItem.run,
                        kind = sectionItem.kind,
                        runFoldKey = sectionItem.runFoldKey,
                        slotCount = sectionItem.slotCount,
                        shownSlots = sectionItem.slot..sectionItem.slot + lastSlotItem - item,
                        hasToggle = wholeSectionKind == null,
                        foldedRuns = foldedRuns,
                        defaultLabels = defaultLabels,
                        headerStyle = headerStyle,
                        chevronSize = chevronSize,
                        monospaceLyricsStyle = monospaceLyricsStyle,
                        monospaceChordStyle = monospaceChordStyle,
                        textMeasurements = textMeasurements,
                    )
                    item = lastSlotItem
                }
            }
            item++
        }
    }
}

/**
 * The [shownSlots] of one run of tablature or grid lines, under the run's own toggle where it has one ([hasToggle], and
 * [foldedRuns] given): the toggle is drawn with the run's first slot, and a folded run draws nothing else.
 */
@Composable
private fun SectionRun(
    run: List<ChordProLine>,
    kind: FoldableKind,
    runFoldKey: String,
    slotCount: Int,
    shownSlots: IntRange,
    hasToggle: Boolean,
    foldedRuns: FoldedRuns?,
    defaultLabels: DefaultSectionLabels,
    headerStyle: TextStyle,
    chevronSize: Dp,
    monospaceLyricsStyle: TextStyle,
    monospaceChordStyle: TextStyle,
    textMeasurements: SongTextMeasurements,
) {
    val runModifier = if (!hasToggle || foldedRuns == null) {
        Modifier
    } else {
        if (0 in shownSlots) FoldToggleRow(
            kind = kind,
            label = run.first().environmentLabel ?: defaultLabels.labelOf(kind),
            toggle = FoldToggle(isExpanded = !foldedRuns.isCollapsed(runFoldKey), onToggled = { foldedRuns.toggle(runFoldKey) }),
            style = headerStyle,
            chevronSize = chevronSize,
        )
        if (foldedRuns.isCollapsed(runFoldKey)) return
        Modifier.fadingIn(isFadingIn = foldedRuns.hasBeenToggled(runFoldKey))
    }
    when (kind) {
        FoldableKind.TAB -> {
            val lines = remember(run) { run.map { (it as? ChordProLine.Tab)?.text.orEmpty() } }
            val rows = remember(lines, monospaceLyricsStyle, textMeasurements) {
                textMeasurements.tabRows(lines, monospaceLyricsStyle)
            }
            shownSlots.forEach { slot ->
                SongTabBlock(
                    modifier = runModifier.fillMaxWidth(),
                    lines = lines,
                    rows = rows,
                    slot = slot,
                    isLastSlot = slot == slotCount - 1,
                )
            }
        }

        FoldableKind.GRID -> Column(modifier = runModifier.fillMaxWidth()) {
            val alignedLines = remember(run, textMeasurements) { textMeasurements.gridBars(run) }
            shownSlots.forEach { slot ->
                alignedLines.getOrNull(slot)?.let { bars ->
                    SongGridLine(
                        bars = bars,
                        lyricsStyle = monospaceLyricsStyle,
                        chordStyle = monospaceChordStyle,
                    )
                }
            }
        }
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

private val INLINE_COMMENT_GAP = 12.dp

private val FOLD_TOGGLE_HORIZONTAL_PADDING = 8.dp
private val FOLD_TOGGLE_VERTICAL_PADDING = 4.dp
