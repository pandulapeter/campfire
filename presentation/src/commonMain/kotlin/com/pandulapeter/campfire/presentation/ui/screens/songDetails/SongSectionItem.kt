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

import com.pandulapeter.campfire.presentation.ui.songInfo.SongInfoEditing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels

/** One chunk of a section of [SongLyrics], drawn by what kind of section it is. */
@Composable
internal fun SongSectionItem(
    modifier: Modifier,
    section: RenderSection,
    items: IntRange,
    notation: ChordNotation,
    isSongInfoShown: Boolean,
    songInfoEditing: SongInfoEditing?,
    playingControls: SongPlayingControls?,
    playingTools: SongPlayingTools?,
    animatesControls: Boolean,
    chordDiagrams: ChordDiagrams?,
    tuningChipVisibility: MutableTransitionState<Boolean>,
    chordLayouts: ChordCellLayouts?,
    onChordFoldToggled: (() -> Unit)?,
    isChordsSectionFadingIn: Boolean,
    headerStyle: TextStyle,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
    textMeasurements: SongTextMeasurements,
    foldedRuns: FoldedRuns?,
    defaultLabels: DefaultSectionLabels,
    fontScale: Float,
) = when (section) {
    is RenderSection.Metadata -> SongMetadataSection(
        modifier = modifier,
        metadata = section.metadata,
        isSongInfoShown = isSongInfoShown,
        songInfoEditing = songInfoEditing,
        playingControls = playingControls,
        playingTools = playingTools,
        readsDefaults = section.readsDefaults,
        animatesControls = animatesControls,
        titleStyle = headerStyle,
        chordStyle = chordStyle,
        fontScale = fontScale,
        instrumentChips = chordDiagrams?.onInstrumentClicked?.takeIf { section.instrumentChips != null }?.let { onInstrumentClicked ->
            {
                SongInstrumentChips(
                    chordDiagrams = chordDiagrams,
                    headerStyle = headerStyle,
                    fontScale = fontScale,
                    tuningChipVisibility = tuningChipVisibility,
                    onInstrumentClicked = onInstrumentClicked,
                    onTuningClicked = chordDiagrams.onTuningClicked ?: {},
                )
            }
        },
    )

    is RenderSection.Chords -> SongChordsSection(
        modifier = modifier.padding(horizontal = CARD_PADDING),
        section = section,
        items = items,
        layouts = checkNotNull(chordLayouts),
        chordDiagrams = chordDiagrams,
        onFoldToggled = onChordFoldToggled,
        isFadingIn = isChordsSectionFadingIn,
        headerStyle = headerStyle,
        fontScale = fontScale,
    )

    is RenderSection.Comment -> SongComment(
        modifier = modifier.padding(horizontal = CARD_PADDING),
        comment = section,
        notation = notation,
        fontScale = fontScale,
    )

    is RenderSection.KeyChange -> SongKeyChange(
        modifier = modifier.padding(horizontal = CARD_PADDING),
        keyChange = section,
        chordStyle = chordStyle,
    )

    is RenderSection.Timing -> SongTimingLine(
        modifier = modifier,
        timing = section,
        style = chordStyle,
    )

    is RenderSection.Lines -> LinesSectionItem(
        modifier = modifier,
        section = section,
        items = items,
        notation = notation,
        headerStyle = headerStyle,
        lyricsStyle = lyricsStyle,
        chordStyle = chordStyle,
        textMeasurements = textMeasurements,
        foldedRuns = foldedRuns,
        defaultLabels = defaultLabels,
        fontScale = fontScale,
    )
}

/** A section of lyrics, tablature or grids, drawn on a card where it is a chorus. */
@Composable
private fun LinesSectionItem(
    modifier: Modifier,
    section: RenderSection.Lines,
    items: IntRange,
    notation: ChordNotation,
    headerStyle: TextStyle,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
    textMeasurements: SongTextMeasurements,
    foldedRuns: FoldedRuns?,
    defaultLabels: DefaultSectionLabels,
    fontScale: Float,
) = if (section.isOnCard) {
    // The card is drawn by the layout behind the chunks of every piece; the chunk only takes
    // the card's corners, so that a press on its title row is drawn inside them.
    val cardShape = MaterialTheme.shapes.large
    val zeroCorner = CornerSize(0.dp)
    val isFirstChunk = items.first == 0
    val isLastChunk = items.last >= section.itemCount - 1
    CompositionLocalProvider(LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        SongSectionContent(
            modifier = modifier.clip(
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
            notation = notation,
            fontScale = fontScale,
        )
    }
} else {
    // The same horizontal padding as inside a card, so that every section's text starts at the
    // same x position whether it is carded or not.
    SongSectionContent(
        modifier = modifier.padding(horizontal = CARD_PADDING),
        section = section,
        items = items,
        isOnCard = false,
        headerStyle = headerStyle,
        lyricsStyle = lyricsStyle,
        chordStyle = chordStyle,
        textMeasurements = textMeasurements,
        foldedRuns = foldedRuns,
        defaultLabels = defaultLabels,
        notation = notation,
        fontScale = fontScale,
    )
}
