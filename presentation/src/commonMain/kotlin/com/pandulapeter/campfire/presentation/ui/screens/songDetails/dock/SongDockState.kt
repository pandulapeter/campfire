/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.LaunchedEffect
import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.PageTurn
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.presentation.ui.chords.hasSongChords
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.preferences.SongSections
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.PAGE_HORIZONTAL_PADDING
import com.pandulapeter.campfire.presentation.ui.state.songText.SongDockFacts
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams

/**
 * The song details screen's dock: its [rows], and either its [layout] as rows under the song or its [panelLayout] as a
 * side panel at the song's end (null for no dock), beside which [layout] is the one row of scale suggestions under the
 * song, the rows under it being [rowsUnder] (those drawn, and their [layout], as a change of them is staged, with the
 * [visibleParts] of them faded in, [rememberDockStage]: [staged]), the [height] the rows take from the bottom of the screen (the bottom
 * inset included where they cover it) and the [panelWidth] the panel takes from its end (the end inset included), each
 * followed as it animates to a new one, which the pages' grid waits for ([isSettled]). Each
 * page composed puts what the dock draws for it in [contents], its stepper, which says where its reader is headed,
 * in [steppers], and how wide its scale chips are in one line in [scalesWidths], by its index; until the song's is
 * known ([areScalesMeasured]) its scale chips are drawn unseen, as they may yet move to a row of their own. The
 * instrument card ends in the tuner where [onTunerClicked] is given (the Dock's Tuner switch and the Tuner feature on).
 */
internal class SongDockState(
    val rows: DockRows,
    private val staged: DockStaged,
    val panelLayout: DockLayout?,
    private val target: Dp,
    animatedHeight: State<Dp>,
    val panelTarget: Dp,
    animatedPanelWidth: State<Dp>,
    val contents: SnapshotStateMap<Int, DockContent>,
    val steppers: SnapshotStateMap<Int, SongStepper>,
    val scalesWidths: SnapshotStateMap<Int, Dp>,
    val areScalesMeasured: Boolean,
    val onTunerClicked: (() -> Unit)?,
) {
    val rowsUnder get() = staged.rows
    val layout get() = staged.layout
    val visibleParts get() = staged.visible

    /** The parts drawn of [rowsUnder] laid out as [layout], those handed to the setlist's pager bar included. */
    val parts get() = staged.parts

    /** Whether the dock has a row under the song, rather than its controls on the setlist's pager bar or no dock. */
    val hasRowsUnder get() = (layout?.height ?: 0.dp) > 0.dp
    val height by animatedHeight
    val panelWidth by animatedPanelWidth
    val isSidePanel get() = panelLayout != null
    val isHeightSettled get() = height == target

    /** The room under the rows that is the bottom inset they cover: none over a setlist's pager bar, which covers it. */
    val rowsBottomInset get() = layout?.let { (target - it.height).coerceAtLeast(0.dp) } ?: 0.dp
    val isSettled get() = isHeightSettled && panelWidth == panelTarget
}

/**
 * The dock as the switches in [userPreferences], the song the pager is on or heading for ([targetPage], [songKey], its
 * [songText] read ahead of its page, then what its page handed up in [SongDockState.contents], kept in [dockFacts] for
 * the next time), and the window decide it: its rows (the chords with [chordDiagrams] drawn on an
 * instrument with shapes, on one without its instrument card alone, the scale suggestions, the tempo chip with the metronome on, each only where the song has
 * something for it, [DockRows.forSong]), and whether it is a side panel ([isDockSidePanel], which the reading mode weighs too) or rows under the song, which get as much room as the
 * reading mode and a setlist's pager bar ([hasPagerBar]) leave them ([dockBudgetOf]) in a window that is not short and
 * outside an import preview ([isImportPreview]), whose card takes the room of a second row. Rows without the pager bar cover the bottom inset of
 * [contentPadding] too, and the panel its end inset.
 */
@Composable
internal fun rememberSongDock(
    userPreferences: UserPreferences?,
    chordDiagrams: ChordDiagrams?,
    hasPagerBar: Boolean,
    isImportPreview: Boolean,
    contentPadding: PaddingValues,
    targetPage: Int,
    songKey: String?,
    songText: String?,
    dockFacts: MutableMap<String, SongDockFacts>,
    onTunerClicked: (() -> Unit)?,
): SongDockState {
    // An import preview has no metronome.
    val isMetronomeShown = userPreferences?.isMetronomeEnabled != false && userPreferences?.isDockMetronomeEnabled != false && !isImportPreview
    val switchedRows = DockRows(
        hasChordRow = chordDiagrams?.tuning != null,
        hasScaleRow = SongSections.of(userPreferences).areScaleSuggestionsShown,
        hasTempo = isMetronomeShown,
        // An instrument without shapes keeps its card, the way back to one with diagrams, as do the diagrams switched off
        // while the card holds the tuner (rememberChordDiagrams).
        hasInstrumentChip = chordDiagrams != null && chordDiagrams.tuning == null,
    )
    val contents = remember { mutableStateMapOf<Int, DockContent>() }
    val scalesWidths = remember { mutableStateMapOf<Int, Dp>() }
    val songRows = rememberSongRows(switchedRows, contents[targetPage], scalesWidths[targetPage], songKey, songText, dockFacts)
    val rows = songRows.rows
    val turnsByColumn = userPreferences?.songLayout != SongLayout.CONTINUOUS && userPreferences?.pageTurn == PageTurn.COLUMN
    val windowSize = LocalWindowInfo.current.containerDpSize
    val layoutDirection = LocalLayoutDirection.current
    val endInset = contentPadding.calculateEndPadding(layoutDirection)
    val panelWidth = dockPanelWidthOf(tempoChipWidth = dockTallTempoChipWidth())
    val isSidePanel = isDockSidePanel(
        rows = rows,
        windowWidth = windowSize.width,
        sideInsets = contentPadding.calculateStartPadding(layoutDirection) + endInset,
        panelWidth = panelWidth,
        isContinuous = userPreferences?.songLayout == SongLayout.CONTINUOUS,
    )
    val rowHeight = dockRowHeight()
    val bottomPadding = dockBottomPaddingOf(bottomInset = contentPadding.calculateBottomPadding())
    // Beside a side panel the rows under the song are the scale suggestions alone, which the panel has no width for.
    val rowsUnder = if (isSidePanel) DockRows(hasChordRow = false, hasScaleRow = rows.hasScaleRow, hasTempo = false) else rows
    val areControlsCompact = windowSize.width < DOCK_COMPACT_WINDOW_WIDTH
    val controlWidths = DockControlWidths(
        card = chordDiagrams?.takeIf { rows.hasInstrumentCard }?.let { dockLineCardWidth(it, hasTuner = onTunerClicked != null) } ?: 0.dp,
        tempoChip = if (rows.hasTempo) dockTempoChipWidth() else 0.dp,
    )
    // The rows' width inside their padding, and the pager bar's, which is as wide.
    val rowWidth = windowSize.width - contentPadding.calculateStartPadding(layoutDirection) - endInset - PAGE_HORIZONTAL_PADDING * 2
    val scalesFit = scalesFitBesideControls(controlWidths, rowWidth, songRows.scalesWidth)
    val fitsPagerBar = hasPagerBar && controlsFitPagerBar(controlWidths, rowWidth)
    val layout = remember(rows, windowSize.height, turnsByColumn, hasPagerBar, isImportPreview, rowHeight, isSidePanel, bottomPadding, areControlsCompact, scalesFit, fitsPagerBar) {
        if (isSidePanel) {
            dockLayoutOf(rows = rowsUnder, budget = 0.dp, isOneRow = true, rowHeight = rowHeight, bottomPadding = bottomPadding)
        } else {
            dockLayoutOf(
                rows = rows,
                budget = dockBudgetOf(windowHeight = windowSize.height, turnsByColumn = turnsByColumn, hasPagerBar = hasPagerBar),
                isOneRow = windowSize.height < SHORT_WINDOW_HEIGHT || isImportPreview,
                rowHeight = rowHeight,
                bottomPadding = bottomPadding,
                scalesFitBesideControls = scalesFit,
                controlsFitPagerBar = fitsPagerBar,
            )?.copy(areControlsCompact = areControlsCompact)
        }
    }
    val panelLayout = remember(rows, rowHeight, isSidePanel) { if (isSidePanel) dockPanelLayoutOf(rows, rowHeight) else null }
    val staged = rememberDockStage(rows = rowsUnder, layout = layout, snaps = songRows.isDataArriving)
    // A setlist's pager bar, under both the rows and the side panel, covers the bottom inset for them.
    val target = staged.layout?.let { it.height + if (hasPagerBar) 0.dp else contentPadding.calculateBottomPadding() } ?: 0.dp
    val panelTarget = if (panelLayout == null) 0.dp else panelWidth + endInset
    return SongDockState(
        rows = rows,
        staged = staged,
        panelLayout = panelLayout,
        target = target,
        animatedHeight = animateDockDp(target, snaps = songRows.isDataArriving),
        panelTarget = panelTarget,
        animatedPanelWidth = animateDockDp(panelTarget, snaps = songRows.isDataArriving),
        contents = contents,
        steppers = remember { mutableStateMapOf() },
        scalesWidths = scalesWidths,
        areScalesMeasured = songRows.scalesWidth != null,
        onTunerClicked = onTunerClicked,
    )
}

/**
 * The dock's rows for the song the pager is on or heading for ([songKey], its page's [content]), [switchedRows] fitted to
 * what it has ([DockRows.forSong]): what its page last said, kept in [facts] for the screen opened on it again; before
 * that, what its [songText] says at a glance (whether it has chords, which a song of chords has scales for too), so that
 * the dock is right on the song's first frame and its page laid out once; else the last song's while its text has not
 * been read, or the switches' alone before any song is known. The width of its scale chips its page measured
 * ([scalesWidth]) is kept the same way. [SongRows.isDataArriving] where the rows or that width changed while neither the
 * song nor the switches did: the song's page saying what it has, data arriving rather than a change the reader made,
 * which is then not animated.
 */
@Composable
private fun rememberSongRows(
    switchedRows: DockRows,
    content: DockContent?,
    scalesWidth: Dp?,
    songKey: String?,
    songText: String?,
    facts: MutableMap<String, SongDockFacts>,
): SongRows {
    val known = remember { KnownSongRows() }
    if (songKey != null && songText != null && songKey !in facts) {
        // Read here, in the composition that first needs it: the page, composed as the pager is measured, hands up what
        // it has a frame later, by which time it would have been laid out against the wrong dock.
        val hasChords = hasSongChords(ChordProParser.parse(songText), ChordNotation.STANDARD)
        facts[songKey] = SongDockFacts(hasChords = hasChords, hasScales = hasChords)
    }
    if (songKey != null && (content != null || scalesWidth != null)) {
        val stored = facts[songKey] ?: SongDockFacts()
        facts[songKey] = SongDockFacts(
            hasChords = content?.hasChordCells ?: stored.hasChords,
            hasScales = content?.hasScaleSuggestions ?: stored.hasScales,
            scalesWidth = scalesWidth ?: stored.scalesWidth,
        )
    }
    val songFacts = songKey?.let { facts[it] } ?: known.facts
    val rows = remember(switchedRows, songFacts?.hasChords, songFacts?.hasScales) {
        switchedRows.forSong(hasChords = songFacts?.hasChords ?: true, hasScales = songFacts?.hasScales ?: true)
    }
    val isDataArriving = (rows != known.rows || songFacts?.scalesWidth != known.facts?.scalesWidth) &&
        songKey == known.songKey && switchedRows == known.switchedRows
    known.facts = songFacts
    known.rows = rows
    known.songKey = songKey
    known.switchedRows = switchedRows
    return SongRows(rows = rows, scalesWidth = songFacts?.scalesWidth, isDataArriving = isDataArriving)
}

/** The dock's rows for a song and the width of its scale chips, see [rememberSongRows]. */
private class SongRows(val rows: DockRows, val scalesWidth: Dp?, val isDataArriving: Boolean)

/** What [rememberSongRows] last decided, and for what. Not a state: it is written and read in the same composition. */
private class KnownSongRows(
    var facts: SongDockFacts? = null,
    var rows: DockRows? = null,
    var songKey: String? = null,
    var switchedRows: DockRows? = null,
)

/**
 * [target] as the dock's height or the side panel's width follows it: animated, as a switch flipped, a window resized,
 * or a setlist's next song changes it, but snapped where [snaps] says the change is a song's page saying what it has,
 * and snapped in the very composition that learns of it rather than a frame later, so that the page is laid out once.
 */
@Composable
private fun animateDockDp(target: Dp, snaps: Boolean): State<Dp> {
    val dp = remember { DockDp(Animatable(target, Dp.VectorConverter)) }
    if (snaps) dp.snapped = target
    LaunchedEffect(target) {
        if (snaps) dp.animatable.snapTo(target) else dp.animatable.animateTo(target)
        dp.snapped = null
    }
    return dp
}

/**
 * The value of [animateDockDp]: the one it [snapped] to until its animatable has caught up, else the animatable's. Not
 * a state of its own: the snap is written and read in the same composition, and the animatable's value is one.
 */
private class DockDp(val animatable: Animatable<Dp, *>) : State<Dp> {
    var snapped: Dp? = null
    override val value get() = snapped ?: animatable.value
}

/**
 * How tall the dock's row of chips is: they and the tempo chip are drawn at the interface's text size, which a large
 * system text size grows past a chip's.
 */
@Composable
internal fun dockRowHeight() = maxOf(DOCK_ROW_HEIGHT, songControlHeight(MaterialTheme.typography.titleSmall))
