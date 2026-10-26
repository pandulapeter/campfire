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

import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SectionItem
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A change of how a song is shown (a song preference switched: chords, their diagrams, scale suggestions, the metronome)
 * narrated in three steps rather than all at once, since it moves nearly everything on the page in one go, and leaving,
 * moving, and arriving at the same time read as chaos:
 *
 * 1. [Phase.LEAVING]: what the change takes off the page ([leaving]) fades out where it stands, the page still the one
 *    before the change.
 * 2. [Phase.MOVING]: the page is the new one, and everything that stayed glides from where it was to its new place.
 * 3. [Phase.ARRIVING]: once it has, what the change brings fades in where it landed.
 *
 * Driven by the song details page, which holds the page before the change through the first step; read by the parts of
 * the song that leave or arrive.
 */
internal class SongChange {

    var phase by mutableStateOf(Phase.IDLE)

    /** What the change takes off the page, null where it takes nothing or no change is under way. */
    var leaving by mutableStateOf<LeavingParts?>(null)

    /** Whether the change puts the chords back over the lyrics, which then fade in with [arrivingAlpha]. */
    var areChordsArriving by mutableStateOf(false)

    /** How opaque what is leaving is drawn, which is state: read it where it is drawn. */
    val leavingAlpha = Animatable(1f)

    /** How opaque what arrived with the change is drawn, which is state: read it where it is drawn. */
    val arrivingAlpha = Animatable(1f)

    val isUnderWay get() = phase != Phase.IDLE

    /** How opaque the chords over the lyrics are drawn, which is state: read it where they are drawn. */
    fun chordAlpha() = when {
        leaving?.chords == true -> leavingAlpha.value
        areChordsArriving -> arrivingAlpha.value
        else -> 1f
    }

    enum class Phase { IDLE, LEAVING, MOVING, ARRIVING }
}

/** What a [SongChange] takes off the page, see [leavingPartsOf]. */
@Immutable
internal data class LeavingParts(
    /** The chords over the lyrics, the transposition and the capo, the key changes, and the lines lyrics-only mode drops. */
    val chords: Boolean,
    val chordsSection: Boolean,
    val scalesSection: Boolean,
    val instrumentPill: Boolean,
    /** The tempo and the time signature, and the changes of them further down the song. */
    val timing: Boolean,
) {

    val isEmpty get() = !chords && !chordsSection && !scalesSection && !instrumentPill && !timing
}

/** The [SongChange] of the song being drawn, one that is never under way where nobody drives one (the editor's preview). */
internal val LocalSongChange = staticCompositionLocalOf { SongChange() }

/** Fades out with the [SongChange] where [isLeaving]. */
internal fun Modifier.leavingWith(change: SongChange, isLeaving: Boolean) =
    if (isLeaving) graphicsLayer { alpha = change.leavingAlpha.value } else this

/** Fades in with the [SongChange] where [isArriving]. */
internal fun Modifier.arrivingWith(change: SongChange, isArriving: Boolean) =
    if (isArriving) graphicsLayer { alpha = change.arrivingAlpha.value } else this

/**
 * Whether the chunk of this section that holds its [items] is taken off the page with the [parts] that leave: a section
 * of its own kind, or a chunk of nothing but lines lyrics-only mode drops (tablature, grids, blocks kept as written,
 * chord rows), the section's header included only where none of its lines stays.
 */
internal fun RenderSection.leavesWith(parts: LeavingParts, items: IntRange): Boolean = when (this) {
    is RenderSection.Chords -> parts.chordsSection
    is RenderSection.Scales -> parts.scalesSection
    is RenderSection.KeyChange -> parts.chords
    is RenderSection.Timing -> parts.timing
    is RenderSection.Lines -> parts.chords && items.all { sectionItems[it].isChordsOnly() } &&
        (items.first > 0 || sectionItems.all { it.isChordsOnly() })
    is RenderSection.Comment, is RenderSection.Metadata -> false
}

private fun SectionItem.isChordsOnly() = when (this) {
    is SectionItem.RunSlot -> true
    is SectionItem.Inline -> part is RenderSection.KeyChange
    is SectionItem.Line -> when (val line = line) {
        is ChordProLine.Lyrics -> line.text.isBlank() && line.chords.isNotEmpty()
        is ChordProLine.Tab, is ChordProLine.Grid, is ChordProLine.Verbatim -> true
        ChordProLine.Blank -> false
    }
}
