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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * What starts the dock for one song's [content]: the key chip ([DockKeyChip]) where there is a [dockKey], [height] tall,
 * always first, then the tuner and the instrument where the key chip does not hold them ([DockInstrumentTools], the tuner
 * as a round button after a key chip that opens the Playing sheet in one row). In the diagram
 * row ([tallHeight], its height), a key chip that opens the Playing sheet stands over the tuner's pill
 * ([DockKeyAndTuner]) where the tuner is on. A change of which of these there are (read only mode switched, a switch
 * of the Dock section) crossfades as the start resizes; one that keeps them (a new key, another page) is drawn in place.
 *
 * In the side panel ([pillWidth], the tempo chip's width) everything the start holds is that wide, one column of
 * controls with the tempo chip under it: the key chip on top, then in read only mode the tuner's pill and the instrument
 * pill ([DockInstrumentTools]), as the key chip stands over the tuner's pill outside it.
 */
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KEY_AND_TUNER_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
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
        val keyHoldsTuner = content.keyHoldsTuner(keyOpensPlaying = keyOpensPlaying, isTall = tallHeight != null)
        return listOf(
            content.showsInstrumentPill(keyOpensPlaying),
            content.showsTuner(keyHoldsTuner),
            dockKey != null,
            keyOpensPlaying,
            keyHoldsTuner,
        )
    }
}

/**
 * The items of [DockStart]: the key chip ([DockKeyChip]) where there is a [dockKey], [height] tall, then the tuner and
 * the instrument where the key chip does not hold them ([DockInstrumentTools]), so that the key always reads first, in a
 * row and in the side panel's column alike. In the diagram row ([tallHeight], its height), a key chip that opens the
 * Playing sheet stands over the tuner's pill ([DockKeyAndTuner]) where the tuner is on, both [pillWidth] wide where it
 * is given; a key chip alone there is as tall as the row, the capo on a line of its own.
 */
@Composable
private fun DockStartItems(content: DockContent, dockKey: DockKey?, height: Dp, tallHeight: Dp?, pillWidth: Dp?) {
    val keyOpensPlaying = dockKey?.opensPlaying == true
    val keyHoldsTuner = content.keyHoldsTuner(keyOpensPlaying = keyOpensPlaying, isTall = tallHeight != null)
    if (dockKey != null) {
        DockStartKey(content = content, dockKey = dockKey, height = height, tallHeight = tallHeight, pillWidth = pillWidth, keyHoldsTuner = keyHoldsTuner)
    }
    DockInstrumentTools(content = content, keyOpensPlaying = keyOpensPlaying, keyHoldsTuner = keyHoldsTuner, pillWidth = pillWidth, pillHeight = height)
}

/** The key chip of [DockStartItems], over the tuner's pill where the diagram row has room for both ([DockKeyAndTuner]). */
@Composable
private fun DockStartKey(content: DockContent, dockKey: DockKey, height: Dp, tallHeight: Dp?, pillWidth: Dp?, keyHoldsTuner: Boolean) {
    val onPlayingClicked = content.playingControls?.onPlayingClicked?.takeIf { dockKey.opensPlaying }
    val onTunerClicked = content.playingTools?.onTunerClicked
    val chordDiagrams = content.chordDiagrams
    if (tallHeight != null && keyHoldsTuner && onPlayingClicked != null && onTunerClicked != null && chordDiagrams != null) {
        DockKeyAndTuner(
            modifier = if (pillWidth == null) Modifier else Modifier.width(pillWidth),
            dockKey = dockKey,
            chordDiagrams = chordDiagrams,
            height = tallHeight,
            onPlayingClicked = onPlayingClicked,
            onTunerClicked = onTunerClicked,
        )
    } else if (tallHeight != null && pillWidth == null && !content.hasInstrumentTools(keyOpensPlaying = dockKey.opensPlaying, keyHoldsTuner = keyHoldsTuner)) {
        // Alone at the start of the diagram row, it is as tall as the tempo chip at its end.
        DockKeyChip(dockKey = dockKey, height = tallHeight, onClick = onPlayingClicked, isTall = true)
    } else {
        DockKeyChip(modifier = if (pillWidth == null) Modifier else Modifier.width(pillWidth), dockKey = dockKey, height = height, onClick = onPlayingClicked)
    }
}
