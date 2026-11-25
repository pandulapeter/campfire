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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp

/**
 * What starts the dock for one song's [content]: the instrument and the tuner where the key chip does not hold them
 * ([DockInstrumentTools]), then the key chip ([DockKeyChip]) where there is a [dockKey], [height] tall. In the diagram
 * row ([tallHeight], its height), a key chip that opens the Playing sheet stands over the tuner's pill
 * ([DockKeyAndTuner]) where the tuner is on. A change of which of these there are (read only mode switched, a switch
 * of the Dock section) crossfades as the start resizes; one that keeps them (a new key, another page) is drawn in place.
 */
@Composable
internal fun DockStart(content: DockContent, dockKey: DockKey?, height: Dp, tallHeight: Dp? = null) = AnimatedContent(
    targetState = DockStartLook(content, dockKey),
    contentKey = { it.shapeIn(tallHeight) },
    transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
    contentAlignment = Alignment.CenterStart,
) { shown ->
    Row(horizontalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP), verticalAlignment = Alignment.CenterVertically) {
        DockStartItems(content = shown.content, dockKey = shown.dockKey, height = height, tallHeight = tallHeight)
    }
}

/** What [DockStart] draws from, drawn as it was while it leaves. */
private data class DockStartLook(val content: DockContent, val dockKey: DockKey?) {

    /** Which items the start has, whatever they name: what a change of crossfades. */
    fun shapeIn(tallHeight: Dp?): List<Boolean> {
        val keyOpensPlaying = dockKey?.opensPlaying == true
        return listOf(
            content.showsInstrumentPill(keyOpensPlaying),
            content.showsTuner(keyOpensPlaying),
            dockKey != null,
            keyOpensPlaying,
            tallHeight != null && keyOpensPlaying && content.playingTools?.onTunerClicked != null && content.chordDiagrams != null,
        )
    }
}

/**
 * The items of [DockStart]: the instrument and the tuner where the key chip does not hold them
 * ([DockInstrumentTools]), then the key chip ([DockKeyChip]) where there is a [dockKey], [height] tall. In the diagram
 * row ([tallHeight], its height), a key chip that opens the Playing sheet stands over the tuner's pill
 * ([DockKeyAndTuner]) where the tuner is on.
 */
@Composable
private fun DockStartItems(content: DockContent, dockKey: DockKey?, height: Dp, tallHeight: Dp?) {
    DockInstrumentTools(content = content, keyOpensPlaying = dockKey?.opensPlaying == true)
    if (dockKey == null) return
    val onPlayingClicked = content.playingControls?.onPlayingClicked?.takeIf { dockKey.opensPlaying }
    val onTunerClicked = content.playingTools?.onTunerClicked
    val chordDiagrams = content.chordDiagrams
    if (tallHeight != null && onPlayingClicked != null && onTunerClicked != null && chordDiagrams != null) {
        DockKeyAndTuner(
            dockKey = dockKey,
            chordDiagrams = chordDiagrams,
            height = tallHeight,
            onPlayingClicked = onPlayingClicked,
            onTunerClicked = onTunerClicked,
        )
    } else {
        DockKeyChip(dockKey = dockKey, height = height, onClick = onPlayingClicked)
    }
}
