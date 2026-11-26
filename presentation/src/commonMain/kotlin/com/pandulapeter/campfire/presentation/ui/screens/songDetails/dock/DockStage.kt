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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * A part of the dock's rows under the song that a change can take away or bring: a row, what stands at one of its ends, or
 * what its middle shows. Parts that are one place drawn differently (the diagrams at either size, the chip row's chords and
 * scales together or apart, the metronome panel with or without its card) are parts of their own, so that a change of
 * them leaves and arrives like any other rather than snapping in place.
 */
internal enum class DockPart {
    CHIP_ROW,
    CHIP_ROW_CARD,
    CHIP_ROW_CHORDS_AND_SCALES,
    CHIP_ROW_CHORDS,
    CHIP_ROW_SCALES,
    CHIP_ROW_TEMPO,
    DIAGRAM_ROW,
    DIAGRAM_ROW_CARD,
    FULL_DIAGRAMS,
    SMALL_DIAGRAMS,
    DIAGRAM_ROW_TEMPO,
    METRONOME_PANEL,
    METRONOME_PANEL_WITH_CARD,
}

/**
 * The parts the dock's rows under the song draw for [rows] laid out as [layout] (none without a layout): the metronome
 * panel where the click is all they hold, else the chip row (the one row, or the scale row over the diagrams) and the
 * diagram row, each with what stands at its ends, as `SongDock` draws them.
 */
internal fun dockPartsOf(rows: DockRows, layout: DockLayout?): Set<DockPart> {
    if (layout == null) return emptySet()
    if (rows.isMetronomeOnly) return setOf(if (rows.hasInstrumentCard) DockPart.METRONOME_PANEL_WITH_CARD else DockPart.METRONOME_PANEL)
    val hasDiagramRow = rows.hasChordRow && layout.tier != DockTier.CHORD_CHIPS
    return if (hasDiagramRow) {
        setOfNotNull(
            DockPart.CHIP_ROW.takeIf { rows.hasScaleRow },
            DockPart.CHIP_ROW_SCALES.takeIf { rows.hasScaleRow },
            DockPart.DIAGRAM_ROW,
            DockPart.DIAGRAM_ROW_CARD.takeIf { rows.hasInstrumentCard },
            if (layout.tier == DockTier.SMALL_DIAGRAMS) DockPart.SMALL_DIAGRAMS else DockPart.FULL_DIAGRAMS,
            DockPart.DIAGRAM_ROW_TEMPO.takeIf { rows.hasTempo },
        )
    } else {
        setOfNotNull(
            DockPart.CHIP_ROW,
            DockPart.CHIP_ROW_CARD.takeIf { rows.hasInstrumentCard },
            when {
                rows.hasChordRow && rows.hasScaleRow -> DockPart.CHIP_ROW_CHORDS_AND_SCALES
                rows.hasChordRow -> DockPart.CHIP_ROW_CHORDS
                rows.hasScaleRow -> DockPart.CHIP_ROW_SCALES
                else -> null
            },
            DockPart.CHIP_ROW_TEMPO.takeIf { rows.hasTempo },
        )
    }
}

/**
 * The dock's rows under the song as a change is staged ([rememberDockStage]): the [rows] and [layout] drawn, and the
 * [visible] parts among them; the rest are drawn faded out, and an end of a row that is not visible takes no room.
 */
internal class DockStaged(val rows: DockRows, val layout: DockLayout?, val visible: Set<DockPart>)

/**
 * [rows] laid out as [layout], with every change of their parts ([dockPartsOf]) staged as a change of the song's look is
 * (`rememberStagedSongFrame`): what leaves fades out with the old layout held, the new layout is then drawn (its height and
 * the rows' places gliding to it, which the pages' grid waits for), and what arrives fades in once they have come to rest.
 * A change of the layout alone (a window resized, which brings or takes no part) is drawn at once and glides, and where
 * [snaps] says the change is a song's page saying what it has, data arriving, everything is drawn at once.
 */
@Composable
internal fun rememberDockStage(rows: DockRows, layout: DockLayout?, snaps: Boolean): DockStaged {
    val parts = dockPartsOf(rows, layout)
    val stage = remember { DockStage(rows, layout, parts) }
    if (snaps) {
        stage.laidOut = parts
        stage.arrived = parts
    }
    val isLeaving = parts != stage.laidOut
    if (!isLeaving) {
        stage.heldRows = rows
        stage.heldLayout = layout
    }
    LaunchedEffect(parts) {
        if (stage.laidOut != parts) {
            // Only the old layout's parts that are still on show have anything to fade out.
            if ((stage.arrived intersect stage.laidOut).any { it !in parts }) delay(DOCK_LEAVING_MILLIS)
            val movesRows = stage.laidOut.isRowChange(parts) || stage.heldLayout?.height != stage.targetLayout?.height
            stage.laidOut = parts
            if (movesRows) delay(DOCK_MOVING_MILLIS)
        }
        stage.arrived = parts
    }
    stage.targetLayout = layout
    return if (isLeaving) {
        DockStaged(rows = stage.heldRows, layout = stage.heldLayout, visible = stage.arrived intersect parts)
    } else {
        DockStaged(rows = rows, layout = layout, visible = stage.arrived intersect parts)
    }
}

/**
 * How opaque a part of the dock is drawn: fading in where it [isVisible], out where not. A part composed while not
 * visible yet (one a change brings) starts out unseen.
 */
@Composable
internal fun dockPartAlpha(isVisible: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (isVisible) 1f else 0f,
    animationSpec = if (isVisible) MaterialTheme.motionScheme.defaultEffectsSpec() else MaterialTheme.motionScheme.fastEffectsSpec(),
)

/**
 * [value], or the last one it was where it is null now: what the song's page stops handing up (its tempo, its diagrams)
 * as a switch takes it, which the dock still draws while it fades out.
 */
@Composable
internal fun <T : Any> rememberLastOf(value: T?): T? {
    val last = remember { LastOf<T>() }
    value?.let { last.value = it }
    return last.value
}

/** What [rememberLastOf] last had. Not a state: it is written and read in the same composition. */
private class LastOf<T : Any>(var value: T? = null)

/** Whether going from these parts to [other] brings or takes a row, which then moves the rest. */
private fun Set<DockPart>.isRowChange(other: Set<DockPart>) = ROW_PARTS.any { (it in this) != (it in other) }

private val ROW_PARTS = setOf(DockPart.CHIP_ROW, DockPart.DIAGRAM_ROW, DockPart.METRONOME_PANEL, DockPart.METRONOME_PANEL_WITH_CARD)

/**
 * Where [rememberDockStage] is: the parts whose layout is drawn ([laidOut]) and those faded in ([arrived]), which are
 * state, and the rows and layout held while what leaves fades out and the layout last asked for, written while composing.
 */
private class DockStage(var heldRows: DockRows, var heldLayout: DockLayout?, parts: Set<DockPart>) {

    var laidOut by mutableStateOf(parts)
    var arrived by mutableStateOf(parts)
    var targetLayout: DockLayout? = heldLayout
}

/** How long what a change takes from the dock fades out before the new layout is drawn: its fast effects spring's. */
private const val DOCK_LEAVING_MILLIS = 150L

/**
 * How long the dock's height and its rows glide before what the change brings fades in: most of the way of the spring
 * they move on, so that it arrives in a dock that has come to rest.
 */
private const val DOCK_MOVING_MILLIS = 250L
