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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * What starts the dock for one song's [content]: the instrument and the tuner where the key chip does not hold them
 * ([DockInstrumentTools]), then the key chip ([DockKeyChip]) where there is a [dockKey], [height] tall. In the diagram
 * row ([tallHeight], its height), a key chip that opens the Playing sheet stands over the tuner's pill
 * ([DockKeyAndTuner]) where the tuner is on. A change of which of these there are (read only mode switched, a switch
 * of the Dock section) crossfades as the start resizes; one that keeps them (a new key, another page) is drawn in place.
 *
 * In the side panel ([pillWidth], the tempo chip's width) the key chip and the tuner's pill are that wide, so the three
 * read as one column of controls, and whatever else the start holds wraps onto centred lines rather than running past
 * the panel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DockStart(
    modifier: Modifier = Modifier,
    content: DockContent,
    dockKey: DockKey?,
    height: Dp,
    tallHeight: Dp? = null,
    pillWidth: Dp? = null,
) = AnimatedContent(
    modifier = modifier,
    targetState = DockStartLook(content, dockKey),
    contentKey = { it.shapeIn(tallHeight) },
    transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
    contentAlignment = if (pillWidth == null) Alignment.CenterStart else Alignment.TopCenter,
) { shown ->
    if (pillWidth == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP), verticalAlignment = Alignment.CenterVertically) {
            DockStartItems(content = shown.content, dockKey = shown.dockKey, height = height, tallHeight = tallHeight, pillWidth = null)
        }
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            DockStartItems(content = shown.content, dockKey = shown.dockKey, height = height, tallHeight = tallHeight, pillWidth = pillWidth)
        }
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
 * ([DockKeyAndTuner]) where the tuner is on, both [pillWidth] wide where it is given.
 */
@Composable
private fun DockStartItems(content: DockContent, dockKey: DockKey?, height: Dp, tallHeight: Dp?, pillWidth: Dp?) {
    DockInstrumentTools(content = content, keyOpensPlaying = dockKey?.opensPlaying == true)
    if (dockKey == null) return
    val onPlayingClicked = content.playingControls?.onPlayingClicked?.takeIf { dockKey.opensPlaying }
    val onTunerClicked = content.playingTools?.onTunerClicked
    val chordDiagrams = content.chordDiagrams
    if (tallHeight != null && onPlayingClicked != null && onTunerClicked != null && chordDiagrams != null) {
        DockKeyAndTuner(
            modifier = if (pillWidth == null) Modifier else Modifier.width(pillWidth),
            dockKey = dockKey,
            chordDiagrams = chordDiagrams,
            height = tallHeight,
            onPlayingClicked = onPlayingClicked,
            onTunerClicked = onTunerClicked,
        )
    } else {
        DockKeyChip(modifier = if (pillWidth == null) Modifier else Modifier.width(pillWidth), dockKey = dockKey, height = height, onClick = onPlayingClicked)
    }
}
